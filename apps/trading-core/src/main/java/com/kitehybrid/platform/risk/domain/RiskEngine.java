package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.order.domain.Signal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class RiskEngine {
    public record Decision(boolean approved, String reason, Instant evaluatedAt) {}
    private final List<RiskRule> rules;
    private final Clock clock;

    public RiskEngine(List<RiskRule> rules, Clock clock) {
        this.rules = List.copyOf(rules);
        this.clock = Objects.requireNonNull(clock);
        if (this.rules.isEmpty()) throw new IllegalArgumentException("Risk rules cannot be empty");
    }

    public Decision evaluate(Signal signal) {
        Instant now = clock.instant();
        if (signal == null) return new Decision(false, "INVALID_SIGNAL", now);
        for (RiskRule rule : rules) {
            try {
                var rejection = Objects.requireNonNull(rule.rejection(signal));
                if (rejection.isPresent()) return new Decision(false, rejection.get(), now);
            } catch (RuntimeException failure) {
                // Do not expose exception messages (may contain sensitive upstream data).
                return new Decision(false, "RISK_EVALUATION_ERROR", now);
            }
        }
        return new Decision(true, "APPROVED", now);
    }
}
