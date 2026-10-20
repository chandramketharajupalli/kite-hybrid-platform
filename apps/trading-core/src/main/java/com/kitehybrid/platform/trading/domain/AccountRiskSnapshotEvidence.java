package com.kitehybrid.platform.trading.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/** Captured account-wide snapshot only, never inferred from per-order decisions. No broker or risk evaluator. */
public record AccountRiskSnapshotEvidence(String accountScope, String currency, BigDecimal exposure,
        BigDecimal exposureLimit, Map<String, BigDecimal> additionalLimits, EvidenceProvenance provenance)
        implements EvidenceProvenance.Snapshot {
    public AccountRiskSnapshotEvidence {
        accountScope = EvidenceProvenance.text(accountScope);
        if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency code required");
        EvidenceProvenance.nonnegative(exposure);
        Objects.requireNonNull(exposureLimit);
        if (exposureLimit.signum() <= 0) throw new IllegalArgumentException("Positive exposure limit required");
        additionalLimits = Map.copyOf(Objects.requireNonNull(additionalLimits));
        additionalLimits.forEach((key, value) -> { EvidenceProvenance.text(key); EvidenceProvenance.nonnegative(value); });
        Objects.requireNonNull(provenance).requireSyntheticRetention();
    }
    public BigDecimal utilizationPercentage() {
        return exposure.multiply(BigDecimal.valueOf(100)).divide(exposureLimit, 8, RoundingMode.HALF_EVEN);
    }
}
