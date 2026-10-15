# Phase 14.18B frozen correction plan

Baseline: bb4622a12bf143cfc9d01c8a3ec8b5d7ef281218; develop and local origin/develop match; clean index/worktree. All 1,255 raw tracked hashes and 13 Phase 14.0A newline reconciliations verified before edits. G1, 18 frozen sources, 63 research files and K/L governance pass.

## Reproduction and available information

The external Phase 14.19 diagnostic changes only the 09:35-09:40 IST candle volume from 10,000 to 100. Identical completed history, signal and opening price 102 produce quantities 10 and 1 at 09:35. The report SHA-256 is 67394b32f359e9ac9cc508021536dacfd314b1b4812b73b7927dcb39c82fd252.
At 09:35, the preceding candle's final OHLCV and the new opening-price observation are available. The new candle's final volume/high/low/close are unavailable until 09:40. An opening-price observation is explicitly distinguished from a completed OHLCV observation.
Call graph: portfolio.run -> paper.simulate -> phase1415.execute; execute gates fixed requested quantity on entry-candle volume; paper.size and Coordinator.enter also read that volume.

## Frozen causal policy

Version Phase1418BCausalExecution.v1: NEXT_OPEN_PREVIOUS_COMPLETED_VOLUME_ALL_OR_NONE. Signals use completed candles. Order creation and quantity fixation occur at the next eligible opening-price observation, before fill at that same explicit opening event. Liquidity proxy is strictly the immediately preceding completed five-minute candle, in the same session, ending at the decision timestamp. Missing, stale or zero proxy liquidity denies entry; no fallback. Requested quantity cannot be retrospectively resized using future data. Opening price, cash, risk, costs and prior liquidity determine a fixed integral quantity. Reject if the entire fixed quantity cannot pass the declared participation/cash/exposure model; partial fills remain unsupported. This is a deterministic research proxy, not a guarantee of actual exchange liquidity.

Record signal completion, order creation, quantity decision, liquidity availability, earliest execution, fill confirmation and data cutoff. Opening-price availability equals decision time; completed liquidity availability must not exceed it. SHA-256 binds canonical JSON policy bytes, and exact policy/version enter execution, paper, portfolio and job checkpoint identities.

Stop/target/gap/collision/EOD price rules are unchanged. Exit confirmation is at candle completion; exit volume is therefore available then. Insufficient exit liquidity must retain prior fills and an unresolved position, HALT paper execution, and prevent research qualification. No fictitious exit price or completed trade is reported for an unfilled exit. Subsequent sessions must not erase unresolved exposure.

## Allowed changes and compatibility

Only existing source files phase1415/execution.py, phase1415/research.py, phase1415/jobs.py, phase1417/paper.py and phase1418/portfolio.py may change. Execution centralizes the policy and causal volume/timing helpers. Research rejects unresolved execution; jobs bind the new execution policy. Paper/portfolio reuse these helpers and preserve unresolved accounting. Add test_phase1418b.py, a read-only verifier, the four requested documents and necessary additive synthetic evidence under phase-14.18b only. No strategy definitions, phase14 frozen implementation, costs, qualification thresholds, canonical policy v2 or historical evidence may change.

Old execution/checkpoint versions are incompatible; fail closed, never migrate. Historical synthetic reports remain historical and are not recomputed in place. Previously recorded economic outcomes may change under causal liquidity assumptions; no genuine performance is asserted. The entire source dependency fingerprint changes. All 1,250 other tracked files must stay byte-identical; original five source files archived externally.

## Validation and budgets

Synthetic-only exact reproduction, future-volume/price prefix invariance, quantity fixation, missing/stale liquidity, fill timing, all-or-none, exits/gaps/collisions/EOD, reservation/release, exposure, HALT, replay and policy mismatch. Run focused tests including existing phase1415/1417/1418/1418a regressions, then full Python suite, Ruff and mypy. Single process; each gate 1,200-second limit; external logs/temp/cache/JUnit; maximum 256 MiB synthetic scenario outputs. Python -B, PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain -p no:cacheprovider; Ruff --no-cache; external mypy cache. Preserve every failure and rerun. Recheck protected hashes after each gate. Two deterministic manifest verification replays plus secret/canary, LF/Git-byte and exact inventory checks.

## Stop conditions and deliverables

Stop on protected-file changes, unexpected inventory, ambiguous liquidity availability, unresolved no-lookahead failures or unsupported accounting. Correct only within this frozen scope; scope expansion requires additive amendment. Deliver plan, architecture, methodology, validation, evidence manifest, tests and synthetic correction evidence. Keep raw logs and archives external. No staging/commit/push, broker/credential access, downloads or operational HALT changes. Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN; July SEALED; operational NO_GO. Phase14.19 remains deferred.
