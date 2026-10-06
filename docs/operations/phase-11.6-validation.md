# Phase 11.6 — frozen implementations and development reuse

**PART_A_COMPLETE_DEVELOPMENT_REUSE_ONLY.**
**PART_B_INSUFFICIENT_PROSPECTIVE_DATA.** No prospective confirmation, strategy
selection, paper trading, live routing or July evaluation occurred.

Baseline verification before inspection/editing: clean develop, HEAD and
origin/develop `3da3a3f5d9c256cc5ecee33e563550015f885812`, whitespace clean.
The authoritative hypothesis Markdown and machine registration were read first.
Their document hash, embedded H1/H2 prose, criteria and registration hash agree.
Parent corpus reference, Phase 11.4 report and Phase 11.5 diagnostic hashes also
match the required identities. The original corpus was independently loaded through
the existing offline integrity/schema/calendar/content checks; no new bars were fetched.

The [implementation protocol](phase-11.6-implementation-plan.md) preceded candidate
evaluation. [Architecture](../architecture/frozen-hypothesis-research.md) describes
the wrapper/state/identity boundaries. Exact frozen rules:

- H1 delegates EMA 9/21 entry/exit behavior, gates only original BUY crossovers on
  signed efficiency of 30 changes from 31 completed same-session closes, >=0.20.
  Missing/zero-path entries are skipped, never queued.
- H2 delegates original VWAP crossover/sell behavior, records a pending flat entry,
  and requires the next consecutive completed close strictly above prefix VWAP.
  Confirmation creates the BUY intent; the following open is the earliest fill.
  Rejection/expiry/session change or end/invalid chronology clears pending state.
- Quantity one, original entry window, forced exit, full-cash ledger, next-open model,
  dated fees and 5-bps primary slippage are unchanged. No filters, exit delays,
  parameter tuning, combined candidate or alternative G1 variant was added.

## Freeze and retained identities

Source/configuration/protocol freeze, written with zero candidate evaluations:
`b04dc1c1ef72419f601a8ce494f3082f35992a1120a18625a8df77eb0e31d740`.
Retained as [implementation-freeze-g1.json](../../research/phase-11.6/implementation-freeze-g1.json).

H1 implementation:
`17d249536d96a469442011454c42a99787a50d08966831b51938ac59964b7f04`.
H2 implementation:
`ccb5ebe8ad5fce034a30ddb6f8d4272d18b8c1136c7caac7698fd75b64bd6b67`.
Both bind the original registration
`2fd4b740d3459e366a7d0a26483936d1cf81d700a6b270c334c65e5e951e78d2`.

Development report:
[`6191638161b8bab6c1131946ce1c5bd2d6b41609bee779287b4ee80ae522bcf1`](../../research/phase-11.6/6191638161b8bab6c1131946ce1c5bd2d6b41609bee779287b4ee80ae522bcf1.json).
It retains twenty evaluations, candidate/comparator identities, per-trade audits,
all daily P&Ls, original marked drawdown, concentration, 0/5/10-bps fixed-trade
repricing and the DEVELOPMENT_REUSE_ONLY designation. Full result envelopes are in
ignored `data/phase116-reuse`, pinned by freeze and exact result fingerprints.
No source/rule/configuration/criteria change occurred after evaluation began.

The five disjoint monthly partitions cover **99 sessions / 37,125 bars**, February
2–June 30. Each starts cold and flat with INR 100000. Six April–June comparator
fingerprints exactly match Phase 11.4's original EMA/VWAP validation results.
February/March separate monthly identities are new partition identities with the
same unchanged baseline rules. No overlapping expanding-TRAIN totals are pooled.
All amounts below are INR, quantity one; display rounding is two decimals.

## Development-reuse mechanism observations, not confirmation

