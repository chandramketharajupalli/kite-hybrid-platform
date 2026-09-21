package com.kitehybrid.platform.strategy.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.StrategyId;
import java.util.Objects;

public record StrategyDefinition(StrategyId id, String version) {
    public StrategyDefinition {
        Objects.requireNonNull(id);
        if (version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,31}"))
            throw new IllegalArgumentException("Invalid strategy version");
    }
}
