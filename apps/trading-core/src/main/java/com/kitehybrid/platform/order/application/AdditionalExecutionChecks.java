package com.kitehybrid.platform.order.application;

import com.kitehybrid.platform.order.domain.OrderRecord;
import java.util.Map;

/** Additional restrictions are reevaluated at authorization and immediately before transport. */
@FunctionalInterface
public interface AdditionalExecutionChecks {
    /** Read durable evidence first; the returned evaluator must not perform blocking I/O. */
    java.util.function.Function<java.math.BigDecimal, Map<ExecutionReadiness.Gate, ExecutionDenialReason>> prepare(OrderRecord order);
}
