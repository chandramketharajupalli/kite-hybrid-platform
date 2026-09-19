package com.kitehybrid.platform.risk.domain;

import com.kitehybrid.platform.order.domain.Signal;
import java.util.Optional;

/** Foundation validation only; reference price is not a trusted market-data valuation. */
public final class PositiveReferencePriceRiskRule implements RiskRule {
    @Override public Optional<String> rejection(Signal signal) {
        return signal.referencePrice().signum() > 0 ? Optional.empty() : Optional.of("INVALID_REFERENCE_PRICE");
    }
}
