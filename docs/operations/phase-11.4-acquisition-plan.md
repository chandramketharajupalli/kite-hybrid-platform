# Phase 11.4 acquisition and research generation 1

Recorded before any Phase 11.4 Kite request or corpus experiment. Baseline
`a9713184bf3a94ee37df05307674059b81bb9720`, clean develop matching origin/develop.
All source access below: 2026-10-06 (Asia/Kolkata).

## Bounded proposal

NSE:SBIN, minute, 2026-02-02 through 2026-07-31 inclusive. Six months is enough
for three expanding development folds and a final month holdout. It deliberately
ends before CAS and excludes the February 1 budget-session date. No automatic
extension to other instruments, intervals or dates. No local price adjustments.

One GET per confirmed session, not the provider maximum range. Maximum 122
historical requests, one profile GET and one instrument-master GET: 124 total.
The exact calendar-derived plan must fit this budget before the first network
request. Production pacing is one request start/second, below the documented
three historical requests/second. No retries, polling or network replay.
Authentication, reference, response or quality failures stop acquisition.
DB replay and restart of completed chunks need no historical GET.

Calendar input: `research/phase-11.4/calendar.json`, explicit every date, versioned
and fingerprinted. Confirmed regular windows are [09:15,15:30) Asia/Kolkata;
expected count derives from each window, not a universal 375 constant. Dates
outside this dataset remain unknown. A discovered exception requires a new
calendar version and review, never quiet bar deletion.

## Authoritative evidence and limits

- [Kite historical API](https://kite.trade/docs/connect/v3/historical/): minute
  OHLCV GET, offset timestamps, archived data spanning years. No guaranteed
  per-symbol retention floor or exact current maximum range is specified in the
  primary page. One-session requests stay within the already validated adapter
  boundary. Continuous futures/OI are disabled. The Phase 11.1 empirical
  interval-start certification and half-open filtering are retained.
- [Kite limits](https://kite.trade/docs/connect/v3/exceptions/#api-rate-limit):
  historical requests limited to three/second. A forum answer suggesting 30 days
  was inspected, but public author role was only Member; it is not a safety rule.
- [NSE CMTR71775](https://nsearchives.nseindia.com/content/circulars/CMTR71775.pdf):
  capital-market 2026 holidays. Within this range: March 3/26/31, April 3/14,
  May 1/28, June 26. The circular distinguishes weekend holidays and November
  Muhurat, which is outside this corpus.
- [Zerodha current holiday calendar](https://zerodha.com/marketintel/holiday-calendar/)
  independently agrees on those trading holidays. Settlement-only dates are
  not removed. January 15 exceptional closure is outside the range.
- [NSE timings](https://www.nseindia.com/static/market-data/market-timings)
  supplies regular cash open/close. This evidence plus the dated holiday dataset
  is used, not a permanent Monday-Friday business rule.
- [NSE CAS](https://www.nseindia.com/static/products-services/closing-auction-session)
  describes eligible-stock continuous/auction separation. [Official Zerodha
  notice](https://zerodha.com/z-connect/general/everything-you-need-to-know-about-closing-auction-session-cas)
  gives August 3, 2026 effective date: eligible-stock continuous trading ends
  15:15, followed by auction through 15:35. This corpus does not model that auction
  or extend the pre-change calendar beyond July.
- [SBIN issuer filing](https://nsearchives.nseindia.com/corporate/SBIN_08052026154613_BSE_NSE_DividendRecordDate_08052026.pdf)
  records dividend INR 17.35 and May 16 record date. No local dividend adjustment
  or overnight dividend entitlement is simulated. The public corporate-actions
  table/API did not supply a complete response in this review; absence of splits
  or bonuses is not asserted. This is an explicit adjustment/continuity limitation,
  not permission to adjust bars. The issuer filing corroborates recent SBIN naming;
  current registry identity alone is not proof of long-horizon token continuity.

## Storage and preservation

Dedicated research PostgreSQL only, separate container/volume/database from
development. Explicit V11 research migration; normal application remains V10.
Read development token only in verified read-only transactions; no token writes,
auth exchange or application startup. Compare token fingerprint internally and
trading-table counts before/after. Process-local UTC/safety settings only.
HALT guard remains active. Exact route/query allowlist and fixed call counters.

Each complete day exports unchanged HistoricalResearchDataset.v1. Corpus manifest
composes those bounded exports; source/calendar/content identity excludes runtime
acquisition timing. Operational timestamps and exact artifact hashes remain
separate evidence. Persisted conflicts stop; no silent revisions. Missing or
unknown sessions mean NOT_CERTIFIED, with no development evaluation.

## Predeclared walk-forward generation

All boundaries exchange-local midnight, half-open:

| Fold | TRAIN | VALIDATION |
| --- | --- | --- |
| 1 | Feb 2–Apr 1 | Apr 1–May 1 |
| 2 | Feb 2–May 1 | May 1–Jun 1 |
| 3 | Feb 2–Jun 1 | Jun 1–Jul 1 |

Final TEST: [Jul 1, Aug 1), sealed. No TEST performance, trades or selection.
Four trials only, one parameter set each: EMA 9/21; VWAP crossover; opening range
15 bars; RSI recovery 14, oversold 30, exit 50. Quantity 1 for all. Initial cash
INR 100000 each partition, full cash simulation, entry 09:15–14:45, forced exit
15:15, adverse slippage 5 bps. Each partition starts cold and flat; each feature
resets daily. No state or capital carries across partitions.

Costs: existing `nse-retail-intraday-20261006-v1`, explicitly FIXED_AS_OF
2026-10-06. This is a current-cost scenario on older prices, not historical invoice
reconstruction. Record every fold and trial; no automatic ranking or winner.
Parameters are mechanics fixtures, not recommendations. Changes after observing
results require a new research generation. Repeated trials increase overfitting
risk; TEST must not be used to revise development choices.
