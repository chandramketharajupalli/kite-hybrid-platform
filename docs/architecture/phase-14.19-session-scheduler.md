# Offline session scheduler

The Phase 14.19 scheduler accepts explicit synthetic clock commands. It has no daemon, broker adapter, credential loader, network polling or operational database. Calendar sessions are dated UTC instants interpreted in Asia/Kolkata; holidays, special hours and early closes are explicit calendar evidence. Synthetic calendars cannot authorize genuine operation.

`phase1419.scheduler.run` composes existing `entry_signal`, qualification admission, `Coordinator`, execution pricing, liquidity, sizing, costs and portfolio reports. The only shared-code changes extract the existing admission and exit-reference decisions into reusable helpers. Strategy definitions, qualification thresholds, risk limits and causal execution policy are unchanged.

Completed observations contain final OHLCV and must end at the current clock tick. Opening observations contain only a price and instrument identity. At a tick, completed-bar exits and marks are processed first, then signals from the completed prefix, then entries at the newly observed opening price. Prior completed volume controls quantity through the Phase 14.18B coordinator. Current entry-bar final volume, high, low and close are never sizing inputs. Exit prices are confirmed only when the completed exit candle is available. Missing exit liquidity retains the position and halts entries.

Signals are ordered by completion time, canonical universe rank and strategy ID. Each pair is unique, so the signal fingerprint breaks no remaining ties. One symbol cannot have competing open positions. Decimal cash, reservations, exposure, fee and daily-loss checks remain owned by the existing coordinator.

The normal lifecycle is CREATED, PREMARKET_CHECK, READY, SESSION_OPEN, RUNNING, ENTRY_CUTOFF, EOD_LIQUIDATION, RECONCILING, COMPLETED. Invalid input produces PAPER_HALTED. FAILED and COMPLETED are terminal. Explicit recovery records RECOVERY_PENDING and restores an incomplete accepted state; it cannot clear HALT. At EOD an existing HALT still permits valid exits, but cannot become a successful session.

Each clock command is one atomic persisted bundle containing chained session and portfolio events and reconciled account state. Exceptions before publication discard that uncommitted in-memory step. Recovery replays accepted bundles and commits the next command once. An exclusive lock prevents concurrent ownership; an abandoned lock or pending file fails closed for separate inspection. This is a tested single-process model, not distributed consensus or independent rollback resistance.

Software, registry, qualification, dataset, calendar, cutoff, full-family and both policy bindings form the session identity. Legacy or altered checkpoints are rejected. Completed sessions support read-only verification, never automatic reopening. Report publication precedes the final summary marker; missing or conflicting reports reject verification.
