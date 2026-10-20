package com.kitehybrid.platform.trading.domain;

import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.strategy.domain.StrategyDefinition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One instrument/strategy/version/timeframe/period. No cross-stock aggregation or production qualification. */
public record StockStrategyQualificationEvidence(InstrumentId instrumentId, StrategyDefinition strategy,
        Duration timeframe, Instant periodStart, Instant periodEnd, long evaluatedTrades, long wins, long losses,
        String costModel, BigDecimal transactionCosts, BigDecimal slippageBasisPoints,
        EvidenceProvenance dataset, Optional<PolicyEvidence> policy, EvidenceProvenance provenance)
        implements EvidenceProvenance.Snapshot {
    public record PolicyEvidence(String policyId, String version, String fingerprint, long minimumTrades,
                                 BigDecimal minimumWinPercentage, boolean remainingChecksPassed) {
        public PolicyEvidence {
            policyId = EvidenceProvenance.text(policyId);
            version = EvidenceProvenance.text(version);
            fingerprint = EvidenceProvenance.digest(fingerprint);
            if (minimumTrades < 1) throw new IllegalArgumentException("Positive sample requirement required");
            EvidenceProvenance.nonnegative(minimumWinPercentage);
            if (minimumWinPercentage.compareTo(BigDecimal.valueOf(100)) > 0)
                throw new IllegalArgumentException("Invalid percentage threshold");
        }
    }
    public StockStrategyQualificationEvidence {
        Objects.requireNonNull(instrumentId);
        Objects.requireNonNull(strategy);
        Objects.requireNonNull(timeframe);
        if (timeframe.isZero() || timeframe.isNegative()) throw new IllegalArgumentException("Positive timeframe required");
        Objects.requireNonNull(periodStart);
        Objects.requireNonNull(periodEnd);
        if (!periodStart.isBefore(periodEnd)) throw new IllegalArgumentException("Nonempty backtest period required");
        if (evaluatedTrades < 0 || wins < 0 || losses < 0 || wins > evaluatedTrades || losses != evaluatedTrades - wins)
            throw new IllegalArgumentException("Wins plus losses must equal evaluated trades");
        costModel = EvidenceProvenance.text(costModel);
        EvidenceProvenance.nonnegative(transactionCosts);
        EvidenceProvenance.nonnegative(slippageBasisPoints);
        Objects.requireNonNull(dataset).requireSyntheticRetention();
        Objects.requireNonNull(policy);
        Objects.requireNonNull(provenance).requireSyntheticRetention();
        if (provenance.observedAt().isBefore(periodEnd) || dataset.observedAt().isAfter(provenance.observedAt()))
            throw new IllegalArgumentException("Evaluation must follow period and dataset observation");
        if (!provenance.expiresAt().isAfter(dataset.observedAt())
                || provenance.expiresAt().isAfter(dataset.expiresAt()))
            throw new IllegalArgumentException("Evaluation cannot outlive dataset evidence");
    }
    /** Zero trades means no rate, not fabricated zero performance. Eight decimal places, HALF_EVEN. */
    public Optional<BigDecimal> winPercentage() {
        return evaluatedTrades == 0 ? Optional.empty() : Optional.of(BigDecimal.valueOf(wins)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(evaluatedTrades), 8, RoundingMode.HALF_EVEN));
    }
    /** Reuses the existing conservative API states. No QUALIFIED state is supported for fixtures. */
    public TradingProjection.QualificationState decision() {
        if (policy.isEmpty() || evaluatedTrades < policy.get().minimumTrades())
            return TradingProjection.QualificationState.INSUFFICIENT_EVIDENCE;
        var required = policy.get();
        boolean belowThreshold = BigDecimal.valueOf(wins).multiply(BigDecimal.valueOf(100))
                .compareTo(required.minimumWinPercentage().multiply(BigDecimal.valueOf(evaluatedTrades))) < 0;
        return belowThreshold || !required.remainingChecksPassed()
                ? TradingProjection.QualificationState.REJECTED : TradingProjection.QualificationState.INSUFFICIENT_EVIDENCE;
    }
}
