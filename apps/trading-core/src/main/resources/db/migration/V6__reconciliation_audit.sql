CREATE TABLE trading.reconciliation_decisions (
    reconciliation_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES trading.orders(order_id),
    state_before VARCHAR(24) NOT NULL,
    state_after VARCHAR(24),
    outcome VARCHAR(32) NOT NULL,
    reason VARCHAR(48) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    local_version BIGINT NOT NULL CHECK (local_version >= 0)
);

CREATE TABLE trading.reconciliation_trades (
    reconciliation_id UUID NOT NULL REFERENCES trading.reconciliation_decisions(reconciliation_id),
    broker_trade_id VARCHAR(128) NOT NULL,
    broker_order_id VARCHAR(128) NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    price NUMERIC(36,18) NOT NULL,
    filled_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (broker_trade_id, broker_order_id)
);

CREATE INDEX reconciliation_decisions_order_idx ON trading.reconciliation_decisions(order_id, observed_at);
