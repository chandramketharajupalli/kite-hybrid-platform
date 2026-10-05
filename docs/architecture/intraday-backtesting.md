# Deterministic intraday backtesting — Phase 11.2

## Boundary decision before implementation

Baseline `622ecb3`, clean develop, matches origin/develop. Java already owns
canonical HistoricalBar, explicit TradingCalendar, immutable PostgreSQL history,
closed-bar queries and pinned HistoricalFingerprint replay. Python owns quant
research under AGENTS.md. The live StrategyOrderCoordinator creates orders and
can invoke risk approval; it is deliberately not reused.

The boundary is a versioned research dataset export from a pinned repository
snapshot, followed by an offline Python simulation. The exporter reads only
repository replay/evidence; it never acquires data. The Python engine has no
database, HTTP, broker, account, live signal or execution dependencies. Its
position/cash ledger is a simulation artifact, never authoritative trading state.
No startup bean, controller, scheduler, migration or execution configuration is added.

## Initial contract

One NSE cash instrument, one-minute interval-start bars, explicit Asia/Kolkata
calendar, complete confirmed sessions and a maximum 31-day window. Missing,
unknown, split, partial or inconsistent sessions fail closed in this initial
engine. Non-trading dates require explicit evidence. Input retains the canonical
bar hash, query window, decision/dataset cutoffs and calendar/source provenance.
It does not claim vendor corrections were known at historical decision time.

At each bar OPEN the previous close's intent may fill. At bar END the strategy
receives an immutable prefix of completed bars and the simulated state. It has
no access to the current next candle through that context. Thus a 09:16 decision
using the completed 09:15 candle may fill at the 09:16 open, never the known
09:15 close. Instantaneous next-open execution is an explicit idealized model:
there is no latency, liquidity or queue simulation, and zero volume does not
prove fillability. Positive volume is required for simulated execution.

Initial ledger is flat -> long -> flat. Pyramiding and partial exits are rejected;
overselling never creates a short. Intent identity is a deterministic sequence.
Pending intents cannot cross sessions. Final signals are recorded unfilled.
Cash is explicitly configured; buys need full notional plus costs, with no MIS
leverage or real collateral. This research constraint does not emulate broker RMS.

Entry start, last entry and forced exit are explicit minute-aligned local times.
Forced exit is a pre-scheduled liquidation at the configured bar OPEN, before
strategy intents at that instant. It uses that open plus adverse slippage and
costs; it does not use a future close. Missing/untradeable exit data invalidates
the run. Every session finishes flat. These are research cutoffs, not claimed
broker auto-square-off times.

Slippage is explicit adverse basis points. Costs are explicit fixed-per-fill
plus notional basis points (zero is available for tests). No Indian statutory
rates are guessed. Calibrated costs, liquidity, latency and availability data
are necessary before treating results as realistic. Fill prices are theoretical
decimals, not an assertion of executable tick-grid prices.

Buy costs are paid from cash and retained on the open lot. Closing trade net
P&L = sell proceeds - buy notional - both fees. Open gross unrealized P&L uses
the latest completed close. Equity = cash + marked position; equivalently,
initial cash + realized net P&L + open gross unrealized P&L - open entry fee.
Final flat equity equals cash and initial cash plus the sum of net trades.

All money uses Decimal/BigDecimal. Bounded inputs and a private decimal context
isolate ambient precision; intermediate monetary operations are exact or fail.
Only reported ratios use explicit rounding. Metrics include costs, net profit,
return, win rate and drawdown; no unsupported annualized Sharpe is reported.

Results contain engine and strategy versions, immutable sorted parameters,
configuration, pinned dataset identity, intents/outcomes, fills, trades, equity
and metrics. Canonical JSON plus SHA-256 supplies reproducible result identity.
No wall clock, random ID or database row order enters that identity. Strategy
implementations must themselves be deterministic: the interface is capability
limited, not a sandbox against arbitrary hostile Python code.

