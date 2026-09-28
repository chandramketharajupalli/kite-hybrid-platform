package com.kitehybrid.platform;

import com.kitehybrid.platform.config.*;
import com.kitehybrid.platform.operator.application.*;
import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.shared.application.*;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeTradingHaltTest {
    static final Instant NOW=Instant.parse("2026-09-28T06:00:00Z");
    @Configuration @EnableConfigurationProperties(TradingProperties.class) static class Properties {}

    @ParameterizedTest @ValueSource(booleans={true,false})
    void everySpringContextStartsHaltedAndStartupHaltCannotBeOverridden(boolean startup) {
        var runner=new ApplicationContextRunner().withUserConfiguration(Properties.class,RuntimeTradingHaltConfiguration.class)
                .withPropertyValues("trading.emergency-stop="+startup);
        var previous=new java.util.concurrent.atomic.AtomicReference<RuntimeTradingHalt>();
        runner.run(c->{
            var halt=c.getBean(RuntimeTradingHalt.class);
            previous.set(halt);
            assertTrue(halt.getAsBoolean()); assertEquals(RuntimeTradingHalt.State.HALTED,halt.status().runtimeState());
            assertEquals(!startup,halt.resume(halt.epoch())); assertEquals(startup,halt.getAsBoolean());
        });
        assertTrue(previous.get().getAsBoolean(), "Context close reactivates the latch");
        runner.run(c->assertTrue(c.getBean(RuntimeTradingHalt.class).getAsBoolean()));
    }
    @Test void unavailableStartupEvidenceFailsClosedAndStaleConfirmationCannotResume() {
        var unavailable=new RuntimeTradingHalt(()->{throw new IllegalStateException();});
        assertFalse(unavailable.resume(unavailable.epoch())); assertTrue(unavailable.getAsBoolean());
        var halt=new RuntimeTradingHalt(()->false); var confirmation=halt.epoch();
        halt.halt(); assertFalse(halt.resume(confirmation));
        assertTrue(halt.resume(halt.epoch())); var epoch=halt.epoch();
        halt.halt(); assertTrue(halt.resume(halt.epoch())); assertFalse(halt.runningAt(epoch));
    }
    @Test void concurrentHaltsAreVisibleAndIdempotent() throws Exception {
        var halt=new RuntimeTradingHalt(()->false); assertTrue(halt.resume(halt.epoch()));
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(8)) {
            var results=new ArrayList<Future<Boolean>>();
            for(int i=0;i<32;i++) results.add(pool.submit(()->{start.await(); halt.halt(); return halt.getAsBoolean();}));
            start.countDown(); for(var result:results) assertTrue(result.get(5,TimeUnit.SECONDS));
        }
        assertTrue(halt.status().effectiveHalted());
    }
    static final class Fixture {
        final RuntimeTradingHalt halt=new RuntimeTradingHalt(()->false);
        final ExecutionSession session=mock(ExecutionSession.class);
        final io.micrometer.core.instrument.MeterRegistry metrics;
        final RuntimeExecutionArming arm;
        final OrderRepository orders=mock(OrderRepository.class);
        final ExecutionSafetyPolicy policy=mock(ExecutionSafetyPolicy.class);
        final OrderApplicationService application=mock(OrderApplicationService.class);
        final OrderId id=new OrderId(new UUID(0,1));
        final OrderRecord record=mock(OrderRecord.class);
        final OperatorExecutionService operator;
        Fixture() { this(new SimpleMeterRegistry()); }
        Fixture(io.micrometer.core.instrument.MeterRegistry metrics) {
            this.metrics=metrics;
            this.arm=new RuntimeExecutionArming(metrics,session::executionIdentity,halt);
            when(session.enabled()).thenReturn(true); when(session.executionIdentity()).thenReturn(Optional.of(new UUID(0,2)));
            when(record.id()).thenReturn(id); when(record.version()).thenReturn(2L); when(orders.find(id)).thenReturn(Optional.of(record));
            when(policy.inspect(record)).thenAnswer(call->report());
            operator=new OperatorExecutionService(true,new OrderExecutionProperties(true),
                    new LiveTestProperties(true,Set.of(new InstrumentId(new UUID(0,3))),1,BigDecimal.TEN,Duration.ofSeconds(30)),
                    arm,session,halt,orders,policy,application,Clock.fixed(NOW,ZoneOffset.UTC));
        }
        ExecutionReadiness report() {
            var gates=new EnumMap<ExecutionReadiness.Gate,ExecutionDenialReason>(ExecutionReadiness.Gate.class);
            for(var gate:ExecutionReadiness.Gate.values()) gates.put(gate,ExecutionDenialReason.NONE);
            if (!arm.armed(NOW)) {
                gates.put(ExecutionReadiness.Gate.RUNTIME_ARMED,ExecutionDenialReason.DISARMED);
                gates.put(ExecutionReadiness.Gate.SESSION_BOUND,ExecutionDenialReason.DISARMED);
            }
            if (halt.getAsBoolean()) gates.put(ExecutionReadiness.Gate.EMERGENCY_STOP_CLEAR,ExecutionDenialReason.EMERGENCY_STOP);
            return new ExecutionReadiness(gates);
        }
        void resume() { assertEquals("RESUME_SUCCESS DISARMED",operator.resume(operator.prepareResume())); }
    }
    @Test void haltHasNoDependencyOnDatabaseSessionMarketOrDurableAudit() {
        var f=new Fixture(); f.resume(); assertTrue(f.operator.arm(f.id,Duration.ofSeconds(20)).armed());
        reset(f.orders,f.policy,f.session,f.application);
        when(f.orders.find(any())).thenThrow(new IllegalStateException("database unavailable"));
        when(f.session.executionIdentity()).thenThrow(new IllegalStateException("session unavailable"));
        assertEquals("HALT_ACTIVE",f.operator.halt()); assertEquals("HALT_ALREADY_ACTIVE",f.operator.halt());
        assertTrue(f.halt.getAsBoolean()); assertFalse(f.arm.armed(NOW));
        assertEquals(RuntimeExecutionArming.PermitState.CONSUMED,f.arm.status(NOW).permitState());
        verifyNoInteractions(f.orders,f.policy,f.session,f.application);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void haltDoesNotWaitForBlockedArmAndOldArmCannotSurviveResume(boolean resumeAgain) throws Exception {
        var f=new Fixture(); f.resume(); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(f.policy.inspect(f.record)).thenAnswer(call->{var report=f.report(); entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); return report;});
        try(var pool=Executors.newFixedThreadPool(2)) {
            var arming=pool.submit(()->f.operator.arm(f.id,Duration.ofSeconds(20)));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            try {
                pool.submit(f.operator::halt).get(2,TimeUnit.SECONDS);
                if (resumeAgain) f.resume();
            } finally { release.countDown(); }
            assertFalse(arming.get(5,TimeUnit.SECONDS).armed()); assertFalse(f.arm.armed(NOW));
        }
        verifyNoInteractions(f.application);
    }
    @Test void haltNeverRecyclesClaimedPermissionAndResumeRequiresCompletion() {
        var f=new Fixture(); f.resume(); assertTrue(f.operator.arm(f.id,Duration.ofSeconds(20)).armed());
        var attempt=f.arm.claim(f.id,NOW); f.operator.halt();
        assertEquals(RuntimeExecutionArming.PermitState.CLAIMED,f.arm.status(NOW).permitState());
        assertEquals("RESUME_DENIED",f.operator.resume(f.operator.prepareResume()));
        assertThrows(RuntimeException.class,()->f.arm.claim(f.id,NOW));
        f.arm.complete(attempt); assertEquals(RuntimeExecutionArming.PermitState.CONSUMED,f.arm.status(NOW).permitState());
        f.resume(); assertFalse(f.arm.armed(NOW));
    }
    @Test void epochRevokesArmWithoutDependingOnExplicitDisarm() {
        var f=new Fixture(); f.resume(); assertTrue(f.operator.arm(f.id,Duration.ofSeconds(20)).armed());
        f.halt.halt(); assertFalse(f.arm.armed(NOW)); assertTrue(f.halt.getAsBoolean());
        f.resume(); assertFalse(f.arm.armed(NOW));
    }
    @Test void metricFailureCannotPreventHaltOrPreserveUnusedPermission() {
        var metrics=mock(io.micrometer.core.instrument.MeterRegistry.class);
        when(metrics.counter("execution.armed")).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        when(metrics.counter("execution.disarmed")).thenThrow(new IllegalStateException("private-observability-failure"));
        var f=new Fixture(metrics); f.resume(); assertTrue(f.operator.arm(f.id,Duration.ofSeconds(20)).armed());
        assertEquals("HALT_ACTIVE",f.operator.halt()); assertTrue(f.halt.getAsBoolean());
        assertFalse(f.arm.armed(NOW)); assertEquals(RuntimeExecutionArming.PermitState.CONSUMED,f.arm.status(NOW).permitState());
    }
    @Test void haltAndResumeLogOnlyBoundedOperationalEvents() {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(OperatorExecutionService.class);
        var previous=logger.getLevel();
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender); logger.setLevel(ch.qos.logback.classic.Level.INFO);
        try {
            var f=new Fixture(); f.resume(); f.operator.halt(); f.operator.halt();
            var old=f.operator.prepareResume(); f.operator.halt(); f.operator.resume(old);
            var messages=appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
            assertFalse(messages.isEmpty());
            assertTrue(messages.stream().allMatch(m->m.matches("Operator control action=RUNTIME_(HALT_(ACTIVATED|ALREADY_ACTIVE)|RESUME_(REQUEST|SUCCESS|DENIED)) reason=NONE")));
            assertTrue(messages.stream().anyMatch(m->m.contains("RESUME_DENIED")));
        } finally {logger.detachAppender(appender); logger.setLevel(previous); appender.stop();}
    }
}
