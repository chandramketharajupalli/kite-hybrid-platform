package com.kitehybrid.platform.trading.domain;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Objects;

/** Evidence contract v1. Fingerprints bind supplied bytes, not independent authenticity. */
public record EvidenceProvenance(String sourceIdentity, String fingerprint, Instant observedAt,
                                 Instant expiresAt, Origin origin, Retention retention) {
    public enum Origin { SYNTHETIC_FIXTURE, LOCAL_UNVERIFIED }
    public enum Retention { NOT_AUTHORIZED, SYNTHETIC_ONLY }

    public EvidenceProvenance {
        sourceIdentity = text(sourceIdentity);
        fingerprint = digest(fingerprint);
        Objects.requireNonNull(observedAt);
        Objects.requireNonNull(expiresAt);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(retention);
        if (!observedAt.isBefore(expiresAt)) throw new IllegalArgumentException("Invalid evidence interval");
    }

    /** No independently approved real-data ingestion/retention mechanism exists in this phase. */
    public boolean realDataIngestionAuthorized() { return false; }
    public boolean syntheticRetentionPermitted() {
        return origin == Origin.SYNTHETIC_FIXTURE && retention == Retention.SYNTHETIC_ONLY;
    }
    public void requireSyntheticRetention() {
        if (!syntheticRetentionPermitted()) throw new IllegalArgumentException("Real-data retention not authorized");
    }

    public sealed interface Snapshot permits InstrumentSnapshotEvidence, StrategyDefinitionEvidence,
            StockStrategyQualificationEvidence, AccountRiskSnapshotEvidence {
        EvidenceProvenance provenance();
        default boolean tradingAuthorized() { return false; }
    }

    static String text(String value) {
        if (value == null || value.isBlank() || value.length() > 256
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Bounded nonblank text required");
        }
        return value;
    }
    static String digest(String value) {
        if (value == null || !value.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("SHA-256 required");
        return value;
    }
    static BigDecimal nonnegative(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Nonnegative decimal required");
        return value;
    }
}
