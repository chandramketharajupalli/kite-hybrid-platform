package com.kitehybrid.platform.trading.application;

import com.kitehybrid.platform.trading.domain.*;
import com.kitehybrid.platform.shared.domain.Identifiers.InstrumentId;
import com.kitehybrid.platform.strategy.domain.StrategyDefinition;
import java.time.Duration;
import java.time.Instant;

/**
 * Read-only evidence query boundary v1. Implementations may read approved persisted snapshots only;
 * never invoke providers, RiskService.evaluate(), refreshes, research jobs, or persistence writes.
 * No production implementation/bean exists here. Defaults return MISSING with no invented data.
 *
 * Future PostgreSQL design (proposal, not an existing schema or migration):
 * - Separate immutable payloads for each snapshot kind; UUID evidence_id primary keys, source
 *   identity, schema version, SHA-256, observed_at/expires_at TIMESTAMPTZ, provenance and permission
 *   bindings. Use NUMERIC for monetary values, BIGINT counts, explicit currency and timeframe units.
 * - Uniqueness: instrument/source/snapshot-version; strategy-id/version (fingerprint conflicts reject);
 *   instrument/strategy-id/version/timeframe/period/policy-id/version/evaluation-fingerprint;
 *   account/source/observation-time/fingerprint. Never overwrite an existing evidence version.
 * - Identical key and canonical payload hash is an idempotent replay; differing payload is a
 *   conflict, never an upsert. Canonical encoding/schema evolution requires a reviewed contract.
 * - Index exact query keys and observed_at descending; validity and retention must be checked on
 *   every read using an injected clock. Do not silently choose a different strategy version.
 * - Reject ingestion absent independently approved source/retention bindings, enforce scope and
 *   expiry, and prevent reads after revocation/expiry. Cleanup belongs to separately approved
 *   retention operations, not GET. Audit metadata itself requires retention authorization.
 * - Introduce new tables through separately approved additive Flyway migrations; do not rewrite
 *   historic checksums or overload strategy_evaluations/risk_decisions. Use a SELECT-only role for
 *   adapters. Versioned readers must reject unknown schemas rather than fabricate defaults.
 * This design neither provisions a database nor approves genuine data or production authorization.
 */
public interface EvidenceSnapshotQueryPort {
    default EvidenceAvailability<InstrumentSnapshotEvidence> instrument(InstrumentId id, long version) {
        return EvidenceAvailability.missing();
    }
    default EvidenceAvailability<StrategyDefinitionEvidence> strategy(StrategyDefinition definition) {
        return EvidenceAvailability.missing();
    }
    default EvidenceAvailability<StockStrategyQualificationEvidence> qualification(InstrumentId id,
            StrategyDefinition definition, Duration timeframe, Instant periodStart, Instant periodEnd,
            String policyId, String policyVersion) {
        return EvidenceAvailability.missing();
    }
    default EvidenceAvailability<AccountRiskSnapshotEvidence> accountRisk(String accountScope) {
        return EvidenceAvailability.missing();
    }
    static EvidenceSnapshotQueryPort unavailable() { return new EvidenceSnapshotQueryPort() { }; }
}
