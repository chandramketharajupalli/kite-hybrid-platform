CREATE TABLE trading.orders (
    order_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    instrument_id UUID NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY','SELL')),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    order_type VARCHAR(16) NOT NULL,
    product VARCHAR(16) NOT NULL,
    validity VARCHAR(24) NOT NULL,
    limit_price NUMERIC(36,18),
    trigger_price NUMERIC(36,18),
    disclosed_quantity BIGINT NOT NULL CHECK (disclosed_quantity >= 0 AND disclosed_quantity <= quantity),
    variety VARCHAR(16) NOT NULL,
    state VARCHAR(24) NOT NULL,
    broker_order_id VARCHAR(128),
    failure_category VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    CHECK (updated_at >= created_at)
);

CREATE TABLE trading.order_idempotency (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    command_fingerprint CHAR(64) NOT NULL CHECK (command_fingerprint ~ '^[0-9a-f]{64}$'),
    order_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
