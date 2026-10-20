-- Development-only synthetic evidence. Not in default db/migration.
-- Explicit opt-in tests must pin defaultSchema(public), schemas(public), and include
-- db/migration plus db/evidence from the first migration. Do not move an existing
-- public/trading flyway_schema_history or repair history. Existing databases require
-- separate environment authorization and history preflight; this file does neither.
-- No roles, grants, writer, cleanup job, data import or production activation.
-- Expiry denies reads; deletion/retention operations need separate approval.

-- CHECK rejects SQL NULL too: required JSON values cannot be missing or JSON null.
CREATE FUNCTION trading.evidence_required_fields(value JSONB, names TEXT[]) RETURNS BOOLEAN
LANGUAGE sql IMMUTABLE STRICT AS $$
    SELECT coalesce(bool_and(value ? name AND value->name <> 'null'::jsonb), false)
    FROM unnest(names) AS name;
$$;

CREATE TABLE trading.evidence_instrument_snapshots (
    evidence_id UUID PRIMARY KEY CHECK (evidence_id <> '00000000-0000-0000-0000-000000000000'),
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    revision BIGINT NOT NULL CHECK (revision > 0),
    source_identity VARCHAR(256) NOT NULL CHECK (btrim(source_identity) <> ''),
    authorization_reference VARCHAR(256) NOT NULL CHECK (btrim(authorization_reference) <> ''),
    source_fingerprint VARCHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[a-f0-9]{64}$'),
    payload_sha256 VARCHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[a-f0-9]{64}$'),
    origin VARCHAR(32) NOT NULL CHECK (origin = 'SYNTHETIC_FIXTURE'),
    retention VARCHAR(32) NOT NULL CHECK (retention = 'SYNTHETIC_ONLY'),
    observed_at TIMESTAMPTZ NOT NULL CHECK (isfinite(observed_at)),
    expires_at TIMESTAMPTZ NOT NULL CHECK (isfinite(expires_at) AND expires_at > observed_at),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object' AND octet_length(payload::text) <= 65536),
    CHECK ((payload #>> '{provenance,sourceIdentity}') IS NOT NULL
        AND payload #>> '{provenance,sourceIdentity}' = source_identity),
    CHECK ((payload #>> '{provenance,fingerprint}') IS NOT NULL
        AND payload #>> '{provenance,fingerprint}' = source_fingerprint),
    CHECK (payload #>> '{provenance,origin}' IS NOT NULL AND payload #>> '{provenance,origin}' = origin),
    CHECK (payload #>> '{provenance,retention}' IS NOT NULL AND payload #>> '{provenance,retention}' = retention),
    CHECK (payload #>> '{provenance,observedAt}' IS NOT NULL
        AND (payload #>> '{provenance,observedAt}')::timestamptz = observed_at),
    CHECK (payload #>> '{provenance,expiresAt}' IS NOT NULL
        AND (payload #>> '{provenance,expiresAt}')::timestamptz = expires_at),
    instrument_id UUID NOT NULL CHECK (instrument_id <> '00000000-0000-0000-0000-000000000000'),
    snapshot_version BIGINT NOT NULL CHECK (snapshot_version > 0),
    UNIQUE (source_identity, instrument_id, snapshot_version),
    UNIQUE (evidence_id, source_identity, instrument_id),
    CHECK (payload->>'instrumentId' IS NOT NULL AND (payload->>'instrumentId')::uuid = instrument_id),
    CHECK (payload->>'snapshotVersion' IS NOT NULL AND (payload->>'snapshotVersion')::bigint = snapshot_version),
    CHECK (jsonb_typeof(payload->'exchange') = 'string' AND length(payload->>'exchange') BETWEEN 1 AND 256),
    CHECK (jsonb_typeof(payload->'symbol') = 'string' AND length(payload->>'symbol') BETWEEN 1 AND 256),
    CHECK (trading.evidence_required_fields(payload, ARRAY['instrumentId','snapshotVersion','exchange','symbol','provenance']) AND payload ? 'token')
);
CREATE INDEX evidence_instrument_snapshots_read ON trading.evidence_instrument_snapshots (instrument_id, snapshot_version, source_identity, observed_at DESC, revision DESC, evidence_id DESC);

CREATE TABLE trading.evidence_strategy_snapshots (
    evidence_id UUID PRIMARY KEY CHECK (evidence_id <> '00000000-0000-0000-0000-000000000000'),
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    revision BIGINT NOT NULL CHECK (revision > 0),
    source_identity VARCHAR(256) NOT NULL CHECK (btrim(source_identity) <> ''),
    authorization_reference VARCHAR(256) NOT NULL CHECK (btrim(authorization_reference) <> ''),
    source_fingerprint VARCHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[a-f0-9]{64}$'),
    payload_sha256 VARCHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[a-f0-9]{64}$'),
    origin VARCHAR(32) NOT NULL CHECK (origin = 'SYNTHETIC_FIXTURE'),
    retention VARCHAR(32) NOT NULL CHECK (retention = 'SYNTHETIC_ONLY'),
    observed_at TIMESTAMPTZ NOT NULL CHECK (isfinite(observed_at)),
    expires_at TIMESTAMPTZ NOT NULL CHECK (isfinite(expires_at) AND expires_at > observed_at),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object' AND octet_length(payload::text) <= 65536),
    CHECK ((payload #>> '{provenance,sourceIdentity}') IS NOT NULL
        AND payload #>> '{provenance,sourceIdentity}' = source_identity),
    CHECK ((payload #>> '{provenance,fingerprint}') IS NOT NULL
        AND payload #>> '{provenance,fingerprint}' = source_fingerprint),
    CHECK (payload #>> '{provenance,origin}' IS NOT NULL AND payload #>> '{provenance,origin}' = origin),
    CHECK (payload #>> '{provenance,retention}' IS NOT NULL AND payload #>> '{provenance,retention}' = retention),
    CHECK (payload #>> '{provenance,observedAt}' IS NOT NULL
        AND (payload #>> '{provenance,observedAt}')::timestamptz = observed_at),
    CHECK (payload #>> '{provenance,expiresAt}' IS NOT NULL
        AND (payload #>> '{provenance,expiresAt}')::timestamptz = expires_at),
    strategy_id VARCHAR(64) NOT NULL CHECK (strategy_id ~ '^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}$'),
    strategy_version VARCHAR(32) NOT NULL CHECK (strategy_version ~ '^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,31}$'),
    UNIQUE (source_identity, strategy_id, strategy_version),
    UNIQUE (evidence_id, source_identity, strategy_id, strategy_version),
    CHECK (payload->>'strategyId' IS NOT NULL AND payload->>'strategyId' = strategy_id),
    CHECK (payload->>'strategyVersion' IS NOT NULL AND payload->>'strategyVersion' = strategy_version),
    CHECK (jsonb_typeof(payload->'parameters') = 'object'),
    CHECK (jsonb_typeof(payload->'entryConditions') = 'array' AND jsonb_array_length(payload->'entryConditions') BETWEEN 1 AND 1024),
    CHECK (jsonb_typeof(payload->'exitConditions') = 'array' AND jsonb_array_length(payload->'exitConditions') BETWEEN 1 AND 1024),
    CHECK (jsonb_typeof(payload->'universe') = 'array' AND jsonb_array_length(payload->'universe') BETWEEN 1 AND 1024),
    CHECK (trading.evidence_required_fields(payload, ARRAY['strategyId','strategyVersion','timeframe','parameters','entryConditions','exitConditions','universe','provenance']))
);
CREATE INDEX evidence_strategy_snapshots_read ON trading.evidence_strategy_snapshots (strategy_id, strategy_version, source_identity, observed_at DESC, revision DESC, evidence_id DESC);

CREATE TABLE trading.evidence_qualification_snapshots (
    evidence_id UUID PRIMARY KEY CHECK (evidence_id <> '00000000-0000-0000-0000-000000000000'),
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    revision BIGINT NOT NULL CHECK (revision > 0),
    source_identity VARCHAR(256) NOT NULL CHECK (btrim(source_identity) <> ''),
    authorization_reference VARCHAR(256) NOT NULL CHECK (btrim(authorization_reference) <> ''),
    source_fingerprint VARCHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[a-f0-9]{64}$'),
    payload_sha256 VARCHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[a-f0-9]{64}$'),
    origin VARCHAR(32) NOT NULL CHECK (origin = 'SYNTHETIC_FIXTURE'),
    retention VARCHAR(32) NOT NULL CHECK (retention = 'SYNTHETIC_ONLY'),
    observed_at TIMESTAMPTZ NOT NULL CHECK (isfinite(observed_at)),
    expires_at TIMESTAMPTZ NOT NULL CHECK (isfinite(expires_at) AND expires_at > observed_at),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object' AND octet_length(payload::text) <= 65536),
    CHECK ((payload #>> '{provenance,sourceIdentity}') IS NOT NULL
        AND payload #>> '{provenance,sourceIdentity}' = source_identity),
    CHECK ((payload #>> '{provenance,fingerprint}') IS NOT NULL
        AND payload #>> '{provenance,fingerprint}' = source_fingerprint),
    CHECK (payload #>> '{provenance,origin}' IS NOT NULL AND payload #>> '{provenance,origin}' = origin),
    CHECK (payload #>> '{provenance,retention}' IS NOT NULL AND payload #>> '{provenance,retention}' = retention),
    CHECK (payload #>> '{provenance,observedAt}' IS NOT NULL
        AND (payload #>> '{provenance,observedAt}')::timestamptz = observed_at),
    CHECK (payload #>> '{provenance,expiresAt}' IS NOT NULL
        AND (payload #>> '{provenance,expiresAt}')::timestamptz = expires_at),
    instrument_id UUID NOT NULL,
    instrument_evidence_id UUID NOT NULL,
    strategy_id VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    strategy_evidence_id UUID NOT NULL,
    timeframe VARCHAR(128) NOT NULL CHECK (btrim(timeframe) <> ''),
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL CHECK (period_end > period_start),
    policy_id VARCHAR(256) NOT NULL CHECK (btrim(policy_id) <> ''),
    policy_version VARCHAR(256) NOT NULL CHECK (btrim(policy_version) <> ''),
    evaluated_trades BIGINT NOT NULL CHECK (evaluated_trades >= 0),
    wins BIGINT NOT NULL CHECK (wins >= 0 AND wins <= evaluated_trades),
    losses BIGINT NOT NULL CHECK (losses >= 0 AND losses = evaluated_trades - wins),
    transaction_costs NUMERIC NOT NULL CHECK (transaction_costs >= 0 AND transaction_costs <> 'NaN'::numeric AND transaction_costs <> 'Infinity'::numeric),
    slippage_basis_points NUMERIC NOT NULL CHECK (slippage_basis_points >= 0 AND slippage_basis_points <> 'NaN'::numeric AND slippage_basis_points <> 'Infinity'::numeric),
    UNIQUE (source_identity, instrument_id, strategy_id, strategy_version, timeframe, period_start, period_end, policy_id, policy_version, revision),
    FOREIGN KEY (instrument_evidence_id, source_identity, instrument_id)
        REFERENCES trading.evidence_instrument_snapshots(evidence_id, source_identity, instrument_id),
    FOREIGN KEY (strategy_evidence_id, source_identity, strategy_id, strategy_version)
        REFERENCES trading.evidence_strategy_snapshots(evidence_id, source_identity, strategy_id, strategy_version),
    CHECK (observed_at >= period_end),
    CHECK (payload->>'instrumentId' IS NOT NULL AND (payload->>'instrumentId')::uuid = instrument_id),
    CHECK (payload->>'strategyId' IS NOT NULL AND payload->>'strategyId' = strategy_id),
    CHECK (payload->>'strategyVersion' IS NOT NULL AND payload->>'strategyVersion' = strategy_version),
    CHECK (payload->>'timeframe' IS NOT NULL AND payload->>'timeframe' = timeframe),
    CHECK (payload->>'periodStart' IS NOT NULL AND (payload->>'periodStart')::timestamptz = period_start),
    CHECK (payload->>'periodEnd' IS NOT NULL AND (payload->>'periodEnd')::timestamptz = period_end),
    CHECK ((payload->>'evaluatedTrades')::bigint = evaluated_trades AND (payload->>'wins')::bigint = wins AND (payload->>'losses')::bigint = losses),
    CHECK ((payload->>'transactionCosts')::numeric = transaction_costs AND (payload->>'slippageBasisPoints')::numeric = slippage_basis_points),
    CHECK (payload->'policy' = 'null'::jsonb OR (trading.evidence_required_fields(payload->'policy', ARRAY['policyId','version','fingerprint','minimumTrades','minimumWinPercentage','remainingChecksPassed']) AND payload #>> '{policy,policyId}' = policy_id AND payload #>> '{policy,version}' = policy_version)),
    CHECK (trading.evidence_required_fields(payload->'dataset', ARRAY['sourceIdentity','fingerprint','observedAt','expiresAt','origin','retention'])),
    CHECK (payload #>> '{dataset,origin}' IS NOT NULL AND payload #>> '{dataset,origin}' = 'SYNTHETIC_FIXTURE'),
    CHECK (payload #>> '{dataset,retention}' IS NOT NULL AND payload #>> '{dataset,retention}' = 'SYNTHETIC_ONLY'),
    CHECK (payload #>> '{dataset,sourceIdentity}' IS NOT NULL AND payload #>> '{dataset,sourceIdentity}' = source_identity),
    CHECK ((payload #>> '{dataset,observedAt}')::timestamptz <= observed_at
        AND (payload #>> '{dataset,expiresAt}')::timestamptz >= expires_at),
    CHECK (trading.evidence_required_fields(payload, ARRAY['instrumentId','strategyId','strategyVersion','timeframe','periodStart','periodEnd','evaluatedTrades','wins','losses','costModel','transactionCosts','slippageBasisPoints','dataset','provenance']) AND payload ? 'policy')
);
CREATE INDEX evidence_qualification_snapshots_read ON trading.evidence_qualification_snapshots (instrument_id, strategy_id, strategy_version, timeframe, period_start, period_end, policy_id, policy_version, source_identity, observed_at DESC, revision DESC, evidence_id DESC);

CREATE TABLE trading.evidence_account_risk_snapshots (
    evidence_id UUID PRIMARY KEY CHECK (evidence_id <> '00000000-0000-0000-0000-000000000000'),
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    revision BIGINT NOT NULL CHECK (revision > 0),
    source_identity VARCHAR(256) NOT NULL CHECK (btrim(source_identity) <> ''),
    authorization_reference VARCHAR(256) NOT NULL CHECK (btrim(authorization_reference) <> ''),
    source_fingerprint VARCHAR(64) NOT NULL CHECK (source_fingerprint ~ '^[a-f0-9]{64}$'),
    payload_sha256 VARCHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[a-f0-9]{64}$'),
    origin VARCHAR(32) NOT NULL CHECK (origin = 'SYNTHETIC_FIXTURE'),
    retention VARCHAR(32) NOT NULL CHECK (retention = 'SYNTHETIC_ONLY'),
    observed_at TIMESTAMPTZ NOT NULL CHECK (isfinite(observed_at)),
    expires_at TIMESTAMPTZ NOT NULL CHECK (isfinite(expires_at) AND expires_at > observed_at),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object' AND octet_length(payload::text) <= 65536),
    CHECK ((payload #>> '{provenance,sourceIdentity}') IS NOT NULL
        AND payload #>> '{provenance,sourceIdentity}' = source_identity),
    CHECK ((payload #>> '{provenance,fingerprint}') IS NOT NULL
        AND payload #>> '{provenance,fingerprint}' = source_fingerprint),
    CHECK (payload #>> '{provenance,origin}' IS NOT NULL AND payload #>> '{provenance,origin}' = origin),
    CHECK (payload #>> '{provenance,retention}' IS NOT NULL AND payload #>> '{provenance,retention}' = retention),
    CHECK (payload #>> '{provenance,observedAt}' IS NOT NULL
        AND (payload #>> '{provenance,observedAt}')::timestamptz = observed_at),
    CHECK (payload #>> '{provenance,expiresAt}' IS NOT NULL
        AND (payload #>> '{provenance,expiresAt}')::timestamptz = expires_at),
    account_scope VARCHAR(256) NOT NULL CHECK (btrim(account_scope) <> ''),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    exposure NUMERIC NOT NULL CHECK (exposure >= 0 AND exposure <> 'NaN'::numeric AND exposure <> 'Infinity'::numeric),
    exposure_limit NUMERIC NOT NULL CHECK (exposure_limit > 0 AND exposure_limit <> 'NaN'::numeric AND exposure_limit <> 'Infinity'::numeric),
    UNIQUE (source_identity, account_scope, observed_at, revision),
    CHECK (payload->>'accountScope' IS NOT NULL AND payload->>'accountScope' = account_scope),
    CHECK (payload->>'currency' IS NOT NULL AND payload->>'currency' = currency),
    CHECK ((payload->>'exposure')::numeric = exposure AND (payload->>'exposureLimit')::numeric = exposure_limit),
    CHECK (jsonb_typeof(payload->'additionalLimits') = 'object'),
    CHECK (trading.evidence_required_fields(payload, ARRAY['accountScope','currency','exposure','exposureLimit','additionalLimits','provenance']))
);
CREATE INDEX evidence_account_risk_snapshots_read ON trading.evidence_account_risk_snapshots (account_scope, source_identity, observed_at DESC, revision DESC, evidence_id DESC);

CREATE FUNCTION trading.reject_evidence_snapshot_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Evidence snapshots are immutable; no update/delete/truncate permission' USING ERRCODE = '55000';
END;
$$;
CREATE TRIGGER evidence_instrument_snapshots_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON trading.evidence_instrument_snapshots FOR EACH STATEMENT EXECUTE FUNCTION trading.reject_evidence_snapshot_mutation();
CREATE TRIGGER evidence_strategy_snapshots_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON trading.evidence_strategy_snapshots FOR EACH STATEMENT EXECUTE FUNCTION trading.reject_evidence_snapshot_mutation();
CREATE TRIGGER evidence_qualification_snapshots_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON trading.evidence_qualification_snapshots FOR EACH STATEMENT EXECUTE FUNCTION trading.reject_evidence_snapshot_mutation();
CREATE TRIGGER evidence_account_risk_snapshots_immutable BEFORE UPDATE OR DELETE OR TRUNCATE ON trading.evidence_account_risk_snapshots FOR EACH STATEMENT EXECUTE FUNCTION trading.reject_evidence_snapshot_mutation();

-- Exact immutable reference linkage; no strategy from another stock/timeframe or later period.
CREATE FUNCTION trading.check_evidence_qualification_links() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM trading.evidence_instrument_snapshots i
        JOIN trading.evidence_strategy_snapshots s ON s.evidence_id = NEW.strategy_evidence_id
        WHERE i.evidence_id = NEW.instrument_evidence_id
          AND i.authorization_reference = NEW.authorization_reference
          AND s.authorization_reference = NEW.authorization_reference
          AND i.observed_at <= NEW.period_start AND s.observed_at <= NEW.period_start
          AND i.expires_at >= NEW.expires_at AND s.expires_at >= NEW.expires_at
          AND s.payload->>'timeframe' = NEW.timeframe
          AND s.payload->'universe' @> jsonb_build_array(NEW.instrument_id::text)
    ) THEN
        RAISE EXCEPTION 'Invalid qualification reference scope/chronology' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER evidence_qualification_links BEFORE INSERT ON trading.evidence_qualification_snapshots
FOR EACH ROW EXECUTE FUNCTION trading.check_evidence_qualification_links();
