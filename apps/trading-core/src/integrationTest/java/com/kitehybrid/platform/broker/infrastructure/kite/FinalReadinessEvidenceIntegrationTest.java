package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.*;
import com.kitehybrid.platform.broker.domain.read.*;
import com.kitehybrid.platform.account.application.ValidateBrokerProfileUseCase;
import com.kitehybrid.platform.instrument.application.RefreshInstrumentRegistryUseCase;
import com.kitehybrid.platform.order.domain.OrderState;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.kitehybrid.platform.broker.infrastructure.kite.OneOrderOperatorIntegrationTest.*;

/** Reproduction first; all account evidence, sessions, databases and mutation HTTP are synthetic. */
@Testcontainers @Isolated
class FinalReadinessEvidenceIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = OneOrderOperatorIntegrationTest.POSTGRES;
    static TimeZone zone;
    @BeforeAll static void utc() { zone=TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")); }
    @AfterAll static void restore() { TimeZone.setDefault(zone); }
    static void changeAccount(OneOrderOperatorIntegrationTest.Fixture f, String change) {
        switch (change) {
            case "cash" -> {
                var z=BigDecimal.ZERO;
                var available=new BrokerMargins.AvailableMargin(z,z,z,z,z,z);
                var used=new BrokerMargins.UtilisedMargin(z,z,z,z,z,z,z,z,z,z,z,z);
                var segment=new BrokerMargins.SegmentMargin(true,z,available,used);
                when(f.reads.margins()).thenReturn(new BrokerMargins(Map.of(TradingReadTypes.MarginSegment.EQUITY,
                        segment,TradingReadTypes.MarginSegment.COMMODITY,segment)));
            }
            case "holding" -> when(f.reads.holdings()).thenReturn(List.of(new BrokerHolding(INSTRUMENT.id(), "SYNTHETIC",
                    TradingReadTypes.Product.DELIVERY,1001,0,0,0,0,0,0,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,
                    BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,false,Optional.empty())));
            case "position" -> {
                var p=mock(BrokerPosition.class); when(p.instrumentId()).thenReturn(INSTRUMENT.id()); when(p.quantity()).thenReturn(1001L);
                when(p.product()).thenReturn(TradingReadTypes.Product.DELIVERY); when(p.multiplier()).thenReturn(BigDecimal.ONE);
                when(f.reads.positions()).thenReturn(new BrokerPositions(List.of(p),List.of()));
            }
            case "open-order" -> {
                var order=mock(BrokerOrder.class); when(order.status()).thenReturn(TradingReadTypes.OrderStatus.OPEN_PENDING);
                when(f.reads.orders()).thenReturn(List.of(order));
            }
            case "unavailable" -> when(f.reads.margins()).thenThrow(new IllegalStateException("synthetic read failed"));
            case "incomplete" -> when(f.reads.margins()).thenReturn(null);
            case "missing-positions" -> when(f.reads.positions()).thenReturn(null);
            case "missing-holdings" -> when(f.reads.holdings()).thenReturn(null);
            case "missing-orders" -> when(f.reads.orders()).thenReturn(null);
            default -> fail(change);
        }
    }
    @ParameterizedTest @ValueSource(strings={"cash","holding","position","open-order","unavailable","incomplete","missing-positions","missing-holdings","missing-orders"})
    void changedAccountDeniesDespiteHistoricalApproval(String change) throws Exception {
        try (var f=new OneOrderOperatorIntegrationTest().new Fixture()) {
            f.approve(); f.arm(); changeAccount(f,change);
            assertFalse(f.operator.preflight(f.id).ready());
            assertThrows(RuntimeException.class, () -> f.operator.execute(f.id)); f.assertCounts(0); f.consumed();
            assertEquals(OrderState.RISK_APPROVED, f.orders.find(f.id).orElseThrow().state());
        }
    }
    @Test void authenticatedSessionWithPendingApplicationInitializationCannotArm() throws Exception {
        try (var f=new OneOrderOperatorIntegrationTest().new Fixture()) {
            var store=mock(KiteAccessTokenStore.class);
            when(store.loadCurrent()).thenReturn(Optional.of(new KiteAccessToken("syntheticInitialization",NOW,NOW.plusSeconds(3600))));
            var profiles=mock(ValidateBrokerProfileUseCase.class);
            doAnswer(call -> { f.session.profileValidated(); return null; }).when(profiles).validate();
            var refresh=mock(RefreshInstrumentRegistryUseCase.class);
            when(refresh.refresh()).thenThrow(new IllegalStateException("synthetic initialization failure"));
            var auth=new KiteAuthenticationUseCase(mock(KiteAuthenticationGateway.class),f.session,store,profiles,refresh,f.clock);
            f.initialization.set(auth);
            var status=auth.restore();
            assertTrue(status.authenticated()); assertTrue(status.tokenAvailable()); assertFalse(status.initializationReady());
            assertTrue(f.session.executionIdentity().isPresent());
            f.approve();
            assertFalse(f.operator.preflight(f.id).ready());
            assertEquals(com.kitehybrid.platform.order.application.ExecutionDenialReason.AUTHENTICATION_NOT_INITIALIZED,
                    f.operator.preflight(f.id).gates().get(com.kitehybrid.platform.order.application.ExecutionReadiness.Gate.INITIALIZATION_READY));
            assertFalse(f.operator.arm(f.id,java.time.Duration.ofSeconds(30)).armed());
            assertThrows(RuntimeException.class, () -> f.operator.execute(f.id)); f.assertCounts(0);
        }
    }
    @ParameterizedTest @ValueSource(strings={"cash","holding","position","open-order","unavailable","incomplete","missing-positions","missing-holdings","missing-orders"})
    void accountChangeAtFinalTransportPreservesHistoricalApproval(String change) throws Exception {
        try (var f=new OneOrderOperatorIntegrationTest().new Fixture()) {
            f.approve(); f.arm();
            var historical=f.jdbc.queryForList("SELECT * FROM trading.risk_decisions");
            f.beforeDispatch=()->changeAccount(f,change);
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id));
            f.assertCounts(0); assertEquals(1,f.gatewayCalls.get());
            assertEquals(OrderState.FAILED,f.orders.find(f.id).orElseThrow().state());
            assertEquals(historical,f.jdbc.queryForList("SELECT * FROM trading.risk_decisions"));
            String reason=switch(change) {
                case "cash" -> "ACCOUNT_INSUFFICIENT_MARGIN";
                case "holding","position" -> "ACCOUNT_POSITION_LIMIT";
                case "open-order" -> "ACCOUNT_OPEN_ORDERS";
                default -> "ACCOUNT_EVIDENCE_UNAVAILABLE";
            };
            assertTrue(f.jdbc.queryForList("SELECT reason FROM trading.execution_authorizations",String.class).contains(reason));
            f.consumed();
        }
    }
    @ParameterizedTest @ValueSource(strings={"initialization","initialization-outage","session","halt","price","stale","reference"})
    void finalTransportRejectsEachVolatileChange(String change) throws Exception {
        try (var f=new OneOrderOperatorIntegrationTest().new Fixture()) {
            f.approve(); f.arm();
            f.beforeDispatch=()->{
                switch(change) {
                    case "initialization" -> f.initialization.set(()->false);
                    case "initialization-outage" -> f.initialization.set(()->{throw new IllegalStateException("synthetic");});
                    case "session" -> { f.session.install(new KiteAccessToken("syntheticReplaced",NOW,NOW.plusSeconds(3600))); f.session.profileValidated(); }
                    case "halt" -> f.operator.halt();
                    case "price" -> f.market.update(new com.kitehybrid.platform.marketdata.domain.Tick(INSTRUMENT.id(),new BigDecimal("21"),NOW),f.publication);
                    case "stale" -> f.now.set(NOW.plusSeconds(5));
                    case "reference" -> f.registry.replace(List.of(SbinCandidateRehearsalIntegrationTest.SBIN),NOW);
                    default -> fail(change);
                }
            };
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id)); f.assertCounts(0); f.consumed();
            assertEquals(change.equals("halt")?OrderState.SUBMITTING:OrderState.FAILED,f.orders.find(f.id).orElseThrow().state());
        }
    }
    @Test void initializationChangeAfterReadyDeniesBeforeAdmission() throws Exception {
        try (var f=new OneOrderOperatorIntegrationTest().new Fixture()) {
            f.approve(); f.arm(); var identity=f.session.executionIdentity();
            f.initialization.set(()->false);
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id));
            assertEquals(identity,f.session.executionIdentity()); f.assertCounts(0); f.consumed();
            assertEquals(OrderState.RISK_APPROVED,f.orders.find(f.id).orElseThrow().state());
        }
    }
    static BrokerMargins cash(String value) {
        var z=BigDecimal.ZERO; var cash=new BigDecimal(value);
        var used=new BrokerMargins.UtilisedMargin(z,z,z,z,z,z,z,z,z,z,z,z);
        var available=new BrokerMargins.AvailableMargin(z,cash,cash,cash,z,z);
        var segment=new BrokerMargins.SegmentMargin(true,cash,available,used);
        return new BrokerMargins(Map.of(TradingReadTypes.MarginSegment.EQUITY,segment,TradingReadTypes.MarginSegment.COMMODITY,segment));
    }
    @ParameterizedTest @ValueSource(strings={"11","10.99"})
    void finalUsableCashIncludesReserveWithEqualityPassing(String value) throws Exception {
        try (var f=new OneOrderOperatorIntegrationTest().new Fixture()) {
            f.approve(); f.arm(); f.beforeDispatch=()->when(f.reads.margins()).thenReturn(cash(value));
            if(value.equals("11")) { f.operator.execute(f.id); f.assertCounts(1); }
            else { assertThrows(RuntimeException.class,()->f.operator.execute(f.id)); f.assertCounts(0); }
            f.consumed();
        }
    }
    @Test void accountReadsAreFixedPerObservationAndNeverFetchTrades() throws Exception {
        try (var f=new OneOrderOperatorIntegrationTest().new Fixture()) {
            f.approve(); f.arm(); clearInvocations(f.reads);
            assertTrue(f.operator.preflight(f.id).ready()); assertReads(f,1);
            clearInvocations(f.reads); f.operator.execute(f.id); f.assertCounts(1); assertReads(f,2);
        }
    }
    static void assertReads(OneOrderOperatorIntegrationTest.Fixture f,int count) {
        verify(f.reads,times(count)).positions(); verify(f.reads,times(count)).holdings();
        verify(f.reads,times(count)).margins(); verify(f.reads,times(count)).orders();
        verify(f.reads,never()).trades(); verifyNoMoreInteractions(f.reads);
    }
    @ParameterizedTest @ValueSource(strings={"initialization","price","halt"})
    void volatileFencesAreRecheckedAfterAccountReads(String change) throws Exception {
        try (var f=new OneOrderOperatorIntegrationTest().new Fixture()) {
            f.approve(); f.arm();
            f.beforeDispatch=()->when(f.reads.margins()).thenAnswer(call->{
                switch(change) {
                    case "initialization" -> f.initialization.set(()->false);
                    case "price" -> f.market.update(new com.kitehybrid.platform.marketdata.domain.Tick(INSTRUMENT.id(),new BigDecimal("21"),NOW),f.publication);
                    case "halt" -> f.operator.halt();
                    default -> fail(change);
                }
                return cash("100000");
            });
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id)); f.assertCounts(0); f.consumed();
        }
    }

    @ParameterizedTest @ValueSource(ints={0,2,100,50})
    void currentSbinHoldingsUseSharedPositionAndExposureLimits(int quantity) throws Exception {
        var sbin=SbinCandidateRehearsalIntegrationTest.SBIN;
        try (var f=new OneOrderOperatorIntegrationTest().new Fixture(sbin,"risk.max-exposure=500")) {
            f.operator.resume(f.operator.prepareResume());
            var c=command("synthetic-sbin-capacity");
            f.id=f.application.place(new com.kitehybrid.platform.order.domain.command.PlaceOrder(c.idempotencyKey(),sbin.id(),
                    c.side(),c.quantity(),c.orderType(),c.product(),c.validity(),c.limitPrice(),c.triggerPrice(),c.disclosedQuantity(),c.variety())).id();
            assertTrue(f.context.getBean(com.kitehybrid.platform.risk.application.RiskService.class).evaluate(f.id).approved()); f.arm();
            f.beforeDispatch=()->when(f.reads.holdings()).thenReturn(List.of(new BrokerHolding(sbin.id(),"SYNTHETIC",
                    TradingReadTypes.Product.DELIVERY,quantity,0,0,0,0,0,0,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ONE,
                    BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,false,Optional.empty())));
            if(quantity<=2) { f.operator.execute(f.id); f.assertCounts(1); }
            else {
                var denied=assertThrows(RuntimeException.class,()->f.operator.execute(f.id)); f.assertCounts(0);
                assertEquals(quantity==100?"ACCOUNT_POSITION_LIMIT":"ACCOUNT_EXPOSURE_LIMIT",denied.getMessage());
            }
            f.consumed();
        }
    }
    @Test void twoInstancesCompetingWithChangedFinalCapacityCannotPost() throws Exception {
        try (var f=new OneOrderOperatorIntegrationTest().new Fixture()) {
            f.approve(); f.arm();
            var second=f.boot(f.authenticated()).getBean(com.kitehybrid.platform.operator.application.OperatorExecutionService.class);
            second.resume(second.prepareResume()); assertTrue(second.arm(f.id,java.time.Duration.ofSeconds(30)).armed());
            f.beforeDispatch=()->changeAccount(f,"cash");
            var start=new java.util.concurrent.CountDownLatch(1);
            try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var a=pool.submit(()->attempt(start,f.operator,f.id)); var b=pool.submit(()->attempt(start,second,f.id)); start.countDown();
                assertFalse(a.get(10,java.util.concurrent.TimeUnit.SECONDS)); assertFalse(b.get(10,java.util.concurrent.TimeUnit.SECONDS));
            }
            assertEquals(1,f.gatewayCalls.get()); f.assertCounts(0); f.consumed();
            assertEquals(com.kitehybrid.platform.order.application.RuntimeExecutionArming.PermitState.CONSUMED,second.status().permitState());
        }
    }
    @Test void haltDoesNotWaitForBlockedFinalAccountRead() throws Exception {
        try (var f=new OneOrderOperatorIntegrationTest().new Fixture()) {
            f.approve(); f.arm();
            var entered=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1);
            f.beforeDispatch=()->when(f.reads.margins()).thenAnswer(call->{ entered.countDown();
                if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("synthetic timeout");
                return cash("100000"); });
            try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var execution=pool.submit(()->assertThrows(RuntimeException.class,()->f.operator.execute(f.id)));
                try {
                    assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS));
                    assertEquals("HALT_ACTIVE",pool.submit(f.operator::halt).get(2,java.util.concurrent.TimeUnit.SECONDS));
                    assertTrue(f.operator.haltStatus().effectiveHalted());
                } finally { release.countDown(); }
                execution.get(10,java.util.concurrent.TimeUnit.SECONDS);
            }
            f.assertCounts(0); f.consumed(); assertEquals(OrderState.SUBMITTING,f.orders.find(f.id).orElseThrow().state());
        }
    }

    @Test void changedNetPositionCanViolateExposureWithoutViolatingQuantity() throws Exception {
        try(var f=new OneOrderOperatorIntegrationTest().new Fixture("risk.max-exposure=500")) {
            f.approve(); f.arm();
            var position=mock(BrokerPosition.class);
            when(position.instrumentId()).thenReturn(INSTRUMENT.id()); when(position.quantity()).thenReturn(50L);
            when(position.product()).thenReturn(TradingReadTypes.Product.DELIVERY); when(position.multiplier()).thenReturn(BigDecimal.ONE);
            f.beforeDispatch=()->when(f.reads.positions()).thenReturn(new BrokerPositions(List.of(position),List.of(position)));
            var denied=assertThrows(RuntimeException.class,()->f.operator.execute(f.id));
            assertEquals("ACCOUNT_EXPOSURE_LIMIT",denied.getMessage()); f.assertCounts(0); f.consumed();
        }
    }
    @Test void initializationProviderFailureDoesNotPreventHalt() throws Exception {
        try(var f=new OneOrderOperatorIntegrationTest().new Fixture()) {
            f.approve(); f.arm(); f.initialization.set(()->{throw new IllegalStateException("synthetic unavailable");});
            assertEquals("HALT_ACTIVE",f.operator.halt());
            assertTrue(f.operator.haltStatus().effectiveHalted()); assertFalse(f.operator.status().armed());
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id)); f.assertCounts(0); f.consumed();
        }
    }

}
