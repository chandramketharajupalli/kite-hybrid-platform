# Phase 11.6 prospective feasibility — acquisition not reached

**INSUFFICIENT_PROSPECTIVE_DATA. STOP PART B.** No prospective strategy result,
corpus acquisition, request permit or successful confirmation is produced.

The runtime clock returned 2026-10-06 18:38:41 UTC, or **October 7, 2026,
00:08:41 IST**. This check precedes choosing any acquisition window. The month
definition is three complete calendar trading months, as stated in the Part A
protocol before real candidate evaluation. October is incomplete. Excluding
February–June development and sealed July leaves **at most August and September**
as complete consecutive post-corpus months. Two is insufficient even if every
minute of both months were available and genuinely untouched. No partial October,
July substitution, arbitrary ninety-day interval or earlier development month is used.

From the ordinary weekday/session policy and published holidays, **October 6** is
the latest calendar-indicated completed ordinary session at that runtime. October 7
has not opened. This is a calendar inference, not a claim that Kite's October 6
minute export was fetched or certified. Actual provider retention/publication and
exceptional-session completeness remain unverified because the prerequisite fails
before a historical request is authorized. Only the original February–July corpus
has retained canonical certification; its latest retained session is July 31 and
July remains unevaluated.

## Current authoritative session evidence

Public documentation was reviewed on October 7, 2026 IST; these are unauthenticated
documentation reads, not Kite/account/streaming requests.

- [NSE CAS specification](https://www.nseindia.com/static/products-services/closing-auction-session)
  (page updated August 12, 2026): CAS applies initially to derivative-eligible cash
  stocks; its separate auction runs 15:15–15:35, including matching/confirmation.
  Non-CAS continuous trading remains 09:15–15:30. This is a session distinction,
  not evidence that auction candles obey continuous next-open execution semantics.
- [Zerodha market timings](https://support.zerodha.com/category/trading-and-markets/trading-faqs/market-sessions/articles/what-are-the-market-timings)
  dates the change to August 3 and describes eligible-stock continuous trading as
  09:15–15:15. Its simplified table ends CAS at 15:30; NSE explicitly includes
  matching to 15:35, so the simplified table must not define full auction coverage.
- [NSE CMTR71775](https://nsearchives.nseindia.com/content/circulars/CMTR71775.pdf)
  lists October trading holidays on October 2 and 20 and identifies November 8
  Muhurat trading with timings to be notified separately. It does not identify
  October 6 as a holiday. [Zerodha's current holiday calendar](https://zerodha.com/marketintel/holiday-calendar/)
  agrees on those October dates and distinguishes settlement-only closures.

A future certified calendar must confirm SBIN eligibility for each relevant date,
separate continuous from auction sessions, derive expected minute counts from the
recorded windows, and explicitly address holidays, exceptions and any special
sessions. Unknown dates must fail certification or follow a policy frozen before
acquisition. A 09:15–15:15 continuous window would contain 360 interval-start minutes;
that arithmetic is not a certified post-August corpus or permission to assume every
day has 360 bars. No post-August bars were inspected here.

The unchanged development forced exit at 15:15 cannot silently be treated as a
continuous-session next-open fill when continuous trading ends at 15:15. The
existing engine requires forced exit strictly inside the session. A future protocol
must resolve that issue explicitly, identically for candidate and comparator, before
acquisition/results. No Part A cutoff, engine rule, calendar or safety control was
changed to accommodate it. Auction execution needs a separately justified model;
it is not implied by G1's existing continuous next-open fills.

## Gates not reached

No exact prospective acquisition window is selected/frozen, because a complete
eligible three-month interval is unavailable. No per-date prospective calendar is
certified. No prospective SBIN corporate-action continuity review is claimed:
dividends, splits, bonuses and other actions require an authoritative review for the
eventual frozen range, with predeclared fail/segment handling before performance.
No claim that an unseen range is action-free is made.

No new acquisition system, database/container, schema migration or token access is
needed for this blocked feasibility result. Historical request budget and actual
historical requests are both **zero**. Development and research databases were not
accessed; no before/after token/account read was needed because no real acquisition
occurred. Normal V10 trading and isolated historical-storage contracts are unchanged.

The mathematical earliest three-month post-July block would span August–October,
but October has not completed. That observation does not select or authorize the
block and does not establish untouched outcomes, calendar certification or corporate
continuity. A later phase must record exact dates, untouched-data evidence, calendar,
corporate-action policy, corpus identity rules, bounded Phase 11.4 request plan,
candidate/comparator implementations, identical configuration, costs/slippage,
capital, frozen success criteria and immutable output policy before any acquisition.
The existing HistoricalCorpusPlan/Acquisition/research PostgreSQL path must be
reused then. July remains SEALED_NOT_EVALUATED throughout.

Implementation freeze referenced by this feasibility assessment:
`b04dc1c1ef72419f601a8ce494f3082f35992a1120a18625a8df77eb0e31d740`.
H1: `17d249536d96a469442011454c42a99787a50d08966831b51938ac59964b7f04`.
H2: `ccb5ebe8ad5fce034a30ddb6f8d4272d18b8c1136c7caac7698fd75b64bd6b67`.
The G1 registration and prospective success criteria are unchanged. Development
reuse cannot satisfy them, regardless of its performance.
