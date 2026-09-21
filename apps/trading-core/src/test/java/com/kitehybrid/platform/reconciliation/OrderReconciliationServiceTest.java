package com.kitehybrid.platform.reconciliation;

import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.order.application.OrderRepository;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.reconciliation.application.*;
import com.kitehybrid.platform.reconciliation.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import com.kitehybrid.platform.shared.domain.BrokerCorrelationId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.kitehybrid.platform.broker.domain.read.TradingReadTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderReconciliationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-21T05:00:00Z");
    private static final InstrumentId INSTRUMENT = new InstrumentId(UUID.randomUUID());

    @Test void knownBrokerOrderAdvancesAndReconciliationHasNoExecutionDependency() {
        var local = order(OrderState.SUBMITTING, Optional.of("broker-1"));
        var repo = new Repo(local); var store = new Store(repo);
        var broker = broker(OrderStatus.OPEN, 0);
        var service = new OrderReconciliationService(repo, () -> List.of(broker), List::of, store,
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        var result = service.reconcile(local.id());
        assertEquals(ReconciliationOutcome.ADVANCED, result.outcome());
        assertEquals(OrderState.SUBMITTED, result.stateAfter().orElseThrow());
        assertEquals(1, store.calls);
    }

    @Test void ambiguousWithoutPersistedBrokerIdentityNeverGuesses() {
        var local = order(OrderState.SUBMITTING, Optional.empty());
        var store = new Store();
        var service = new OrderReconciliationService(new Repo(local), () -> List.of(broker(OrderStatus.OPEN, 0)),
                List::of, store, Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        assertEquals(ReconciliationOutcome.AMBIGUOUS, service.reconcile(local.id()).outcome());
        assertEquals(0, store.stateChanges);
    }

    @Test void duplicateTradesArePassedToDurableStoreForDatabaseDeduplication() {
        var local = order(OrderState.SUBMITTED, Optional.of("broker-1"));
        var trade = new BrokerTrade("trade-1", "broker-1", Optional.empty(), INSTRUMENT, Side.BUY,
                Product.DELIVERY, 1, BigDecimal.TEN, NOW, Optional.of(NOW));
        var store = new Store();
        var service = new OrderReconciliationService(new Repo(local), () -> List.of(broker(OrderStatus.FILLED, 1)),
                () -> List.of(trade, trade), store, Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        assertEquals(ReconciliationOutcome.FILLED, service.reconcile(local.id()).outcome());
        assertEquals(2, store.lastTrades);
    }

    @Test void correlationMatchAttachesBrokerIdentityWithoutResubmission() {
        var correlation = BrokerCorrelationId.generate();
        var base = order(OrderState.SUBMITTING, Optional.empty());
        var local = new OrderRecord(base.id(), base.command(), OrderState.SUBMITTING, Optional.empty(),
                Optional.of(correlation), Optional.empty(), NOW, NOW, 1);
        var observed = new BrokerOrder("broker-correlated", Optional.empty(), Optional.empty(), INSTRUMENT, Side.BUY,
                TradingReadTypes.OrderType.MARKET, Product.DELIVERY, Validity.DAY, Variety.REGULAR, OrderStatus.OPEN,
                1, 0, 1, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, NOW, Optional.of(NOW),
                Optional.of(correlation), Optional.of(NOW));
        var repo = new Repo(local); var store = new Store(repo);
        var service = new OrderReconciliationService(repo, () -> List.of(observed), List::of, store,
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        var decision = service.reconcile(local.id());
        assertEquals(ReconciliationReason.CORRELATION_RECOVERED, decision.reason());
        assertEquals(Optional.of("broker-correlated"), repo.find(local.id()).orElseThrow().brokerOrderId());
    }

    @Test void multipleCorrelationMatchesAndCharacteristicMismatchFailClosed() {
        var correlation = BrokerCorrelationId.generate(); var base = order(OrderState.SUBMITTING, Optional.empty());
        var local = new OrderRecord(base.id(), base.command(), base.state(), Optional.empty(), Optional.of(correlation),
                Optional.empty(), NOW, NOW, 1);
        var first = new BrokerOrder("broker-a", Optional.empty(), Optional.empty(), INSTRUMENT, Side.BUY,
                TradingReadTypes.OrderType.MARKET, Product.DELIVERY, Validity.DAY, Variety.REGULAR, OrderStatus.OPEN,
                1, 0, 1, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, NOW, Optional.of(NOW), Optional.of(correlation), Optional.of(NOW));
        var repo = new Repo(local); var store = new Store(repo);
        var multiple = new OrderReconciliationService(repo, () -> List.of(first, first), List::of, store,
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        assertEquals(ReconciliationOutcome.CONFLICT, multiple.reconcile(local.id()).outcome());
        var wrong = new BrokerOrder("broker-b", Optional.empty(), Optional.empty(), new InstrumentId(UUID.randomUUID()), Side.BUY,
                TradingReadTypes.OrderType.MARKET, Product.DELIVERY, Validity.DAY, Variety.REGULAR, OrderStatus.OPEN,
                1, 0, 1, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, NOW, Optional.of(NOW), Optional.of(correlation), Optional.of(NOW));
        var mismatch = new OrderReconciliationService(new Repo(local), () -> List.of(wrong), List::of, new Store(),
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        assertEquals(ReconciliationOutcome.CONFLICT, mismatch.reconcile(local.id()).outcome());
    }

    private static BrokerOrder broker(OrderStatus status, long filled) {
        return new BrokerOrder("broker-1", Optional.empty(), Optional.empty(), INSTRUMENT, Side.BUY,
                TradingReadTypes.OrderType.MARKET, Product.DELIVERY, Validity.DAY, Variety.REGULAR, status, 1, filled,
                1 - filled, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, NOW, Optional.of(NOW), Optional.of(NOW));
    }
    private static OrderRecord order(OrderState state, Optional<String> broker) {
        var command = new PlaceOrder("reconcile-" + UUID.randomUUID(), INSTRUMENT, OrderSide.BUY, 1,
                com.kitehybrid.platform.order.domain.command.OrderType.MARKET, OrderProduct.DELIVERY, OrderValidity.DAY, Optional.empty(), Optional.empty(), 0, OrderVariety.REGULAR);
        return new OrderRecord(new OrderId(UUID.randomUUID()), command, state, broker, Optional.empty(), NOW, NOW, 1);
    }
    private static final class Repo implements OrderRepository {
        private OrderRecord record; Repo(OrderRecord record) { this.record = record; }
        public IdempotencyClaim claimIdempotency(String k, String f, OrderId i) { return IdempotencyClaim.CREATED; }
        public IdempotencyClaim createIfAbsent(OrderRecord r, String f) { record = r; return IdempotencyClaim.CREATED; }
        public void create(OrderRecord r) { record = r; }
        public Optional<OrderRecord> find(OrderId id) { return record.id().equals(id) ? Optional.of(record) : Optional.empty(); }
        public Optional<OrderRecord> findByIdempotencyKey(String key) { return Optional.empty(); }
        public boolean compareAndSet(OrderRecord e, OrderRecord n) { if (record.version() != e.version()) return false; record = n; return true; }
        public boolean attachBrokerOrderId(OrderRecord e, OrderRecord n) { return compareAndSet(e, n); }
    }
    private static final class Store implements ReconciliationStore {
        private final Repo repo;
        Store() { this(null); }
        Store(Repo repo) { this.repo = repo; }
        int calls; int stateChanges; int lastTrades;
        public boolean apply(OrderRecord e, OrderRecord n, ReconciliationDecision d, List<BrokerTrade> t) {
            calls++; lastTrades = t.size(); if (e.state() != n.state()) stateChanges++; if (repo != null) repo.record = n; return true;
        }
    }
}
