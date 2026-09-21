package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import java.time.Instant;
import java.util.Objects;
import java.util.List;

/** One immutable decision per persisted order version. No payloads or credentials. */
public record RiskDecision(OrderId orderId, long orderVersion, Outcome outcome, RiskReason reason,
                           Instant evaluatedAt, String policyVersion) {
    public enum Outcome { APPROVED, REJECTED }
    public RiskDecision {
        Objects.requireNonNull(orderId); Objects.requireNonNull(outcome); Objects.requireNonNull(reason);
        Objects.requireNonNull(evaluatedAt); Objects.requireNonNull(policyVersion);
        if (orderVersion < 0 || !policyVersion.matches("cash-v1:[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid risk audit");
        if ((outcome == Outcome.APPROVED) != (reason == RiskReason.APPROVED))
            throw new IllegalArgumentException("Inconsistent risk decision");
    }
    public boolean approved() { return outcome == Outcome.APPROVED; }
    /** Stable bounded representation for audit/metrics consumers. */
    public List<RiskReason> reasons() { return List.of(reason); }
}
