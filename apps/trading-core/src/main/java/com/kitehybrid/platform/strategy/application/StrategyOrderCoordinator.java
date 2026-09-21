package com.kitehybrid.platform.strategy.application;

import com.kitehybrid.platform.config.TradingProperties;
import com.kitehybrid.platform.order.application.OrderApplicationService;
import com.kitehybrid.platform.order.domain.Signal;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.risk.application.RiskService;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import com.kitehybrid.platform.strategy.domain.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

/** Strategy-to-order proposal boundary. It deliberately stops after optional risk evaluation. */
public final class StrategyOrderCoordinator {
    private final StrategyEvaluationStore evaluations; private final OrderApplicationService orders;
    private final Optional<RiskService> risk; private final Clock clock; private final boolean halted;
    private final MeterRegistry metrics;
    public StrategyOrderCoordinator(StrategyEvaluationStore evaluations, OrderApplicationService orders,
                                    Optional<RiskService> risk, Clock clock, TradingProperties trading,
                                    MeterRegistry metrics) {
        this.evaluations = Objects.requireNonNull(evaluations); this.orders = Objects.requireNonNull(orders);
        this.risk = Objects.requireNonNull(risk); this.clock = Objects.requireNonNull(clock);
        this.halted = trading.emergencyStop(); this.metrics = Objects.requireNonNull(metrics);
    }
    public StrategyEvaluation evaluate(String eventKey, Strategy strategy, StrategyInput input) {
        Objects.requireNonNull(strategy); Objects.requireNonNull(input);
        var definition = strategy.definition(); var signal = strategy.evaluate(input);
        var deterministicSignal = new Signal(new SignalId(deterministicId(definition.id().value() + ":" + definition.version() + ":" + eventKey + ":signal")),
                definition.id(), signal.instrumentId(), signal.side(), signal.quantity(), signal.referencePrice(), signal.timestamp(),
                definition.version(), signal.reason());
        var intentId = deterministicId(definition.id().value() + ":" + definition.version() + ":" + eventKey + ":intent");
        var actionable = deterministicSignal.side() != Signal.Side.HOLD && !halted && input.fresh();
        var draft = new StrategyEvaluation(eventKey, definition.id(), definition.version(), deterministicSignal,
                actionable ? Optional.of(new OrderIntentId(intentId)) : Optional.empty(), Optional.empty(), input.evaluatedAt());
        var claim = evaluations.claim(draft);
        if (claim == StrategyEvaluationStore.Claim.CONFLICT) throw new IllegalStateException("STRATEGY_EVALUATION_CONFLICT");
        var persisted = claim == StrategyEvaluationStore.Claim.EXISTING
                ? evaluations.find(eventKey, definition.id().value(), definition.version()).orElseThrow() : draft;
        if (claim == StrategyEvaluationStore.Claim.EXISTING && persisted.orderId().isPresent()) return persisted;
        if (claim == StrategyEvaluationStore.Claim.EXISTING) {
            deterministicSignal = persisted.signal();
            actionable = deterministicSignal.side() != Signal.Side.HOLD && !halted && input.fresh();
            intentId = persisted.intentId().map(OrderIntentId::value).orElse(intentId);
        }
        metrics.counter("strategy.evaluations", "strategy", definition.id().value()).increment();
        if (!actionable) {
            metrics.counter("strategy.no_action", "reason", halted ? "EMERGENCY_STOP" : deterministicSignal.reason().name()).increment();
            return draft;
        }
        var intent = new TradeIntent(new OrderIntentId(intentId), definition.id(), definition.version(), signal.instrumentId(),
                signal.side(), signal.quantity(), OrderType.MARKET, Optional.empty(), signal.timestamp(), signal.id());
        metrics.counter("strategy.signals", "action", signal.side().name()).increment();
        metrics.counter("strategy.intents", "action", signal.side().name()).increment();
        var order = orders.place(new PlaceOrder(orderKey(definition, eventKey), intent.instrumentId(),
                intent.side() == Signal.Side.BUY ? OrderSide.BUY : OrderSide.SELL, intent.quantity(), intent.orderType(),
                OrderProduct.DELIVERY, OrderValidity.DAY, intent.limitPrice(), Optional.empty(), 0, OrderVariety.REGULAR));
        var completed = new StrategyEvaluation(eventKey, definition.id(), definition.version(), deterministicSignal,
                Optional.of(intent.id()), Optional.of(order.id()), input.evaluatedAt());
        if (!evaluations.attachOrder(persisted, completed)) throw new IllegalStateException("STRATEGY_EVALUATION_VERSION_CONFLICT");
        metrics.counter("strategy.orders_proposed").increment();
        risk.ifPresent(r -> r.evaluate(order.id()));
        return completed;
    }
    private static String orderKey(StrategyDefinition definition, String eventKey) {
        var raw = "strategy:" + definition.id().value() + ":" + definition.version() + ":" + eventKey;
        if (raw.length() <= 128) return raw;
        return "strategy:" + HexFormat.of().formatHex(sha256(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 96);
    }
    private static byte[] sha256(byte[] value) { try { return MessageDigest.getInstance("SHA-256").digest(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static UUID deterministicId(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }
}
