package com.kitehybrid.platform.operator.console;

import com.kitehybrid.platform.operator.application.OperatorExecutionService;
import com.kitehybrid.platform.order.application.ExecutionReadiness;
import com.kitehybrid.platform.order.application.RuntimeExecutionArming;
import com.kitehybrid.platform.reconciliation.application.OrderReconciliationService;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;

/** Explicit controlling-terminal host. No Spring component, HTTP, worker, history or startup hook. */
public final class TrustedOperatorConsole {
    private final OperatorExecutionService operator;
    private final Optional<OrderReconciliationService> reconciliation;
    private final java.util.concurrent.atomic.AtomicBoolean running=new java.util.concurrent.atomic.AtomicBoolean();
    public TrustedOperatorConsole(OperatorExecutionService operator, Optional<OrderReconciliationService> reconciliation) {
        this.operator=java.util.Objects.requireNonNull(operator);
        this.reconciliation=java.util.Objects.requireNonNull(reconciliation);
    }
    /** Only the reviewed terminal launcher calls this in production. Tests supply scripted local I/O. */
    public void run(Supplier<String> input, Consumer<String> output) {
        run(input, output, false);
    }
    /** Input-only reader keeps HALT reachable while the calling thread performs a manual command. */
    public void runInteractive(Supplier<String> input, Consumer<String> output) {
        run(input, output, true);
    }
    private void run(Supplier<String> input, Consumer<String> output, boolean interactive) {
        if (!running.compareAndSet(false,true)) throw new IllegalStateException("Operator console already running");
        OrderId observedId=null;
        ExecutionReadiness observed=null;
        HaltAwareConsoleInput terminal = null;
        try {
            java.util.function.Function<String,String> read;
            if (interactive) { terminal = new HaltAwareConsoleInput(operator, input, output); read = terminal::read; }
            else read = prompt -> { output.accept(prompt); return input.get(); };
            while (true) {
                String line=read.apply("operator> halt | halt-status | resume | status | preflight <OrderId> | arm <OrderId> <seconds>s | execute <OrderId> | disarm | reconcile <OrderId>");
                if (line == null) return;
                try {
                    if (line.length() > 128 || !line.equals(line.strip())) throw new IllegalArgumentException();
                    String[] words=line.split(" ",-1);
                    if (words.length == 1 && words[0].equals("halt")) {
                        observed=null; observedId=null; output.accept(operator.halt()); continue;
                    }
                    if (words.length == 1 && words[0].equals("halt-status")) {
                        output.accept(operator.haltStatus().toString()); continue;
                    }
                    if (words.length == 1 && words[0].equals("resume")) {
                        observed=null; observedId=null;
                        var confirmation=operator.prepareResume();
                        var reply=read.apply("Type CONFIRM resume to release only the runtime halt. Execution remains DISARMED.");
                        if ("CONFIRM resume".equals(reply)) output.accept(operator.resume(confirmation));
                        else { if ("halt".equals(reply)) operator.halt(); operator.disarm(); output.accept("RESUME_DENIED CONFIRMATION_REQUIRED"); }
                        continue;
                    }
                    if (words.length == 1 && words[0].equals("status")) {
                        output.accept(operator.haltStatus().toString()); output.accept(operator.status().toString()); continue;
                    }
                    if (words.length == 1 && words[0].equals("disarm")) {
                        operator.disarm(); observed=null; observedId=null; output.accept("DISARMED"); continue;
                    }
                    if (words.length < 2 || words.length > 3) throw new IllegalArgumentException();
                    OrderId id=parseId(words[1]);
                    switch (words[0]) {
                        case "preflight" -> {
                            requireLength(words,2); observed=operator.preflight(id); observedId=id;
                            output.accept(observed.toString());
                        }
                        case "arm" -> {
                            requireLength(words,3);
                            if (!words[2].matches("[1-9][0-9]{0,3}s")) throw new IllegalArgumentException();
                            var duration=Duration.ofSeconds(Long.parseLong(words[2].substring(0,words[2].length()-1)));
                            boolean eligible=id.equals(observedId) && observed != null && OperatorExecutionService.canArm(observed);
                            observed=null; observedId=null;
                            if (!eligible || !confirm(read,"arm",words[1])) {
                                operator.disarm(); output.accept("DENIED PREFLIGHT_OR_CONFIRMATION_REQUIRED"); break;
                            }
                            output.accept(operator.arm(id,duration).toString());
                        }
                        case "execute" -> {
                            requireLength(words,2);
                            boolean eligible=id.equals(observedId) && observed != null && observed.ready();
                            observed=null; observedId=null;
                            if (!eligible || !confirm(read,"execute",words[1])) {
                                operator.disarm(); output.accept("DENIED PREFLIGHT_OR_CONFIRMATION_REQUIRED"); break;
                            }
                            output.accept("EXECUTION_RESULT " + operator.execute(id).state().name());
                        }
                        case "reconcile" -> {
                            requireLength(words,2); operator.disarm(); observed=null; observedId=null;
                            if (operator.status().permitState() == RuntimeExecutionArming.PermitState.CLAIMED)
                                throw new IllegalStateException();
                            LoggerFactory.getLogger(TrustedOperatorConsole.class).info("Operator control action=RECONCILE_REQUEST reason=NONE");
                            output.accept("RECONCILIATION_RESULT " + reconciliation.orElseThrow().reconcile(id).outcome().name());
                        }
                        default -> throw new IllegalArgumentException();
                    }
                } catch (RuntimeException denied) {
                    operator.disarm(); observed=null; observedId=null;
                    // Neither parser failures nor provider exception messages reach the terminal/logs.
                    output.accept("DENIED INVALID_COMMAND_OR_EVIDENCE");
                }
            }
        } finally {
            try { if (terminal != null) terminal.close(); operator.halt(); }
            finally { try { operator.disarm(); } finally { running.set(false); } }
        }
    }
    private static OrderId parseId(String text) {
        if (!text.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IllegalArgumentException();
        return new OrderId(UUID.fromString(text));
    }
    private static void requireLength(String[] words, int count) { if (words.length != count) throw new IllegalArgumentException(); }
    private boolean confirm(java.util.function.Function<String,String> read, String operation, String id) {
        var reply=read.apply("Type CONFIRM " + operation + " followed by the exact platform OrderId to continue.");
        if ("halt".equals(reply)) operator.halt();
        return ("CONFIRM " + operation + " " + id).equals(reply);
    }
}
