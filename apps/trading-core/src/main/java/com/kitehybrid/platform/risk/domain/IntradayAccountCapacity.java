package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.instrument.domain.Instrument;
import com.kitehybrid.platform.order.domain.ConservativeOrderValuation;
import com.kitehybrid.platform.order.domain.command.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.kitehybrid.platform.risk.domain.RiskReason.*;

/** NSE cash BUY/MARKET/MIS/DAY/REGULAR only. Funding is a gate, never a notional sizing budget. */
public final class IntradayAccountCapacity {
    private IntradayAccountCapacity() {}
    public record Funding(BigDecimal cash, BigDecimal eligibleCollateral, BigDecimal excludedCredits,
                          BigDecimal collateralDeductions, BigDecimal effectiveCapacity) {
        @Override public String toString() { return "IntradayFunding[withheld]"; }
    }
    public static OrderMarginQuote.Request request(PlaceOrder c, Instrument instrument) {
        return new OrderMarginQuote.Request(c.instrumentId(),instrument.exchange(),instrument.tradingSymbol(),
                TradingReadTypes.Side.valueOf(c.side().name()),TradingReadTypes.OrderType.valueOf(c.orderType().name()),
                TradingReadTypes.Product.valueOf(c.product().name()),TradingReadTypes.Validity.valueOf(c.validity().name()),
                TradingReadTypes.Variety.valueOf(c.variety().name()),c.quantity());
    }
    public static Funding funding(CashAccountCapacity.Capacity account, BrokerMargins margins, OrderMarginQuote quote) {
        var e=margins.segments().get(TradingReadTypes.MarginSegment.EQUITY);
        if(e==null || !e.enabled()) throw new IllegalArgumentException("Equity unavailable");
        var a=e.available(); var u=e.utilised();
        // Required nonnegative components; P&L, net and cash may legitimately be signed.
        for(var v:List.of(a.collateral(),a.intradayPayin(),a.adhocMargin(),u.payout(),u.holdingSales(),
                u.liquidCollateral(),u.stockCollateral(),u.span(),u.exposure(),u.delivery(),u.turnover()))
            if(v.signum()<0) throw new IllegalArgumentException("Unsupported margin component");
        var excluded=a.intradayPayin().add(a.adhocMargin()).add(u.realisedMarkToMarket().max(BigDecimal.ZERO))
                .add(u.unrealisedMarkToMarket().max(BigDecimal.ZERO)).add(u.optionPremium().abs());
        // Keep the CNC cash floor, then exclude credits instead of treating them as new buying power.
        var cash=account.usableCash().subtract(excluded);
        var deductions=u.liquidCollateral().add(u.stockCollateral());
        var collateral=quote.collateralTerms().map(t->a.collateral().min(t.eligibleAdjustedCollateral())
                .subtract(deductions).max(BigDecimal.ZERO)).orElse(BigDecimal.ZERO);
        var netBound=e.net().subtract(excluded).subtract(u.debits().max(BigDecimal.ZERO))
                .subtract(u.payout()).subtract(u.holdingSales());
        return new Funding(cash,collateral,excluded,deductions,cash.add(collateral).min(netBound));
    }
    public static RiskReason check(PlaceOrder command, OrderRiskInput input, CashAccountCapacity.Capacity account,
                                   ConservativeOrderValuation valuation, RiskLimits limits, Instant now) {
        var exposure=CashAccountCapacity.checkExposure(account,command.quantity(),valuation,limits);
        if(exposure!=APPROVED) return exposure;
        try {
            var q=input.marginQuote().orElse(null);
            if(q==null || !q.request().equals(request(command,input.instruments().byId().get(command.instrumentId())))
                    || q.receivedAt().isAfter(now)
                    || Duration.between(q.receivedAt(),now).compareTo(limits.marketDataMaxAge())>=0)
                return MARGIN_ESTIMATE_UNAVAILABLE;
            return checkFunding(account,input.margins(),q,limits);
        } catch(RuntimeException malformed) { return MIS_MARGIN_UNAVAILABLE; }
    }
    public static RiskReason checkFunding(CashAccountCapacity.Capacity account,BrokerMargins margins,OrderMarginQuote q,RiskLimits limits) {
        try {
            var f=funding(account,margins,q);
            var cashRequired=q.collateralTerms().map(OrderMarginQuote.CollateralTerms::minimumCash).orElse(q.requiredMargin())
                    .add(q.charges()).add(limits.cashReserve());
            if(q.collateralTerms().isEmpty() && f.cash().compareTo(cashRequired)<0
                    && margins.segments().get(TradingReadTypes.MarginSegment.EQUITY).available().collateral().signum()>0)
                return COLLATERAL_UNSUPPORTED;
            if(f.cash().compareTo(cashRequired)<0 || f.effectiveCapacity().compareTo(q.requiredMargin().add(q.charges()).add(limits.cashReserve()))<0)
                return MIS_MARGIN_INSUFFICIENT;
            return APPROVED;
        } catch(RuntimeException malformed) { return MIS_MARGIN_UNAVAILABLE; }
    }

}
