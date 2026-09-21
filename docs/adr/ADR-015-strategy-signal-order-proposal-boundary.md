# ADR-015: Strategy proposals stop before execution

Strategies consume only broker-independent `InstrumentId`, `Tick`, market-data
health, a deterministic clock, and typed strategy configuration. They return an
immutable `Signal`. A signal is converted into a broker-independent
`TradeIntent`; only the strategy coordinator may pass an actionable intent to
`OrderApplicationService.place()`.

The coordinator persists a deterministic strategy evaluation before creating a
validated order. The key is strategy ID, immutable strategy version, and a
bounded logical event key. PostgreSQL uniqueness and the order idempotency key
make replay and concurrent duplicate processing resolve to one order.

The reference threshold strategy is test architecture only: below threshold
means BUY, above means SELL, exact threshold means HOLD. Missing, stale,
degraded, or halted input produces no actionable intent. Optional risk
evaluation may advance `VALIDATED` to `RISK_APPROVED`, then the flow stops.
No strategy code can invoke execution, the gateway, Kite infrastructure, or
reconciliation infrastructure. `RISK_APPROVED` remains an explicit later
execution checkpoint.
