package com.kitehybrid.platform.shared.application;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Process-local safety latch. No I/O, credentials, persistence or execution capability. */
public final class RuntimeTradingHalt implements BooleanSupplier {
    public enum State { HALTED, RUNNING }
    public record Status(State runtimeState, boolean startupHalted, boolean effectiveHalted) {}
    /** Identity, rather than a reusable boolean, fences work across halt/resume cycles. */
    public static final class Epoch {
        private final State state;
        private Epoch(State state) { this.state = state; }
    }
    private final BooleanSupplier startupHalt;
    private final AtomicReference<Epoch> current = new AtomicReference<>(new Epoch(State.HALTED));

    public RuntimeTradingHalt(BooleanSupplier startupHalt) { this.startupHalt = Objects.requireNonNull(startupHalt); }
    private boolean startupHalted() {
        try { return startupHalt.getAsBoolean(); }
        catch (RuntimeException unavailable) { return true; }
    }
    @Override public boolean getAsBoolean() { return current.get().state == State.HALTED || startupHalted(); }
    public Status status() {
        var state = current.get().state;
        boolean startup = startupHalted();
        return new Status(state, startup, startup || state == State.HALTED);
    }
    /** Always changes identity: even repeated HALT invalidates outstanding resume confirmations. */
    public boolean halt() { return current.getAndSet(new Epoch(State.HALTED)).state != State.HALTED; }
    public Epoch epoch() { return current.get(); }
    /** Diagnostic evidence only: unknown startup state denies observation, while execution stays halted. */
    public boolean knownHaltedAt(Epoch observed) {
        if (observed == null || observed.state != State.HALTED || current.get() != observed) return false;
        try { return startupHalt.getAsBoolean() && current.get() == observed; }
        catch (RuntimeException unavailable) { return false; }
    }
    public boolean runningAt(Epoch observed) {
        return observed != null && observed.state == State.RUNNING && current.get() == observed && !startupHalted();
    }
    /** Caller must obtain the epoch before explicit confirmation. A concurrent halt wins over that confirmation. */
    public boolean resume(Epoch observed) {
        return observed != null && observed.state == State.HALTED && !startupHalted()
                && current.compareAndSet(observed, new Epoch(State.RUNNING));
    }
}
