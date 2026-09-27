package com.kitehybrid.platform.order.application;

import com.kitehybrid.platform.shared.application.ExecutionSession;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.OrderCommandValidationException;
import com.kitehybrid.platform.risk.application.RiskDecisionStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.function.*;
import static com.kitehybrid.platform.order.application.ExecutionDenialReason.*;

/** Broker-independent authorization; observes state and records decisions, never dispatches. */
public final class ExecutionSafetyPolicy {
    private final OrderExecutionProperties p;
    private final RuntimeExecutionArming arm;
    private final BooleanSupplier stop;
    private final ExecutionSession session;
    private final RiskDecisionStore risks;
    private final InstrumentRegistry instruments;
    private final LatestMarketDataStore market;
    private final Supplier<MarketDataHealth> health;
    private final OrderRepository orders;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final ExecutionAuthorizationAuditStore audit;
    private final AdditionalExecutionChecks additional;

    public ExecutionSafetyPolicy(OrderExecutionProperties p, RuntimeExecutionArming arm, BooleanSupplier stop,
            ExecutionSession session, RiskDecisionStore risks, InstrumentRegistry instruments,
            LatestMarketDataStore market, Supplier<MarketDataHealth> health, OrderRepository orders,
            Clock clock, MeterRegistry metrics, ExecutionAuthorizationAuditStore audit) {
        this(p, arm, stop, session, risks, instruments, market, health, orders, clock, metrics, audit, order -> price -> java.util.Map.of());
    }
    public ExecutionSafetyPolicy(OrderExecutionProperties p, RuntimeExecutionArming arm, BooleanSupplier stop,
            ExecutionSession session, RiskDecisionStore risks, InstrumentRegistry instruments,
            LatestMarketDataStore market, Supplier<MarketDataHealth> health, OrderRepository orders,
            Clock clock, MeterRegistry metrics, ExecutionAuthorizationAuditStore audit, AdditionalExecutionChecks additional) {
        this.p=p; this.arm=arm; this.stop=stop; this.session=session; this.risks=risks;
        this.instruments=instruments; this.market=market; this.health=health; this.orders=orders;
        this.clock=clock; this.metrics=metrics; this.audit=java.util.Objects.requireNonNull(audit);
        this.additional=java.util.Objects.requireNonNull(additional);
    }
    public ExecutionAuthorizationDecision evaluate(OrderRecord order) {
        var reason=check(order, false);
        return record(order, reason, clock.instant());
    }
    /** Called after durable admission and again immediately before transport dispatch. */
    public void validateDispatch(OrderRecord approved, OrderRecord submitting) {
        var current=orders.find(submitting.id()).orElse(null);
        var reason = current == null || current.version()!=submitting.version() || current.state()!=OrderState.SUBMITTING
                || !current.command().equals(submitting.command()) || !current.brokerCorrelationId().equals(submitting.brokerCorrelationId()) ? ORDER_VERSION_CHANGED : check(approved, true);
        if (reason != NONE) {
            record(submitting, reason, clock.instant());
            throw new OrderCommandValidationException(reason.name());
        }
    }
    public ExecutionAuthorizationDecision deny(OrderRecord order, ExecutionDenialReason reason) {
        return record(order, reason, clock.instant());
    }
    private ExecutionAuthorizationDecision record(OrderRecord order, ExecutionDenialReason reason, Instant now) {
        var decision=new ExecutionAuthorizationDecision(reason==NONE, reason, now, order.id(), order.version(), p.auditVersion());
        audit.record(decision);
        metrics.counter("execution.authorization", "result", decision.allowed()?"allowed":"denied").increment();
        if (!decision.allowed()) metrics.counter("execution.denied", "reason", reason.name()).increment();
        return decision;
    }
    /** Read-only evidence; no authorization audit write, transition, or broker invocation. */
    public ExecutionReadiness inspect(OrderRecord order) { return inspect(order, false); }

