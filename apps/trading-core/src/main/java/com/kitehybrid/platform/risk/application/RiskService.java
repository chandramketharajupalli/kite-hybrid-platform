package com.kitehybrid.platform.risk.application;

import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.*;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.risk.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.HashSet;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import static com.kitehybrid.platform.risk.domain.RiskReason.*;

/** On-demand only. No gateway, scheduler, execution callback, or dependency on broker infrastructure. */
public final class RiskService {
    private final RiskDecisionStore decisions;
    private final RiskEngine engine;
    private final RiskLimits limits;
    private final BooleanSupplier halted;
    private final InstrumentRegistry instruments;
    private final LatestMarketDataStore market;
    private final Supplier<MarketDataHealth> health;
    private final BrokerPositionsProvider positions;
    private final BrokerHoldingsProvider holdings;
    private final BrokerMarginsProvider margins;
    private final BrokerOrdersProvider orders;
    private final Clock clock;
    private final MeterRegistry metrics;

    public RiskService(RiskDecisionStore decisions, RiskEngine engine, RiskLimits limits, BooleanSupplier halted,
                       InstrumentRegistry instruments, LatestMarketDataStore market, Supplier<MarketDataHealth> health,
                       BrokerPositionsProvider positions, BrokerHoldingsProvider holdings, BrokerMarginsProvider margins,
                       BrokerOrdersProvider orders, Clock clock, MeterRegistry metrics) {
        this.decisions = decisions; this.engine = engine; this.limits = limits; this.halted = halted;
        this.instruments = instruments; this.market = market; this.health = health; this.positions = positions;
        this.holdings = holdings; this.margins = margins; this.orders = orders; this.clock = clock; this.metrics = metrics;
    }

    public RiskDecision evaluate(OrderId id) {
        metrics.counter("risk.evaluations").increment();
        try {
            var result = decisions.evaluate(id, this::evaluateValidated);
            metrics.counter(result.approved() ? "risk.approvals" : "risk.rejections",
                    "reason", result.reason().name()).increment();
            return result;
        } catch (RuntimeException failure) {
            var reason = failure instanceof RiskEvaluationException safe ? safe.reason() : STORAGE_UNAVAILABLE;
            metrics.counter("risk.unavailable", "reason", reason.name()).increment();
            // Unknown orders, non-validated orders, and unavailable storage never become an
            // approval. Return a bounded decision so callers cannot mistake an exception for
            // an implicit approval; storage-backed decisions remain immutable when possible.
            return new RiskDecision(id, 0, RiskDecision.Outcome.REJECTED, reason,
                    clock.instant(), limits.version());
        }
    }

    private RiskDecision evaluateValidated(OrderRecord order) {
        try {
            if (halted.getAsBoolean() || !limits.enabled() || !limits.configured())
                return engine.evaluate(order, null, limits, halted.getAsBoolean());
            var snapshot = instruments.snapshot();
            var positionSnapshot = positions.positions();
            var holdingSnapshot = holdings.holdings();
            var marginSnapshot = margins.margins();
            var orderSnapshot = orders.orders();
            var ids = new HashSet<com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId>();
            ids.add(order.command().instrumentId());
            positionSnapshot.net().forEach(p -> ids.add(p.instrumentId()));
            holdingSnapshot.forEach(h -> ids.add(h.instrumentId()));
            var ticks = market.snapshot(ids);
            var currentHealth = health.get();
            if (instruments.snapshot().version() != snapshot.version())
                return rejected(order, INSTRUMENT_REGISTRY_STALE);
            var input = new OrderRiskInput(snapshot, ticks,
                    currentHealth != null && currentHealth.status() == MarketDataHealth.Status.FRESH
                            && currentHealth.reason() == MarketDataHealth.Reason.NONE,
                    positionSnapshot, holdingSnapshot, marginSnapshot, orderSnapshot);
            // Read clocks/health after broker reads so a slow read cannot approve stale input.
            return engine.evaluate(order, input, limits, halted.getAsBoolean());
        } catch (RuntimeException unavailable) {
            return rejected(order, BROKER_STATE_UNAVAILABLE);
        }
    }

    private RiskDecision rejected(OrderRecord order, RiskReason reason) {
        return new RiskDecision(order.id(), order.version(), RiskDecision.Outcome.REJECTED,
                reason, clock.instant(), limits.version());
    }
}
