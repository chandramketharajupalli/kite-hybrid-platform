CREATE TABLE trading.risk_decisions (
    order_id UUID PRIMARY KEY REFERENCES trading.orders(order_id),
    order_version BIGINT NOT NULL CHECK (order_version >= 0),
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('APPROVED', 'REJECTED')),
    reason VARCHAR(48) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    policy_version VARCHAR(80) NOT NULL CHECK (policy_version ~ '^cash-v1:[0-9a-f]{64}$'),
    CHECK ((outcome = 'APPROVED') = (reason = 'APPROVED'))
);
