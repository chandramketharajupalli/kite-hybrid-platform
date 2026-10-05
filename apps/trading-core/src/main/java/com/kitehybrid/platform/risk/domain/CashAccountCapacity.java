package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.broker.domain.read.TradingReadTypes;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import com.kitehybrid.platform.order.domain.ConservativeOrderValuation;
import com.kitehybrid.platform.order.domain.command.OrderType;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Shared conservative account arithmetic for risk and observational sizing. No I/O or order creation. */
public final class CashAccountCapacity {
    private CashAccountCapacity() {}
    public record Capacity(BigDecimal heldUnits, BigDecimal existingExposure, BigDecimal usableCash) {}

    /** Exact-order capacity contract shared by historical risk and current execution observations. */
    public static RiskReason check(Capacity capacity, long quantity, ConservativeOrderValuation valuation, RiskLimits limits) {
        if (quantity <= 0) throw new IllegalArgumentException("Invalid capacity quantity");
        if (capacity.heldUnits().add(BigDecimal.valueOf(quantity)).compareTo(BigDecimal.valueOf(limits.maxPositionQuantity())) > 0)
            return RiskReason.POSITION_LIMIT;
        if (capacity.existingExposure().add(valuation.conservativeNotional()).compareTo(limits.maxExposure()) > 0)
            return RiskReason.EXPOSURE_LIMIT;
        if (capacity.usableCash().compareTo(valuation.conservativeNotional().add(limits.cashReserve())) < 0)
            return RiskReason.INSUFFICIENT_MARGIN;
        return RiskReason.APPROVED;
    }

    /** Unsupported or incomplete evidence throws a bounded domain denial. */
    public static Capacity inspect(OrderRiskInput in, RiskLimits limits, InstrumentId target, Instant now) {
        if (in == null || in.positions() == null || in.holdings() == null || in.margins() == null || in.orders() == null)
            throw denied(RiskReason.BROKER_STATE_UNAVAILABLE);
        for (var order : in.orders())
            if (order.status() != TradingReadTypes.OrderStatus.FILLED && order.status() != TradingReadTypes.OrderStatus.CANCELLED
                    && order.status() != TradingReadTypes.OrderStatus.REJECTED) throw denied(RiskReason.OPEN_BROKER_ORDERS);
        Map<InstrumentId, BigDecimal> quantities = new HashMap<>();
        for (var holding : in.holdings()) {
            if (holding.discrepancy() || holding.marginFunded().isPresent() || holding.product() != TradingReadTypes.Product.DELIVERY)
                throw denied(RiskReason.ACCOUNT_STATE_UNSUPPORTED);
            // Buckets can overlap: preserve the existing conservative upper bound.
            var q = BigDecimal.valueOf(holding.quantity()).add(BigDecimal.valueOf(holding.unsettledQuantity()))
                    .add(BigDecimal.valueOf(holding.collateralQuantity()));
            quantities.merge(holding.instrumentId(), q, BigDecimal::add);
        }
        for (var position : in.positions().net()) {
            if (position.quantity() < 0 || position.product() != TradingReadTypes.Product.DELIVERY
                    || position.multiplier().compareTo(BigDecimal.ONE) != 0) throw denied(RiskReason.ACCOUNT_STATE_UNSUPPORTED);
            quantities.merge(position.instrumentId(), BigDecimal.valueOf(position.quantity()), BigDecimal::add);
        }
        for (var position : in.positions().day())
            if (position.quantity() < 0 || position.product() != TradingReadTypes.Product.DELIVERY
                    || position.multiplier().compareTo(BigDecimal.ONE) != 0) throw denied(RiskReason.ACCOUNT_STATE_UNSUPPORTED);
        BigDecimal exposure = BigDecimal.ZERO;
        for (var entry : quantities.entrySet()) {
            if (entry.getValue().signum() == 0) continue;
            var instrument = in.instruments().byId().get(entry.getKey());
            if (instrument == null || instrument.type() != InstrumentType.CASH) throw denied(RiskReason.ACCOUNT_STATE_UNSUPPORTED);
            var tick = in.ticks().get(entry.getKey());
            var reason = CashOrderRiskRules.priceReason(tick, now, limits.marketDataMaxAge());
            if (reason != RiskReason.APPROVED) throw denied(reason);
            var unit = ConservativeOrderValuation.evaluate(OrderType.MARKET, 1, tick.lastPrice(), Optional.empty(), limits.priceBuffer()).conservativeUnitPrice();
            exposure = exposure.add(entry.getValue().multiply(unit));
        }
        var equity = in.margins().segments().get(TradingReadTypes.MarginSegment.EQUITY);
        if (equity == null || !equity.enabled()) throw denied(RiskReason.BROKER_STATE_UNAVAILABLE);
        var available = equity.available();
        var usable = available.cash().min(available.openingBalance()).min(available.liveBalance()).min(equity.net())
                .subtract(equity.utilised().debits().max(BigDecimal.ZERO))
                .subtract(equity.utilised().payout().max(BigDecimal.ZERO))
                .subtract(equity.utilised().holdingSales().max(BigDecimal.ZERO));
        return new Capacity(quantities.getOrDefault(target, BigDecimal.ZERO), exposure, usable);
    }
    public static final class Denied extends IllegalArgumentException {
        private final RiskReason reason;
        private Denied(RiskReason reason) { super(reason.name()); this.reason = reason; }
        public RiskReason reason() { return reason; }
    }
    private static Denied denied(RiskReason reason) { return new Denied(reason); }
}
