CREATE TABLE trading.execution_authorizations (
    authorization_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES trading.orders(order_id),
    allowed BOOLEAN NOT NULL,
    reason VARCHAR(48) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    order_version BIGINT NOT NULL CHECK (order_version >= 0),
    policy_version VARCHAR(128) NOT NULL
);
CREATE INDEX execution_authorizations_order_idx ON trading.execution_authorizations(order_id, evaluated_at);
