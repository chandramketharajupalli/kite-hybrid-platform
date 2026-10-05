# Phase 11.2 — deterministic intraday backtesting

Date: 2026-10-05. Result: **ENGINE_VALIDATED_SYNTHETICALLY**.
This is an offline research foundation, not evidence of strategy profitability,
realistic execution quality or authorization to trade.

## Baseline and architecture review

Before inspection/editing, branch was develop, HEAD and origin/develop both
`622ecb3a44cd1cf0ac4131d47609eabbd59c31a1` (`Certify Kite historical data acquisition`),
and the working tree was clean. No reset, stash, clean, checkout, commit or push.

Reviewed Phase 11.0/11.1 architecture and validation, historical domain/query/
ingestion/provider/repository/transport boundaries, historical tests, instrument
identity, live strategy/order/risk boundaries, and intraday-first architecture.
AGENTS.md assigns quant research to Python. Existing StrategyOrderCoordinator
can create orders and invoke risk approval, so it is not used by the backtester.
The design was recorded before implementation in
[intraday-backtesting.md](../architecture/intraday-backtesting.md).

Reusable boundaries: canonical HistoricalBar and HistoricalFingerprint, explicit
TradingCalendar, repository query cutoffs, pinned replay and immutable provenance.
Java adds only an explicit read-only export; Python owns simulation. The Java
exporter invokes repository replay/evidence, never acquisition or append. Python
has no broker, database, HTTP, live signal, account or execution capability.

## Implemented contract

- One NSE cash instrument, MINUTE interval, UTC timestamps, explicit Asia/Kolkata
  sessions. Confirmed complete sessions required; gaps, duplicates, disorder,
  unknown/non-trading contradictions and partial/split sessions fail closed.
- Versioned `HistoricalResearchDataset.v1` JSON boundary. Exact decimal strings,
  canonical bar and calendar hashes, query window/cutoffs, semantic source and
  reference provenance. The shared fixture is explicitly synthetic.
- Deterministic completed-bar decisions. At 09:17 the strategy sees the 09:15
  and 09:16 candles; it cannot obtain 09:17 through its context. Intent fills use
  the next bar's open, with adverse slippage, rather than the signal bar's close.
- Flat -> long -> flat simulation; no short, pyramiding or partial exits. Invalid
  oversells and insufficient cash are rejected without clamping or retries.
- Explicit initial cash and entry/last-entry/forced-exit local times. Cash covers
  full fill notional plus costs. This does not emulate MIS collateral or leverage.
- Pre-scheduled forced liquidation at the configured bar open takes priority.
  Missing or zero-volume forced-exit data invalidates the run. Pending intents
  never cross a session; final signals are unfilled. Every successful run ends flat.
- Immutable intents/outcomes, fills, trades, simulated ledger and equity curve.
  Realized net P&L incorporates both fees exactly once; open marks use completed
  closes. Final cash = final equity = initial cash + sum of closed net trades.
- Explicit adverse bps slippage and configured fixed/bps costs, including explicit
  zero options. No current broker/statutory rate is guessed. No rounding of fill
  money to paise; private precision-80 arithmetic traps inexact monetary work.
  Ratios alone round to eight fractional digits, HALF_EVEN.
- Metrics: trade/win/loss/breakeven counts, gross/net P&L, costs, return, win rate,
  maximum observed drawdown and drawdown percent. Initial equity participates in
  drawdown. No unsubstantiated Sharpe or annualization convention.
- Canonical result JSON and SHA-256 fingerprint include engine/strategy versions,
  caller-pinned implementation hash, sorted parameters, configuration, dataset
  artifact identity and all outputs. No wall clock, random run ID or row ordering.

## Deterministic example — synthetic only

The shared fixture is five synthetic minute bars for platform NSE:SBIN identity,
with a synthetic 09:15–09:20 session. It is not the real Phase 11.1 session and
does not assert NSE had a five-minute trading day.

Test scenario: initial cash 1000; explicit zero costs/slippage; buy two after the
first close, sell two after the third. Entry fills at 102 (next open), exit at
106 (next open), gross/net profit 8, final flat cash/equity 1008. This is an
accounting fixture, not a strategy recommendation.

Dataset content fingerprint:
`27e07b4a936f8bb4374a160692997498982e0055ae3a213d71d775188022a2aa`

Pinned result fingerprint:
`523723b226fb27f04c460f3b4ecfbf5c087be05d37c95ce6dd997637bedba7e5`

With 10 bps adverse slippage, fixed fee 1 per fill and 2 bps notional fee:
entry 102.102, exit 105.894, gross P&L 7.584, total costs 2.0831984, net P&L
5.5008016, final cash 1005.5008016. Tests independently assert these decimal values.

## Validation executed

