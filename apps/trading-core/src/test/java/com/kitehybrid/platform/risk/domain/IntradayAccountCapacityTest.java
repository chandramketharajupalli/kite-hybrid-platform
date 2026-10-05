package com.kitehybrid.platform.risk.domain;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.instrument.domain.*;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.kitehybrid.platform.risk.domain.RiskReason.*;
class IntradayAccountCapacityTest {
    static final Instant NOW=Instant.parse("2026-10-05T06:00:00Z");
    static final Instrument SBIN=Instrument.create(new BrokerInstrumentId("ZERODHA","synthetic"),"SBIN","NSE","CASH",InstrumentType.CASH,
            Optional.empty(),Optional.empty(),new BigDecimal("0.05"),1);
    static final BigDecimal Z=BigDecimal.ZERO;
    static BigDecimal d(String s) { return new BigDecimal(s); }
    final RiskLimits limits=new RiskLimits(true,100,d("10000"),100,d("10000"),Duration.ofSeconds(5),Duration.ofSeconds(60),d("1.1"),BigDecimal.ONE);
    static PlaceOrder command(OrderProduct product) { return new PlaceOrder("synthetic",SBIN.id(),OrderSide.BUY,10,OrderType.MARKET,
            product,OrderValidity.DAY,Optional.empty(),Optional.empty(),0,OrderVariety.REGULAR); }
    static BrokerMargins margins(String cash,String collateral,boolean enabled) {
        var c=d(cash);var co=d(collateral);
        var segment=new BrokerMargins.SegmentMargin(enabled,c.add(co),new BrokerMargins.AvailableMargin(Z,c,c,c,co,Z),
                new BrokerMargins.UtilisedMargin(Z,Z,Z,Z,Z,Z,Z,Z,Z,Z,Z,Z));
        var commodity=new BrokerMargins.SegmentMargin(true,d("999999999"),new BrokerMargins.AvailableMargin(Z,d("999999999"),d("999999999"),d("999999999"),Z,Z),segment.utilised());
        return new BrokerMargins(Map.of(TradingReadTypes.MarginSegment.EQUITY,segment,TradingReadTypes.MarginSegment.COMMODITY,commodity));
    }
    OrderMarginQuote quote(String required,boolean terms) { return new OrderMarginQuote(IntradayAccountCapacity.request(command(OrderProduct.INTRADAY),SBIN),
            d(required),Z,terms?Optional.of(new OrderMarginQuote.CollateralTerms(d("1000000"),Z)):Optional.empty(),NOW); }
    OrderRiskInput input(BrokerMargins m,OrderMarginQuote q,String price) {
        return new OrderRiskInput(InstrumentSnapshot.validated(List.of(SBIN),1,NOW),Map.of(SBIN.id(),new Tick(SBIN.id(),d(price),NOW)),true,
                new BrokerPositions(List.of(),List.of()),List.of(),m,List.of(),Optional.ofNullable(q));
    }
    RiskReason evaluate(OrderProduct product,OrderRiskInput input) {
        var record=new OrderRecord(new OrderId(UUID.randomUUID()),command(product),OrderState.VALIDATED,Optional.empty(),Optional.empty(),NOW,NOW,1);
        return CashOrderRiskRules.evaluate(record,input,limits,false,NOW);
    }
    @Test void marginIsNotNotionalAndCashOnlyMisWorks() {
        var input=input(margins("2001","0",true),quote("2000",false),"800");
        assertEquals(APPROVED,evaluate(OrderProduct.INTRADAY,input));
        assertEquals(INSUFFICIENT_MARGIN,evaluate(OrderProduct.DELIVERY,input));
        assertEquals(ORDER_VALUE_LIMIT,evaluate(OrderProduct.INTRADAY,input(margins("1000000","1000000",true),quote("2000",true),"910")));
    }
    @Test void explicitCollateralTermsAreRequiredWhenCashCannotCoverMargin() {
        assertEquals(COLLATERAL_UNSUPPORTED,evaluate(OrderProduct.INTRADAY,input(margins("1","2000",true),quote("2000",false),"800")));
        assertEquals(APPROVED,evaluate(OrderProduct.INTRADAY,input(margins("1","2000",true),quote("2000",true),"800")));
        assertEquals(MIS_MARGIN_INSUFFICIENT,evaluate(OrderProduct.INTRADAY,input(margins("1","1999.99",true),quote("2000",true),"800")));
        assertEquals(MIS_MARGIN_INSUFFICIENT,evaluate(OrderProduct.INTRADAY,input(margins("0.99","999999",true),quote("2000",true),"800")));
    }
    @Test void suppliedNegativeCashShapeDoesNotBecomeReadyFromNet() {
        var base=margins("-14681.5","1104220.75181",true);var e=base.segments().get(TradingReadTypes.MarginSegment.EQUITY);
        var m=new BrokerMargins(Map.of(TradingReadTypes.MarginSegment.EQUITY,new BrokerMargins.SegmentMargin(true,d("1089539.25181"),
                new BrokerMargins.AvailableMargin(Z,d("-14681.5"),d("-14681.5"),d("318.5"),e.available().collateral(),Z),e.utilised()),
                TradingReadTypes.MarginSegment.COMMODITY,base.segments().get(TradingReadTypes.MarginSegment.COMMODITY)));
        var input=input(m,quote("2000",true),"800");
        assertEquals(0,d("-14681.5").compareTo(CashAccountCapacity.inspect(input,limits,SBIN.id(),NOW).usableCash()));
        assertEquals(INSUFFICIENT_MARGIN,evaluate(OrderProduct.DELIVERY,input));
        assertEquals(MIS_MARGIN_INSUFFICIENT,evaluate(OrderProduct.INTRADAY,input));
    }
    @Test void disabledEquityAndHugeCommodityCannotFundNse() {
        assertEquals(BROKER_STATE_UNAVAILABLE,evaluate(OrderProduct.INTRADAY,input(margins("999999","999999",false),quote("2000",true),"800")));
        assertEquals(MIS_MARGIN_INSUFFICIENT,evaluate(OrderProduct.INTRADAY,input(margins("1","0",true),quote("2000",true),"800")));
    }
    @ParameterizedTest @ValueSource(strings={"missing","stale","future","quantity","identity","zero","negative","overflow"})
    void incompleteOrWrongQuotesNeverApprove(String fault) {
        var q=quote("2000",true);
        if(List.of("zero","negative","overflow").contains(fault)) {
            String value=switch(fault){case "zero"->"0";case "negative"->"-1";default->"1e30";};
            assertThrows(RuntimeException.class,()->quote(value,true));return;
        }
        if(fault.equals("missing")) q=null;
        else if(fault.equals("stale")||fault.equals("future")) q=new OrderMarginQuote(q.request(),q.requiredMargin(),q.charges(),q.collateralTerms(),NOW.plusSeconds(fault.equals("stale")?-5:1));
        else { var r=q.request();q=new OrderMarginQuote(new OrderMarginQuote.Request(fault.equals("identity")?new com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId(UUID.randomUUID()):r.instrumentId(),
                r.exchange(),r.symbol(),r.side(),r.orderType(),r.product(),r.validity(),r.variety(),fault.equals("quantity")?11:10),q.requiredMargin(),q.charges(),q.collateralTerms(),NOW); }
        assertEquals(MARGIN_ESTIMATE_UNAVAILABLE,evaluate(OrderProduct.INTRADAY,input(margins("1000000","1000000",true),q,"800")));
    }
    @Test void marginCashRequirementAndChargesRemainCashOnly() {
        var q=quote("2000",true);
        q=new OrderMarginQuote(q.request(),q.requiredMargin(),d("5"),Optional.of(new OrderMarginQuote.CollateralTerms(d("10000"),d("1000"))),NOW);
        assertEquals(APPROVED,evaluate(OrderProduct.INTRADAY,input(margins("1006","1000",true),q,"800")));
        assertEquals(MIS_MARGIN_INSUFFICIENT,evaluate(OrderProduct.INTRADAY,input(margins("1005.99","100000",true),q,"800")));
    }
    @Test void intradayPositionsAreIncludedAlongsideHoldingsWithoutCountingDayTwice() {
        var base=input(margins("100000","0",true),quote("2000",false),"800");
        var totals=new BrokerPosition.SideTotals(0,Z,Z);
        var position=new BrokerPosition(SBIN.id(),TradingReadTypes.Product.INTRADAY,1,0,BigDecimal.ONE,Z,Z,Z,Z,Z,Z,Z,Z,totals,totals,totals,totals);
        var withPosition=new OrderRiskInput(base.instruments(),base.ticks(),true,new BrokerPositions(List.of(position),List.of(position)),
                List.of(),base.margins(),List.of(),base.marginQuote());
        var capacity=CashAccountCapacity.inspectIntraday(withPosition,limits,SBIN.id(),NOW);
        assertEquals(0,BigDecimal.ONE.compareTo(capacity.heldUnits()));
        assertEquals(0,d("880").compareTo(capacity.existingExposure()));
        assertEquals(APPROVED,evaluate(OrderProduct.INTRADAY,withPosition));
        assertEquals(ACCOUNT_STATE_UNSUPPORTED,evaluate(OrderProduct.DELIVERY,withPosition));
    }
    @Test void excludedCreditsAndUtilisedCollateralCannotBecomeNewBuyingPower() {
        var base=margins("100","3000",true);var e=base.segments().get(TradingReadTypes.MarginSegment.EQUITY);
        var m=new BrokerMargins(Map.of(TradingReadTypes.MarginSegment.EQUITY,new BrokerMargins.SegmentMargin(true,d("3100"),
                new BrokerMargins.AvailableMargin(d("10"),d("100"),d("100"),d("100"),d("3000"),d("20")),
                new BrokerMargins.UtilisedMargin(d("5"),Z,d("10"),d("10"),d("10"),d("2"),Z,d("3"),Z,d("100"),d("200"),Z)),
                TradingReadTypes.MarginSegment.COMMODITY,e));
        var in=input(m,quote("2000",true),"800");var a=CashAccountCapacity.inspectIntraday(in,limits,SBIN.id(),NOW);
        var f=IntradayAccountCapacity.funding(a,m,in.marginQuote().orElseThrow());
        assertEquals(0,d("60").compareTo(f.excludedCredits()));assertEquals(0,d("30").compareTo(f.cash()));
        assertEquals(0,d("300").compareTo(f.collateralDeductions()));assertEquals(0,d("2700").compareTo(f.eligibleCollateral()));
        assertEquals(0,d("2730").compareTo(f.effectiveCapacity()));
    }
    @ParameterizedTest @ValueSource(strings={"collateral","payin","adhoc","liquid","stock","payout","sales"})
    void negativeRequiredMarginComponentsFailClosed(String field) {
        var z=BigDecimal.ZERO;var n=d("-1");var c=d("100000");
        var a=new BrokerMargins.AvailableMargin(field.equals("adhoc")?n:z,c,c,c,field.equals("collateral")?n:z,field.equals("payin")?n:z);
        var u=new BrokerMargins.UtilisedMargin(z,z,z,z,z,field.equals("payout")?n:z,z,field.equals("sales")?n:z,z,
                field.equals("liquid")?n:z,field.equals("stock")?n:z,z);
        var e=new BrokerMargins.SegmentMargin(true,c,a,u);
        assertEquals(MIS_MARGIN_UNAVAILABLE,evaluate(OrderProduct.INTRADAY,input(new BrokerMargins(Map.of(
                TradingReadTypes.MarginSegment.EQUITY,e,TradingReadTypes.MarginSegment.COMMODITY,e)),quote("2000",true),"800")));
    }

