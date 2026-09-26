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

    public ExecutionSafetyPolicy(OrderExecutionProperties p, RuntimeExecutionArming arm, BooleanSupplier stop,
            ExecutionSession session, RiskDecisionStore risks, InstrumentRegistry instruments,
            LatestMarketDataStore market, Supplier<MarketDataHealth> health, OrderRepository orders,
            Clock clock, MeterRegistry metrics, ExecutionAuthorizationAuditStore audit) {
        this.p=p; this.arm=arm; this.stop=stop; this.session=session; this.risks=risks;
        this.instruments=instruments; this.market=market; this.health=health; this.orders=orders;
        this.clock=clock; this.metrics=metrics; this.audit=java.util.Objects.requireNonNull(audit);
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
    private ExecutionDenialReason check(OrderRecord o, boolean dispatch) {
        if (!p.enabled()) return EXECUTION_DISABLED;
        if (!session.enabled() || session.executionIdentity().isEmpty()) return AUTHENTICATION_UNAVAILABLE;
        if (!arm.armed(clock.instant())) return DISARMED;
        if (stop.getAsBoolean()) return EMERGENCY_STOP;
        if (o.state()!=OrderState.RISK_APPROVED) return INVALID_ORDER_STATE;
        var r=risks.find(o.id()).orElse(null);
        if (r==null || !r.approved()) return RISK_APPROVAL_MISSING;
        if (!r.orderId().equals(o.id()) || r.orderVersion()!=o.version()-1) return ORDER_VERSION_CHANGED;
        if (!r.policyVersion().equals(p.riskPolicyVersion())) return RISK_POLICY_MISMATCH;
        if (instruments.findById(o.command().instrumentId()).isEmpty() || !p.allowedInstruments().contains(o.command().instrumentId())) return INSTRUMENT_NOT_ALLOWED;
        if (o.command().quantity()>p.maxQuantity()) return QUANTITY_CAP_EXCEEDED;
        if (o.brokerCorrelationId().isEmpty()) return CORRELATION_MISSING;
        if (dispatch ? orders.hasBlockingExposureExcept(o.id()) : orders.hasDangerousUnresolvedOrders()) return RECONCILIATION_REQUIRED;
        var h=health.get();
        if (h==null || h.connectionState()!=MarketDataGateway.State.CONNECTED
                || h.status()!=MarketDataHealth.Status.FRESH || h.reason()!=MarketDataHealth.Reason.NONE
                || h.desiredSubscriptions()<=0 || h.activeSubscriptions()!=h.desiredSubscriptions()) return MARKET_DATA_UNAVAILABLE;
        var t=market.latest(o.command().instrumentId()).orElse(null);
        if (t==null || t.lastPrice().signum()<=0) return MARKET_DATA_UNAVAILABLE;
        // Evidence reads can wait on PostgreSQL. Never validate ages against a pre-read clock sample.
        var now=clock.instant();
        if (!session.enabled() || session.executionIdentity().isEmpty()) return AUTHENTICATION_UNAVAILABLE;
        if (!arm.armed(now)) return DISARMED;
        if (stop.getAsBoolean()) return EMERGENCY_STOP;
        if (!fresh(r.evaluatedAt(), now, p.riskDecisionMaxAge())) return RISK_APPROVAL_EXPIRED;
        if (!fresh(t.receivedAt(), now, p.marketDataMaxAge()) || t.exchangeTimestamp().filter(at -> !fresh(at, now, p.marketDataMaxAge())).isPresent()) return MARKET_DATA_STALE;
        BigDecimal price=o.command().limitPrice().map(value -> value.max(t.lastPrice())).orElse(t.lastPrice());
        if (price.signum()<=0 || price.multiply(BigDecimal.valueOf(o.command().quantity())).compareTo(p.maxNotional())>0) return NOTIONAL_CAP_EXCEEDED;
        return NONE;
    }
    private static boolean fresh(Instant observed, Instant now, Duration age) {
        return !observed.isAfter(now) && Duration.between(observed,now).compareTo(age)<0;
    }
}