| Check | Result |
| --- | --- |
| Focused Python backtest tests | 64 passed in final full run |
| Full Python suite | 78 passed |
| Ruff | PASS |
| Strict mypy | PASS, 10 source files |
| Focused Java historical/export/architecture tests | 44 passed |
| Full Java unit/architecture suite | 1199 passed, zero failures/errors/skips |
| Focused disposable PostgreSQL and fake historical pipeline | 9 passed, zero failures/errors/skips |
| Project artifact/safety-default verifier | PASS |
| Secret scan | PASS, zero findings |
| `git diff --check` | PASS |

Coverage includes closed-bar cutoffs, no future context, future-price changes not
affecting earlier contexts, next-open vs known-close fills, final unfilled intents,
multi-session/non-trading-date behavior, no overnight position, forced-exit
priority/unavailability, entry/lot/cash rejection, oversells and partial exits,
profitable and losing trades, exact fee accounting, adverse slippage, drawdown,
no-trade metrics, large decimals/quantities, invalid arithmetic representations,
immutable contexts, bounded callback failure, ambient Decimal context isolation,
canonical scale equivalence and golden fingerprint replay.

Java exports are byte-identical under UTC, America/New_York and Asia/Kolkata JVM
timezones. Both languages validate the same shared artifact and bar/calendar
fingerprints. Python uses explicit UTC/Asia/Kolkata only; AST guards prohibit
wall-clock access and default-zone conversion in engine code. The Java test is
the JVM timezone regression; Python simulation does not run inside a JVM.

The PostgreSQL export test uses Testcontainers PostgreSQL 17.6, a dynamically
allocated disposable database, and a read-only export transaction verified by
`SHOW transaction_read_only = on`. It asserts repeat export equality and unchanged
historical rows/provenance. Orders and token tables remain empty. Existing
concurrent ingestion, conflict, replay, closed-bar cutoff and V10/V11 isolation
regressions also pass. The fake historical transport pipeline uses loopback only.

The full execution/database integration suite was not repeated: Phase 10
implementations are untouched. Full unit/architecture plus the focused research
PostgreSQL/pipeline suite cover the changed Java boundary. No real dataset was
re-fetched; the Phase 11.1 disposable real dataset had not been retained.

Commands (Java runs in a clean child process without deployment/JVM overrides):

```powershell
.\mvnw.cmd '-Dtest=HistoricalResearchExporterTest,HistoricalDataTest,HistoricalArchitectureTest,KiteHistoricalAdapterTest' test
.\mvnw.cmd -Pintegration '-DskipUnitTests=true' '-Dit.test=PostgresHistoricalBarRepositoryTest,KiteHistoricalPipelineTest' verify
.\mvnw.cmd test
# From apps/strategy-engine; suppress writes to existing tracked bytecode caches:
$env:PYTHONDONTWRITEBYTECODE = '1'
uv run pytest -q
uv run ruff check src tests
uv run mypy
# From repository root:
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

Ignored local test logs: `tmp/phase112-focused-unit.log`,
`tmp/phase112-focused-integration.log`, `tmp/phase112-unit.log`.
Initial Python test runs regenerated five pre-existing tracked bytecode files;
only those generated bytes were returned to their exact baseline contents.
They are not part of this change. Subsequent runs suppressed bytecode writes.

## Safety and production changes

Real Kite calls, historical fetches, account reads, order mutations and WebSocket
connections: **ZERO**. Real development DB reads/writes and token mutations:
**ZERO**. Real HALT resume, arm, execute, permits, authorizations and risk/order
creation: **ZERO**. No `.env`, execution default, risk/funding logic, Phase 10
₹10,000 invariant, normal Flyway path or migration changed. No commit/push.

Production additions are confined to three Python backtest files (`__init__`,
dataset, engine) and Java HistoricalResearchExporter. Existing production source
files are unchanged. Also added: dataset schema/shared synthetic fixture, focused
tests and architecture/operations documentation. Existing test changes strengthen
historical architecture and disposable PostgreSQL export coverage.

## Remaining research limitations / next phase

This engine supplies deterministic mechanics, not calibrated real-market fills.
No liquidity participation limit, queue/latency, executable tick rounding,
intrabar path, partial fills, MIS leverage/RMS, portfolio, shorts or optimization.
Positive-volume bars are required for fills; that alone does not prove a real
order of the simulated size could have filled. Costs need explicit Indian equity
calibration before realistic performance claims.

Dataset pins establish reproducibility, not authenticity. Retain reviewed source,
calendar and instrument evidence plus strategy implementation and parameters.
Historical vendor finality/publication timing, corporate actions, symbol continuity
and a persistent dataset/version catalog remain separate work. Arbitrary callback
code must be deterministic; this interface is not an untrusted-code sandbox.

Phase 11.3 can add strategies against the completed-bar callback, then out-of-sample
and walk-forward research. Any later live signal still requires Java risk and
authorization/execution safety; simulation can never bypass that boundary.