    @Test void observedPayinAndCollateralShapeDoesNotTurnUnknownEligibilityIntoApproval() {
        // Synthetic account fixture reproducing bounded field relationships, not a current portfolio or tick.
        var available=new BrokerMargins.AvailableMargin(Z,d("-14681.5"),d("-14681.5"),d("318.5"),d("1089220.75181"),d("15000"));
        var utilised=new BrokerMargins.UtilisedMargin(Z,Z,d("100"),Z,Z,Z,Z,Z,Z,Z,d("1089220.75181"),Z);
        var equity=new BrokerMargins.SegmentMargin(true,d("1089539.25181"),available,utilised);
        var margins=new BrokerMargins(Map.of(TradingReadTypes.MarginSegment.EQUITY,equity,
                TradingReadTypes.MarginSegment.COMMODITY,margins("0","0",false).segments().get(TradingReadTypes.MarginSegment.EQUITY)));
        var base=quote("191.60000000000002",false);
        var r=base.request();
        var oneShare=new OrderMarginQuote.Request(r.instrumentId(),r.exchange(),r.symbol(),r.side(),r.orderType(),r.product(),r.validity(),r.variety(),1);
        var estimate=new OrderMarginQuote(oneShare,base.requiredMargin(),d("0.374966948"),Optional.empty(),NOW);
        var synthetic=input(margins,estimate,"800");
        assertEquals(0,equity.net().compareTo(available.collateral().add(available.liveBalance())));
        assertEquals(0,available.liveBalance().compareTo(available.openingBalance().add(available.intradayPayin())));
        var capacity=CashAccountCapacity.inspectIntraday(synthetic,limits,SBIN.id(),NOW);
        assertEquals(0,d("-14681.5").compareTo(capacity.usableCash()));
        var funding=IntradayAccountCapacity.funding(capacity,margins,estimate);
        assertEquals(0,d("-29781.5").compareTo(funding.cash()));
        assertEquals(0,BigDecimal.ZERO.compareTo(funding.eligibleCollateral()));
        assertEquals(COLLATERAL_UNSUPPORTED,IntradayAccountCapacity.checkFunding(capacity,margins,estimate,limits));
        assertEquals(INSUFFICIENT_MARGIN,CashAccountCapacity.check(capacity,1,
                ConservativeOrderValuation.evaluate(OrderType.MARKET,1,d("800"),Optional.empty(),limits.priceBuffer()),limits));
    }

}
