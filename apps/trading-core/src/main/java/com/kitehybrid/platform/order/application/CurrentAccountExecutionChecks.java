package com.kitehybrid.platform.order.application;

import com.kitehybrid.platform.broker.application.read.*;
import com.kitehybrid.platform.instrument.application.InstrumentRegistry;
import com.kitehybrid.platform.marketdata.application.LatestMarketDataStore;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.risk.domain.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import static com.kitehybrid.platform.order.application.ExecutionDenialReason.*;

/** Read ports only. No risk store, order repository, gateway, diagnostic HTTP or credential operations. */
public final class CurrentAccountExecutionChecks implements AccountExecutionChecks {
    private final BrokerPositionsProvider positions;
    private final BrokerHoldingsProvider holdings;
    private final BrokerMarginsProvider margins;
    private final BrokerOrdersProvider orders;
    private final InstrumentRegistry instruments;
    private final LatestMarketDataStore market;
    private final RiskLimits limits;
    private final Clock clock;
    public CurrentAccountExecutionChecks(BrokerPositionsProvider positions, BrokerHoldingsProvider holdings,
            BrokerMarginsProvider margins, BrokerOrdersProvider orders, InstrumentRegistry instruments,
            LatestMarketDataStore market, RiskLimits limits, Clock clock) {
        this.positions=Objects.requireNonNull(positions); this.holdings=Objects.requireNonNull(holdings);
        this.margins=Objects.requireNonNull(margins); this.orders=Objects.requireNonNull(orders);
        this.instruments=Objects.requireNonNull(instruments); this.market=Objects.requireNonNull(market);
        this.limits=Objects.requireNonNull(limits); this.clock=Objects.requireNonNull(clock);
    }
    @Override public Function<Optional<ConservativeOrderValuation>, ExecutionDenialReason> prepare(OrderRecord order) {
        try {
            var reference=instruments.snapshot();
            // These broker components have no common observation timestamp or transactional snapshot.
            // They are usable only in this synchronous operation, never persisted or reused by policy.
            var p=Objects.requireNonNull(positions.positions());
            var h=List.copyOf(holdings.holdings());
            var m=Objects.requireNonNull(margins.margins());
            var o=List.copyOf(orders.orders());
            var ids=new HashSet<com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId>();
            ids.add(order.command().instrumentId());
            p.net().forEach(row -> ids.add(row.instrumentId()));
            h.forEach(row -> ids.add(row.instrumentId()));
            return valuation -> {
                try {
                    var ticks=market.snapshot(ids); var now=clock.instant();
                    if (instruments.snapshot().version()!=reference.version() || reference.refreshedAt().isAfter(now)
                            || Duration.between(reference.refreshedAt(),now).compareTo(limits.registryMaxAge())>=0)
                        return INSTRUMENT_NOT_ALLOWED;
                    if (valuation.isEmpty()) return NOTIONAL_CAP_EXCEEDED;
                    var input=new OrderRiskInput(reference,ticks,true,p,h,m,o);
                    var capacity=CashAccountCapacity.inspect(input,limits,order.command().instrumentId(),now);
                    return map(CashAccountCapacity.check(capacity,order.command().quantity(),valuation.get(),limits));
                } catch (CashAccountCapacity.Denied denied) { return map(denied.reason()); }
                catch (RuntimeException invalid) { return ACCOUNT_EVIDENCE_UNAVAILABLE; }
            };
        } catch (RuntimeException unavailable) { return valuation -> ACCOUNT_EVIDENCE_UNAVAILABLE; }
    }
    private static ExecutionDenialReason map(RiskReason reason) {
        return switch (reason) {
            case APPROVED -> NONE;
            case POSITION_LIMIT -> ACCOUNT_POSITION_LIMIT;
            case EXPOSURE_LIMIT -> ACCOUNT_EXPOSURE_LIMIT;
            case INSUFFICIENT_MARGIN -> ACCOUNT_INSUFFICIENT_MARGIN;
            case OPEN_BROKER_ORDERS -> ACCOUNT_OPEN_ORDERS;
            case ACCOUNT_STATE_UNSUPPORTED -> ACCOUNT_STATE_UNSUPPORTED;
            case MARKET_DATA_STALE -> MARKET_DATA_STALE;
            case MARKET_DATA_UNAVAILABLE, INVALID_MARKET_PRICE -> MARKET_DATA_UNAVAILABLE;
            default -> ACCOUNT_EVIDENCE_UNAVAILABLE;
        };
    }
}
