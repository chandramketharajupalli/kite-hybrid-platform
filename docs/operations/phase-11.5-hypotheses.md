# Phase 11.5 registered hypotheses — generation 1

Status: **REGISTERED_NOT_IMPLEMENTED_NOT_EVALUATED**. Registration follows the
predeclared Phase 11.5 diagnostic analysis and precedes any candidate computation.
This is development-informed hypothesis generation, not independent confirmation.
No strategy is selected and July remains SEALED_NOT_EVALUATED.

Evidence: parent corpus
`702631ba2f5ef2959cdfa687f87959e9347818c5e772a2dd266b058c30ad280c`,
Phase 11.4 report `ae497b05d9fed7475c3d30f0f2bdc04c975c8c3c7a9057ad4c924175417de110`,
Phase 11.5 analysis `f2b20e24137c9453a40f7296fa6e5f70451b5e94873364b976aa910d4959664c`.
Development [2026-02-02,2026-07-01); excluded TEST [2026-07-01,2026-08-01),
Asia/Kolkata. Feature/analysis session-decimal40-v1 + phase115-diagnostics-v1;
cost nse-retail-intraday-20261006-v1 FIXED_AS_OF 2026-10-06;
slippage adverse-bps-v1, 5 bps primary. Machine registration is in
`research/phase-11.5/hypotheses-v1.json`.

## H1 — directional persistence before EMA entry

Mechanism: EMA crossings during noisy price paths may trigger after transient
movements whose gross displacement cannot pay round-trip friction. Requiring a
completed rolling directional path could reject some short whipsaws before entry.

Evidence: EMA validation raw gross is +30.90/+17.50/+45.70, but mean raw profit
0.228/share across months is far below about 2.113/share combined friction.
LOW full-session efficiency has worse net/trade than HIGH in all validation months,
but the first TRAIN fold contradicts that ordering. <=5-minute trades lose raw
gross in all three validation months; >30-minute cohorts profit in all TRAIN and
validation evaluations. These ex-post duration/efficiency observations motivate
the mechanism but do not establish the predictive value of a rolling feature.

One frozen candidate variant: preserve EMA 9/21, quantity one, baseline exit,
entry cutoff and forced exit. At an otherwise valid EMA BUY decision, require
31 completed same-session closes. Compute signed efficiency over exactly 30 changes:
`(close[t] - close[t-30]) / sum(abs(close[i] - close[i-1]))`.
Enter only if this is **>=0.20**; missing history/zero path denies entry.
The threshold is a round, mechanism-based proposal, not an optimized result or the
observed full-session quantile. No slope, volume, weekday, gap or time-bucket filter
is added. A vetoed crossover is skipped, not queued for a later non-crossover bar.
Exits are unchanged; this does not force losers to survive thirty minutes.

Falsifiable expectation: trade count and friction/session fall, and raw gross per
executed trade rises enough to improve primary net P&L versus unchanged EMA on
matched future sessions. A filter that merely trades less while losing useful
trends, remains net negative, or works in only one month does not establish an edge.

## H2 — one completed confirmation bar before VWAP entry

Mechanism: immediate VWAP recrossing may reflect local noise around an evolving
volume-weighted price. Waiting one completed bar could reduce short-lived crossings
and repeated paid round trips, at the cost of delayed entry and missed trends.

Evidence: VWAP median flat intervals are 3/3/2 minutes and repeated entries
127/118/158. <=5-minute cohort raw gross is -71.60/-52.50/-60.50, net
-263.69/-208.24/-288.32. LOW efficiency has worse net/trade than HIGH in all TRAIN
and validation evaluations; LOW validation raw gross is negative in all months.
Most losing trades lack cost-covering held-path favorable excursion (101 of 432
losses do have it). All zero-slippage monthly repricings remain net negative.
This motivates testing entry persistence rather than claiming an exit-only remedy.

One frozen candidate variant: preserve quantity one, session VWAP definition,
baseline sell rule, entry cutoff and forced exit. While flat, the original upward
close/VWAP crossover at completed bar t creates only a pending research entry.
At the next consecutive completed bar t+1, propose BUY if its close is still strictly
above its own prefix VWAP and the decision is within the baseline entry window.
Execution remains the following bar open. Otherwise discard the pending entry;
a subsequent attempt requires a fresh original upward crossover. Clear pending
state at session end. Never confirm on the crossover bar. No new exit delay,
cooldown, re-entry cap, volatility gate or threshold search is included.

Falsifiable expectation: <=5-minute round trips and friction/session fall, with
improved net P&L against unchanged VWAP on matched future sessions. Confirmation
may instead select worse prices and miss profitable brief dislocations; reduced
turnover alone is not success. The candidate has not been backtested here.

## Frozen comparison and rejection rules

Exactly one H1 and one H2 variant, evaluated independently against their own
unchanged baseline; never combine them or search nearby thresholds/confirmation
lengths within this generation. Retain all failed variants and every fold. No
opening-range/RSI exit hypothesis is registered from sparse positive cells.

Implementation correctness may later use synthetic fixtures and already-exposed
development data, explicitly labeled development reuse rather than confirmation.
Prospective confirmation requires a separately authorized, certified, untouched
three-month corpus. Its exact windows, calendar, regular-session scope and shared
intraday cutoffs must be frozen before data inspection; post-August session changes
must be handled explicitly for both candidate and comparator. This registration
does not authorize acquisition or reopening July. No prospective result exists.

For each variant, predeclare success as: at least 20 trades and 5 traded sessions
in EACH month; lower total trade count and combined fees/slippage per eligible
session over the three months; positive total raw and primary net P&L; positive
monthly primary net AND better net than its comparator in at least two of three
months. Report all months, concentration, marked drawdown, and fixed 0/5/10-bps
sensitivities without choosing a cheaper scenario. A sparse month is
INSUFFICIENT_SAMPLE, not success. A third-month loss remains visible; passing these
descriptive gates would still not authorize trading or prove statistical robustness.

Candidate H1 must additionally improve aggregate raw gross/trade; H2 must reduce
the absolute number of <=5-minute round trips. Failure of a mechanistic condition
falsifies that explanation even if incidental P&L improves. Changing any rule,
threshold, criterion or adding a dimension requires a new research generation.
Neither holding duration, full-session efficiency, hindsight quantiles, future
MAE/MFE nor candidate performance is available to a decision-time strategy.
