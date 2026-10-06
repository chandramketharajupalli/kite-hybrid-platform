# Phase 11.5 diagnostic registration, generation 1

Recorded before computing regime or trade diagnostics. Baseline verified clean:
develop, HEAD and origin/develop b61eb97a6d35b25d643f851399c6d85bbf0cf14b.
Only pinned offline NSE:SBIN ONE_MINUTE cash-equity MIS research is authorized.
Development is [2026-02-02,2026-07-01) Asia/Kolkata. TEST is
[2026-07-01,2026-08-01), SEALED_NOT_EVALUATED. No selection or candidate evaluation.
No network, account, database, live execution, environment-file, Java safety,
commit or push operation is part of this phase.

Parent corpus: 702631ba2f5ef2959cdfa687f87959e9347818c5e772a2dd266b058c30ad280c.
Parent report: ae497b05d9fed7475c3d30f0f2bdc04c975c8c3c7a9057ad4c924175417de110.
Cost scenario: nse-retail-intraday-20261006-v1, FIXED_AS_OF 2026-10-06;
slippage adverse-bps-v1, 5 bps primary. Preserve all 24 results and all parameters.
Replay each result and require its exact Phase 11.4 fingerprint before analysis.

## Fixed dimensions and conventions

Single dimensions only; no intersections, weekdays, parameter grids or ranking.
Every summary retains strategy, fold and TRAIN/VALIDATION. Never pool overlapping
TRAIN windows. Validation may be discussed jointly only alongside all three months.
Minimum evidence: 5 distinct traded sessions AND 20 trades per cell; otherwise
INSUFFICIENT_SAMPLE, even if profitable. This is descriptive, not significance.

- Volatility: full-session range/open and realized volatility sqrt(sum of squared
  simple close returns, including first close/open). Both diagnostic; only realized
  volatility defines volatility labels. No annualization.
- Trend: abs(last close-first open)/[abs(first close-first open) + sum absolute
  consecutive close changes]. Zero path gives zero. Including the initial leg
  keeps efficiency in [0,1]. Full-session EX_POST_DIAGNOSTIC_ONLY.
- Opening gap: signed difference and percentage from previous confirmed session
  close, magnitude and UP/DOWN/FLAT. First session UNKNOWN; no external close.
- Opening ranges: exactly 5, 15, 30 completed opening bars: high, low, range/open.
  Each unavailable until its final bar closes. Label each range separately.
- Early relative volume: first 15 completed bars / mean first-15 volume of up to
  20 preceding confirmed development sessions, minimum 5. Zero reference or
  insufficient history gives UNKNOWN. Never include current/future reference days.
- Quantile labels LOW/MEDIUM/HIGH use nearest-rank development-session tertiles
  (ceil(n/3), ceil(2n/3)); equality belongs to lower bucket, ties are not split.
  Apply to volatility, efficiency, absolute gap %, three range %, relative volume.
  All distribution-derived labels are EX_POST_DIAGNOSTIC_ONLY, even if underlying
  raw values become available intraday. Record thresholds, never optimize to P&L.
- NSE-local time: OPENING [09:15,09:45), MORNING [09:45,11:30),
  MIDDAY [11:30,13:30), AFTERNOON [13:30,15:30). Entry and exit separately.
- Holding: <=5, (5,15], (15,30], >30 minutes.
- MAE/MFE: nonnegative price excursions from slipped entry price using highs/lows
  of bars with entry <= start and end <= exit. Include entry candle; exclude exit
  candle (exit at open). Include exit reference open as terminal observed point
  for whole-trade excursions; never exit candle high/low or anything after exit.
  First-five-minute excursions use only held completed bars through min(entry+5,
  exit), plus terminal exit open only if exit is within five minutes. Record
  observed duration, first held bar adverse/favorable excursions, first-five close
  change, and whether five full minutes were observed. No intrabar order inference.
- Entry VWAP distance uses only completed bars before entry. No full-session VWAP
  enters a tradable feature. Session close-vs-VWAP is ex-post only.
- Exit giveback: MFE*quantity minus realized slipped gross P&L; report losing trades
  with positive favorable excursion and favorable excursion exceeding total fee
  plus exit slippage. Neither is an achievable alternative-exit backtest.
- Costs/churn: gross before slippage, slippage debit, gross after slippage (the
  original engine gross), fees, net; reconcile both identities. Costs/abs(aggregate
  engine gross), costs/sum(abs(trade gross)), average cost/round trip and fee/turnover;
  zero denominators are null. Trades and turnover per all eligible sessions,
  repeated entries and within-session flat minutes between exit and next entry.
- Limited sensitivity: exactly 0, 5, 10 bps, repricing identical observed reference
  fills and recalculating the same dated fees. This is FIXED_TRADES_REPRICING_ONLY,
  not a new strategy run or a cash-admission/latency simulation. Primary remains 5.
- Outcome concentration: all daily P&Ls including no-trade days, best/worst and
  top/bottom 3 sums, contribution to sum absolute daily P&L (null if zero).
- Every cell: counts, distinct sessions, gross before/after slippage, slippage,
  fees, net, win/loss/breakeven, win rate, mean/median net. Closed-trade cumulative
  drawdown is explicitly distinct from original engine mark-to-market drawdown.

No Sharpe/Sortino. Full-session statistics and future excursions live in a separate
diagnostic namespace with no imports into features/strategies/Decision. The report
records raw feature availability separately from ex-post labels. All analysis
functions reject bars/results outside development before calculating statistics.

## Evidence and hypotheses

Canonical JSON hashes exclude wall clocks, random IDs and paths. Include plan,
analysis-source, parent corpus/report, feature and cost/slippage pins. Content-addressed
reports must never overwrite different evidence. Changes create new identities.
Hypotheses are registered only after diagnosis, before any candidate implementation
or evaluation: at most three, each with mechanism, development evidence, tradable
inputs, explicit rule proposal, expected failure/success and future validation plan.
They are development-informed proposals, not prospectively confirmed findings.
Any extra analysis dimension after results is POST_HOC_EXPLORATORY and requires
a new research generation before hypothesis registration. No July authorization.
Corporate-action continuity, dividend gaps, vendor point-in-time finality, idealized
next-open fills and dated tariff limitations of Phase 11.4 remain in force.
