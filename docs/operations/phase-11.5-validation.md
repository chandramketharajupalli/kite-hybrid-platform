# Phase 11.5 — SBIN intraday diagnostic findings

**DIAGNOSTIC_COMPLETE; HYPOTHESES_REGISTERED_NOT_EVALUATED.** No strategy is
selected, no parameters are changed, and July TEST remains SEALED_NOT_EVALUATED.
Only the certified, already-pinned corpus and Phase 11.3/11.4 evidence were used.

Baseline verified before inspection/editing: clean develop, HEAD and origin/develop
`b61eb97a6d35b25d643f851399c6d85bbf0cf14b`; initial whitespace check passed.
The [analysis registration](phase-11.5-analysis-plan.md) preceded computation.
[Architecture](../architecture/intraday-regime-analysis.md) defines availability,
excursion intervals, arithmetic, evidence identities and sealed-boundary enforcement.

## Evidence and scope

Development: February 2–June 30, 2026, **99 sessions / 37,125 bars**. April, May and
June validation have 20, 19 and 21 sessions. All 24 original TRAIN/VALIDATION results
were replayed without strategy/engine changes and matched their exact Phase 11.4
fingerprints AND metrics. All losses are retained. Expanding TRAIN windows overlap
and are never pooled. Validation totals below add disjoint monthly P&Ls, not returns
on a continuously compounded account.

Parent corpus: `702631ba2f5ef2959cdfa687f87959e9347818c5e772a2dd266b058c30ad280c`.
Parent Phase 11.4 report:
`ae497b05d9fed7475c3d30f0f2bdc04c975c8c3c7a9057ad4c924175417de110`.
Canonical diagnostic report:
[`f2b20e24137c9453a40f7296fa6e5f70451b5e94873364b976aa910d4959664c`](../../research/phase-11.5/f2b20e24137c9453a40f7296fa6e5f70451b5e94873364b976aa910d4959664c.json).
The filename is its SHA-256. It contains session records/thresholds, every trade,
every fold's single-dimension summaries, daily distributions/concentration, original
drawdown, cost burden, excursions and fixed-fill sensitivity. Date/time/Decimal
representations round-trip without changing its fingerprint. No July metrics exist.
Two complete diagnostic generations from the verified replay cache produced the
same fingerprint. Hypothesis-registration fingerprint:
`2fd4b740d3459e366a7d0a26483936d1cf81d700a6b270c334c65e5e951e78d2`.

Costs remain `nse-retail-intraday-20261006-v1`, FIXED_AS_OF 2026-10-06; primary
slippage remains adverse-bps-v1, **5 bps per fill**. Quantity is one share. INR values
below are rounded to two decimals; JSON retains deterministic Decimal precision.
“Raw gross” is before slippage/fees; “engine gross” subtracts slippage but not fees.
Thus raw gross − slippage − fees = net. This distinction is essential to question A.

## A–B: signal, slippage and fee attribution

| Family | Month | Trades | Raw gross | Slippage debit | Engine gross | Fees | Net |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| EMA | Apr | 139 | 30.90 | 148.60 | -117.70 | 157.93 | -275.63 |
| EMA | May | 138 | 17.50 | 136.78 | -119.28 | 145.38 | -264.66 |
| EMA | Jun | 135 | 45.70 | 136.59 | -90.89 | 145.18 | -236.07 |
| VWAP | Apr | 146 | 49.95 | 156.32 | -106.37 | 166.14 | -272.52 |
| VWAP | May | 137 | -13.80 | 136.18 | -149.98 | 144.74 | -294.72 |
| VWAP | Jun | 179 | 31.50 | 181.02 | -149.52 | 192.39 | -341.92 |
| Opening range | Apr | 15 | 15.45 | 15.94 | -0.49 | 16.95 | -17.44 |
| Opening range | May | 10 | -23.30 | 9.99 | -33.29 | 10.62 | -43.91 |
| Opening range | Jun | 15 | -3.00 | 14.97 | -17.97 | 15.92 | -33.89 |
| RSI | Apr | 34 | -4.80 | 36.32 | -41.12 | 38.61 | -79.73 |
| RSI | May | 39 | -26.90 | 39.00 | -65.90 | 41.45 | -107.35 |
| RSI | Jun | 43 | 12.25 | 43.19 | -30.94 | 45.90 | -76.84 |