`StrategySpec.implementation_fingerprint` is the caller's SHA-256 pin for retained
strategy implementation source; the engine does not claim to attest arbitrary
Python closures. Name/version, that pin and sorted parameter strings enter result
identity. Retain implementation source alongside the dataset artifact and config.

## API and replay

1. On an isolated research repository, retain a `HistoricalBarRepository.Dataset`
   returned by an explicit query and its reviewed calendar. Call
   `new HistoricalResearchExporter(repository).export(pinned, calendar, instrument)`.
   It replays the pinned hash and reads first-observation evidence only. A changed
   manifest fails instead of silently switching to latest history. No broker token
   is exported. No filesystem or live application entry point is created.
2. In Python, load the JSON using `Dataset.from_json(text)`. The versioned schema
   lives in `contracts/schemas/v1/HistoricalResearchDataset.v1.schema.json`.
   This transport representation reuses the Java bar/calendar encoding; it is
   not a second historical ingestion pipeline.
3. Construct `Config` with explicit cash, three local times, `AdverseBps(bps=...)`
   and `ConfiguredCosts(fixed_per_fill=..., notional_bps=...)`; numeric inputs use
   Decimal or plain decimal strings. Construct a pinned `StrategySpec` and provide
   a `BacktestStrategy.on_decision(Decision)` callback returning `Intent` or None.
4. Call `strategy_engine.backtest.engine.run(dataset, config, spec, strategy)`.
   Retain `canonical_json(result)` plus `result.fingerprint`. Replay with a fresh
   deterministic strategy instance and compare both bytes and fingerprint.

Executable fixture examples are in `apps/strategy-engine/tests/test_backtest.py`.
They consume a synthetic five-minute session with explicit synthetic provenance;
they do not represent a normal NSE session or a trading recommendation. The
Phase 11.1 disposable real dataset was not retained, so it is not silently
substituted for this fixture or re-fetched in Phase 11.2.

No engine I/O or result persistence is implicit. Dataset JSON is bounded to 32 MB,
31 days and 44,640 minute bars. Context contains copied immutable completed-bar
prefixes, not a view exposing future backing data. Prefix delivery is quadratic
over a run and is intentionally bounded here; a future rolling-view optimization
must preserve the no-look-ahead boundary. The current engine is not benchmarked
for large corpus optimization.

Zero slippage/cost require explicit zero configuration; neither is an implicit
research default. All monetary operations run at precision 80 with Inexact
trapping; supported bounded inputs fit this budget. Monetary values are not
rounded to paise during fills. Percentage metrics alone use eight fractional
digits with HALF_EVEN rounding. Win rate uses all completed trades, including
breakeven trades in the denominator; no-trade ratios are zero. Drawdown includes
initial cash and each completed-bar equity observation, not unseen intrabar lows.
Closed-bar marks are a simulated information convention, not proof of vendor
publication latency. A forced exit failure invalidates the entire run, returning
no successful research result.

## Future handoff

Phase 11.3 can add research strategies through the callback without changing
execution accounting. No example is a trading recommendation. Further research
needs calibrated costs, corporate-action/identity continuity and point-in-time
availability/revisions. Multi-instrument, partial positions, shorts and leverage
require separately versioned accounting contracts. F&O, CNC research expansion,
Forex and commodity are outside this engine.

Live signals must still pass order intent -> Java RiskService -> authorization
boundary -> ExecutionSafetyPolicy -> broker adapter. Backtest results confer no
authorization and do not alter the Phase 10 ₹10,000 first-live ceiling.

Phase 11.3 extends this foundation with [research features/experiments](intraday-strategy-research.md)
and a side/date-aware cost schedule. Legacy `ConfiguredCosts` and the Phase 11.2
golden result are preserved. Engine-produced decisions now also contain confirmed
UTC session bounds; research views require them. Calibrated fees have a distinct
engine version and retain their complete date/basis/rate configuration in results.
