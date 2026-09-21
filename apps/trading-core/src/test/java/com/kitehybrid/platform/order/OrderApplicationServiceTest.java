package com.kitehybrid.platform.order;

import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.instrument.infrastructure.InMemoryInstrumentRegistry;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-21T05:00:00Z");
    private final InMemoryInstrumentRegistry instruments = new InMemoryInstrumentRegistry();
    private final FakeRepository repository = new FakeRepository();
    private final FakeGateway gateway = new FakeGateway();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final Instrument instrument = Instrument.create(new BrokerInstrumentId("ZERODHA", "123"), "ABC", "NSE",
            "CASH", InstrumentType.CASH, Optional.empty(), Optional.empty(), new BigDecimal("0.05"), 1);

    OrderApplicationServiceTest() {
        instruments.replace(List.of(instrument), NOW);
    }

    @Test void validMarketAndLimitCommandsPersistValidatedWithPlatformIdentity() {
        var service = service(false);
        var market = service.place(place("market", OrderType.MARKET, Optional.empty(), Optional.empty()));
        var limit = service.place(place("limit", OrderType.LIMIT, Optional.of(new BigDecimal("10.25")), Optional.empty()));
        assertEquals(OrderState.VALIDATED, market.state()); assertEquals(OrderState.VALIDATED, limit.state());
        assertNotEquals(market.id(), limit.id()); assertEquals(2, repository.records.size());
    }

    @Test void repeatedIdenticalPlaceReturnsTheSameDurableRecordAndConflictFails() {
        var service = service(false); var command = place("same", OrderType.MARKET, Optional.empty(), Optional.empty());
        var first = service.place(command); var second = service.place(command);
        assertEquals(first.id(), second.id()); assertEquals(1, repository.records.size());
        var conflict = place("same", OrderType.LIMIT, Optional.of(BigDecimal.ONE), Optional.empty());
        assertThrows(OrderCommandValidationException.class, () -> service.place(conflict));
    }

    @Test void validationRejectsBadPriceCombinationsAndUnknownOrNonLotQuantity() {
        var service = service(false);
        assertThrows(OrderCommandValidationException.class, () -> service.place(place("bad-market", OrderType.MARKET,
                Optional.of(BigDecimal.ONE), Optional.empty())));
        assertThrows(OrderCommandValidationException.class, () -> service.place(place("bad-limit", OrderType.LIMIT,
                Optional.empty(), Optional.empty())));
        assertThrows(OrderCommandValidationException.class, () -> service.place(place("bad-stop", OrderType.STOP_MARKET,
                Optional.empty(), Optional.empty())));
        assertThrows(OrderCommandValidationException.class, () -> service.place(new PlaceOrder("bad-lot",
                new com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId(UUID.randomUUID()), OrderSide.BUY,
                1, OrderType.MARKET, OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0,
                OrderVariety.REGULAR)));
    }

    @Test void validatedOrderCannotExecuteUntilRiskApprovalAndDisabledGateRemainsSafe() {
        var disabled = service(false); var record = disabled.place(place("disabled", OrderType.MARKET, Optional.empty(), Optional.empty()));
        assertThrows(OrderExecutionException.class, () -> disabled.executeRiskApproved(record.id()));
        assertEquals(0, gateway.placeCalls.get());
        assertEquals(OrderState.VALIDATED, repository.find(record.id()).orElseThrow().state());

        var enabled = service(true); var accepted = enabled.place(place("enabled", OrderType.MARKET, Optional.empty(), Optional.empty()));
        assertThrows(OrderCommandValidationException.class, () -> enabled.executeRiskApproved(accepted.id()));
        assertEquals(OrderState.VALIDATED, repository.find(accepted.id()).orElseThrow().state());
        approve(accepted);
        var submitted = enabled.executeRiskApproved(accepted.id());
        assertEquals(OrderState.SUBMITTED, submitted.state()); assertEquals(Optional.of("fake-1"), submitted.brokerOrderId());
        assertEquals(1, gateway.placeCalls.get());
    }

    @Test void ambiguousSubmissionRemainsSubmittingAndCannotAutomaticallyResubmit() {
        gateway.failure = new OrderExecutionException(OrderExecutionException.Category.AMBIGUOUS);
        var service = service(true); var record = service.place(place("ambiguous", OrderType.MARKET, Optional.empty(), Optional.empty()));
        approve(record);
        assertThrows(OrderExecutionException.class, () -> service.executeRiskApproved(record.id()));
        assertEquals(OrderState.SUBMITTING, repository.find(record.id()).orElseThrow().state());
        assertThrows(OrderCommandValidationException.class, () -> service.executeRiskApproved(record.id()));
        assertEquals(1, gateway.placeCalls.get());
    }

    @Test void cancelIsPlatformIdentityBasedAndRepeatedCancelIsIdempotent() {
        var service = service(true); var created = service.place(place("cancel", OrderType.MARKET, Optional.empty(), Optional.empty()));
        approve(created);
        var submitted = service.executeRiskApproved(created.id());
        var open = submitted.transitionTo(OrderState.OPEN, NOW); repository.records.put(open.id(), open);
        var cancelled = service.cancel(new CancelOrder("cancel-command", open.id()));
        assertEquals(OrderState.CANCELLED, cancelled.state()); assertEquals(1, gateway.cancelCalls.get());
        assertEquals(cancelled.id(), service.cancel(new CancelOrder("cancel-command", open.id())).id());
        assertEquals(1, gateway.cancelCalls.get());
    }

    @Test void executionRequiresKnownPlatformOrderIdentityAndCannotUseBrokerId() {
        var service = service(true);
        assertThrows(OrderCommandValidationException.class,
                () -> service.executeRiskApproved(new OrderId(UUID.randomUUID())));
        assertEquals(0, gateway.placeCalls.get());
    }

    @Test void rejectedRiskDecisionCannotBeExecuted() {
        var service = service(true);
        var validated = service.place(place("rejected-risk", OrderType.MARKET, Optional.empty(), Optional.empty()));
        var rejected = validated.transitionTo(OrderState.REJECTED, NOW);
        assertTrue(repository.compareAndSet(validated, rejected));
        assertThrows(OrderCommandValidationException.class, () -> service.executeRiskApproved(validated.id()));
        assertEquals(0, gateway.placeCalls.get());
    }

    private OrderApplicationService service(boolean enabled) {
        return new OrderApplicationService(repository, new OrderCommandValidator(instruments), gateway,
                new OrderExecutionProperties(enabled), clock, new SimpleMeterRegistry());
    }
    private void approve(OrderRecord validated) {
        var approved = validated.transitionTo(OrderState.RISK_APPROVED, NOW);
        assertTrue(repository.compareAndSet(validated, approved));
    }
    private PlaceOrder place(String key, OrderType type, Optional<BigDecimal> price, Optional<BigDecimal> trigger) {
        return new PlaceOrder(key, instrument.id(), OrderSide.BUY, 1, type,
                OrderProduct.DELIVERY, OrderValidity.DAY, price, trigger, 0, OrderVariety.REGULAR);
    }
    private static final class FakeGateway implements OrderExecutionGateway {
        final AtomicInteger placeCalls = new AtomicInteger(); final AtomicInteger cancelCalls = new AtomicInteger();
        OrderExecutionException failure;
        public String place(OrderRecord order) { placeCalls.incrementAndGet(); if (failure != null) throw failure; return "fake-1"; }
        public void modify(OrderRecord order, ModifyOrder command) {}
        public void cancel(OrderRecord order, CancelOrder command) { cancelCalls.incrementAndGet(); if (failure != null) throw failure; }
    }
    private static final class FakeRepository implements OrderRepository {
        final Map<OrderId, OrderRecord> records = new HashMap<>(); final Map<String, String> keys = new HashMap<>();
        public synchronized IdempotencyClaim claimIdempotency(String key, String fingerprint, OrderId id) {
            var existing = keys.putIfAbsent(key, fingerprint + "|" + id.value());
            if (existing == null) return IdempotencyClaim.CREATED;
            return existing.startsWith(fingerprint + "|") ? IdempotencyClaim.EXISTING : IdempotencyClaim.CONFLICT;
        }
        public synchronized IdempotencyClaim createIfAbsent(OrderRecord record, String fingerprint) {
            var existing = keys.get(record.command().idempotencyKey());
            if (existing == null) { keys.put(record.command().idempotencyKey(), fingerprint + "|" + record.id().value()); records.put(record.id(), record); return IdempotencyClaim.CREATED; }
            return existing.startsWith(fingerprint + "|") ? IdempotencyClaim.EXISTING : IdempotencyClaim.CONFLICT;
        }
        public synchronized void create(OrderRecord record) { records.put(record.id(), record); }
        public synchronized Optional<OrderRecord> find(OrderId id) { return Optional.ofNullable(records.get(id)); }
        public synchronized Optional<OrderRecord> findByIdempotencyKey(String key) {
            var value = keys.get(key);
            if (value == null) return Optional.empty();
            var id = value.substring(value.indexOf('|') + 1);
            return records.values().stream().filter(r -> r.id().value().toString().equals(id)).findFirst();
        }
        public synchronized boolean compareAndSet(OrderRecord expected, OrderRecord next) {
            var current = records.get(expected.id());
            if (current == null || current.version() != expected.version()) return false;
            records.put(next.id(), next); return true;
        }
        public boolean attachBrokerOrderId(OrderRecord expected, OrderRecord next) { return compareAndSet(expected, next); }
    }
}
