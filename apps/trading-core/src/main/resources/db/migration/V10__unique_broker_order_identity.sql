-- Single-account platform: one broker acknowledgement cannot identify two local orders.
-- Historical NULL identities remain valid. Existing duplicates require operator investigation;
-- migration intentionally fails rather than guessing which order owns an identity.
CREATE UNIQUE INDEX orders_broker_order_id_unique
    ON trading.orders(broker_order_id) WHERE broker_order_id IS NOT NULL;
