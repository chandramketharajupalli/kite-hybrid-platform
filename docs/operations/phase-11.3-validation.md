# Phase 11.3 — strategy research framework and cost calibration

Date: 2026-10-06. **OFFLINE_FRAMEWORK_VALIDATED**.
No profitability claim, parameter winner or strategy selected for live trading.

## Baseline and reviewed boundaries

Before inspecting/editing: clean develop, HEAD and origin/develop both
`a4ccfc7cfb8b067219c4250440c93c366a67dcc7` (`Add deterministic intraday backtesting engine`).
Reviewed Phase 11.2 design/validation and source, historical data/certification,
intraday-first architecture, AGENTS.md, Python configuration/tests, Java exporter,
shared schema and synthetic fixture. Quant research remains Python; trading/risk/
execution remains Java. The existing historical contract is unchanged.

Design: [intraday-strategy-research.md](../architecture/intraday-strategy-research.md).
Authoritative public cost sources, applicability, observation dates, formulas and
settlement limits: [cost calibration](phase-11.3-cost-calibration.md).

## Deliverables

- Pure Decimal features: simple return, SMA, SMA-seeded EMA, typical-price session
  VWAP, Wilder RSI/ATR, inclusive/prior rolling extrema, volume SMA/relative volume,
  and completed opening range. Explicit None warm-up, precision and zero-volume rules.
- Immutable current-session view derived only from completed Decision bars and
  calendar-confirmed session bounds. Future/gapped input and missing evidence deny.
  No feature or strategy receives Dataset; only experiment orchestration does.
- Four immutable stateless baselines: EMA crossover, VWAP crossover, opening-range
  breakout and RSI recovery. Validated explicit parameters, no defaults tuned to P&L,
  no pyramiding, no hidden run state, entry-window suppression and simulated intents only.
- Versioned experiment/report/selection representations. Artifact/content pins,
  instrument, window partitions, engine/feature/cost/slippage versions, canonical
  parameters and implementation fingerprint contribute to reproducibility.
- Chronological non-overlapping TRAIN/VALIDATION/TEST. Cold and flat each partition
  and session; equal initial capital. Default development evaluation omits TEST.
  Final TEST is an explicit operation bound to a frozen experiment selection.
- Bounded explicit comparisons, maximum 16 trials, stable ID order and no automatic
  ranking/selection. Selection records retain trial/report fingerprints and criterion;
  reports containing TEST cannot be used by the selection-record factory.
- Pure cost schedule with brokerage cap, netted-basis STT, exchange/IPFT split,
  SEBI, GST and buy-only stamp. Fee component audits reconcile exactly to engine costs.
  Unknown dates fail before simulation; older data needs explicit FIXED_AS_OF scope.

The observation-day tariff is `nse-retail-intraday-20261006-v1`, application window
[2026-10-06,2026-10-07). This is not a claim all components began on October 6.
Its current-rate scenario can be applied to earlier synthetic/pinned datasets
only explicitly. `EXACT_ACCRUAL_NOT_CONTRACT_NOTE` records that invoice/day rounding
is not reproduced. No real account tariff or eligibility is inferred.

## Preserved engine contracts

Closed-bar context and next-open fills remain intact. No same-bar close fills,
overnight position, negative cash/short, automatic quantity reduction or retry.
Forced liquidation remains planned simulation, not broker RMS auto-square-off.
Fees reduce cash and net P&L exactly once. Adverse slippage affects fee notional.
Legacy configured-cost results retain their Phase 11.2 golden fingerprint:
`523723b226fb27f04c460f3b4ecfbf5c087be05d37c95ce6dd997637bedba7e5`.
Calibrated fees use engine `intraday-next-open-v2-costs` with the full schedule
inside result configuration. No Phase 10 or Java production implementation changed.

## Validation executed

| Check | Result |
| --- | --- |
| New focused research tests | 37 passed in final suite |
| Full Python suite | 115 passed |
| Ruff | PASS |
| Strict mypy | PASS, 17 source files |
| Full Java unit/architecture suite | 1199 passed; zero failures/errors/skips |
| Project verifier | PASS |
| Secret scan | PASS, zero findings |
| `git diff --check` | PASS |

