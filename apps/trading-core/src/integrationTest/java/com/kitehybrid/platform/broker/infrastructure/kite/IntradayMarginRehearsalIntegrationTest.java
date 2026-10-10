package com.kitehybrid.platform.broker.infrastructure.kite;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.broker.application.auth.KiteAccessToken;
import com.kitehybrid.platform.marketdata.domain.Tick;
import com.kitehybrid.platform.order.domain.*;
import com.kitehybrid.platform.order.domain.command.*;
import com.kitehybrid.platform.risk.application.RiskService;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.kitehybrid.platform.broker.infrastructure.kite.OneOrderOperatorIntegrationTest.*;
@Testcontainers @Isolated
class IntradayMarginRehearsalIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=OneOrderOperatorIntegrationTest.POSTGRES;
    static TimeZone zone;
    @BeforeAll static void utc(){zone=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC"));}
    @AfterAll static void restore(){TimeZone.setDefault(zone);}
    static BigDecimal d(String s){return new BigDecimal(s);}
    static BrokerMargins margins(String cash,String collateral) {
        var z=BigDecimal.ZERO;var c=d(cash);var co=d(collateral);
        var segment=new BrokerMargins.SegmentMargin(true,c.add(co),new BrokerMargins.AvailableMargin(z,c,c,c,co,z),
                new BrokerMargins.UtilisedMargin(z,z,z,z,z,z,z,z,z,z,z,z));
        return new BrokerMargins(Map.of(TradingReadTypes.MarginSegment.EQUITY,segment,TradingReadTypes.MarginSegment.COMMODITY,segment));
    }
    static OrderMarginQuote quote(OrderMarginQuote.Request request,String margin,boolean terms) {
        return new OrderMarginQuote(request,d(margin),BigDecimal.ZERO,terms?Optional.of(new OrderMarginQuote.CollateralTerms(d("1000000"),BigDecimal.ZERO)):Optional.empty(),NOW);
    }
    OneOrderOperatorIntegrationTest.Fixture fixture(String... overrides) throws Exception {
        var properties=new ArrayList<>(List.of("risk.price-buffer=1.10",
                "kite.order-execution.max-quantity=100","kite.live-test.max-quantity=100",
                "kite.order-execution.max-notional=10000","kite.live-test.max-notional=10000"));
        properties.addAll(List.of(overrides));
        var f=new OneOrderOperatorIntegrationTest().new Fixture(SbinCandidateRehearsalIntegrationTest.SBIN,properties.toArray(String[]::new));
        f.market.update(new Tick(f.instrument.id(),d("800"),NOW),f.publication);
        when(f.reads.margins()).thenReturn(margins("101","19900"));
        f.marginEstimator.set(r->quote(r,"2000",true));return f;
    }
    void approve(OneOrderOperatorIntegrationTest.Fixture f) {
        f.operator.resume(f.operator.prepareResume());
        f.id=f.application.place(new PlaceOrder("synthetic-mis",f.instrument.id(),OrderSide.BUY,11,OrderType.MARKET,
                OrderProduct.INTRADAY,OrderValidity.DAY,Optional.empty(),Optional.empty(),0,OrderVariety.REGULAR)).id();
        assertTrue(f.context.getBean(RiskService.class).evaluate(f.id).approved());f.arm();
    }
    @Test void syntheticDiagnosticHasNoOrderPermitOrHaltEffectsInDisposableRuntime() throws Exception {
        try(var f=fixture()) {
            var halt=f.context.getBean(com.kitehybrid.platform.shared.application.RuntimeTradingHalt.class);
            assertTrue(halt.getAsBoolean());
            var before=f.operator.status();
            var request=new OrderMarginQuote.Request(f.instrument.id(),"NSE",f.instrument.tradingSymbol(),
                    TradingReadTypes.Side.BUY,TradingReadTypes.OrderType.MARKET,TradingReadTypes.Product.INTRADAY,
                    TradingReadTypes.Validity.DAY,TradingReadTypes.Variety.REGULAR,1);
            var input=new com.kitehybrid.platform.risk.domain.OrderRiskInput(f.registry.snapshot(),
                    f.market.snapshot(Set.of(f.instrument.id())),true,f.reads.positions(),f.reads.holdings(),
                    margins("2002.25","0"),f.reads.orders(),Optional.of(new OrderMarginQuote(request,d("2000"),d("1.25"),Optional.empty(),NOW)));
            var result=com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.summarizeSynthetic(input,request,
                    f.context.getBean(com.kitehybrid.platform.risk.domain.RiskLimits.class),d("10000"),NOW,NOW,f.session.authenticated(),halt.getAsBoolean());
            assertEquals(com.kitehybrid.platform.risk.domain.RiskReason.APPROVED,result.funding().cashOnlyFunding());
            assertEquals(com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.State.BLOCKED,
                    result.checks().get(com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.Check.AUTHORIZATION));
            var limits=f.context.getBean(com.kitehybrid.platform.risk.domain.RiskLimits.class);
            var capacity=com.kitehybrid.platform.risk.domain.CashAccountCapacity.inspectIntraday(input,limits,request.instrumentId(),NOW);
            var contract=com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.collateralContract(capacity,input.margins(),
                    input.marginQuote().orElseThrow(),request,f.instrument,limits,NOW,NOW,NOW,f.session.authenticated(),
                    com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.Origin.SYNTHETIC);
            assertEquals("KiteMisCollateralEvidence.v1",contract.version());
            assertTrue(contract.questions().values().stream().allMatch(t->t.status()==com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.Proof.UNKNOWN));
            assertTrue(contract.observations().values().stream().noneMatch(v->v.classification()==com.kitehybrid.platform.risk.domain.IntradayFundingEvidence.Evidence.BROKER_AUTHORITATIVE));
            assertEquals(before,f.operator.status());assertTrue(halt.getAsBoolean());f.assertCounts(0);
            assertEquals(0,f.gatewayCalls.get());
            assertEquals(0,f.jdbc.queryForObject("SELECT count(*) FROM trading.orders",Integer.class));
            assertEquals(0,f.jdbc.queryForObject("SELECT count(*) FROM trading.execution_authorizations",Integer.class));
        }
    }
    @ParameterizedTest @ValueSource(strings={"unchanged","cash","required-margin","charges"})
    void cashOnlyMisEqualityMustSurviveFinalRefreshIncludingChargesAndReserve(String change) throws Exception {
        try(var f=fixture()) {
            when(f.reads.margins()).thenReturn(margins("2002.25","0"));
            f.marginEstimator.set(r->new OrderMarginQuote(r,d("2000"),d("1.25"),Optional.empty(),NOW));
            approve(f);assertTrue(f.operator.preflight(f.id).ready());
            var historicalRisk=f.jdbc.queryForList("SELECT * FROM trading.risk_decisions");
            f.beforeDispatch=()->{
                if(change.equals("cash")) when(f.reads.margins()).thenReturn(margins("2002.24","0"));
                if(change.equals("required-margin")) f.marginEstimator.set(r->
                        new OrderMarginQuote(r,d("2000.01"),d("1.25"),Optional.empty(),NOW));
                if(change.equals("charges")) f.marginEstimator.set(r->
                        new OrderMarginQuote(r,d("2000"),d("1.26"),Optional.empty(),NOW));
            };
            if(change.equals("unchanged")) {f.operator.execute(f.id);f.assertCounts(1);}
            else {
                var denied=assertThrows(RuntimeException.class,()->f.operator.execute(f.id));
                assertEquals("MIS_MARGIN_INSUFFICIENT",denied.getMessage());f.assertCounts(0);
                assertEquals(OrderState.FAILED,f.orders.find(f.id).orElseThrow().state());
            }
            f.consumed();assertEquals(historicalRisk,f.jdbc.queryForList("SELECT * FROM trading.risk_decisions"));
        }
    }

    @Test void collateralAssistedSyntheticMisKeepsFullNotionalAndBoundedReadCount() throws Exception {
        try(var f=fixture()) {
            approve(f);var calls=new AtomicInteger();f.marginEstimator.set(r->{calls.incrementAndGet();return quote(r,"2000",true);});
            clearInvocations(f.reads);assertTrue(f.operator.preflight(f.id).ready());assertEquals(1,calls.get());
            f.operator.execute(f.id);f.assertCounts(1);assertEquals(3,calls.get());
            assertTrue(f.form.get().contains("product=MIS"));assertTrue(f.form.get().contains("quantity=11"));
            FinalReadinessEvidenceIntegrationTest.assertReads(f,3);f.consumed();
        }
    }
    @ParameterizedTest @ValueSource(strings={"collateral","cash","required-margin","estimate-outage","unknown-terms","wrong-quantity","open-order","position","price","stale","initialization","session","reference","halt","stale-quote","future-quote","auth-loss"})
    void changedFinalEvidenceNeverDispatchesOrRewritesRisk(String change) throws Exception {
        try(var f=fixture()) {
            approve(f);var risk=f.jdbc.queryForList("SELECT * FROM trading.risk_decisions");
            f.beforeDispatch=()->{
                switch(change) {
                    case "collateral"->when(f.reads.margins()).thenReturn(margins("101","1000"));
                    case "cash"->when(f.reads.margins()).thenReturn(margins("0.99","19900"));
                    case "required-margin"->f.marginEstimator.set(r->quote(r,"30000",true));
                    case "estimate-outage"->f.marginEstimator.set(r->{throw new IllegalStateException("synthetic unavailable");});
                    case "unknown-terms"->f.marginEstimator.set(r->quote(r,"2000",false));
                    case "wrong-quantity"->f.marginEstimator.set(r->quote(new OrderMarginQuote.Request(r.instrumentId(),r.exchange(),r.symbol(),r.side(),r.orderType(),r.product(),r.validity(),r.variety(),12),"2000",true));
                    case "open-order"->when(f.reads.orders()).thenReturn(List.of(new BrokerOrder("synthetic-pending",Optional.empty(),Optional.empty(),f.instrument.id(),
                            TradingReadTypes.Side.BUY,TradingReadTypes.OrderType.MARKET,TradingReadTypes.Product.INTRADAY,TradingReadTypes.Validity.DAY,
                            TradingReadTypes.Variety.REGULAR,TradingReadTypes.OrderStatus.OPEN,1,0,1,0,0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
                            NOW,Optional.of(NOW),Optional.of(NOW))));
                    case "position"->{var z=BigDecimal.ZERO;var totals=new BrokerPosition.SideTotals(0,z,z);
                        var position=new BrokerPosition(f.instrument.id(),TradingReadTypes.Product.INTRADAY,100,0,BigDecimal.ONE,z,z,z,z,z,z,z,z,totals,totals,totals,totals);
                        when(f.reads.positions()).thenReturn(new BrokerPositions(List.of(position),List.of(position)));}
                    case "price"->f.market.update(new Tick(f.instrument.id(),d("900"),NOW),f.publication);
                    case "stale"->f.now.set(NOW.plusSeconds(5));
                    case "initialization"->f.initialization.set(()->false);
                    case "session"->{f.session.install(new KiteAccessToken("syntheticReplacement",NOW,NOW.plusSeconds(3600)));f.session.profileValidated();}
                    case "reference"->f.registry.replace(List.of(INSTRUMENT),NOW);
                    case "halt"->f.operator.halt();
                    case "auth-loss"->f.session.invalidate();
                    case "stale-quote","future-quote"->f.marginEstimator.set(r->new OrderMarginQuote(r,d("2000"),BigDecimal.ZERO,
                            Optional.empty(),change.equals("stale-quote")?NOW.minusSeconds(60):NOW.plusNanos(1)));
                    default->fail(change);
                }
            };
            var denied=assertThrows(RuntimeException.class,()->f.operator.execute(f.id));f.assertCounts(0);f.consumed();
            assertEquals(risk,f.jdbc.queryForList("SELECT * FROM trading.risk_decisions"));
            if(List.of("collateral","cash","required-margin").contains(change)) assertEquals("MIS_MARGIN_INSUFFICIENT",denied.getMessage());
            if(change.equals("unknown-terms")) assertEquals("COLLATERAL_UNSUPPORTED",denied.getMessage());
            if(List.of("estimate-outage","wrong-quantity","stale-quote","future-quote").contains(change)) assertEquals("MARGIN_ESTIMATE_UNAVAILABLE",denied.getMessage());
        }
    }
    @Test void haltRemainsImmediateDuringBlockedMarginCalculation() throws Exception {
        try(var f=fixture()) {
            approve(f);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            f.beforeDispatch=()->f.marginEstimator.set(r->{entered.countDown();try {
                if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("synthetic timeout");
            }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("interrupted");}return quote(r,"2000",true);});
            try(var pool=Executors.newFixedThreadPool(2)) {
                var execution=pool.submit(()->assertThrows(RuntimeException.class,()->f.operator.execute(f.id)));
                try {assertTrue(entered.await(10,TimeUnit.SECONDS));assertEquals("HALT_ACTIVE",pool.submit(f.operator::halt).get(2,TimeUnit.SECONDS));}
                finally {release.countDown();}
                execution.get(10,TimeUnit.SECONDS);
            }
            f.assertCounts(0);f.consumed();assertEquals(OrderState.SUBMITTING,f.orders.find(f.id).orElseThrow().state());
        }
    }
    @ParameterizedTest @EnumSource(value=Mode.class,names={"SUCCESS","REJECT","AUTH","MALFORMED","RESPONSE_LOST","RESET","TIMEOUT"})
    void everyMisOutcomeConsumesOneAttempt(Mode mode) throws Exception {
        try(var f=fixture()) {
            approve(f);f.mode=mode;
            if(mode==Mode.SUCCESS) f.operator.execute(f.id);else assertThrows(RuntimeException.class,()->f.operator.execute(f.id));
            f.assertCounts(1);f.consumed();
        }
    }
    @Test void responseLossReconcilesWhileHaltedWithoutAnotherAttempt() throws Exception {
        try(var f=fixture()) {
            approve(f);f.mode=Mode.RESPONSE_LOST;
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id));f.assertCounts(1);f.consumed();
            f.operator.halt();f.brokerObservation();f.console("reconcile "+f.id.value());
            assertEquals(OrderState.SUBMITTED,f.orders.find(f.id).orElseThrow().state());f.assertCounts(1);
            assertFalse(f.operator.status().armed());
        }
    }
    @Test void twoInstancesWithReducedCollateralHaveOneAdmissionWinnerAndNoPost() throws Exception {
        try(var f=fixture()) {
            approve(f);
            var second=f.boot(f.authenticated(),"risk.price-buffer=1.10","kite.order-execution.max-quantity=100",
                    "kite.live-test.max-quantity=100","kite.order-execution.max-notional=10000","kite.live-test.max-notional=10000").getBean(com.kitehybrid.platform.operator.application.OperatorExecutionService.class);
            second.resume(second.prepareResume());assertTrue(second.arm(f.id,java.time.Duration.ofSeconds(30)).armed());
            f.beforeDispatch=()->when(f.reads.margins()).thenReturn(margins("101","0"));
            var start=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(2)) {
                var a=pool.submit(()->attempt(start,f.operator,f.id));var b=pool.submit(()->attempt(start,second,f.id));start.countDown();
                assertFalse(a.get(10,TimeUnit.SECONDS));assertFalse(b.get(10,TimeUnit.SECONDS));
            }
            assertEquals(1,f.gatewayCalls.get());f.assertCounts(0);f.consumed();
            assertEquals(com.kitehybrid.platform.order.application.RuntimeExecutionArming.PermitState.CONSUMED,second.status().permitState());
        }
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,100})
    void misPreservesExistingHoldingPositionAndExposureCaps(int quantity) throws Exception {
        try(var f=fixture("risk.max-exposure=11000")) {
            approve(f);
            f.beforeDispatch=()->when(f.reads.holdings()).thenReturn(List.of(new BrokerHolding(f.instrument.id(),"SYNTHETIC",
                    TradingReadTypes.Product.DELIVERY,quantity,0,0,0,0,0,0,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,
                    BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,false,Optional.empty())));
            if(quantity<=1) {f.operator.execute(f.id);f.assertCounts(1);}
            else {assertThrows(RuntimeException.class,()->f.operator.execute(f.id));f.assertCounts(0);}
            f.consumed();
        }
    }
    @ParameterizedTest @ValueSource(strings={"price","initialization","halt"})
    void volatileFenceRunsAfterTheMarginCalculation(String change) throws Exception {
        try(var f=fixture()) {
            approve(f);f.beforeDispatch=()->f.marginEstimator.set(r->{
                switch(change) {
                    case "price"->f.market.update(new Tick(f.instrument.id(),d("900"),NOW),f.publication);
                    case "initialization"->f.initialization.set(()->false);
                    case "halt"->f.operator.halt();
                }
                return quote(r,"2000",true);
            });
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id));f.assertCounts(0);f.consumed();
        }
    }

    @Test void acknowledgementPersistenceFailureCannotRetryMis() throws Exception {
        try(var f=fixture()) {
            approve(f);f.jdbc.execute("ALTER TABLE trading.orders ADD CONSTRAINT deny_mis_ack CHECK (broker_order_id IS NULL)");
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id));f.assertCounts(1);f.consumed();
            assertEquals(OrderState.SUBMITTING,f.orders.find(f.id).orElseThrow().state());
        }
    }
    @Test void changedMarginBeforeExecuteCannotReuseOldRiskOrPreflight() throws Exception {
        try(var f=fixture()) {
            approve(f);assertTrue(f.operator.preflight(f.id).ready());
            var risk=f.jdbc.queryForList("SELECT * FROM trading.risk_decisions");
            when(f.reads.margins()).thenReturn(margins("101","0"));
            assertFalse(f.operator.preflight(f.id).ready());
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id));f.assertCounts(0);f.consumed();
            assertEquals(OrderState.RISK_APPROVED,f.orders.find(f.id).orElseThrow().state());
            assertEquals(risk,f.jdbc.queryForList("SELECT * FROM trading.risk_decisions"));
        }
    }

}
