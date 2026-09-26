package com.kitehybrid.platform.order.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import io.micrometer.core.instrument.MeterRegistry;

/** Ephemeral, session-bound operator permission. Never persisted or enabled by configuration. */
public final class RuntimeExecutionArming {
    private record Arm(Instant from, Instant until, UUID session) {}
    private final AtomicReference<Arm> current = new AtomicReference<>();
    private final MeterRegistry metrics;
    private final Supplier<Optional<UUID>> sessionIdentity;
    public RuntimeExecutionArming() { this(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()); }
    public RuntimeExecutionArming(MeterRegistry metrics) { this(metrics, Optional::empty); }
    public RuntimeExecutionArming(MeterRegistry metrics, Supplier<Optional<UUID>> sessionIdentity) {
        this.metrics = java.util.Objects.requireNonNull(metrics);
        this.sessionIdentity = java.util.Objects.requireNonNull(sessionIdentity);
    }
    public boolean armed(Instant now) {
        var arm = current.get();
        if (arm == null) return false;
        if (now.isBefore(arm.from()) || !now.isBefore(arm.until())
                || !sessionIdentity.get().filter(arm.session()::equals).isPresent()) {
            if (current.compareAndSet(arm, null)) metrics.counter("execution.disarmed").increment();
            return false;
        }
        return true;
    }
    public void arm(Duration duration, Instant now) {
        if (duration == null || duration.isNegative() || duration.isZero()
                || duration.compareTo(Duration.ofHours(1)) > 0) throw new IllegalArgumentException("Invalid arm duration");
        var identity = sessionIdentity.get().orElseThrow(() -> new IllegalStateException("Authenticated session required"));
        current.set(new Arm(now, now.plus(duration), identity));
        metrics.counter("execution.armed").increment();
    }
    public void disarm() { if (current.getAndSet(null) != null) metrics.counter("execution.disarmed").increment(); }
}
