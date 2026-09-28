package com.kitehybrid.platform.operator.console;

import com.kitehybrid.platform.operator.application.OperatorExecutionService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Reads terminal input only. It cannot resume, arm, execute or reconcile; it never queues commands. */
final class HaltAwareConsoleInput implements AutoCloseable {
    private final OperatorExecutionService operator;
    private final Consumer<String> output;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<CompletableFuture<String>> waiting = new AtomicReference<>();
    private final Thread reader;

    HaltAwareConsoleInput(OperatorExecutionService operator, Supplier<String> terminal, Consumer<String> output) {
        this.operator = operator;
        this.output = output;
        reader = Thread.ofPlatform().daemon().name("operator-terminal-input").unstarted(() -> {
            try {
                while (!closed.get()) {
                    String line = terminal.get();
                    if (line == null) break;
                    if (closed.get()) break;
                    if (line.equals("halt")) {
                        output.accept(operator.halt());
                        // Also revoke any confirmation/observation waiting on this input.
                        var pending = waiting.getAndSet(null);
                        if (pending != null) pending.complete("halt");
                    } else if (line.equals("halt-status")) {
                        output.accept(operator.haltStatus().toString());
                        var pending = waiting.getAndSet(null);
                        if (pending != null) pending.complete("halt-status");
                    } else {
                        var pending = waiting.getAndSet(null);
                        if (pending != null) pending.complete(line);
                        else {
                            operator.halt();
                            output.accept("DENIED COMMAND_IN_PROGRESS_USE_HALT");
                        }
                    }
                }
            } catch (RuntimeException unavailable) {
                // Provider/input exception text is never emitted.
            } finally { close(); }
        });
        reader.start();
    }
    String read(String prompt) {
        var pending = new CompletableFuture<String>();
        if (!waiting.compareAndSet(null, pending)) throw new IllegalStateException("Concurrent terminal input");
        if (closed.get()) { waiting.compareAndSet(pending, null); return null; }
        output.accept(prompt);
        try { return pending.get(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return null; }
        catch (java.util.concurrent.ExecutionException unavailable) { throw new IllegalStateException("Terminal unavailable"); }
        finally { waiting.compareAndSet(pending, null); }
    }
    @Override public void close() {
        closed.set(true);
        try { operator.halt(); }
        finally {
            var pending = waiting.getAndSet(null);
            if (pending != null) pending.complete(null);
            if (Thread.currentThread() != reader) reader.interrupt();
        }
    }
}
