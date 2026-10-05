package com.kitehybrid.platform.order.application;

import com.kitehybrid.platform.order.domain.ConservativeOrderValuation;
import com.kitehybrid.platform.order.domain.OrderRecord;
import java.util.Optional;
import java.util.function.Function;

/** Four read-only account observations, scoped to one synchronous check; never an authorization/cache. */
@FunctionalInterface
public interface AccountExecutionChecks {
    /** Fetch before local evidence reads. Evaluate with the final current valuation after blocking reads.
     * The returned evaluator must perform no remote or database I/O. */
    Function<Optional<ConservativeOrderValuation>, ExecutionDenialReason> prepare(OrderRecord order);
    AccountExecutionChecks UNAVAILABLE = order -> valuation -> ExecutionDenialReason.ACCOUNT_EVIDENCE_UNAVAILABLE;
}
