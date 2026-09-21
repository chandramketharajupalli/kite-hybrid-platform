# ADR-013: Phase 5B.1 order command foundation remains execution-disabled

Phase 5B.1 introduces broker-independent place, modify and cancel commands,
validated durable order records, idempotency claims, optimistic concurrency and
an execution gateway. The default is `KITE_ORDER_EXECUTION_ENABLED=false`.
No application startup, authentication restoration, market-data connection or
trading-read operation can submit an order.

The pipeline is:

```
OrderCommand -> validation -> PostgreSQL order record -> idempotency
             -> future risk approval -> execution safety gate -> broker adapter
```

Platform UUIDs are the order identity. A Kite order ID is only an attached
external reference. Callers provide a platform order ID for modify/cancel; they
cannot provide an arbitrary broker order ID. The regular Kite adapter is wired
only when the explicit execution property is true, and automated tests use fake
or in-process HTTP infrastructure.

Idempotency is claimed in PostgreSQL before any broker invocation. A process
crash after `SUBMITTING` is persisted and after the broker request may have been
sent is ambiguous. The record remains `SUBMITTING` (or `CANCEL_PENDING` for a
cancel), and the service never retries it automatically. Phase 8 must reconcile
that state using broker-side evidence before any recovery action.

This phase does not implement portfolio risk approval, reconciliation, fills,
or authorization to trade. Holding an instrument in a portfolio is not a
tradability decision.
