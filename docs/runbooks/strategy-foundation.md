# Strategy and signal foundation

The reference strategy is deterministic test code, not investment logic. The
pipeline is:

`market-independent input → Signal → TradeIntent → persisted evaluation → VALIDATED order → optional risk evaluation → STOP`

`Signal` describes an observation. `TradeIntent` proposes an action. An order
is the durable command accepted by the order application service. `VALIDATED`
does not mean risk approved, and `RISK_APPROVED` does not execute an order.

Evaluations are keyed by strategy ID, immutable strategy version, and a bounded
event key. PostgreSQL uniqueness and order idempotency make restart replay and
concurrent duplicate evaluation safe. HOLD, missing/stale/degraded market data,
and the existing emergency stop produce no order. Strategy history stores only
bounded typed fields; it never stores broker payloads, WebSocket packets, or
credentials.
