package com.kitehybrid.platform.risk.application;

import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.shared.domain.Identifiers.OrderId;
import com.kitehybrid.platform.risk.domain.RiskDecision;
import java.util.Optional;
import java.util.function.Function;

/** Only RiskService supplies evaluations. Implementation atomically commits state and audit. */
public interface RiskDecisionStore {
    RiskDecision evaluate(OrderId id, Function<OrderRecord, RiskDecision> evaluator);
    Optional<RiskDecision> find(OrderId id);
}
