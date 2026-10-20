-- OPT-IN ONLY. Keep default Flyway locations unchanged. Use public.flyway_schema_history
-- from migration zero, including V1-V13, on a separately authorized disposable database.
-- No credentials or raw session handles. No production activation or automatic retention deletion.
CREATE TABLE trading.operator_session_owners (
    issuer text NOT NULL CHECK (length(issuer) BETWEEN 1 AND 256),
    subject text NOT NULL CHECK (length(subject) BETWEEN 1 AND 256),
    PRIMARY KEY (issuer, subject)
);
CREATE TABLE trading.operator_sessions (
    digest text PRIMARY KEY CHECK (digest ~ '^[a-f0-9]{64}$'),
    issuer text NOT NULL,
    subject text NOT NULL,
    audience text NOT NULL CHECK (length(audience) BETWEEN 1 AND 256),
    authentication_event uuid NOT NULL,
    authenticated_at timestamptz NOT NULL,
    absolute_expires_at timestamptz NOT NULL,
    last_activity_at timestamptz NOT NULL,
    inactivity_seconds bigint NOT NULL CHECK (inactivity_seconds BETWEEN 1 AND 86400),
    permissions text[] NOT NULL CHECK (cardinality(permissions) BETWEEN 1 AND 7
        AND array_position(permissions,NULL) IS NULL AND permissions <@ ARRAY['INSTRUMENTS_READ','STRATEGIES_READ',
        'QUALIFICATIONS_READ','SIGNALS_READ','POSITIONS_READ','ORDERS_READ','RISK_READ']::text[]),
    accounts text[] NOT NULL CHECK (cardinality(accounts) BETWEEN 1 AND 64 AND array_position(accounts,NULL) IS NULL),
    policy_version text NOT NULL CHECK (length(policy_version) BETWEEN 1 AND 256),
    revoked boolean NOT NULL DEFAULT false,
    revision bigint NOT NULL CHECK (revision > 0),
    FOREIGN KEY (issuer,subject) REFERENCES trading.operator_session_owners(issuer,subject),
    CONSTRAINT operator_session_interval CHECK (authenticated_at < absolute_expires_at
        AND last_activity_at >= authenticated_at AND last_activity_at < absolute_expires_at
        AND inactivity_seconds <= extract(epoch FROM absolute_expires_at-authenticated_at))
);
CREATE UNIQUE INDEX operator_one_active_session ON trading.operator_sessions(issuer,subject) WHERE NOT revoked;
CREATE INDEX operator_auth_event ON trading.operator_sessions(issuer,subject,authentication_event);
CREATE INDEX operator_session_expiry ON trading.operator_sessions(absolute_expires_at);
CREATE TABLE trading.operator_session_audit (
    event_id uuid PRIMARY KEY,
    action text NOT NULL CHECK (action IN ('ESTABLISH','ROTATE','ACTIVITY','REVOKE')),
    observed_at timestamptz NOT NULL
);
CREATE FUNCTION trading.guard_operator_session() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'UPDATE' THEN RAISE EXCEPTION 'Session retention is not authorized' USING ERRCODE='23514'; END IF;
    IF ROW(NEW.digest,NEW.issuer,NEW.subject,NEW.audience,NEW.authentication_event,NEW.authenticated_at,
           NEW.absolute_expires_at,NEW.inactivity_seconds,NEW.permissions,NEW.accounts,NEW.policy_version)
       IS DISTINCT FROM ROW(OLD.digest,OLD.issuer,OLD.subject,OLD.audience,OLD.authentication_event,OLD.authenticated_at,
           OLD.absolute_expires_at,OLD.inactivity_seconds,OLD.permissions,OLD.accounts,OLD.policy_version)
       OR NEW.revision <> OLD.revision+1 OR NEW.last_activity_at < OLD.last_activity_at
       OR (OLD.revoked AND NOT NEW.revoked)
       OR (NEW.last_activity_at <> OLD.last_activity_at AND (OLD.revoked
           OR NEW.last_activity_at >= OLD.last_activity_at + OLD.inactivity_seconds * interval '1 second')) THEN
        RAISE EXCEPTION 'Original grant or lifecycle violation' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER operator_session_guard BEFORE UPDATE OR DELETE ON trading.operator_sessions
    FOR EACH ROW EXECUTE FUNCTION trading.guard_operator_session();
CREATE TRIGGER operator_session_no_truncate BEFORE TRUNCATE ON trading.operator_sessions
    FOR EACH STATEMENT EXECUTE FUNCTION trading.guard_operator_session();
-- Tombstones and audit are retained; retention requires separate reviewed authorization.

CREATE FUNCTION trading.guard_operator_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit retention is not authorized' USING ERRCODE='23514';
END $$;
CREATE TRIGGER operator_audit_append_only BEFORE UPDATE OR DELETE ON trading.operator_session_audit
    FOR EACH ROW EXECUTE FUNCTION trading.guard_operator_audit();
CREATE TRIGGER operator_audit_no_truncate BEFORE TRUNCATE ON trading.operator_session_audit
    FOR EACH STATEMENT EXECUTE FUNCTION trading.guard_operator_audit();
