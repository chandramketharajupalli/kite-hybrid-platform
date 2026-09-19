package com.kitehybrid.platform.shared.domain;

import java.util.Objects;
import java.util.UUID;

/** Typed identities shared across modules; broker tokens never substitute for InstrumentId. */
public final class Identifiers {
    private Identifiers() {}
    public record InstrumentId(UUID value) {
        public InstrumentId { Objects.requireNonNull(value); }
    }
    public record SignalId(UUID value) {
        public SignalId { Objects.requireNonNull(value); }
    }
    public record StrategyId(String value) {
        public StrategyId {
            if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}"))
                throw new IllegalArgumentException("Invalid strategy ID");
        }
    }
    public record OrderIntentId(UUID value) {
        public OrderIntentId { Objects.requireNonNull(value); }
    }
    public record OrderId(UUID value) {
        public OrderId { Objects.requireNonNull(value); }
    }
    public record CorrelationId(UUID value) {
        public CorrelationId { Objects.requireNonNull(value); }
    }
}
