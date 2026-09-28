package com.kitehybrid.platform;

import com.kitehybrid.platform.operator.console.TrustedOperatorConsole;
import com.kitehybrid.platform.order.application.RuntimeExecutionArming;
import com.kitehybrid.platform.order.domain.OrderState;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HaltConsoleTest {
    @ParameterizedTest @ValueSource(strings={"CONFIRM resume", "confirm resume", "CONFIRM resume ", "halt", "", "resume --force"})
    void resumeRequiresExactSeparateConfirmationAndEofRehalts(String confirmation) {
        var f=new RuntimeTradingHaltTest.Fixture(); var lines=new ArrayDeque<>(List.of("resume",confirmation,"halt-status"));
        var output=new ArrayList<String>();
        new TrustedOperatorConsole(f.operator,Optional.empty()).run(lines::poll,output::add);
        assertEquals(confirmation.equals("CONFIRM resume"),output.contains("RESUME_SUCCESS DISARMED"));
        assertTrue(f.halt.getAsBoolean()); assertFalse(f.arm.armed(RuntimeTradingHaltTest.NOW));
        verifyNoInteractions(f.application,f.orders,f.policy);
    }
    @Test void staleConfirmationAfterConcurrentHaltCannotResume() {
        var f=new RuntimeTradingHaltTest.Fixture(); var lines=new ArrayDeque<>(List.of("resume","CONFIRM resume"));
        var output=new ArrayList<String>();
        new TrustedOperatorConsole(f.operator,Optional.empty()).run(()->{
            if(lines.size()==1) f.operator.halt();
            return lines.poll();
        },output::add);
        assertTrue(output.contains("RESUME_DENIED")); assertTrue(f.halt.getAsBoolean());
    }
    @ParameterizedTest @ValueSource(strings={"halt","resume","execute"})
    void terminalReaderCanHaltBlockedManualExecutionWithoutExecutingOnReaderThread(String busyInput) throws Exception {
        var f=new RuntimeTradingHaltTest.Fixture(); f.resume(); assertTrue(f.operator.arm(f.id,Duration.ofSeconds(20)).armed());
        var input=new LinkedBlockingQueue<String>(); var output=new LinkedBlockingQueue<String>();
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var executionThread=new AtomicReference<Thread>(); var consoleThread=new AtomicReference<Thread>();
        when(f.record.state()).thenReturn(OrderState.SUBMITTED);
        when(f.application.executeRiskApproved(f.id)).thenAnswer(call->{
            executionThread.set(Thread.currentThread()); entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); return f.record;
        });
        try(var pool=Executors.newSingleThreadExecutor()) {
            var host=pool.submit(()->{
                consoleThread.set(Thread.currentThread());
                new TrustedOperatorConsole(f.operator,Optional.empty()).runInteractive(()->{
                    try { var line=input.take(); return line.equals("EOF") ? null : line; }
                    catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); return null; }
                },output::add);
            });
            try {
                await(output,"operator>"); input.add("preflight "+f.id.value());
                await(output,"operator>"); input.add("execute "+f.id.value());
                await(output,"Type CONFIRM"); input.add("CONFIRM execute "+f.id.value());
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                input.add(busyInput.equals("execute") ? "execute "+f.id.value() : busyInput);
                await(output,busyInput.equals("halt") ? "HALT_ACTIVE" : "DENIED COMMAND_IN_PROGRESS_USE_HALT");
                assertTrue(f.halt.getAsBoolean()); assertFalse(f.arm.armed(RuntimeTradingHaltTest.NOW));
                assertEquals(RuntimeExecutionArming.PermitState.CLAIMED,f.arm.status(RuntimeTradingHaltTest.NOW).permitState());
                assertSame(consoleThread.get(),executionThread.get());
            } finally { release.countDown(); }
            await(output,"operator>"); input.add("execute "+f.id.value());
            await(output,"DENIED"); input.add("EOF"); host.get(5,TimeUnit.SECONDS);
        }
        verify(f.application,times(1)).executeRiskApproved(f.id);
        assertTrue(f.halt.getAsBoolean()); assertEquals(RuntimeExecutionArming.PermitState.CONSUMED,f.arm.status(RuntimeTradingHaltTest.NOW).permitState());
    }
    @Test void interactiveInputFailureHaltsWithoutPrintingProviderDetails() {
        var f=new RuntimeTradingHaltTest.Fixture(); f.resume(); var output=new CopyOnWriteArrayList<String>();
        new TrustedOperatorConsole(f.operator,Optional.empty()).runInteractive(()->{throw new IllegalStateException("private-input-payload");},output::add);
        assertTrue(f.halt.getAsBoolean()); assertFalse(output.stream().anyMatch(v->v.contains("private-input-payload")));
    }
    private static void await(BlockingQueue<String> output,String prefix) throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<deadline) {
            var line=output.poll(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            if(line!=null && line.startsWith(prefix)) return;
        }
        fail("Missing bounded terminal event: "+prefix);
    }
}