All twelve are **CASE B at the original engine-gross convention**: already losing
after slippage, before fees. They are not all negative before execution friction.
EMA has positive raw gross in all three months; VWAP does in April/June, RSI only
June, and opening range only April. Nothing here establishes executable gross edge.

Across disjoint validation months, EMA raw gross **94.10** is overwhelmed by
421.98 slippage and 448.49 fees, leaving **-776.37**. Its average raw outcome is
0.228/share/round trip versus 1.024 slippage and 1.089 fees. VWAP raw gross **67.65**
faces 473.53 slippage and 503.28 fees, leaving **-909.15**; average raw outcome
0.146 versus 1.025 slippage and 1.089 fees. RSI raw gross is **-19.45**, net
**-263.92**; opening range raw gross **-10.85**, net **-95.24**. Both underlying
direction and friction contribute; “reduce brokerage” alone cannot explain a remedy.

Fee/absolute engine-gross ratios for EMA are 1.34/1.22/1.60, VWAP 1.56/0.97/1.29,
RSI 0.94/0.63/1.48 and opening range 34.29/0.32/0.89. April opening range's very
large ratio reflects a near-zero denominator, not enormous absolute fees. The report
also supplies fees/sum absolute trade gross, fee/turnover and average round-trip
fees; undefined ratios are null rather than infinite or silently zero.

## C–E: regime differences, concentration and gross-positive cohorts

All labels are EX_POST_DIAGNOSTIC_ONLY. Development tertiles were chosen without
P&L optimization. Minimum evidence is five traded sessions AND twenty trades per
cell. The detailed JSON preserves every cell, including empty/sparse/losing cells.
These are descriptive comparisons, not adjusted significance tests.

| Planned dimension | Cross-month finding and limitation |
| --- | --- |
| Volatility | No repeatable sufficiently sampled profitable session regime. June high-volatility EMA net +12.00 is only 14 trades/3 sessions, INSUFFICIENT_SAMPLE. April/May same label net -68.49/-117.55. |
| Trend efficiency | VWAP LOW net/trade -2.49/-2.29/-2.42 versus HIGH -0.68/-1.89/-1.24, all six cells sufficiently sampled. HIGH still loses. EMA LOW -2.59/-1.91/-2.24 versus HIGH -1.18/-1.75/-1.43; direction agrees in validation, but first TRAIN fold does not show the same ordering. |
| Gap direction/magnitude | UP and DOWN crossover cohorts lose in all months. June MEDIUM-gap EMA has engine gross +12.56 but net -22.39 (33 trades/6 sessions); April/May engine gross -30.06/-30.63. No stable positive regime. Corporate-action gaps remain unadjusted. |
| Opening ranges 5/15/30 | April VWAP HIGH 15/30-minute ranges show net +3.53/+10.67, but May gives -133.72 for both. June HIGH 5-minute EMA/VWAP positive cells have only 2 sessions and cannot support a claim. No robust window selection. |
| Early relative volume | All EMA/VWAP validation LOW/MEDIUM/HIGH cells have negative net. Several HIGH/LOW monthly cells fail the session minimum. Medium relative volume has positive raw crossover gross across months but negative engine gross and net; normalization supplies no proven execution margin. |
| Entry time | EMA MORNING raw gross -9.80/-7.50/-2.70 and net -121.58/-116.09/-111.13. June MIDDAY EMA engine gross +12.51 becomes -37.01 net, whereas April/May engine gross is negative. No sufficiently sampled entry-time cohort has positive engine gross in all three months. |
| Exit time | VWAP AFTERNOON exits have engine gross +96.10/+11.66/+41.90 and net +64.11/-20.37/-1.15 (28/30/40 trades). This mixes CASE C in April with CASE A in May/June. Exit time is an outcome-selected cohort, not an entry rule. |
| Holding duration | EMA/VWAP >30-minute cohorts are CASE C in every validation month, with sufficient samples. Short crossing trades lose even before slippage. Duration is endogenous: surviving trends last longer under these exits. Forcing every losing trade to last longer is unsupported. |

