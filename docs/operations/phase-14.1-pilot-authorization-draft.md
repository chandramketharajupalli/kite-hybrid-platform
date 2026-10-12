# Phase 14.1 separate owner authorization draft — UNAPPROVED

This is a proposal, not an executable approval. Current real request budget is
ZERO. No approval from an earlier margin read transfers to these endpoints.
The offline planner has no dispatch path, even if an approval document is supplied.
Future approved acquisition requires independently reviewed wiring and safety gates.

## Public documentation review (2026-10-12)

The official [instrument documentation](https://kite.trade/docs/connect/v3/market-quotes/)
describes a daily CSV snapshot and differentiates instrument_token from
exchange_token; numeric tokens can be reused. Our source date is separately
recorded from the observation timestamp.

The official [historical documentation](https://kite.trade/docs/connect/v3/historical/)
lists GET /instruments/historical/:instrument_token/:interval, the 5minute interval,
from/to parameters and optional continuous/oi flags. Plan continuous=0, oi=0.
These pages do not establish a guaranteed six/twelve-month history for each stock,
equity adjustment semantics or the account's maximum permitted five-minute window.

The [rate-limit page](https://kite.trade/docs/connect/v3/exceptions/) lists historical
requests at 3/second. Proposed pacing is stricter: one/second, concurrency one,
zero automatic retries or redirects. Stop on 401/403/429/5xx, malformed responses,
timeout or uncertain outcome. No automatic token refresh; record Retry-After as
safe response metadata and require owner review before any separately approved restart.

The public [product page](https://zerodha.com/products/api/) lists historical data
under the Connect subscription (currently INR500/month). No account was accessed:
actual entitlement, existing plan, taxes, automated-use/data-retention/licensing
terms and redistribution rights remain UNVERIFIED. No purchase is authorized.

## Independent decision A: instrument master

Prefer an externally supplied immutable snapshot and acquisition attestation.
If the owner instead requests acquisition, the proposed scope is exactly one GET
https://api.kite.trade/instruments (existing adapter route), no other account reads,
32 MiB decompressed maximum and a separately bounded compressed traffic limit.
Required approval fields below must be completed before any dispatch. Import
selection is NSE cash; full-master receipt does not authorize trading other segments.

## Independent decision B: historical pilot

Propose TWO independently reviewed liquid NSE common equities, ONE explicit
completed NSE session each: exactly TWO GETs, at most 1 MiB response per request,
2 MiB total response budget, concurrency one, one-second minimum start spacing,
10-second connection and 30-second read timeout, hard 120-second overall stop.
No historical pilot is ready to approve until actual symbol/token mappings,
calendar and corporate-action evidence are available. The general offline planner
ceiling (2-5 instruments, <=5 dates, <=25 requests) does not expand this proposal.

| Required binding field | Current value |
|---|---|
| Owner approval ID/signature and expiry | PENDING |
| Credential owner and approved operator (identifiers only) | PENDING |
| Valid-session custody/security attestation | PENDING; do not restore/login |
| Selected exchange/symbol/instrument_token pairs | PENDING; no invented tokens |
| Raw master hash, source, observation and publisher date | PENDING |
| NSE session date and exact from/to in IST | PENDING |
| Exact serialized routes/query parameters and request-plan hash | PENDING |
| Calendar and corporate-action evidence hashes | PENDING |
| UTC allowed start/stop and operator shutdown procedure | PENDING |
| Account eligibility/terms review and maximum monetary spend | PENDING; currently INR0 authorized |
| Compressed/decompressed traffic limits and cumulative timeout | PENDING binding review |
| Audit destination and disposable research-only DB | PENDING |
| Independent preservation/HALT/read-only safety preconditions | PENDING |

Never write token values, cookies or credential-bearing responses to the approval
record. Instrument identifiers are reference data, distinct from access tokens.
Stop on any changed mapping, hash, safety state, request count or byte budget.
No order/position/HALT mutations. Scaling beyond this pilot requires a new decision
after real-data provenance and quality review. Operational NO_GO remains unchanged.
