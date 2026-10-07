# Phase 12.0 universe and study protocol — generation 1

Written before historical acquisition or strategy evaluation on 2026-10-07.
Baseline: clean develop, HEAD/origin f09abd04adb0f2eec99189ac0e0485ed71532cc8.
The Phase 11.6 freeze file matches the required SHA-256. No frozen code or
Phase 11 evidence may change.

## Selection declared before outcomes

Exactly **HDFCBANK, ICICIBANK, LT, RELIANCE, SBIN**, in canonical symbol order.
This is a purposive infrastructure sample, not an unbiased sample of all NSE
equities or a reconstruction of an investable index. Three banking identities
(including the existing SBIN control), construction and energy exercise different
cash-equity identities. No strategy returns, recent trends, future volume or
full-period liquidity measurements enter selection. No replacement members.

Eligibility date: **2025-06-30**. Evidence version: NSE Indices Nifty 50
Whitepaper 2025, Exhibit 10, page 8, published as Series 4 September 2025 on
the official Nifty 50 page. This documents the five companies in the index
through June 2025. Index eligibility is only a dated liquidity proxy; it does
not guarantee liquidity, uninterrupted membership or executable fills in 2026.
Selection evidence available by **2025-09-30**; actual retrospective protocol
registration is **2026-10-07**, not falsely backdated to 2025.

- https://niftyindices.com/docs/default-source/indices/nifty-50/nifty-50-whitepaper_2025.pdf
- https://www.niftyindices.com/indices/equity/broad-based-indices/nifty--50

Accessed 2026-10-07. The rolling factsheet URL currently serves September 2026
despite a January date in search indexing; it is rejected as historical evidence.
No claim of survival-bias-free broad-market inference is made. Subsequent
delisting, mapping ambiguity, unavailable history or corporate-action problems
make the whole fixed universe NOT_CERTIFIED; they do not remove a member.

Exact platform IDs use the existing Java InstrumentIdentity v1 contract.
One bounded current broker instrument-master reference read may establish
ZERODHA token mappings. A current mapping is not historical index evidence or
proof of token continuity. Unresolved identities prohibit historical acquisition.
The machine universe must bind the exact mapping and evidence before acquisition.

## Window, calendar and corporate actions

Common half-open development range: [2026-02-02, 2026-07-01), Asia/Kolkata.
Reuse research/phase-11.4/calendar.json and its original fingerprint, with the
same explicit date/session definitions. There are 99 regular sessions and
37,125 expected minute bars per member, 495 instrument-sessions / 185,625 bars.
February 1 special session is outside the declared range. July is excluded.
ONE_MINUTE is represented by the existing export contract's MINUTE interval.

Before evaluation each member needs a review of split, bonus, dividend, merger,
symbol change and identity events in the window. Dividends are disclosed, with
no adjustment or overnight entitlement. Known split/bonus/material identity
events or incomplete continuity evidence yield CORPORATE_ACTION_UNRESOLVED;
no automatic segmentation, deletion, substitution or back-adjustment. Full
universe comparison requires all five members CERTIFIED. An empty dynamic
corporate-actions webpage does not establish absence of actions.

## Conditional acquisition budget

Use existing HistoricalCorpusPlan, HistoricalCorpusAcquisition,
PostgresHistoricalBarRepository and HistoricalResearchExporter only.
Each member: 99 daily chunks; maximum 99 historical GETs. Total maximum 495.
Complete local chunks must be independently verified/reused; SBIN reuse may
reduce actual requests to at most 396. No July historical request is permitted.
At most one profile and one instrument reference GET per authorized invocation.
No retries or automatic restart. A restart recomputes missing daily work,
reuses complete chunks, and refuses partial/conflicting persisted sessions.

Official Kite limit reviewed 2026-10-07: historical 3 requests/second:
https://kite.trade/docs/connect/v3/exceptions/#api-rate-limit
Conservative pacing: one request start/second, sequential only. No network
budget becomes executable until exact identities and the complete per-session
plan are frozen and prerequisites pass. Authentication failure stops without
token refresh, save, clear or login automation.

Dedicated research PostgreSQL/container/volume only; normal trading Flyway V10
unchanged, research V11 explicit. Development connection must be read-only;
verify development/token preservation and active HALT before and after any real
acquisition. Raw data stays in ignored data/. No execution/streaming/account
trading endpoints, no .env changes, no commit/push.

## Fixed study and reporting rules

Only original Phase 11.4 EMA 9/21, VWAP crossover, opening range 15 bars,
RSI 14 recovery above 30 with exit 50; quantity one. The retained strategy
implementation identities and configurations are reused unchanged. Initial
cash INR 100000 independently for each partition; entry 09:15–14:45,
forced exit 15:15; next-open fills; adverse 5-bps slippage; existing
nse-retail-intraday-20261006-v1 FIXED_AS_OF cost scenario. This is not
historical contract-note reconstruction or live capital planning.

Three expanding folds, all half-open, exchange-local midnight:

| Fold | TRAIN | VALIDATION |
| --- | --- | --- |
| 1 | Feb 2–Apr 1 | Apr 1–May 1 |
| 2 | Feb 2–May 1 | May 1–Jun 1 |
| 3 | Feb 2–Jun 1 | Jun 1–Jul 1 |

Cold/flat start each partition and daily feature reset. Exactly 120 independent
evaluations (5 × 4 × 3 × 2), including 60 validation cells. No TEST field or
evaluation branch; no H1/H2 identities accepted. All per-instrument results
retained before aggregate reporting, in canonical order, never ranked by P&L.

Minimum sample: 20 trades AND 5 traded sessions per instrument/partition.
Classify INSUFFICIENT_SAMPLE first; otherwise POSITIVE_NET for net > 0,
POSITIVE_GROSS_COST_ERODED for raw gross > 0 and net <= 0,
NEGATIVE_GROSS for raw gross < 0, and ZERO_GROSS otherwise.
Report exact raw gross, slippage, engine gross, fees, net, marked drawdown,
wins/losses/breakevens, traded sessions, turnover and repeated entries.
Aggregate each strategy/validation month descriptively: positive/negative net
counts, median net, median net/trade (nonzero-trade cells only), median trades,
raw-gross-positive and cost-eroded counts, sufficient-sample count. Insufficient
cells remain visible and never count as sufficient positive evidence.

No pooled expanding-TRAIN total, shared cash, portfolio equity/return, winner,
stock promotion or new hypothesis. Five stocks in one month share market-wide
conditions; breadth does not create five independent temporal confirmations.
All corporate-action limits stay attached to results. Freeze corpus manifest,
verify every v1 export, then content-address study/source identities before
evaluation. Repeating the offline study must reproduce full result/report hashes.
H1/H2 remain SBIN-only pending three complete untouched months. July stays sealed.