There is **no sufficiently sampled session regime with positive engine gross in all
three validation months**. The persistent gross-positive, cost-eroded example is
VWAP's afternoon-exit cohort in May/June, qualified by its ex-post membership.
Opening-range HIGH efficiency has positive net in every month (+42.78/+8.27/+32.38),
but only 5/4/5 trades; **all are INSUFFICIENT_SAMPLE**. It cannot be promoted as
positive evidence. RSI's <=5-minute gross-positive cohort has 7/2/4 trades and
remains net negative; it also fails the minimum.

## F–H, J: entries, exits, excursion and churn

Many losing trades have some favorable excursion, but “some” often does not cover
friction. Using the registered MFE > actual fees + exit-slippage criterion, losing
trades with a cost-covering excursion are:

| Family | April | May | June | Validation total / losing trades |
| --- | ---: | ---: | ---: | ---: |
| EMA | 20 | 33 | 27 | 80 / 360 |
| VWAP | 34 | 35 | 32 | 101 / 432 |
| RSI | 3 | 2 | 1 | 6 / 102 |
| Opening range | 5 | 2 | 5 | 12 / 22 |

Exit giveback exists, especially for the small opening-range sample, but the
majority of crossover/RSI losses never had a held-path excursion large enough even
under this favorable diagnostic comparison. MFE is a hindsight upper excursion,
not executable profit: high/low order, timing and alternative-exit fees are unknown.
We implement no stop, target or exit modification and cannot causally blame exits.

Mean MFE/MAE price amounts for RSI are 0.88/3.52, 0.88/4.13, 0.96/2.59. Its first
held candle closes below the slipped entry in 28/34, 33/39 and 33/43 trades. EMA
has 116/139, 119/138, 114/135; VWAP 109/146, 109/137, 153/179. These observations
include entry slippage, so they demonstrate adverse behavior relative to the modeled
fill, not pure directional adverse selection at an executable midpoint. Full and
censored first-five-minute MAE/MFE and entry VWAP distance are retained per trade.
RSI's weak favorable excursions and worse adverse excursions provide little support
for an exit-only rescue. Opening range remains too sparse for a strong diagnosis.

VWAP trades/session are **7.30/7.21/8.52**, repeated entries **127/118/158**, median
within-session flat time **3/3/2 minutes**. LOW-efficiency VWAP sessions have
9.57/7.00/13.00 trades per eligible session and raw gross -16.55/-14.10/-24.95.
HIGH-efficiency raw gross is +59.60/+7.50/+40.00, yet net remains negative. This is
consistent with repeated low-edge crossings plus friction; May's trade count does
not monotonically improve with efficiency, so frequency alone is not the explanation.
EMA trades/session are 6.95/7.26/6.43, repeated entries 119/119/114 and median flat
time 15/16/14.5 minutes. Both price behavior and churn matter.

## I: duration and TRAIN consistency

| Family / holding cohort | April net (trades) | May net (trades) | June net (trades) |
| --- | ---: | ---: | ---: |
| EMA <=5 min | -152.92 (45) | -108.75 (36) | -85.84 (27) |
| EMA >30 min | +78.71 (31) | +61.85 (30) | +74.20 (31) |
| VWAP <=5 min | -263.69 (87) | -208.24 (76) | -288.32 (109) |
| VWAP >30 min | +101.10 (20) | +4.89 (24) | +67.10 (25) |

