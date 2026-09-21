package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.broker.domain.read.TradingReadTypes;
import com.kitehybrid.platform.instrument.domain.InstrumentType;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.OrderState;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import static com.kitehybrid.platform.risk.domain.RiskReason.*;

/** Conservative cash-buy checkpoint, not broker margin calculation or executable authorization. */
final class CashOrderRiskRules {
    private CashOrderRiskRules() {}
    static RiskReason evaluate(OrderRecord order, OrderRiskInput in, RiskLimits limits, boolean halted, Instant now) {
        if (order.state() != OrderState.VALIDATED) return ORDER_NOT_VALIDATED;
        if (halted) return TRADING_HALTED;
        if (!limits.enabled()) return RISK_DISABLED;
        if (!limits.configured()) return RISK_UNCONFIGURED;
        if (in == null) return BROKER_STATE_UNAVAILABLE;
        var c = order.command();
        var registry = in.instruments();
        if (!fresh(registry.refreshedAt(), now, limits.registryMaxAge())) return INSTRUMENT_REGISTRY_STALE;
        var instrument = registry.byId().get(c.instrumentId());
        if (instrument == null) return INSTRUMENT_NOT_FOUND;
        if (instrument.type() != InstrumentType.CASH || instrument.lotSize() <= 0
                || instrument.tickSize().signum() <= 0) return INSTRUMENT_NOT_TRADABLE;
        // No claim that a live master contains an exchange/account tradability entitlement.
        if (!instrument.exchange().equals("NSE") && !instrument.exchange().equals("BSE")) return INSTRUMENT_NOT_TRADABLE;
        if (c.quantity() % instrument.lotSize() != 0) return INVALID_LOT_SIZE;
        for (var price : java.util.List.of(c.limitPrice(), c.triggerPrice())) {
            if (price.isPresent() && (price.get().signum() <= 0
                    || price.get().remainder(instrument.tickSize()).signum() != 0)) return INVALID_TICK_SIZE;
        }
        if (c.side() != OrderSide.BUY || c.product() != OrderProduct.DELIVERY
                || (c.orderType() != OrderType.MARKET && c.orderType() != OrderType.LIMIT)
                || c.validity() == OrderValidity.TIME_TO_LIVE || c.disclosedQuantity() != 0
                || c.triggerPrice().isPresent()
                || (c.orderType() == OrderType.LIMIT) != c.limitPrice().isPresent()) return UNSUPPORTED_ORDER;
        if (c.quantity() > limits.maxOrderQuantity()) return ORDER_QUANTITY_LIMIT;
        if (!in.marketHealthy()) return MARKET_DATA_UNAVAILABLE;
        var tick = in.ticks().get(c.instrumentId());
        var priceReason = priceReason(tick, now, limits.marketDataMaxAge());
        if (priceReason != APPROVED) return priceReason;
        BigDecimal reference = tick.lastPrice().max(c.limitPrice().orElse(BigDecimal.ZERO))
                .multiply(limits.priceBuffer());
        BigDecimal value = reference.multiply(BigDecimal.valueOf(c.quantity()));
        if (value.compareTo(limits.maxOrderValue()) > 0) return ORDER_VALUE_LIMIT;
        if (in.positions() == null || in.holdings() == null || in.margins() == null || in.orders() == null)
            return BROKER_STATE_UNAVAILABLE;
        // No outstanding broker orders: their fills/reservations cannot be atomically read with positions.
        for (var existing : in.orders()) {
            if (existing.status() != TradingReadTypes.OrderStatus.FILLED
                    && existing.status() != TradingReadTypes.OrderStatus.CANCELLED
                    && existing.status() != TradingReadTypes.OrderStatus.REJECTED) return OPEN_BROKER_ORDERS;
        }
        Map<InstrumentId, BigDecimal> quantities = new HashMap<>();
        for (var holding : in.holdings()) {
            if (holding.discrepancy() || holding.marginFunded().isPresent()
                    || holding.product() != TradingReadTypes.Product.DELIVERY) return ACCOUNT_STATE_UNSUPPORTED;
            // These buckets may overlap. Summation is deliberately an upper bound, never available-to-sell.
            var quantity = BigDecimal.valueOf(holding.quantity()).add(BigDecimal.valueOf(holding.unsettledQuantity()))
                    .add(BigDecimal.valueOf(holding.collateralQuantity()));
            quantities.merge(holding.instrumentId(), quantity, BigDecimal::add);
        }
        for (var position : in.positions().net()) {
            if (position.quantity() < 0 || position.product() != TradingReadTypes.Product.DELIVERY
                    || position.multiplier().compareTo(BigDecimal.ONE) != 0) return ACCOUNT_STATE_UNSUPPORTED;
            quantities.merge(position.instrumentId(), BigDecimal.valueOf(position.quantity()), BigDecimal::add);
        }
        // Day rows are not added to net: doing so would invent exposure; unsupported day products fail closed.
        for (var position : in.positions().day())
            if (position.product() != TradingReadTypes.Product.DELIVERY || position.quantity() < 0
                    || position.multiplier().compareTo(BigDecimal.ONE) != 0) return ACCOUNT_STATE_UNSUPPORTED;
        BigDecimal projected = quantities.getOrDefault(c.instrumentId(), BigDecimal.ZERO)
                .add(BigDecimal.valueOf(c.quantity()));
        if (projected.compareTo(BigDecimal.valueOf(limits.maxPositionQuantity())) > 0) return POSITION_LIMIT;
        BigDecimal exposure = value;
        for (var entry : quantities.entrySet()) {
            if (entry.getValue().signum() == 0) continue;
            var held = registry.byId().get(entry.getKey());
            if (held == null || held.type() != InstrumentType.CASH) return ACCOUNT_STATE_UNSUPPORTED;
            var heldTick = in.ticks().get(entry.getKey());
            var reason = priceReason(heldTick, now, limits.marketDataMaxAge());
            if (reason != APPROVED) return reason;
            exposure = exposure.add(entry.getValue().multiply(heldTick.lastPrice()).multiply(limits.priceBuffer()));
        }
        if (exposure.compareTo(limits.maxExposure()) > 0) return EXPOSURE_LIMIT;
        var equity = in.margins().segments().get(TradingReadTypes.MarginSegment.EQUITY);
        if (equity == null || !equity.enabled()) return BROKER_STATE_UNAVAILABLE;
        var available = equity.available();
        // Cash-only budget: no collateral, leverage, sale proceeds, or discretionary credit.
        var usable = available.cash().min(available.openingBalance()).min(available.liveBalance()).min(equity.net())
                .subtract(equity.utilised().debits().max(BigDecimal.ZERO))
                .subtract(equity.utilised().payout().max(BigDecimal.ZERO))
                .subtract(equity.utilised().holdingSales().max(BigDecimal.ZERO));
        if (usable.compareTo(value.add(limits.cashReserve())) < 0) return INSUFFICIENT_MARGIN;
        return APPROVED;
    }

    private static RiskReason priceReason(com.kitehybrid.platform.marketdata.domain.Tick tick,
                                          Instant now, Duration maxAge) {
        if (tick == null) return MARKET_DATA_UNAVAILABLE;
        if (!fresh(tick.receivedAt(), now, maxAge)
                || tick.exchangeTimestamp().map(t -> !fresh(t, now, maxAge)).orElse(false)) return MARKET_DATA_STALE;
        return tick.lastPrice().signum() > 0 ? APPROVED : INVALID_MARKET_PRICE;
    }
    static boolean fresh(Instant observation, Instant now, Duration age) {
        return !observation.isAfter(now) && Duration.between(observation, now).compareTo(age) <= 0;
    }
}
