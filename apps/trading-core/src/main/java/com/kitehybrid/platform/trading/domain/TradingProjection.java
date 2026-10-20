package com.kitehybrid.platform.trading.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal immutable read contracts, not an HTTP success schema or an authorization grant.
 * Evidence hashes bind supplied bytes only; they do not establish independent provenance.
 */
public sealed interface TradingProjection
        permits TradingProjection.Available, TradingProjection.Unavailable {

    enum Kind { INSTRUMENTS, STRATEGIES, QUALIFICATIONS, RISK_STATUS }
    enum UnavailableReason { EVIDENCE_UNAVAILABLE, SOURCE_UNAVAILABLE, STALE_EVIDENCE, UNVERIFIED_EVIDENCE, INCOMPLETE_EVIDENCE }
    enum EvidenceProvenance { LOCAL_UNVERIFIED, SYNTHETIC_FIXTURE }
    enum QualificationState { INSUFFICIENT_EVIDENCE, REJECTED }

    Kind kind();

    default boolean executionAuthorized() { return false; }

    record Evidence(String sourceSha256, Instant observedAt, EvidenceProvenance provenance) {
        public Evidence {
            sourceSha256 = digest(sourceSha256);
            Objects.requireNonNull(observedAt, "Evidence observation time required");
            Objects.requireNonNull(provenance, "Evidence provenance required");
        }
    }

    /** Available means supplied data, not trusted, current, qualified or execution-ready. */
    record Available(Kind kind, List<Entry> entries, Evidence evidence) implements TradingProjection {
        public Available {
            Objects.requireNonNull(kind, "Projection kind required");
            entries = List.copyOf(Objects.requireNonNull(entries, "Entries required"));
            Objects.requireNonNull(evidence, "Evidence required");
            if (entries.size() > 1000) throw new IllegalArgumentException("Projection exceeds row bound");
            for (Entry entry : entries) {
                if (entry.kind() != kind) throw new IllegalArgumentException("Projection entry kind mismatch");
            }
            if (kind == Kind.RISK_STATUS && entries.size() != 1) {
                throw new IllegalArgumentException("Risk snapshot requires exactly one entry");
            }
        }
    }

    /** Missing data has no row, fabricated metric, qualification percentage or default zero. */
    record Unavailable(Kind kind, UnavailableReason reason) implements TradingProjection {
        public Unavailable {
            Objects.requireNonNull(kind, "Projection kind required");
            Objects.requireNonNull(reason, "Unavailable reason required");
        }
    }

    sealed interface Entry permits InstrumentView, StrategyView, QualificationView, RiskView,
            InstrumentEvidenceView, StrategyEvidenceView, QualificationEvidenceView, AccountRiskEvidenceView {
        Kind kind();
    }

    /** Complete captured evidence; new wire variants do not replace the legacy summaries. */
    record InstrumentEvidenceView(InstrumentSnapshotEvidence snapshot) implements Entry {
        public InstrumentEvidenceView { Objects.requireNonNull(snapshot); }
        @Override public Kind kind() { return Kind.INSTRUMENTS; }
    }

    record StrategyEvidenceView(StrategyDefinitionEvidence snapshot) implements Entry {
        public StrategyEvidenceView { Objects.requireNonNull(snapshot); }
        @Override public Kind kind() { return Kind.STRATEGIES; }
    }

    record QualificationEvidenceView(StockStrategyQualificationEvidence snapshot, QualificationState state)
            implements Entry {
        public QualificationEvidenceView {
            Objects.requireNonNull(snapshot);
            if (Objects.requireNonNull(state) != snapshot.decision())
                throw new IllegalArgumentException("Qualification decision must match captured evidence");
        }
        public QualificationEvidenceView(StockStrategyQualificationEvidence snapshot) {
            this(Objects.requireNonNull(snapshot), snapshot.decision());
        }
        @Override public Kind kind() { return Kind.QUALIFICATIONS; }
    }

    /** Account exposure is not a halt status or an independently captured policy hash. */
    record AccountRiskEvidenceView(AccountRiskSnapshotEvidence snapshot) implements Entry {
        public AccountRiskEvidenceView { Objects.requireNonNull(snapshot); }
        @Override public Kind kind() { return Kind.RISK_STATUS; }
    }

    record InstrumentView(UUID instrumentId, String exchange, String symbol,
                          String referenceSnapshotHash, String identityEvidenceHash) implements Entry {
        public InstrumentView {
            Objects.requireNonNull(instrumentId, "Instrument identity required");
            exchange = text(exchange);
            symbol = text(symbol);
            referenceSnapshotHash = digest(referenceSnapshotHash);
            identityEvidenceHash = digest(identityEvidenceHash);
        }
        @Override public Kind kind() { return Kind.INSTRUMENTS; }
    }

    record StrategyView(String strategyId, String strategyVersion, String definitionHash,
                        String definitionProvenance) implements Entry {
        public StrategyView {
            strategyId = text(strategyId);
            strategyVersion = text(strategyVersion);
            definitionHash = digest(definitionHash);
            definitionProvenance = text(definitionProvenance);
        }
        @Override public Kind kind() { return Kind.STRATEGIES; }
    }

    /** No QUALIFIED state until a separately reviewed genuine-evidence integration exists. */
    record QualificationView(UUID instrumentId, String strategyId, String strategyVersion,
                             String resultHash, QualificationState state) implements Entry {
        public QualificationView {
            Objects.requireNonNull(instrumentId, "Instrument identity required");
            strategyId = text(strategyId);
            strategyVersion = text(strategyVersion);
            resultHash = digest(resultHash);
            Objects.requireNonNull(state, "Qualification state required");
        }
        @Override public Kind kind() { return Kind.QUALIFICATIONS; }
    }

    /** A read snapshot only; halted=false never grants an order or a risk override. */
    record RiskView(boolean halted, String policyHash) implements Entry {
        public RiskView { policyHash = digest(policyHash); }
        @Override public Kind kind() { return Kind.RISK_STATUS; }
    }

    private static String digest(String value) {
        Objects.requireNonNull(value, "Evidence digest required");
        if (!value.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid SHA-256 digest");
        return value;
    }

    private static String text(String value) {
        Objects.requireNonNull(value, "Projection identifier required");
        if (value.isBlank() || value.length() > 256
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid projection identifier");
        }
        return value;
    }
}
