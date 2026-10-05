package com.kitehybrid.platform;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.operator.application.FirstLiveCandidatePlanner;
import com.kitehybrid.platform.risk.domain.*;
import com.kitehybrid.platform.order.domain.command.OrderProduct;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IntradayCandidatePlannerTest {
    final FirstLiveCandidatePlannerTest f=new FirstLiveCandidatePlannerTest();
    OrderRiskInput account(String collateral,boolean quote,long quantity) {
        var z=BigDecimal.ZERO;var cash=new BigDecimal("1000");var c=new BigDecimal(collateral);
        var segment=new BrokerMargins.SegmentMargin(true,cash.add(c),new BrokerMargins.AvailableMargin(z,cash,cash,cash,c,z),
                new BrokerMargins.UtilisedMargin(z,z,z,z,z,z,z,z,z,z,z,z));
        var r=new OrderMarginQuote.Request(f.SBIN.id(),"NSE","SBIN",TradingReadTypes.Side.BUY,TradingReadTypes.OrderType.MARKET,
                TradingReadTypes.Product.INTRADAY,TradingReadTypes.Validity.DAY,TradingReadTypes.Variety.REGULAR,quantity);
        return new OrderRiskInput(f.reference,Map.of(f.SBIN.id(),f.tick),true,new BrokerPositions(List.of(),List.of()),List.of(),
                new BrokerMargins(Map.of(TradingReadTypes.MarginSegment.EQUITY,segment,TradingReadTypes.MarginSegment.COMMODITY,segment)),List.of(),
                quote?Optional.of(new OrderMarginQuote(r,new BigDecimal("2000"),z,Optional.of(new OrderMarginQuote.CollateralTerms(c,z)),f.NOW)):Optional.empty());
    }
    FirstLiveCandidatePlanner.Plan plan(String collateral,boolean quote,long quantity) {
        return f.planner.planIntraday("NSE","SBIN",f.CAP,Duration.ofSeconds(30),f.execution,f.live,
                new FirstLiveCandidatePlanner.Evidence(f.reference,f.tick,f.health,Set.of(f.SBIN.id()),f.active,true,f.headroom),account(collateral,quote,quantity),f.NOW);
    }
    @Test void moreMarginNeverIncreasesFullNotionalAllocation() {
        var a=plan("19000",true,11);var b=plan("999000",true,11);
        assertEquals(FirstLiveCandidatePlanner.Status.SIZED_RECHECK_REQUIRED,a.status());assertEquals(a.status(),b.status());
        assertEquals(11,a.quantity());assertEquals(a.quantity(),b.quantity());assertEquals(new BigDecimal("9680.00"),a.conservativeNotional());
        assertEquals(OrderProduct.INTRADAY,a.product());assertEquals(new BigDecimal("2000"),a.estimatedMarginAndCharges().orElseThrow());
        assertFalse(f.planner.stillCurrent(a,b));
    }
    @Test void missingOrWrongExactQuantityMarginDoesNotAuthorizeOrResize() {
        var a=plan("19000",false,11);assertEquals(11,a.quantity());assertEquals(FirstLiveCandidatePlanner.Status.NOT_READY,a.status());
        assertEquals(FirstLiveCandidatePlanner.Reason.MARGIN_ESTIMATE_UNAVAILABLE,a.reason());
        assertEquals(FirstLiveCandidatePlanner.Reason.MARGIN_ESTIMATE_UNAVAILABLE,plan("19000",true,12).reason());
    }
    @Test void stricterExplicitHeadroomAndZeroLotRemainBindingForMis() {
        for(long cap:List.of(0L,1L)) {
            var h=new FirstLiveCandidatePlanner.Headroom(cap,f.headroom.exposure(),f.headroom.cashAfterReserve(),
                    f.headroom.observedAt(),f.headroom.maxAge(),true);
            var p=f.planner.planIntraday("NSE","SBIN",f.CAP,Duration.ofSeconds(30),f.execution,f.live,
                    new FirstLiveCandidatePlanner.Evidence(f.reference,f.tick,f.health,Set.of(f.SBIN.id()),f.active,true,h),account("19000",true,1),f.NOW);
            assertEquals(cap,p.quantity());assertEquals(cap==0?FirstLiveCandidatePlanner.Status.NOT_ELIGIBLE:FirstLiveCandidatePlanner.Status.SIZED_RECHECK_REQUIRED,p.status());
        }
    }

}
