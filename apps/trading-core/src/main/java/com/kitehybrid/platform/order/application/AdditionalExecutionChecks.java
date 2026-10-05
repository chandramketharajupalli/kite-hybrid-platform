package com.kitehybrid.platform.order.application;

import com.kitehybrid.platform.order.domain.OrderRecord;
import com.kitehybrid.platform.order.domain.ConservativeOrderValuation;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Additional restrictions are reevaluated at authorization and immediately before transport. */
@FunctionalInterface
public interface AdditionalExecutionChecks {
    /** Read durable evidence first; the returned evaluator must not perform blocking I/O. */
    Function<Optional<ConservativeOrderValuation>,
            Map<ExecutionReadiness.Gate, ExecutionDenialReason>> prepare(OrderRecord order);
}
