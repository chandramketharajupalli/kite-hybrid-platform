package com.kitehybrid.platform.strategy.application;

import com.kitehybrid.platform.order.domain.Signal;
import com.kitehybrid.platform.shared.domain.Identifiers.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record StrategyEvaluation(String eventKey, StrategyId strategyId, String strategyVersion,
                                 Signal signal, Optional<OrderIntentId> intentId, Optional<OrderId> orderId,
                                 Instant evaluatedAt) {
    public StrategyEvaluation {
        if (eventKey == null || !eventKey.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw new IllegalArgumentException("Invalid strategy event key");
        Objects.requireNonNull(strategyId); Objects.requireNonNull(strategyVersion); Objects.requireNonNull(signal);
        Objects.requireNonNull(intentId); Objects.requireNonNull(orderId); Objects.requireNonNull(evaluatedAt);
    }
}
