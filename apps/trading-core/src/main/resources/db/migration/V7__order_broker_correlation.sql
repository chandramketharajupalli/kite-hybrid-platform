ALTER TABLE trading.orders ADD COLUMN broker_correlation_id VARCHAR(20);
ALTER TABLE trading.orders ADD CONSTRAINT orders_broker_correlation_format
    CHECK (broker_correlation_id IS NULL OR broker_correlation_id ~ '^[A-Za-z0-9]{20}$');
CREATE UNIQUE INDEX orders_broker_correlation_uidx ON trading.orders(broker_correlation_id)
    WHERE broker_correlation_id IS NOT NULL;
