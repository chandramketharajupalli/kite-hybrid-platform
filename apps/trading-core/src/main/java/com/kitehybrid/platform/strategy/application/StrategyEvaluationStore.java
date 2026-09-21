package com.kitehybrid.platform.strategy.application;

import java.util.Optional;

public interface StrategyEvaluationStore {
    enum Claim { CREATED, EXISTING, CONFLICT }
    Claim claim(StrategyEvaluation evaluation);
    Optional<StrategyEvaluation> find(String eventKey, String strategyId, String strategyVersion);
    boolean attachOrder(StrategyEvaluation expected, StrategyEvaluation completed);
}