    private ExecutionDenialReason check(OrderRecord order, boolean dispatch) {
        if (!p.enabled()) return EXECUTION_DISABLED;
        if (!session.enabled() || session.executionIdentity().isEmpty()) return AUTHENTICATION_UNAVAILABLE;
        if (!arm.armed(clock.instant())) return DISARMED;
        if (stop.getAsBoolean()) return EMERGENCY_STOP;
        return inspect(order, dispatch).reason();
    }
    private ExecutionReadiness inspect(OrderRecord o, boolean dispatch) {
        var gates = new java.util.LinkedHashMap<ExecutionReadiness.Gate, ExecutionDenialReason>();
        try {
            // Sample freshness AFTER potentially blocking evidence reads.
            var r = risks.find(o.id()).orElse(null);
            boolean instrumentAllowed = instruments.findById(o.command().instrumentId()).isPresent()
                    && p.allowedInstruments().contains(o.command().instrumentId());
            boolean reconciliationClear = (dispatch || !orders.hasDangerousUnresolvedOrders()) && !orders.hasBlockingExposureExcept(o.id());
            var additionalEvidence = additional.prepare(o);
            var h = health.get();
            var t = market.latest(o.command().instrumentId()).orElse(null);
            BigDecimal price = t == null ? BigDecimal.ZERO : o.command().limitPrice().map(v -> v.max(t.lastPrice())).orElse(t.lastPrice());
            var extra = additionalEvidence.apply(price);
            var now = clock.instant();
            boolean authenticated = session.enabled() && session.executionIdentity().isPresent();
            boolean armed = arm.armed(now);
            put(gates, ExecutionReadiness.Gate.EXECUTION_CAPABILITY_CONFIGURED, p.enabled(), EXECUTION_DISABLED);
            put(gates, ExecutionReadiness.Gate.AUTHENTICATED, authenticated, AUTHENTICATION_UNAVAILABLE);
            put(gates, ExecutionReadiness.Gate.RUNTIME_ARMED, armed, DISARMED);
            put(gates, ExecutionReadiness.Gate.SESSION_BOUND, armed && authenticated, DISARMED);
            put(gates, ExecutionReadiness.Gate.EMERGENCY_STOP_CLEAR, !stop.getAsBoolean(), EMERGENCY_STOP);
            put(gates, ExecutionReadiness.Gate.ORDER_RISK_APPROVED, o.state() == OrderState.RISK_APPROVED, INVALID_ORDER_STATE);
            var riskReason = r == null || !r.approved() ? RISK_APPROVAL_MISSING
                    : !r.orderId().equals(o.id()) || r.orderVersion() != o.version()-1 ? ORDER_VERSION_CHANGED
                    : !r.policyVersion().equals(p.riskPolicyVersion()) ? RISK_POLICY_MISMATCH
                    : !fresh(r.evaluatedAt(), now, p.riskDecisionMaxAge()) ? RISK_APPROVAL_EXPIRED : NONE;
            gates.put(ExecutionReadiness.Gate.RISK_DECISION_CURRENT, riskReason);
            put(gates, ExecutionReadiness.Gate.INSTRUMENT_ALLOWED, instrumentAllowed, INSTRUMENT_NOT_ALLOWED);
            put(gates, ExecutionReadiness.Gate.QUANTITY_WITHIN_CAP, o.command().quantity() <= p.maxQuantity(), QUANTITY_CAP_EXCEEDED);
            put(gates, ExecutionReadiness.Gate.CORRELATION_PRESENT, o.brokerCorrelationId().isPresent(), CORRELATION_MISSING);
            put(gates, ExecutionReadiness.Gate.RECONCILIATION_CLEAR, reconciliationClear, RECONCILIATION_REQUIRED);
            put(gates, ExecutionReadiness.Gate.MARKET_DATA_HEALTHY, h != null && h.connectionState() == MarketDataGateway.State.CONNECTED
                    && h.status() == MarketDataHealth.Status.FRESH && h.reason() == MarketDataHealth.Reason.NONE
                    && h.desiredSubscriptions() > 0 && h.activeSubscriptions() == h.desiredSubscriptions()
                    && t != null && t.lastPrice().signum() > 0, MARKET_DATA_UNAVAILABLE);
            put(gates, ExecutionReadiness.Gate.MARKET_DATA_FRESH, t != null && fresh(t.receivedAt(), now, p.marketDataMaxAge())
                    && t.exchangeTimestamp().filter(at -> !fresh(at, now, p.marketDataMaxAge())).isEmpty(), MARKET_DATA_STALE);
            put(gates, ExecutionReadiness.Gate.NOTIONAL_WITHIN_CAP, price.signum() > 0
                    && price.multiply(BigDecimal.valueOf(o.command().quantity())).compareTo(p.maxNotional()) <= 0, NOTIONAL_CAP_EXCEEDED);
            gates.putAll(extra);
        } catch (RuntimeException unavailable) {
            for (var gate : ExecutionReadiness.Gate.values()) gates.put(gate, EVIDENCE_UNAVAILABLE);
        }
        return new ExecutionReadiness(gates);
    }
    private static void put(java.util.Map<ExecutionReadiness.Gate, ExecutionDenialReason> gates,
            ExecutionReadiness.Gate gate, boolean passed, ExecutionDenialReason reason) {
        gates.put(gate, passed ? NONE : reason);
    }
    private static boolean fresh(Instant observed, Instant now, Duration age) {
        return !observed.isAfter(now) && Duration.between(observed,now).compareTo(age)<0;
    }
}
