package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.instrument.domain.InstrumentType;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.ConservativeOrderValuation;
import com.kitehybrid.platform.order.domain.OrderState;
import com.kitehybrid.platform.order.domain.command.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
        if (c.side() != OrderSide.BUY || (c.product() != OrderProduct.DELIVERY && c.product() != OrderProduct.INTRADAY)
                || (c.orderType() != OrderType.MARKET && c.orderType() != OrderType.LIMIT)
                || c.validity() == OrderValidity.TIME_TO_LIVE || c.disclosedQuantity() != 0
                || c.triggerPrice().isPresent()
                || (c.orderType() == OrderType.LIMIT) != c.limitPrice().isPresent()) return UNSUPPORTED_ORDER;
        if(c.product()==OrderProduct.INTRADAY && (!instrument.exchange().equals("NSE") || c.orderType()!=OrderType.MARKET
                || c.validity()!=OrderValidity.DAY || c.variety()!=OrderVariety.REGULAR)) return UNSUPPORTED_ORDER;
        if (c.quantity() > limits.maxOrderQuantity()) return ORDER_QUANTITY_LIMIT;
        if (!in.marketHealthy()) return MARKET_DATA_UNAVAILABLE;
        var tick = in.ticks().get(c.instrumentId());
        var priceReason = priceReason(tick, now, limits.marketDataMaxAge());
        if (priceReason != APPROVED) return priceReason;
        var valuation = ConservativeOrderValuation.evaluate(c.orderType(), c.quantity(), tick.lastPrice(),
                c.limitPrice(), limits.priceBuffer());
        BigDecimal value = valuation.conservativeNotional();
        if (!valuation.within(limits.maxOrderValue())) return ORDER_VALUE_LIMIT;
        final CashAccountCapacity.Capacity capacity;
        try { capacity = c.product()==OrderProduct.INTRADAY ? CashAccountCapacity.inspectIntraday(in,limits,c.instrumentId(),now)
                : CashAccountCapacity.inspect(in, limits, c.instrumentId(), now); }
        catch (CashAccountCapacity.Denied denied) { return denied.reason(); }
        return c.product()==OrderProduct.INTRADAY ? IntradayAccountCapacity.check(c,in,capacity,valuation,limits,now)
                : CashAccountCapacity.check(capacity, c.quantity(), valuation, limits);
    }

    static RiskReason priceReason(com.kitehybrid.platform.marketdata.domain.Tick tick,
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
