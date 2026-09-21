# ADR-014: Read-only order reconciliation foundation

Reconciliation compares durable platform orders with broker-independent order
and trade observations. It never depends on `OrderExecutionGateway` and never
places, modifies, cancels, or resubmits an order. PostgreSQL transaction
locking and optimistic versions make each audit/state checkpoint atomic.

Future platform orders persist a 20-character broker-neutral correlation ID
before risk approval and submission. It is sent as Kite's bounded alphanumeric
`tag` and is returned by order reads. Orders with a persisted broker order ID are matched by that ID and then
validated against instrument, side, quantity, type, product, validity, and
limit price. Ambiguous `SUBMITTING` orders without a broker ID are left
ambiguous unless their persisted correlation ID has exactly one matching broker
observation. No heuristic matching is permitted. Tags are not guaranteed unique
by Kite, so PostgreSQL uniqueness and exact consistency checks remain the
platform's responsibility. Historical orders without a persisted correlation
ID are never retrofitted.

Broker order and trade reads are separate point-in-time observations. A read
failure, unknown status, identity conflict, or excessive/invalid fill fails
closed and records an immutable bounded decision. Current-day scope and broker
eventual consistency mean an absent order is recorded as missing; it is never
immediately marked failed or cancelled.

Repeated observations are idempotent. Individual broker trades are deduplicated
by `(broker_trade_id, broker_order_id)`. Startup performs no reconciliation and
no broker I/O. Operator intervention or a later reconciliation policy is
required for ambiguous crash windows and unresolved discrepancies. Risk
approval remains a separate prerequisite for execution.