The >30-minute crossover cohorts are also net positive in all three expanding
TRAIN evaluations. VWAP LOW efficiency is worse than HIGH on net/trade in every
TRAIN and validation evaluation. EMA's first TRAIN fold contradicts that efficiency
ordering. RSI >30-minute validation trades have raw gross -28.85/-55.00/-26.55,
consistent with negative TRAIN duration cohorts, but each monthly cell is sparse.
No “hold longer” or “exit at five minutes” strategy has been tested. These patterns
motivate entry persistence/trend hypotheses, not retrospective trade filtering.

## Session concentration and limited slippage sensitivity

EMA's worst-three-session losses account for 29.22%/30.76%/28.89% of sum absolute
daily P&L; VWAP 33.00%/32.87%/30.15%. Their losses are broader than a single outlier.
RSI May is materially concentrated: May 8 loses 45.65, **42.52%** of absolute daily
P&L; worst three account for 58.71%. Even excluding that worst day descriptively,
May's net remains negative. This is not authorization to exclude it from evidence.
Opening-range top-three positive-session contributions are 33.21%/24.67%/27.16%
of absolute daily P&L; worst-three contributions 30.81%/51.87%/44.65%. Aggregate
near-break-even gross is not robustness. All daily records, best/worst dates and
zero-trade sessions remain in the report.

At **zero slippage with the same fees**, all twelve monthly results remain negative:
EMA -127.03/-127.88/-99.47; VWAP -116.19/-158.54/-160.89;
RSI -43.41/-68.35/-33.65; opening range -1.50/-33.92/-18.92.
At 10 bps all worsen. These three scenarios reprice fixed trades, including the
fee effect of changed notionals; they are not alternative strategy/cash-admission
simulations. No best-cost scenario is selected.

## Registered hypotheses and validation

Exactly two development-informed hypotheses are registered in
[phase-11.5-hypotheses.md](phase-11.5-hypotheses.md). Neither is implemented or
evaluated. No July opening, frozen strategy selection, paper/live promotion or
additional data acquisition is authorized. No new exploratory dimensions were added.

Validation executed: **132 Python tests passed**, Ruff and strict mypy passed
(23 source/test files in mypy's configured scope); project verifier and secret scan
passed (zero findings). Synthetic tests cover window availability, prior-only volume,
zero denominators, ties, minimum samples, price/cost attribution, three sensitivities,
excursion interval boundaries, changed future bars, sealed-period rejection, exact
result/data binding, deterministic serialization and dependency/API isolation.
Java/integration suites were not rerun: no Java, DB, migration, existing engine,
baseline or live behavior changed. All modifications are new offline research,
tests, scripts and documentation/evidence.

Offline replay and report generation:

```powershell
$env:PYTHONDONTWRITEBYTECODE = '1'
uv run --project apps/strategy-engine python scripts/research/replay_phase115.py data/phase114-sbin data/phase115-replay
uv run --project apps/strategy-engine python scripts/research/run_phase115.py data/phase114-sbin data/phase115-replay research/phase-11.5
```

The replay cache is ignored local storage. Reports use content-addressed filenames;
conflicting writes fail. Parent Phase 11.4 tracked evidence/source is unchanged.
The final whitespace and evidence-integrity checks passed. No commit or push.

Real Kite requests/history fetches/account or margin reads/market-data connections/
order mutations: **ZERO**. Real execution, permits, authorizations, OrderRecords,
RiskDecisions, reconciliation changes, HALT resume/arming: **ZERO**. No `.env`,
Java live integration, Phase 10 safeguards or INR 10,000 first-live controls changed.
All work is local file-based research. The dated tariff is not a historical invoice;
corporate-action completeness, dividend gap continuity, vendor finality, execution
latency/liquidity/tick rounding, leverage and RMS limitations remain unresolved.
