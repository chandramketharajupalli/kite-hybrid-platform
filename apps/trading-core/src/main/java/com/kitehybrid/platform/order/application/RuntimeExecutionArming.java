package com.kitehybrid.platform.order.application;

import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.command.OrderCommandValidationException;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import io.micrometer.core.instrument.MeterRegistry;

/** Ephemeral, session/order-bound permission. No monitor is held while consulting the session. */
public final class RuntimeExecutionArming {
    public enum PermitState { NONE, UNUSED, CLAIMED, CONSUMED }
    public record Status(boolean armed, Instant armedAt, Instant expiresAt, PermitState permitState) {}
    private record Arm(Instant from, Instant until, UUID session, OrderId orderId, long version,
                       UUID nonce, PermitState state, boolean active, RuntimeTradingHalt.Epoch haltEpoch) {
        Arm change(PermitState next, boolean enabled) { return new Arm(from,until,session,orderId,version,nonce,next,enabled,haltEpoch); }
    }
    /** Opaque ownership, never serialized or accepted from a transport. */
    public static final class Attempt {
        private final UUID nonce;
        private Attempt(UUID nonce) { this.nonce=nonce; }
    }
    private final AtomicReference<Arm> current = new AtomicReference<>();
    private final MeterRegistry metrics;
    private final Supplier<Optional<UUID>> sessionIdentity;
    private final RuntimeTradingHalt halt;
    public RuntimeExecutionArming() { this(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()); }
    public RuntimeExecutionArming(MeterRegistry metrics) { this(metrics, Optional::empty); }
    public RuntimeExecutionArming(MeterRegistry metrics, Supplier<Optional<UUID>> sessionIdentity) {
        this(metrics, sessionIdentity, null);
    }
    /** Production wiring must always supply the process-local halt; legacy constructors support isolated tests. */
    public RuntimeExecutionArming(MeterRegistry metrics, Supplier<Optional<UUID>> sessionIdentity, RuntimeTradingHalt halt) {
        this.metrics=java.util.Objects.requireNonNull(metrics);
        this.sessionIdentity=java.util.Objects.requireNonNull(sessionIdentity);
        this.halt=halt;
    }
    public boolean armed(Instant now) {
        var arm=current.get();
        if (arm == null || !arm.active() || arm.state() == PermitState.CONSUMED) return false;
        if (halt != null && !halt.runningAt(arm.haltEpoch()) || now.isBefore(arm.from()) || !now.isBefore(arm.until())
                || sessionIdentity.get().filter(arm.session()::equals).isEmpty()) {
            if (current.compareAndSet(arm,arm.change(arm.state() == PermitState.CLAIMED ? PermitState.CLAIMED : PermitState.CONSUMED,false)))
                metrics.counter("execution.disarmed").increment();
            return false;
        }
        return true;
    }
    /** Legacy capability alone cannot pass production first-live order binding. */
    public void arm(Duration duration, Instant now) { install(null,-1,duration,now,halt == null ? null : halt.epoch()); }
    public void arm(OrderRecord order, Duration duration, Instant now) {
        java.util.Objects.requireNonNull(order); install(order.id(),order.version(),duration,now,halt == null ? null : halt.epoch());
    }
    public void arm(OrderRecord order, Duration duration, Instant now, RuntimeTradingHalt.Epoch epoch) {
        java.util.Objects.requireNonNull(order); install(order.id(),order.version(),duration,now,epoch);
    }
    private void install(OrderId id, long version, Duration duration, Instant now, RuntimeTradingHalt.Epoch haltEpoch) {
        if (duration == null || duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Invalid arm duration");
        if (halt != null && !halt.runningAt(haltEpoch)) throw new IllegalStateException("Trading halted");
        var identity=sessionIdentity.get().orElseThrow(() -> new IllegalStateException("Authenticated session required"));
        var next=new Arm(now,now.plus(duration),identity,id,version,UUID.randomUUID(),PermitState.UNUSED,true,haltEpoch);
        while (true) {
            var previous=current.get();
            if (previous != null && previous.state() == PermitState.CLAIMED)
                throw new IllegalStateException("Execution attempt in progress");
            if (current.compareAndSet(previous,next)) break;
        }
        if (halt != null && !halt.runningAt(haltEpoch)) { disarm(); throw new IllegalStateException("Trading halted"); }
        metrics.counter("execution.armed").increment();
    }
    public ExecutionDenialReason bindingReason(OrderRecord order, Instant now) {
        if (!armed(now)) return ExecutionDenialReason.DISARMED;
        var arm=current.get();
        if (!arm.active()) return ExecutionDenialReason.DISARMED;
        if (!order.id().equals(arm.orderId())) return ExecutionDenialReason.ORDER_NOT_ARMED;
        return order.version() == arm.version() ? ExecutionDenialReason.NONE : ExecutionDenialReason.ORDER_VERSION_CHANGED;
    }
    public Attempt claim(OrderId id, Instant now) {
        var arm=current.get();
        if (arm == null || !armed(now) || !id.equals(arm.orderId()) || arm.state() != PermitState.UNUSED
                || !current.compareAndSet(arm,arm.change(PermitState.CLAIMED,true)))
            throw new OrderCommandValidationException(ExecutionDenialReason.PERMIT_UNAVAILABLE.name());
        return new Attempt(arm.nonce());
    }
    public void complete(Attempt attempt) {
        if (attempt == null) return;
        current.updateAndGet(arm -> arm != null && arm.nonce().equals(attempt.nonce)
                ? arm.change(PermitState.CONSUMED,false) : arm);
    }
    public void disarm() {
        while (true) {
            var arm=current.get();
            if (arm == null || !arm.active()) return;
            var next=arm.change(arm.state() == PermitState.CLAIMED ? PermitState.CLAIMED : PermitState.CONSUMED,false);
            if (current.compareAndSet(arm,next)) { metrics.counter("execution.disarmed").increment(); return; }
        }
    }
    public Status status(Instant now) {
        boolean armed=armed(now);
        var arm=current.get();
        return arm == null ? new Status(false,null,null,PermitState.NONE)
                : new Status(armed && arm.active(),armed ? arm.from() : null,armed ? arm.until() : null,arm.state());
    }
}
