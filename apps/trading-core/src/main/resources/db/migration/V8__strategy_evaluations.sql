CREATE TABLE trading.strategy_evaluations (
    evaluation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    strategy_id VARCHAR(64) NOT NULL,
    strategy_version VARCHAR(32) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    signal_id UUID NOT NULL,
    instrument_id UUID NOT NULL,
    action VARCHAR(8) NOT NULL CHECK (action IN ('BUY','SELL','HOLD')),
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    reference_price NUMERIC(36,18) NOT NULL,
    reason VARCHAR(48) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    intent_id UUID,
    order_id UUID REFERENCES trading.orders(order_id),
    UNIQUE(strategy_id, strategy_version, event_key),
    UNIQUE(signal_id)
);