| Variant | Trades | <=5-minute round trips | Raw gross | Slippage | Fees | Net | Raw/trade | Friction/session |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Original EMA | 703 | 181 | 112.10 | 748.65 | 795.68 | -1432.22 | 0.1595 | 15.5992 |
| H1 | 29 | 7 | -9.15 | 30.66 | 32.59 | -72.40 | -0.3155 | 0.6389 |
| Original VWAP | 752 | 461 | 110.35 | 802.34 | 852.74 | -1544.73 | 0.1467 | 16.7180 |
| H2 | 544 | 271 | 90.90 | 579.06 | 615.44 | -1103.60 | 0.1671 | 12.0657 |

Raw gross excludes slippage/fees; raw minus both equals net. Friction/session uses
all eligible sessions, including idle ones. Aggregate ratios use aggregate amounts
and denominators, not averages of monthly ratios. These sums are disjoint-partition
descriptions, not a continuous account return or a prospective criteria score.

H1 produces far fewer trades and lower friction/session, but **raw gross per trade
deteriorates** from +0.1595 to -0.3155. Reduced loss largely reflects reduced exposure;
the intended raw-edge mechanism is not supported in this development reuse. This
does not justify loosening 0.20 or reducing the 30-change lookback. Monthly counts
are only 4/5/4/9/7 trades on 4/5/3/6/6 traded sessions. The sample is sparse;
no prospective minimum-sample or success assessment is applied to reused data.

H2 reduces total trades, short round trips and friction/session, the directions
anticipated by its mechanism. Aggregate raw gross falls, while raw gross/trade
increases slightly. It remains net negative in every month. April and May raw
gross/trade deteriorate versus the original comparator, so the aggregate improvement
is not uniform. Lower churn is an observed behavior, not confirmation of an edge.
No second confirmation bar, alternate threshold or cooldown was tried.

## All monthly outcomes retained

| Hypothesis | Month | Comparator trades | Candidate trades | Candidate raw gross | Slippage | Fees | Candidate net | Comparator net | Candidate marked drawdown |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| H1 | Feb | 147 | 4 | -4.20 | 4.86 | 5.17 | -14.23 | -321.69 | 14.23 |
| H1 | Mar | 144 | 5 | -10.20 | 5.42 | 5.76 | -21.37 | -334.17 | 22.91 |
| H1 | Apr | 139 | 4 | 10.95 | 4.34 | 4.62 | 1.99 | -275.63 | 13.05 |
| H1 | May | 138 | 9 | -0.60 | 8.92 | 9.48 | -19.00 | -264.66 | 23.68 |
| H1 | Jun | 135 | 7 | -5.10 | 7.12 | 7.57 | -19.79 | -236.07 | 21.08 |
| H2 | Feb | 172 | 116 | 74.10 | 135.54 | 144.05 | -205.49 | -340.97 | 228.85 |
| H2 | Mar | 118 | 83 | -15.70 | 89.89 | 95.54 | -201.13 | -294.60 | 202.89 |
| H2 | Apr | 146 | 108 | 31.70 | 115.59 | 122.85 | -206.73 | -272.52 | 229.05 |
| H2 | May | 137 | 100 | -30.40 | 99.62 | 105.88 | -235.91 | -294.72 | 244.81 |
| H2 | Jun | 179 | 137 | 31.20 | 138.42 | 147.12 | -254.34 | -341.92 | 270.22 |

H1's only positive primary month, April, contains four trades across three sessions:
April 13 contributes +10.59 against the month's +1.99 total. It is concentrated
and cannot support a robustness claim. Other candidate H1 months remain negative.
H2 worst-session shares of sum absolute daily P&L are 12.95%, 16.35%, 12.51%,
11.76%, 11.50% from February through June; its losses are not solely one extreme
day. Full best/worst dates, top/bottom-three sums and daily distributions including
zeros are retained for both candidates and comparators.

