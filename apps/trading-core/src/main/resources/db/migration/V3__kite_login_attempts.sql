-- Browser nonces are represented only by SHA-256 digests. No broker credentials are stored here.
CREATE TABLE trading.kite_login_attempts (
    store_id VARCHAR(64) NOT NULL CHECK (store_id ~ '^[0-9a-f]{64}$'),
    nonce_digest VARCHAR(64) NOT NULL CHECK (nonce_digest ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (store_id, nonce_digest),
    CHECK (expires_at > created_at AND expires_at <= created_at + INTERVAL '10 minutes')
);

CREATE INDEX kite_login_attempts_expiry_idx
    ON trading.kite_login_attempts (store_id, expires_at, nonce_digest);