Indicator tests cover N-1/N/N+1 warm-up; known sequences; return sign; RSI flat,
gain-only/loss-only and Wilder smoothing; ATR first-session range; VWAP reset and
zero volume; prior-vs-current extrema; volume denominator zero; future/gapped
view rejection; and isolation from ambient Decimal precision. All features and
all four baseline proposals are invariant when later, unavailable prices change.

Strategy tests verify precise crossover bars and next-open fills, no repeated
entry while remaining above VWAP, opening-range completion before decisions,
immutable/rejected parameters, entry-window respect, exits and fresh/reused-instance
determinism. Fixtures are synthetic mechanics, not parameter selection exercises.

Cost tests independently check 10000 buy / 11000 sell component arithmetic,
caps and very small/large values, GST base, stamp/STT sides, exchange/IPFT not
double-counted, date boundaries, explicit scenario identity, required entry basis,
cash denial including fees and planned forced-exit costs. The current official
500-share example gives unrounded STT 12.8125; the test distinguishes its rounded
settlement 13 from this engine's explicitly unrounded research accrual.

Experiment tests reject overlap, partial sessions, artifact mismatch and model
version mismatch. TEST mutations leave TRAIN/VALIDATION fills, metrics and partition
result fingerprints unchanged; the enclosing experiment/report identity changes
because it deliberately pins the complete parent artifact. Final evaluation requires
the selected experiment fingerprint; changing parameters/specification invalidates it.
Comparison reports retain all trials and omit TEST by default. Research import/AST
guards forbid broker/network/database dependencies, wall-clock/random operations
and unrestricted Dataset imports in features/strategies.

Commands:

```powershell
# apps/strategy-engine, process-local only; preserve existing tracked bytecode files
$env:PYTHONDONTWRITEBYTECODE = '1'
uv run pytest -q
uv run ruff check src tests
uv run mypy
# repository root, clean child environment for Maven
.\mvnw.cmd test
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

Maven ran through the ignored `tmp/phase113-tests.ps1` child wrapper, clearing
ambient deployment/JVM overrides without reading dotenv or changing persistent
environment. Java log: `tmp/phase113-unit.log`. No new dependencies or migrations.
PostgreSQL/integration suites were not rerun: no Java, schema, repository or DB
behavior changed, and all new engine/research paths are tested entirely in memory.
No benchmark, backtest on real corpus, or realistic profitability validation is claimed.

## Safety and Git scope

Real Kite requests, account/margin reads, historical fetches, WebSocket connections,
broker order mutations, real database reads/writes and token changes: **ZERO**.
Real HALT resume/arm/execute/permits/authorizations/orders/risk decisions: **ZERO**.
Public Zerodha/NSE documentation research only. No `.env`, persistent environment,
live configuration, Phase 10 safety/funding or INR 10000 first-live invariant changed.
No commit or push. All modifications remain reviewable in the working tree.

Production changes: Python backtest engine gains confirmed session context and
optional calibrated fees; a pure backtest cost schedule and five research package
files are added. Existing live strategy/domain/config modules and all Java production
code remain untouched. Tests/docs are added or updated. Shared historical schema
and fixture are unchanged. Bytecode writes were disabled throughout this phase.

## Limits and next research step

Current rates are an observation-date scenario, not a historical tariff archive.
Contract-note aggregation/rounding, account-specific fees, liquidity/latency, partial
fills, tick rounding, leverage and broker RMS are not modeled. Current CASH metadata
does not independently certify tax classification: the calibrated scenario explicitly
assumes ordinary taxable equity shares, not exempt ETFs or another product family.

Each partition/session starts cold, reducing trading opportunities while warm-up
completes. Unknown/split/partial sessions remain unsupported. Dataset authenticity,
publication-time availability, corporate actions and identity continuity remain
historical-research requirements. Retain source implementations for declared hashes.

The offline API is not an access-control sandbox or durable test-lock service.
Researchers must not inspect/repeatedly tune against TEST. Future optimization
must record all trials, search space, selection criterion and an untouched test;
more comparisons increase overfitting risk. This phase implements no optimizer,
paper/live strategy routing or scheduling, and authorizes no live order.