H2 <=5-minute round trips decline month-by-month from comparator
110/79/87/76/109 to 57/45/56/42/71. Its friction/session declines from
20.74/13.86/16.12/14.79/17.78 to 13.98/9.76/11.92/10.82/13.60.
H1 friction/session declines from 17.57/16.97/15.33/14.85/13.42 to
0.50/0.59/0.45/0.97/0.70. These are mechanism descriptions only.

The exact predeclared fixed-fill sensitivity is retained, never optimized. H2 remains
negative at zero slippage with fees in every month (-69.95/-111.24/-91.15/-136.28/
-115.92). H1 April is +6.33 at zero, +1.99 at primary 5, and -2.35 at 10 bps;
the other H1 months remain negative even at zero. Repricing holds trades fixed and
recalculates fees; it does not simulate alternative cash admission or liquidity.

## Part B outcome

The [prospective feasibility assessment](phase-11.6-prospective-feasibility.md) and
its [content-addressed record](../../research/phase-11.6/eb3d64548362afe813834e85ff714ae1b42c3dabdc082b01536ca76299907ade.json)
record runtime, official session/holiday sources and the stop condition.
At October 7, only August and September are complete post-July months; the required
third complete month does not exist. **INSUFFICIENT_PROSPECTIVE_DATA** is the outcome,
not a candidate pass/fail. No partial-month substitute or July reuse is allowed.

Current public session documentation was checked. October 6 is calendar-indicated
as the latest completed ordinary session; provider availability is not certified.
The changed continuous/auction boundary also prevents silently carrying the old
15:15 forced-exit assumption into a post-August continuous-only corpus. No Part A
rule was changed. No prospective calendar, corporate-action certification, exact
acquisition window or nonzero request budget was fabricated to bypass the gate.
The future acquisition protocol and real research-DB/token checks were not reached.

## Verification and safety

**149 Python tests passed** before source freeze and real candidate evaluation;
Ruff and strict mypy passed (27 configured files). Existing baseline/golden tests
remain unchanged and pass. New synthetic coverage includes all registered H1
window/threshold/sign/zero/veto/fresh-cross cases, H2 pending/strict-equality/
confirmation/fill/rejection/fresh-cross/session/chronology/expiry behavior, unchanged
sell and forced-exit behavior, future mutation invariance, July overlap denial for
both candidate and comparator, source-freeze conflict rejection, report reconciliation,
canonical round-trip identity and research-only import boundaries.

Project verification and secret scan passed; no findings. Java/integration suites
were not rerun because no Java, database, schema or live behavior changed.
Original Phase 11.4/11.5 evidence and existing baseline/engine source are unchanged.
Implementation, report and registration hashes are rechecked by the offline runner;
every resumed cached result must retain its exact fingerprint. A second report
generation from the verified cache checks deterministic artifact reproduction.

Commands (process-local bytecode suppression preserves tracked legacy bytecode):

```powershell
$env:PYTHONDONTWRITEBYTECODE = '1'
uv run --project apps/strategy-engine python scripts/research/run_phase116.py freeze
uv run --project apps/strategy-engine python scripts/research/run_phase116.py reuse
# In apps/strategy-engine:
uv run pytest -q
uv run ruff check src tests
uv run mypy
```

The freeze operation refuses an existing differing artifact; reuse requires the
unchanged pre-existing freeze. No G1 implementation edit followed real evaluation.
No parameters, cost/slippage scenario, thresholds, exits, capital or registered
success criteria were changed after results. The prospectively registered criteria
remain retained verbatim and are not scored here.

Real Kite historical requests, account/positions/orders/trades/margins reads,
WebSocket connections, order mutations, executions, permits/authorizations,
OrderRecords/RiskDecisions, reconciliation changes, DB/token reads/writes, HALT
resume/arming and paper trading: **ZERO**. No `.env`, Java/live integration,
emergency-stop, Phase 10 or INR 10000 first-live safeguard changes. No commit/push.
Current-cost-scenario, corporate-action continuity, publication, slippage, liquidity,
tick rounding, full-cash and broker RMS limitations remain applicable.
