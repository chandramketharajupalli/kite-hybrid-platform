package com.kitehybrid.platform.reconciliation.application;

import com.kitehybrid.platform.broker.application.BrokerReadException;
import com.kitehybrid.platform.broker.application.read.BrokerOrdersProvider;
import com.kitehybrid.platform.broker.application.read.BrokerTradesProvider;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.order.application.OrderRepository;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.reconciliation.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Read-only broker reconciliation. This class has no execution gateway dependency by design. */
public final class OrderReconciliationService {
    private final OrderRepository orders;
    private final BrokerOrdersProvider brokerOrders;
    private final BrokerTradesProvider brokerTrades;
    private final ReconciliationStore store;
    private final Clock clock;
    private final MeterRegistry metrics;

    public OrderReconciliationService(OrderRepository orders, BrokerOrdersProvider brokerOrders,
                                      BrokerTradesProvider brokerTrades, ReconciliationStore store,
                                      Clock clock, MeterRegistry metrics) {
        this.orders = Objects.requireNonNull(orders); this.brokerOrders = Objects.requireNonNull(brokerOrders);
        this.brokerTrades = Objects.requireNonNull(brokerTrades); this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock); this.metrics = Objects.requireNonNull(metrics);
    }

    public ReconciliationDecision reconcile(OrderId orderId) {
        var local = orders.find(Objects.requireNonNull(orderId)).orElseThrow(
                () -> new IllegalArgumentException("Unknown platform order"));
        var observedAt = clock.instant();
        List<BrokerOrder> observedOrders;
        List<BrokerTrade> observedTrades;
        try {
            observedOrders = List.copyOf(Objects.requireNonNull(brokerOrders.orders()));
            observedTrades = List.copyOf(Objects.requireNonNull(brokerTrades.trades()));
        } catch (RuntimeException ex) {
            metrics.counter("reconciliation.failures", "reason", ReconciliationReason.READ_FAILED.name()).increment();
            return record(local, local, ReconciliationOutcome.BROKER_STATE_UNAVAILABLE,
                    ReconciliationReason.READ_FAILED, observedAt, List.of());
        }
        final var persistedBrokerId = local.brokerOrderId().orElse(null);
        var brokerId = persistedBrokerId;
        var matches = persistedBrokerId != null
                ? observedOrders.stream().filter(o -> persistedBrokerId.equals(o.brokerOrderId())).toList()
                : local.brokerCorrelationId().map(correlation -> observedOrders.stream()
                        .filter(o -> o.correlationId().map(correlation::equals).orElse(false)).toList()).orElse(List.of());
        if (brokerId == null && local.brokerCorrelationId().isEmpty()) {
            return record(local, local, ReconciliationOutcome.AMBIGUOUS,
                    ReconciliationReason.AMBIGUOUS_CORRELATION, observedAt, List.of());
        }
        if (matches.isEmpty()) {
            return record(local, local, ReconciliationOutcome.BROKER_ORDER_MISSING,
                    ReconciliationReason.BROKER_ORDER_MISSING, observedAt, List.of());
        }
        if (matches.size() != 1 || !identityMatches(local, matches.getFirst())) {
            return record(local, local, ReconciliationOutcome.CONFLICT,
                    ReconciliationReason.ORDER_IDENTITY_CONFLICT, observedAt, List.of());
        }
        var broker = matches.getFirst();
        brokerId = matches.getFirst().brokerOrderId();
        final var matchedBrokerId = brokerId;
        var fills = observedTrades.stream().filter(t -> matchedBrokerId.equals(t.brokerOrderId())).toList();
        var uniqueFills = fills.stream().collect(java.util.stream.Collectors.toMap(
                t -> t.brokerTradeId() + "\u0000" + t.brokerOrderId(), t -> t, (first, ignored) -> first)).values().stream().toList();
        var fillQuantity = uniqueFills.stream().mapToLong(BrokerTrade::quantity).sum();
        if (fillQuantity > local.command().quantity()) {
            return record(local, local, ReconciliationOutcome.CONFLICT,
                    ReconciliationReason.INVALID_FILL_QUANTITY, observedAt, fills);
        }
        var target = targetState(local, broker, fillQuantity);
        if (target.isEmpty()) {
            return record(local, local, ReconciliationOutcome.AMBIGUOUS,
                    ReconciliationReason.UNKNOWN_BROKER_STATUS, observedAt, fills);
        }
        var next = local;
        var outcome = ReconciliationOutcome.IN_SYNC;
        var reason = ReconciliationReason.MATCHED;
        if (target.get() != local.state()) {
            try { next = local.brokerOrderId().isEmpty()
                    ? local.reconcileTo(target.get(), matchedBrokerId, observedAt)
                    : local.transitionTo(target.get(), observedAt); }
            catch (IllegalStateException ex) {
                return record(local, local, ReconciliationOutcome.CONFLICT,
                        ReconciliationReason.VERSION_CONFLICT, observedAt, fills);
            }
            outcome = outcomeFor(target.get(), broker.status(), fillQuantity);
            reason = outcome == ReconciliationOutcome.ADVANCED ? ReconciliationReason.STATE_ADVANCED
                    : outcome == ReconciliationOutcome.BROKER_REJECTED ? ReconciliationReason.BROKER_REJECTED
                    : outcome == ReconciliationOutcome.BROKER_CANCELLED ? ReconciliationReason.BROKER_CANCELLED
                    : ReconciliationReason.FILLS_OBSERVED;
        }
        if (local.brokerOrderId().isEmpty() && next == local) {
            try { next = local.reconcileTo(local.state(), matchedBrokerId, observedAt); }
            catch (IllegalStateException ex) { return record(local, local, ReconciliationOutcome.CONFLICT,
                    ReconciliationReason.VERSION_CONFLICT, observedAt, fills); }
            outcome = ReconciliationOutcome.ADVANCED;
            reason = ReconciliationReason.CORRELATION_RECOVERED;
        } else if (local.brokerOrderId().isEmpty()) {
            reason = ReconciliationReason.CORRELATION_RECOVERED;
        }
        return record(local, next, outcome, reason, observedAt, fills);
    }

    private ReconciliationDecision record(OrderRecord before, OrderRecord after, ReconciliationOutcome outcome,
                                          ReconciliationReason reason, Instant observedAt, List<BrokerTrade> fills) {
        var decision = new ReconciliationDecision(UUID.randomUUID(), before.id(), before.state(),
                Optional.of(after.state()), outcome, reason, observedAt, before.version());
        if (!store.apply(before, after, decision, fills)) {
            metrics.counter("reconciliation.conflicts", "reason", ReconciliationReason.VERSION_CONFLICT.name()).increment();
            return new ReconciliationDecision(decision.reconciliationId(), decision.orderId(), decision.stateBefore(),
                    Optional.of(before.state()), ReconciliationOutcome.CONFLICT, ReconciliationReason.VERSION_CONFLICT,
                    observedAt, before.version());
        }
        metrics.counter("reconciliation.runs", "outcome", outcome.name()).increment();
        metrics.counter("reconciliation.orders", "outcome", outcome.name()).increment();
        if (outcome == ReconciliationOutcome.ADVANCED || outcome == ReconciliationOutcome.FILLED
                || outcome == ReconciliationOutcome.PARTIALLY_FILLED || outcome == ReconciliationOutcome.BROKER_REJECTED
                || outcome == ReconciliationOutcome.BROKER_CANCELLED)
            metrics.counter("reconciliation.advanced", "outcome", outcome.name()).increment();
        if (outcome == ReconciliationOutcome.AMBIGUOUS) metrics.counter("reconciliation.ambiguous").increment();
        if (outcome == ReconciliationOutcome.CONFLICT) metrics.counter("reconciliation.conflicts").increment();
        if (outcome == ReconciliationOutcome.BROKER_STATE_UNAVAILABLE)
            metrics.counter("reconciliation.failures", "reason", reason.name()).increment();
        return decision;
    }

    private static Optional<OrderState> targetState(OrderRecord local, BrokerOrder broker, long fillQuantity) {
        if (fillQuantity == local.command().quantity() || broker.status() == TradingReadTypes.OrderStatus.FILLED)
            return Optional.of(OrderState.FILLED);
        if (fillQuantity > 0 || broker.status() == TradingReadTypes.OrderStatus.PARTIALLY_FILLED)
            return Optional.of(OrderState.PARTIALLY_FILLED);
        return switch (broker.status()) {
            case REJECTED -> Optional.of(OrderState.REJECTED);
            case CANCELLED -> Optional.of(OrderState.CANCELLED);
            case OPEN, OPEN_PENDING, RECEIVED, VALIDATION_PENDING ->
                    Optional.of(local.state() == OrderState.SUBMITTING ? OrderState.SUBMITTED : OrderState.OPEN);
            case CANCEL_PENDING -> Optional.of(OrderState.CANCEL_PENDING);
            default -> Optional.empty();
        };
    }

    private static ReconciliationOutcome outcomeFor(OrderState target, TradingReadTypes.OrderStatus status, long fills) {
        if (status == TradingReadTypes.OrderStatus.REJECTED) return ReconciliationOutcome.BROKER_REJECTED;
        if (status == TradingReadTypes.OrderStatus.CANCELLED) return ReconciliationOutcome.BROKER_CANCELLED;
        if (target == OrderState.FILLED) return ReconciliationOutcome.FILLED;
        if (target == OrderState.PARTIALLY_FILLED || fills > 0) return ReconciliationOutcome.PARTIALLY_FILLED;
        return ReconciliationOutcome.ADVANCED;
    }

    private static boolean identityMatches(OrderRecord local, BrokerOrder broker) {
        var c = local.command();
        return broker.instrumentId().equals(c.instrumentId())
                && broker.side() == side(c.side()) && broker.quantity() == c.quantity()
                && broker.orderType() == orderType(c.orderType()) && broker.product() == product(c.product())
                && broker.validity() == validity(c.validity())
                && (c.limitPrice().isEmpty() || broker.price().compareTo(c.limitPrice().get()) == 0);
    }
    private static TradingReadTypes.Side side(OrderSide s) { return s == OrderSide.BUY ? TradingReadTypes.Side.BUY : TradingReadTypes.Side.SELL; }
    private static TradingReadTypes.OrderType orderType(OrderType t) { return switch (t) { case MARKET -> TradingReadTypes.OrderType.MARKET; case LIMIT -> TradingReadTypes.OrderType.LIMIT; case STOP_LIMIT -> TradingReadTypes.OrderType.STOP_LIMIT; case STOP_MARKET -> TradingReadTypes.OrderType.STOP_MARKET; }; }
    private static TradingReadTypes.Product product(OrderProduct p) { return p == OrderProduct.DELIVERY ? TradingReadTypes.Product.DELIVERY : TradingReadTypes.Product.UNKNOWN; }
    private static TradingReadTypes.Validity validity(OrderValidity v) { return v == OrderValidity.DAY ? TradingReadTypes.Validity.DAY : TradingReadTypes.Validity.UNKNOWN; }
}
