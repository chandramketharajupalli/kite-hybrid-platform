# Phase 14.0 historical acquisition proposal — UNAPPROVED

No token or broker endpoint was accessed in this phase. Public API documentation
was reviewed on 2026-10-12: [historical endpoint and intervals](https://kite.trade/docs/connect/v3/historical/)
and [errors and rate limits](https://kite.trade/docs/connect/v3/exceptions/).
They document `5minute`, token-based GET requests with from/to dates, and a
historical rate limit of 3 requests/second. The current implementation uses a
stricter one-second process-wide pacing, one-session/day windows, concurrency one,
continuous=0 and oi=0. Do not infer equity adjustment guarantees, account access,
daily quota or a maximum five-minute range from those pages. Maximum range,
current terms/licensing, account entitlements and redistribution limits must be
verified before real use. No existing margin-GET approval is transferable.

Dry-run planning supports up to 366 days and deduplicates resolved instrument IDs.
Exact count equals 1,115 x confirmed requested sessions, before zero automatic
retries. An illustrative six-month period 2026-04-12..2026-10-12 and twelve-month
period 2025-10-12..2026-10-12 must first receive official calendar evidence.
The machine-readable estimate records calendar-day upper bounds and weekday-only
illustrations; weekdays are not an NSE calendar and exclude special weekend
sessions. Elapsed estimates exclude network/DB overhead, downtime and session
renewal. They are not promised access or completion times.

429 (including Retry-After), authentication errors and transient failures abort
without retries. Completed session checkpoints survive; partial/conflicting
evidence blocks reuse. Any restart requires reviewing the response and waiting
at least the applicable Retry-After delay; the current transport categorizes
errors but does not persist that header. No automatic resumable retry loop is
authorized. Cross-process credential quota coordination remains a pilot prerequisite.

Proposed next decision, not authorization: after independent security/compliance
review, provide a dated master and action/renaming evidence, current valid session
through the existing approved custody process, and a bounded read approval naming
at most three resolved stocks, five dated sessions each, <=15 historical GETs,
one request/second, zero retries, fixed cutoff and explicit expiry. Stop on the
first error, changed mapping, unexpected response or safety-state change. Use an
isolated research DB; attest unchanged HALT/order/position state. Production
five-minute factory remains disabled pending reviewed pilot wiring. No login,
token restoration or operational-state changes are part of this proposal.

Scale to all 1,115 only after a successful pilot, provenance/quality review, API
quota/time validation and new explicit approval. Missing mappings remain visible;
never replace symbols or invent tokens. Live trading remains NO_GO, collateral
MIS NOT_READY and broker funding eligibility unproven.
