# Intraday strategy research — Phase 11.3

Design recorded before implementation. Baseline a4ccfc7, clean develop matching
origin/develop. Reuse the existing historical JSON boundary and Python engine;
no Java trading, database, provider or live signal changes are needed.

Features receive a validated immutable current-session view built from Decision,
never Dataset. The engine supplies the confirmed session open/close. Every input
must have closed by decision time and follow consecutive minute starts from that
session's open. All indicators reset per confirmed session. Missing warm-up is
None, never zero/NaN. Pure calculations use Decimal precision 40, HALF_EVEN;
recurring divisions intentionally round there, independently of ambient context.

Conventions: return is close ratio minus one; SMA uses exactly N trailing closes;
EMA seeds with first N closes' SMA then alpha=2/(N+1). VWAP uses typical price
(high+low+close)/3 weighted by volume since session open; zero total volume is
unavailable. Wilder RSI seeds N gains/losses from N+1 closes; flat=50, gains-only=100,
losses-only=0. True range uses the prior close within the session; first bar uses
high-low. Wilder ATR seeds first N true ranges' mean. Rolling extrema explicitly
include/exclude the latest completed bar. Relative volume divides current volume
by the PRIOR N bars' mean; zero denominator is unavailable. Opening range requires
N completed opening bars; breakout decisions require a later bar.

Four immutable, stateless baselines: EMA crossover, close/VWAP crossover,
opening-range high crossover with low exit, and RSI recovery above oversold with
exit crossing above an explicit higher threshold. No pyramiding; quantity is an
explicit positive parameter. Crossovers use previous and current completed states.
Engine session policy remains authoritative, and strategies suppress entries
outside its window. These are transparent comparison baselines, not recommendations.

Experiments pin the complete input artifact hash, content hash, instrument,
interval, strategy implementation/feature version, validated parameters, engine
config, cost version and explicit chronological TRAIN/VALIDATION/TEST windows.
Each partition begins cold and flat with the same configured initial capital;
no warm-up or state crosses partitions. Windows must cover whole confirmed
sessions. No shuffle or optimizer. TEST is excluded from default evaluation;
a separate explicit final-test operation requires a frozen selection fingerprint.
Synthetic tests may exercise that operation; real TEST must never select a family
or parameter set. Small comparisons retain all supplied trials, with no winner.

Cost calibration is an explicit, bounded observation-date snapshot; see
[cost sources](../operations/phase-11.3-cost-calibration.md). Rates on older data
require a labeled FIXED_AS_OF scenario. This is not historical tariff certification.
The only engine extension is a pure side/date/entry-basis-aware fee schedule and
session evidence on Decision. Legacy configured costs and v1 result fingerprints
must remain unchanged. Calibrated result configuration contains the complete
rate schedule, date bounds, basis and source/version identity.

Costs use exact accrual, not a claim to reproduce contract-note rounding. Flat
round-trip STT uses half the combined buy/sell notional on closure: its sum equals
unrounded session VWAP times non-deliverable quantity, without future information.
Daily two-decimal VWAP and nearest-rupee STT settlement are not simulated. One fill
is one executed order for the brokerage cap. Planned forced exit is our strategy
exit, not broker RMS square-off; account-specific/dealer/debit-balance fees are
outside this full-cash resident-retail research scenario.

Future optimization must retain search space, trial count, unsuccessful trials,
selection criterion and untouched test evaluation. More trials increase selection
bias. Publication latency, corporate actions, liquidity and slippage calibration
remain separate requirements. No research result authorizes live execution.

## Using the offline framework

1. Load the pinned `Dataset` through the existing strict `Dataset.from_json`.
   Reuse the retained calendar and source evidence; no new historical fetch occurs.
2. Construct `BaselineSpec(version="v1", implementation_fingerprint=..., parameters=...)`.
   Parameter dictionaries have one discriminator: `EMA_CROSS` (quantity, fast, slow),
   `VWAP_CROSS` (quantity), `OPENING_RANGE` (quantity, opening_bars), or `RSI_RECOVERY`
   (quantity, lookback, oversold, exit_threshold). There are no tuned defaults.
   The implementation pin must identify retained source, as in Phase 11.2.
3. Construct the existing `Config` with explicit cash, session policy and slippage.
   Use `nse_intraday_snapshot()` only inside its observation-date window, or
   explicitly `nse_intraday_snapshot(fixed_as_of=date(2026, 10, 6))` to run a
   current-rate scenario on older prices. Legacy `ConfiguredCosts` still works.
4. Construct `ExperimentSpec` with artifact hash `digest(canonical_json(dataset))`,
   bar hash, platform instrument, interval, strategy/config, chronological partitions
   and explicit engine/feature/cost/slippage versions. Model-version mismatch rejects.
   Data must cover complete confirmed sessions, and at least one session per partition.
5. `evaluate_development(dataset, spec)` returns only TRAIN and VALIDATION.
   `compare_development(dataset, specs)` accepts 1–16 explicitly supplied comparable
   experiments, sorts by experiment ID and retains every report. It has no grid
   generator, ranking, recommendation or winner selection.
6. An explicit research choice can be recorded through `freeze_selection(reports,
   selected_id, criterion)`. It rejects reports containing TEST performance.
   Retain every development report, including unsuccessful trials. The frozen
   record pins trial reports/count, criterion and selected experiment fingerprint.
7. Only `evaluate_final_test(dataset, spec, frozen_selection)` evaluates TEST.
   It rejects changed selected parameters/specification. It does not train or select.
   Retain `canonical_json(report)` and `report.fingerprint`, source implementation,
   original dataset and frozen selection. Repeated final-test calls are deterministic,
   not a permitted optimization loop. Offline Python is not an access-control sandbox;
   research governance must prohibit tuning against previously inspected TEST results.

Report schemas have explicit v1 identifiers. Reports include full specifications,
partition result fingerprints, fills/trades/metrics and fee component audits.
They can be compared without conflating cost assumptions, slippage, data or dates:
the comparison operation rejects differing configs, partitions or artifact pins.
Each partition resets capital; results are independent evaluations, not a stitched
capital curve. Gaps between partitions are allowed but overlapping/reversed ranges
and partial-session splits are rejected. Validation of the parent artifact's integrity
is not evaluation of held-out performance; TEST indicators/strategies are absent
from development evaluation.

Feature and strategy calculations currently replay the bounded completed prefix.
This favors transparent deterministic conventions over incremental optimization;
no large-corpus performance claim is made. Periods are bounded to 1–1440 minutes.
No pandas/numpy, float conversion, external numerical package or new dependency
is introduced. The domain's Decimal indicators can have recurring values, so
precision-40 rounding is explicit and separate from exact cash/cost arithmetic.

The backtest's legacy zero/fixed-cost path retains engine v1 identity and its
golden fingerprint. Calibrated side-aware fees use `intraday-next-open-v2-costs`.
Decision's session evidence is optional for backward-compatible fixture callers;
research views fail closed when it is absent. Engine-produced decisions always
derive it from the validated calendar, never a weekday or date-change guess.
