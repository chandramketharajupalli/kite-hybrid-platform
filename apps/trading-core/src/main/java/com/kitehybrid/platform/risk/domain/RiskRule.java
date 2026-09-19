package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.order.domain.Signal;
import java.util.Optional;

/** Empty means this rule allows the proposal; a reason means rejection. */
@FunctionalInterface
public interface RiskRule {
    Optional<String> rejection(Signal signal);
}
