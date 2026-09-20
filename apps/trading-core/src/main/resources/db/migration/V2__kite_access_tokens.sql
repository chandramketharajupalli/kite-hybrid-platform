-- Only ciphertext is persisted. The AES-256 key is provided independently through configuration.
CREATE TABLE trading.kite_access_tokens (
    store_id VARCHAR(64) PRIMARY KEY CHECK (store_id ~ '^[0-9a-f]{64}$'),
    token_ciphertext BYTEA NOT NULL CHECK (octet_length(token_ciphertext) > 16),
    nonce BYTEA NOT NULL CHECK (octet_length(nonce) = 12),
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > issued_at)
);
