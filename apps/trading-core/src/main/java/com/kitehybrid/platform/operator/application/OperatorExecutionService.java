package com.kitehybrid.platform.operator.application;

import com.kitehybrid.platform.order.application.*;
import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.command.OrderCommandValidationException;
import com.kitehybrid.platform.shared.application.ExecutionSession;
import com.kitehybrid.platform.shared.application.RuntimeTradingHalt;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.time.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.kitehybrid.platform.order.application.ExecutionDenialReason.*;

/** Explicit trusted in-process boundary. No HTTP, scheduler, event listener or bulk operation. */
public final class OperatorExecutionService {
    private static final Logger LOG = LoggerFactory.getLogger(OperatorExecutionService.class);
    public record ArmResult(boolean armed, Instant armedAt, Instant expiresAt, ExecutionDenialReason reason) {}
    private final boolean enabled;
    private final OrderExecutionProperties execution;
    private final LiveTestProperties live;
    private final RuntimeExecutionArming arm;
    private final ExecutionSession session;
    private final RuntimeTradingHalt stop;
    private final OrderRepository orders;
    private final ExecutionSafetyPolicy policy;
    private final OrderApplicationService application;
    private final Clock clock;
    public OperatorExecutionService(boolean enabled, OrderExecutionProperties execution, LiveTestProperties live,
            RuntimeExecutionArming arm, ExecutionSession session, RuntimeTradingHalt stop, OrderRepository orders,
            ExecutionSafetyPolicy policy, OrderApplicationService application, Clock clock) {
        this.enabled=enabled; this.execution=execution; this.live=live; this.arm=arm; this.session=session;
        this.stop=stop; this.orders=orders; this.policy=policy; this.application=application; this.clock=clock;
    }
    public synchronized ArmResult arm(OrderId id, Duration duration) {
        var haltEpoch = stop.epoch();
        audit("ARM_REQUEST", NONE);
        arm.disarm();
        var reason = !enabled ? OPERATOR_CONTROL_DISABLED : !execution.enabled() ? EXECUTION_DISABLED
                : stop.getAsBoolean() ? EMERGENCY_STOP
                : !session.enabled() || session.executionIdentity().isEmpty() ? AUTHENTICATION_UNAVAILABLE
                : !live.enabled() ? LIVE_TEST_DISABLED
                : !live.configured() || duration == null || duration.isNegative() || duration.isZero()
                    || duration.compareTo(live.armMaxDuration()) > 0 ? ARM_DURATION_INVALID : NONE;
        if (reason == NONE) {
            try {
                var candidate = orders.find(id).orElse(null);
                var before = preflight(id);
                if (candidate == null) reason = ORDER_NOT_FOUND;
                else if (!canArm(before)) reason = before.gates().entrySet().stream()
                        .filter(e -> e.getValue() != NONE && !(e.getValue() == DISARMED
                                && (e.getKey() == ExecutionReadiness.Gate.RUNTIME_ARMED || e.getKey() == ExecutionReadiness.Gate.SESSION_BOUND)))
                        .map(java.util.Map.Entry::getValue).findFirst().orElse(EVIDENCE_UNAVAILABLE);
                else {
                    arm.arm(candidate, duration, clock.instant(), haltEpoch);
                    var after = preflight(id);
                    if (!after.ready()) reason = after.reason();
                }
            } catch (RuntimeException denied) { reason = EVIDENCE_UNAVAILABLE; }
        }
        if (reason != NONE || stop.getAsBoolean()) {
            arm.disarm();
            if (reason == NONE) reason = EMERGENCY_STOP;
        }
        var status = arm.status(clock.instant());
        if (reason == NONE && !status.armed()) reason = DISARMED;
        audit(reason == NONE ? "ARM_SUCCESS" : "ARM_DENIED", reason);
        return new ArmResult(status.armed(), status.armedAt(), status.expiresAt(), reason);
    }
    public RuntimeExecutionArming.Status status() { return arm.status(clock.instant()); }
    public RuntimeTradingHalt.Status haltStatus() { return stop.status(); }
    /** Safety action first. Never waits for the arm/preflight monitor, database or session. */
    public String halt() {
        boolean changed = stop.halt();
        try { arm.disarm(); } catch (RuntimeException unavailable) { /* The halt epoch already revokes every grant. */ }
        finally { safeAudit(changed ? "RUNTIME_HALT_ACTIVATED" : "RUNTIME_HALT_ALREADY_ACTIVE"); }
        return changed ? "HALT_ACTIVE" : "HALT_ALREADY_ACTIVE";
    }
    public RuntimeTradingHalt.Epoch prepareResume() {
        safeAudit("RUNTIME_RESUME_REQUEST");
        return stop.epoch();
    }
    /** Release only the runtime latch. A confirmation predating any HALT cannot release it. */
    public String resume(RuntimeTradingHalt.Epoch confirmation) {
        arm.disarm();
        boolean resumed = enabled && arm.status(clock.instant()).permitState() != RuntimeExecutionArming.PermitState.CLAIMED
                && stop.resume(confirmation);
        safeAudit(resumed ? "RUNTIME_RESUME_SUCCESS" : "RUNTIME_RESUME_DENIED");
        return resumed ? "RESUME_SUCCESS DISARMED" : "RESUME_DENIED";
    }
    public static boolean canArm(ExecutionReadiness report) {
        if (!report.gates().keySet().equals(java.util.EnumSet.allOf(ExecutionReadiness.Gate.class))) return false;
        return report.gates().entrySet().stream().allMatch(e ->
                e.getKey() == ExecutionReadiness.Gate.RUNTIME_ARMED || e.getKey() == ExecutionReadiness.Gate.SESSION_BOUND
                        ? e.getValue() == DISARMED : e.getValue() == NONE);
    }
    public ArmResult disarm() {
        arm.disarm(); audit("DISARM", NONE);
        return new ArmResult(false, null, null, NONE);
    }
    public ExecutionReadiness preflight(OrderId id) {
        ExecutionReadiness result;
        try {
            result = orders.find(id).map(policy::inspect).orElseGet(() -> denied(ORDER_NOT_FOUND));
            if (!enabled) result = denied(OPERATOR_CONTROL_DISABLED);
        } catch (RuntimeException unavailable) { result = denied(EVIDENCE_UNAVAILABLE); }
        if (!result.ready()) audit("PREFLIGHT_DENIED", result.reason());
        return result;
    }
    public OrderRecord execute(OrderId id) {
        audit("EXECUTE_REQUEST", NONE);
        RuntimeExecutionArming.Attempt attempt = null;
        try {
            var report = preflight(id);
            if (!report.ready()) throw new OrderCommandValidationException(report.reason().name());
            attempt = arm.claim(id, clock.instant());
            audit("EXECUTE_ATTEMPT", NONE);
            // Persisted order is authoritative. The application repeats authorization and account admission.
            return application.executeRiskApproved(id);
        } catch (RuntimeException denied) {
            audit("EXECUTE_DENIED", EVIDENCE_UNAVAILABLE);
            throw denied;
        } finally {
            arm.disarm();
            arm.complete(attempt);
            audit("DISARM", NONE);
        }
    }
    private static ExecutionReadiness denied(ExecutionDenialReason reason) {
        var gates = new java.util.EnumMap<ExecutionReadiness.Gate, ExecutionDenialReason>(ExecutionReadiness.Gate.class);
        for (var gate : ExecutionReadiness.Gate.values()) gates.put(gate, reason);
        return new ExecutionReadiness(gates);
    }
    private static void audit(String action, ExecutionDenialReason reason) {
        LOG.info("Operator control action={} reason={}", action, reason);
    }
    private static void safeAudit(String action) {
        try { audit(action, NONE); } catch (RuntimeException unavailable) { /* Safety never depends on logging. */ }
    }
}
