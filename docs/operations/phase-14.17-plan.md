# Phase 14.17 frozen plan

Baseline: `9b3c7f13d4e5c7cbcd29f74b54d1b390d6f7dd64`, develop = origin/develop.
Preflight passed: clean index/worktree, all 22 Phase14.16 additions committed and
byte matched, Phase14.15D/14.16 canonical fingerprints and external receipts verified,
G1, 18 frozen sources, 63 research files, K/L, historical manifests and 13 Phase14.0A
newline reconciliations preserved. Canonical universe: 1140 source rows, 25 duplicates,
1115 enabled unique NSE symbols. External baseline captures all 1211 tracked files:
`b7bfe02e43df407a24ca8b894fcbc3717abf438c3681ee4826b165c2f57fa034`.

## Reuse and methodology

Add only phase1417 Python modules, focused tests, evidence verification and requested
documents/reports. All 1211 baseline files remain immutable. Reuse Phase1415 registry,
evaluate/qualify/wilson, execution/cost accounting and paper event contract; reuse
Phase1416 imported datasets and atomic symbol checkpoints. No parser, indicator,
strategy or accounting redesign; no Java changes or production dependencies.

Qualification preserves frozen_policy(): 13380 hypotheses, 100 OOS completed trades,
30 per fold, 70% observed OOS wins, 60% per fold, positive net expectancy and maximum
10% realized/marked drawdown. Use ordinary two-sided 95% Wilson intervals plus the
existing Bonferroni-adjusted Wilson lower bound > 0.5 at family alpha 0.05. Do not
claim true win probability >=70%, independent trades, or removal of survivor bias.
The project explicitly records correlated-trade limitations; no new inferential
method is approved here.

Use existing fixed-parameter chronological 50% train / 20% validation / 30% holdout
whole IST days, three contiguous holdout folds, six/twelve calendar months. Parameters
freeze before validation. No refit, same-holdout selection, split-straddling trade,
future candle or position carry. Missing history is OUT_OF_SAMPLE_NOT_AVAILABLE.
Expose deterministic primary qualification states while preserving every reason.

## Paper trust boundary and risk gaps

Separate offline event simulation only; no broker adapter, operational DB or HALT.
Require explicit simulation authorization, bound qualification/registry/data/calendar,
nonexpired completed-candle signal, predeclared risk/fees/slippage and session evidence.
Unknown mandatory limits block readiness: risk per trade, daily loss, simultaneous
positions, symbol/portfolio exposure and correlation approval. Existing operational
defaults are disabled/zero, with daily P&L protection deferred in config/README.md;
do not reinterpret those as approved positive paper capital limits.

Synthetic tests may use explicit fixture-only risk limits and a synthetic numerical
qualification proof. Such proof is never a genuine QUALIFIED pair. The fixture-only
authorization scope must remain visible on every event and never grant genuine paper
eligibility. Genuine-mode simulation requires a genuinely qualified bound pair and
complete reviewed risk inputs. No implicit synthetic bypass in genuine mode.

Use frozen executor fills/costs and conservative stop-first collisions. The frozen
executor is all-or-none; partial fill requests must explicitly reject as unsupported,
not invent liquidity or implement a second fill engine. Serialize one position at a
time; unknown correlation review blocks, and no correlated simultaneous allocation
is inferred. Capture signals, eligibility, risk, orders, fills/exits, cash and position
changes in deterministic hash-chained events. Repeated transitions/idempotency keys
reject; identical complete-run replay verifies bytes. Local custody is not independent
rollback protection. Missing exit data halts without claiming liquidation.

## Budgets, validation and stop conditions

One worker, one symbol/window at a time, frozen 30000-candle/288-bar session bounds;
maximum 13380 result rows and 200000 paper events; 256 MiB output budget. No network.
Per gate timeout 20 minutes. Synthetic fixtures only; no genuine data supplied.
Test threshold boundaries, sample/confidence/family/rejection gates, split integrity,
null metrics, replay, paper authorization/sizing/limits, fills/collisions, explicit
unsupported partial fills, EOD, isolated HALT, transitions, idempotency and hash tamper.
Run focused and full Python suites, Ruff no-cache, mypy external cache, and two
deterministic evidence replays. Python -B, PYTHONDONTWRITEBYTECODE=1, pytest
--assert=plain -p no:cacheprovider; unique external temp/JUnit/log/cache paths.
Preserve failed attempts and corrective reruns. Check baseline hashes after each gate.
Stop on preservation/safety failures or frozen methodology defects. No threshold
invention to close policy gaps. Corrections to scope require additive amendments.

## Deliverables

Requested six documents and generation-1 evidence manifest; minimal new source/tests;
13380-row CSV/JSON qualification ledger, walk-forward/rejection/readiness/risk reports
and separate synthetic paper-event examples. Record source/policy/software/data/calendar
fingerprints, exact inventory, tests, secret/canary checks and external raw receipts.
Unavailable genuine metrics remain null and genuine qualified count zero. Record this
plan's SHA-256 externally before implementation and in final evidence.

Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN;
July SEALED; operational NO_GO. No staging, commit, push, credentials, real Kite calls,
downloads, WebSockets, capital-limit changes or infrastructure work.
