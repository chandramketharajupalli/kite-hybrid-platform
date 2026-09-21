package com.kitehybrid.platform.risk.application;

import com.kitehybrid.platform.risk.domain.RiskReason;

/** Unknown/stale orders or unavailable storage cannot produce an approval. */
public final class RiskEvaluationException extends RuntimeException {
    private final RiskReason reason;
    public RiskEvaluationException(RiskReason reason) { super(reason.name()); this.reason = reason; }
    public RiskReason reason() { return reason; }
}
