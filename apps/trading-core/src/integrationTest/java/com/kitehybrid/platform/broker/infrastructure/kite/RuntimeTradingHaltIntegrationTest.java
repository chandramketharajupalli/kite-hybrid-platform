package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.infrastructure.kite.OneOrderOperatorIntegrationTest.Fixture;
import com.kitehybrid.platform.operator.application.OperatorExecutionService;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.OrderState;
import com.kitehybrid.platform.reconciliation.application.OrderReconciliationService;
import com.kitehybrid.platform.risk.application.RiskService;
import com.kitehybrid.platform.risk.domain.RiskReason;
import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static com.kitehybrid.platform.broker.infrastructure.kite.OneOrderOperatorIntegrationTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real operator/policy/DB/adapter; every HTTP mutation remains on guarded loopback infrastructure. */
@Testcontainers @Isolated
class RuntimeTradingHaltIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=OneOrderOperatorIntegrationTest.POSTGRES;
    static TimeZone previous;
    @BeforeAll static void utc() { previous=TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")); }
    @AfterAll static void restoreZone() { TimeZone.setDefault(previous); }
    private Fixture fixture(String... overrides) throws Exception { return new OneOrderOperatorIntegrationTest().new Fixture(overrides); }
    private static long authorizations(Fixture f) { return f.jdbc.queryForObject("SELECT count(*) FROM trading.execution_authorizations",Long.class); }
    private static void halted(Fixture f) {
        assertTrue(f.operator.haltStatus().effectiveHalted()); assertFalse(f.operator.status().armed());
        assertNotEquals(RuntimeExecutionArming.PermitState.UNUSED,f.operator.status().permitState());
    }
    @Test void newContextCannotExecutePersistedApprovalUntilExplicitResumeAndArm() throws Exception {
        try(var f=fixture()) {
            f.approve(); var restarted=f.boot(f.authenticated()); var operator=restarted.getBean(OperatorExecutionService.class);
            assertEquals(RuntimeTradingHalt.State.HALTED,operator.haltStatus().runtimeState());
            assertFalse(operator.preflight(f.id).ready()); assertThrows(RuntimeException.class,()->operator.execute(f.id));
            assertEquals(0,authorizations(f)); f.assertCounts(0);
            assertEquals(OrderState.RISK_APPROVED,f.orders.find(f.id).orElseThrow().state());
        }
    }
    @Test void resumedRuntimeDoesNotEnableDisabledExecution() throws Exception {
        try(var f=fixture("kite.order-execution.enabled=false")) {
            f.approve(); assertFalse(f.operator.haltStatus().effectiveHalted());
            assertEquals(ExecutionDenialReason.EXECUTION_DISABLED,f.operator.preflight(f.id).reason());
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id)); f.assertCounts(0);
        }
    }
    @Test void confirmedResumeOnlyReleasesHaltAndRealPreflightRemainsDisarmedUntilExactArm() throws Exception {
        try(var f=fixture()) {
            f.approve(); f.operator.halt(); String id=f.id.value().toString();
            var lines=new ArrayDeque<>(List.of("resume","CONFIRM resume","preflight "+id,"arm "+id+" 30s",
                    "CONFIRM arm "+id,"preflight "+id,"execute "+id,"CONFIRM execute "+id));
            var output=new ArrayList<String>();
            new com.kitehybrid.platform.operator.console.TrustedOperatorConsole(f.operator,Optional.empty()).run(()->{
                if(lines.size()==6) { assertFalse(f.operator.status().armed()); assertEquals(0,authorizations(f)); f.assertCounts(0); }
                if(lines.size()==2) { assertTrue(f.operator.preflight(f.id).ready()); assertEquals(0,authorizations(f)); f.assertCounts(0); }
                return lines.poll();
            },output::add);
            assertTrue(output.contains("RESUME_SUCCESS DISARMED")); halted(f); f.assertCounts(1);
            assertTrue(f.submittingObserved.get()); assertEquals(OrderState.SUBMITTED,f.orders.find(f.id).orElseThrow().state());
            f.consumed();
        }
    }
    @ParameterizedTest @ValueSource(strings={"before-arm","after-arm","after-ready"})
    void haltDeniesWithoutOrderOrAuthorizationMutation(String boundary) throws Exception {
        try(var f=fixture()) {
            f.approve(); if(!boundary.equals("before-arm")) f.arm();
            if(boundary.equals("after-ready")) assertTrue(f.operator.preflight(f.id).ready());
            var before=f.orders.find(f.id).orElseThrow(); long audit=authorizations(f);
            f.operator.halt(); halted(f);
            var report=f.operator.preflight(f.id); assertFalse(report.ready());
            assertEquals(ExecutionDenialReason.EMERGENCY_STOP,report.gates().get(ExecutionReadiness.Gate.EMERGENCY_STOP_CLEAR));
            assertFalse(f.operator.arm(f.id,Duration.ofSeconds(30)).armed());
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id));
            assertEquals(before,f.orders.find(f.id).orElseThrow()); assertEquals(audit,authorizations(f)); f.assertCounts(0);
        }
    }
    @ParameterizedTest @ValueSource(strings={"database","session","market"})
    void haltDoesNotNeedExternalEvidence(String unavailable) throws Exception {
        try(var f=fixture()) {
            f.approve(); f.arm();
            if(unavailable.equals("database")) doThrow(new IllegalStateException("unavailable")).when(f.orders).find(any());
            if(unavailable.equals("session")) f.session.clear();
            if(unavailable.equals("market")) f.health=null;
            clearInvocations(f.orders,f.reads);
            assertEquals("HALT_ACTIVE",f.operator.halt()); halted(f); verifyNoInteractions(f.orders,f.reads);
            assertFalse(f.operator.arm(f.id,Duration.ofSeconds(30)).armed());
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id)); f.assertCounts(0);
        }
    }
    @Test void finalTransportFenceObservesHaltInsideSessionLockWithoutMakingHaltWait() throws Exception {
        try(var f=fixture()) {
            f.approve(); f.arm(); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
            f.beforeDispatch=()->{entered.countDown(); await(release);};
            try(var pool=Executors.newFixedThreadPool(2)) {
                var execute=pool.submit(()->assertThrows(RuntimeException.class,()->f.operator.execute(f.id)));
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                try {
                    assertEquals(OrderState.SUBMITTING,f.orders.find(f.id).orElseThrow().state());
                    pool.submit(f.operator::halt).get(2,TimeUnit.SECONDS); halted(f); f.assertCounts(0);
                } finally { release.countDown(); }
                execute.get(5,TimeUnit.SECONDS);
            }
            assertEquals(OrderState.SUBMITTING,f.orders.find(f.id).orElseThrow().state());
            assertTrue(f.orders.find(f.id).orElseThrow().brokerOrderId().isEmpty());
            f.consumed(); f.assertCounts(0);
            assertEquals(0,f.jdbc.queryForObject("SELECT count(*) FROM trading.reconciliation_decisions",Long.class));
        }
    }
    @ParameterizedTest @ValueSource(ints={1,2,3})
    void haltAtAdmissionFencesRollsBackBeforeCommit(int fence) throws Exception {
        try(var f=fixture()) {
            f.approve(); f.arm(); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
            var calls=new java.util.concurrent.atomic.AtomicInteger(); var policy=f.context.getBean(ExecutionSafetyPolicy.class);
            doAnswer(call->{ if(calls.incrementAndGet()==fence) {entered.countDown(); await(release);} return call.callRealMethod(); })
                    .when(policy).validateAdmission();
            try(var pool=Executors.newSingleThreadExecutor()) {
                var execute=pool.submit(()->assertThrows(RuntimeException.class,()->f.operator.execute(f.id)));
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                try {
                    // Separate connection cannot see an uncommitted SUBMITTING write at fence 3.
                    assertEquals(OrderState.RISK_APPROVED,f.orders.find(f.id).orElseThrow().state());
                    f.operator.halt(); halted(f);
                } finally { release.countDown(); }
                execute.get(5,TimeUnit.SECONDS);
            }
            assertEquals(OrderState.RISK_APPROVED,f.orders.find(f.id).orElseThrow().state());
            assertEquals(0,f.gatewayCalls.get()); f.consumed(); f.assertCounts(0);
        }
    }
    @Test void haltAfterAuthorizationBeforeAdmissionNeverCommitsSubmitting() throws Exception {
        try(var f=fixture()) {
            f.approve(); f.arm(); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
            var policy=f.context.getBean(ExecutionSafetyPolicy.class);
            doAnswer(call->{var result=call.callRealMethod(); entered.countDown(); await(release); return result;}).when(policy).evaluate(any());
            try(var pool=Executors.newSingleThreadExecutor()) {
                var execute=pool.submit(()->assertThrows(RuntimeException.class,()->f.operator.execute(f.id)));
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                try { f.operator.halt(); } finally {release.countDown();}
                execute.get(5,TimeUnit.SECONDS);
            }
            assertEquals(OrderState.RISK_APPROVED,f.orders.find(f.id).orElseThrow().state()); f.consumed(); f.assertCounts(0);
        }
    }
    @Test void haltWhileWaitingForPostgresAccountLockIsImmediateAndDeniesWhenLockReturns() throws Exception {
        try(var f=fixture(); var connection=f.source.getConnection(); var statement=connection.createStatement()) {
            f.approve(); f.arm(); connection.setAutoCommit(false); statement.execute("SELECT pg_advisory_xact_lock(606001)");
            var entered=new CountDownLatch(1); var policy=f.context.getBean(ExecutionSafetyPolicy.class);
            doAnswer(call->{var result=call.callRealMethod(); entered.countDown(); return result;}).when(policy).validateAdmission();
            try(var pool=Executors.newFixedThreadPool(2)) {
                var execute=pool.submit(()->assertThrows(RuntimeException.class,()->f.operator.execute(f.id)));
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                try { pool.submit(f.operator::halt).get(2,TimeUnit.SECONDS); halted(f); }
                finally {connection.rollback();}
                execute.get(5,TimeUnit.SECONDS);
            }
            assertEquals(OrderState.RISK_APPROVED,f.orders.find(f.id).orElseThrow().state()); f.consumed(); f.assertCounts(0);
        }
    }
    @Test void acknowledgedInFlightRequestCanStillBecomeSubmittedAfterHaltWithoutCancellation() throws Exception {
        try(var f=fixture()) {
            f.approve(); f.arm(); var release=new CountDownLatch(1); f.afterReceipt=()->await(release);
            try(var pool=Executors.newSingleThreadExecutor()) {
                var execute=pool.submit(()->f.operator.execute(f.id));
                assertTrue(f.received.await(5,TimeUnit.SECONDS));
                try { f.operator.halt(); halted(f); f.assertCounts(1); }
                finally {release.countDown();}
                assertEquals(OrderState.SUBMITTED,execute.get(5,TimeUnit.SECONDS).state());
            }
            assertTrue(f.orders.find(f.id).orElseThrow().brokerOrderId().isPresent()); f.consumed(); halted(f); f.assertCounts(1);
        }
    }
    @Test void inFlightRequestIsNotCancelledAndRecoveryRemainsExplicitWhileHalted() throws Exception {
        try(var f=fixture()) {
            f.approve(); f.arm(); f.mode=Mode.TIMEOUT;
            try(var pool=Executors.newSingleThreadExecutor()) {
                var execute=pool.submit(()->assertThrows(RuntimeException.class,()->f.operator.execute(f.id)));
                assertTrue(f.received.await(5,TimeUnit.SECONDS));
                assertTrue(f.console("halt").stream().anyMatch(v->v.equals("HALT_ACTIVE"))); halted(f); f.assertCounts(1);
                f.release.countDown(); execute.get(5,TimeUnit.SECONDS);
            }
            assertEquals(OrderState.SUBMITTING,f.orders.find(f.id).orElseThrow().state()); f.consumed();
            f.brokerObservation(); f.console("reconcile "+f.id.value());
            assertEquals(OrderState.SUBMITTED,f.orders.find(f.id).orElseThrow().state());
            assertTrue(f.orders.find(f.id).orElseThrow().brokerOrderId().isPresent()); halted(f); f.assertCounts(1);
        }
    }
    @ParameterizedTest @EnumSource(value=Mode.class,names={"SUCCESS","REJECT","AUTH","MALFORMED","RESPONSE_LOST","RESET"})
    void haltAfterOutcomeNeverAddsAMutationOrRewindsState(Mode mode) throws Exception {
        try(var f=fixture()) {
            f.approve(); f.arm(); f.mode=mode;
            if(mode==Mode.SUCCESS) f.operator.execute(f.id);
            else assertThrows(RuntimeException.class,()->f.operator.execute(f.id));
            var before=f.orders.find(f.id).orElseThrow(); f.operator.halt(); halted(f); f.consumed();
            assertEquals(before,f.orders.find(f.id).orElseThrow()); f.assertCounts(1);
            assertEquals(0,f.jdbc.queryForObject("SELECT count(*) FROM trading.reconciliation_decisions",Long.class));
        }
    }
    @Test void lostAcknowledgementPersistenceRecoversWhileHaltedWithoutRetry() throws Exception {
        try(var f=fixture()) {
            f.approve(); f.arm(); doThrow(new IllegalStateException("storage unavailable")).when(f.orders).attachBrokerOrderId(any(),any());
            assertThrows(RuntimeException.class,()->f.operator.execute(f.id)); f.operator.halt(); f.consumed();
            assertEquals(OrderState.SUBMITTING,f.orders.find(f.id).orElseThrow().state());
            f.brokerObservation(); f.context.getBean(OrderReconciliationService.class).reconcile(f.id);
            assertTrue(f.orders.find(f.id).orElseThrow().brokerOrderId().isPresent()); halted(f); f.assertCounts(1);
        }
    }
    @Test void riskRejectsWhileHaltedWithoutBrokerReadsAndObservabilityIsNotStopped() throws Exception {
        try(var f=fixture()) {
            f.id=f.application.place(command("halted-risk")).id(); clearInvocations(f.reads);
            assertEquals(RiskReason.TRADING_HALTED,f.context.getBean(RiskService.class).evaluate(f.id).reason());
            verifyNoInteractions(f.reads); assertTrue(f.session.authenticated()); assertNotNull(f.market.latest(INSTRUMENT.id()).orElseThrow());
            assertEquals(OrderState.REJECTED,f.orders.find(f.id).orElseThrow().state()); f.assertCounts(0);
        }
    }
    @Test void anotherServiceSharingTheJvmHaltCanFenceTheFirstServiceAtTransport() throws Exception {
        try(var f=fixture()) {
            f.approve(); f.arm(); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
            var second=new OperatorExecutionService(true,f.context.getBean(OrderExecutionProperties.class),
                    f.context.getBean(com.kitehybrid.platform.operator.application.LiveTestProperties.class),
                    f.context.getBean(RuntimeExecutionArming.class),f.session,f.context.getBean(RuntimeTradingHalt.class),
                    f.orders,f.context.getBean(ExecutionSafetyPolicy.class),f.application,f.clock);
            f.beforeDispatch=()->{entered.countDown(); await(release);};
            try(var pool=Executors.newSingleThreadExecutor()) {
                var execute=pool.submit(()->assertThrows(RuntimeException.class,()->f.operator.execute(f.id)));
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                try { second.halt(); assertTrue(second.haltStatus().effectiveHalted()); halted(f); }
                finally {release.countDown();}
                execute.get(5,TimeUnit.SECONDS);
            }
            assertThrows(RuntimeException.class,()->second.execute(f.id)); f.assertCounts(0);
        }
    }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5,TimeUnit.SECONDS)); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Test interrupted"); }
    }
}
