# Phase 13.2 optional real-read proposal — NOT AUTHORIZED

Proposal only, 2026-10-10. No request has been made. Section 11 of the operator
specification requires separate explicit approval. Silence, development
continuation and an engineering PASS are not approval. Synthetic work continues
without it. Recommendation: obtain the broker clarification first; these two
reads cannot independently prove the four missing terms.

## Exact questions and budget

If separately approved, inspect whether the current normalized equity funds
and holdings expose any authoritative eligibility/category/availability field
beyond the documented aggregates, and whether cash/opening/live fields agree.
Do not infer final eligibility from their values.

| Request | Maximum | Permitted use |
| --- | --- | --- |
| GET `https://api.kite.trade/user/margins` | 1 | Equity field presence/sign/consistency; available vs utilised distinction; commodity excluded |
| GET `https://api.kite.trade/portfolio/holdings` | 1 | Pledge/category/adjusted-amount field presence only; no quantity x price reconstruction |

Total maximum **2 requests**, sequential, at least **1 second** between starts,
no retries, redirects, fallback or pagination expansion. Stop after the first
error; unused budget expires. No profile, orders, trades, calculator POST,
instruments, candles or WebSocket. No executable request/quantity selection.
Current [official rate limits](https://kite.trade/docs/connect/v3/exceptions/#api-rate-limit),
accessed 2026-10-10, list other endpoints at 10 requests/second; this proposal's
one/second ceiling is intentionally stricter and not a new production limit.

## Harness requirements before any request

Use the existing Java read transport/normalizers in a deliberately isolated,
one-shot harness; never boot the ordinary Spring development application.
The harness is not implemented or activated by this proposal. Before a real
run it must pass fake 401/403/429/5xx/timeout and zero-write/allowlist tests and
be reviewed against this exact budget. Approval alone cannot waive these checks.

* Independently verify effective HALT/emergency stop and live execution disabled
  immediately before each request. The harness has no operator, order gateway,
  permit, repository mutation, Flyway or scheduler capabilities. If operational
  safety cannot be verified read-only, abort; never resume/arm or change config.
* Use only an already valid stored credential, with read-only DB credentials/
  transactions. No `.env` modification, token exchange, login, restore/reset,
  refresh, token save/delete or profile validation call. A missing/expired token
  ends the proposal without a network request.
* In memory only, decrypt the existing token without logging it and install it
  in the isolated session as needed for the existing read adapter. This local
  read context is not a trading session or proof of account eligibility.
* Match existing connect/read limits: 10s/30s. Bound decoded and wire responses
  using existing transport limits: margins 64 KiB; holdings 4 MiB. No HTTP body,
  header, wire logging or exception cause output.
* Before and after, compare read-only encrypted-token-row fingerprints, token
  row count and trading-table integrity fingerprints/counts inside the harness.
  Include auth attempts, orders/events, risk decisions, authorization/admission
  and reconciliation state as applicable to actual schema. Emit only identical/
  changed flags. Stop if another writer prevents a stable comparison; do not
  attribute concurrent changes to the harness or overwrite anything.
* Emit only fixed field names, presence/type/validity flags, scoped evidence
  status, local times and bounded denial categories. No balances, identities,
  symbols/ISINs/quantities, request/access tokens, API keys/secrets, authorization
  headers, cookies, session IDs, ciphertext or raw snapshots are retained.

401/403 invalidates only isolated memory; durable token remains untouched.
Any 429/5xx, malformed/oversized/duplicate/trailing JSON, timeout, connectivity
failure, unexpected endpoint, auth loss, safety-state uncertainty, DB/token
integrity mismatch or secret exposure ends the run with NOT_READY. Do not retry,
repair credentials or mutate the account. Close transient resources.

Even two successful responses are observations, not an attestation of eligible
free collateral or cash-component compliance. All unresolved terms stay UNKNOWN.
No approved-security list match or calculator success can fill them. A separately
reviewed broker response to the clarification draft is still needed.
