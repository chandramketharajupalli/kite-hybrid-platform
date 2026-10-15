# Frozen Phase 14.19 plan

Baseline d228ce183af7d208899ec23eef756e1a5f6e308a; develop and origin/develop match, clean worktree/index, all 18 Phase14.18B paths committed. Baseline tracked hashes captured externally. Phase14.18B artifacts and 247 external records verified; frozen G1, 18 sources, 63 research files, K/L and 13 newline reconciliations pass; universe remains 1,115 enabled NSE symbols and family 13,380.

Risk policy Phase1418RiskPolicyCanonical.v2 fingerprint 3dd7a1cf99042ce027011f6a99ca1f298ab62c6254bc85e584171250a925c7d1. Execution Phase1418BCausalExecution.v1 fingerprint 2078a10ecad7df0faef638e3e6e37eb4808c4a8c33af5fb81883b427d0ed5925. Both are mandatory bindings, never silently upgraded.

## Interfaces and minimal refactoring

Reuse phase1415 entry_signal, signal event IDs, causal liquidity/timing and exit pricing; phase1417 numerical qualification/admission; phase1418 Coordinator accounting, reservations, sizing, risk, HALT and reports. Extract the existing exit-price decision into a pure helper in phase1415/execution.py and existing admission checks into a pure helper in phase1417/paper.py, updating their original callers without changing behavior. These are the only two existing files permitted to change. Add phase1419/__init__.py and scheduler.py, tests, read-only verifier and requested documentation/evidence. Do not modify strategy definitions, risk limits, execution policy, frozen implementation or any historical report.

## Architecture and causal events

Single-writer offline runner advances only through explicit UTC clock commands. Calendar declares dated sessions and holidays with Asia/Kolkata semantics, source hash and version; synthetic calendars never authorize genuine readiness. Separate completed-candle observations (available at end) and opening-price observations (available at start). No unfinished high/low/close/volume is passed as an opening observation. Validate both against the immutable authorized synthetic dataset. Evaluate indicators only from received completed prefixes, process exits before marks/signals, order signals by completion/canonical symbol/strategy/fingerprint, then execute pending entries with explicit opening-price events. Use unchanged prior-completed-volume proxy and all-or-none fills. Current exit evidence is available only at completion. Unfilled exits retain positions and halt.

Readiness checks numerical qualification, family identity, authorization, dataset/calendar/corporate-action identity, registry, both policies, cash and EOD settings. Only labeled synthetic authorization is supported; genuine readiness fails closed. Cutoff is versioned, explicit and before close. Existing positions may exit after cutoff/HALT. Missing required observations halt without fabricating fills.

## State and recovery

CREATED -> PREMARKET_CHECK -> READY -> SESSION_OPEN -> RUNNING -> ENTRY_CUTOFF -> EOD_LIQUIDATION -> RECONCILING -> COMPLETED. PAPER_HALTED and FAILED prevent entries; RECOVERY_PENDING records explicit incomplete-run recovery. Invalid transitions, duplicates, clock rollback, policy changes and completion with exposure fail closed. Reopening completed sessions is forbidden; explicit read-only replay verifies their reports.

Persist atomic command/event bundles after each clock step, including complete reconciled coordinator state and chained session audit. Crash during reservation/fill/exit before bundle commit discards that uncommitted in-memory step; deterministic reconstruction reapplies it once. Existing accepted bundles must match replay byte-for-byte. Exclusive single-writer lock; stale real-process lock or pending publish file blocks recovery pending external inspection. Never automatically clear HALT. Missing/corrupt events or completed reports reject replay. Local hashes do not resist independent rollback. Simulated crash hooks cover state and portfolio event boundaries.

## Budgets and validation

One worker, <=1,115 symbols, <=6,690 distinct pair requests, <=100,000 input bars, <=1,115 observations per clock batch, <=200,000 journal events, <=256 MiB report output. Per test gate timeout 1,200 seconds. No throughput claims. Python -B, PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain -p no:cacheprovider, external temp/JUnit/logs/mypy cache, Ruff --no-cache. Focused scheduler tests and full Python suite, Ruff/mypy; no Java changes. Preserve failures. Recheck all baseline hashes except two authorized source files after every gate.

Mandatory tests cover readiness/policy legacy rejection, holidays/special sessions/timezones, lifecycle, incomplete/duplicate/out-of-order events, cutoff, ordering/conflicts, risk/reservations/reconciliation, stop/target/EOD/missing liquidity, HALT persistence, crash/replay/corruption/binding mismatch, future-volume invariance and no broker dependency. Generate synthetic complete and unresolved-session examples, full report inventory, fingerprints, protected/secret/canary/LF/Git-byte checks and two deterministic manifest replays.

Stop on protected changes, unrelated files, ambiguous state, incompatible policy or unresolved causal/accounting failure. Scope amendments are additive. Expected deliverables: five requested documents, new scheduler package/tests/verifier, additive synthetic reports and generation-1 evidence-manifest. Keep raw receipts external. No broker/credential/network/download/order/HALT/infrastructure access. No staging, commit or push. Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN; July SEALED; operational NO_GO.
