# Order reconciliation

Reconciliation is an explicit, read-only recovery checkpoint. It compares one
platform `OrderId` with the broker-independent `BrokerOrdersProvider` and
`BrokerTradesProvider` observations and writes an immutable audit record. It
does not invoke the order gateway and cannot place, modify, cancel, or retry a
command.

The primary matching key is a persisted broker order ID. Future orders also
persist a 20-character broker-neutral correlation ID before submission and send
it as Kite's `tag`. With no broker ID, exactly one broker observation carrying
that exact tag may be considered; zero or multiple matches remain unresolved or
conflicted. Instrument, side, quantity, order type, product, validity, and
limit price are also checked before attaching the broker ID or advancing local
state. Historical orders without a persisted correlation ID are never
retrofitted.

Reads are not an atomic broker snapshot. A failed/inconsistent read is
`BROKER_STATE_UNAVAILABLE`; an unknown status or invalid fill is a conflict.
An order missing from a read is `BROKER_ORDER_MISSING`, never an automatic
failure, because the current Kite read is subject to current-day scope and
eventual consistency. No startup job is enabled. Reconciliation never replaces
risk evaluation and never enables trading.
