# Phase 13.4 real-read proposal — UNAPPROVED / DENY

Prepared 2026-10-10. This file, the phase instruction and prior proposals grant
no permission. Default and current budget: **zero real Kite requests**.
Separate explicit user approval of this exact plan is required before any request.

## Proposed bounded observation

Maximum **one GET https://api.kite.trade/user/margins/equity**. The path is verified
by [Kite funds documentation](https://kite.trade/docs/connect/v3/user/#funds-and-margins),
accessed 2026-10-10. Purpose: determine whether the authenticated equity response
has the expected shape/field presence and obtain a private current observation.
Synthetic fixtures cannot establish what the current account actually returns.
This GET may still prove **none** of the four eligibility terms; public field
semantics remain insufficient. Broker clarification is recommended first.

No profile GET, all-segment GET, holdings, orders, margin calculation, historical
GET, WebSocket, login, token exchange or refresh. No retries, redirects, fallback
or additional validation request. One process, one attempt, no overlapping reader.
The request is counted when attempted, even on timeout or response loss. No second
attempt in this authorization. No executable order candidate or quantity selected.

## Preconditions before the single request

1. Explicit approval naming this proposal and its one-call scope. Recheck current
   Git safety/configuration inventory without altering it. HALT must be active,
   live execution disabled, no operator execution process/permit/arming and no
   concurrent state-changing application. If this cannot be independently
   established without a forbidden call/write, abort with zero requests.
2. Use an isolated, locally controlled one-shot harness, no Spring application,
   Flyway, scheduler, restore/reset cleanup or authentication use case. Only a
   loopback control surface if needed; outbound allowlist is exactly the HTTPS
   origin/path above. The package-local adapter exists, but this phase has not
   activated or certified a real credential-loading harness. That harness and
   one-attempt enforcement need review before a later approved run.
3. Existing valid token and independently established session/account binding
   must already be available through a secure process-local channel. Never mark
   a real session profile-validated just to bypass an auth check. No additional
   profile call is approved; unavailable/expired/unverified binding means abort.
4. Use a dedicated SELECT-only database role, database-enforced read-only
   transactions and no other writable connection. Verify no table write/sequence
   privileges and no startup process with separate credentials. Do not change
   development roles/configuration to make this possible in this phase.
5. Before/after: compare encrypted token-row bytes and private in-memory digest,
   all trading-table counts/content digests and HALT epoch/status. Never print or
   persist token fingerprints/bytes, identifiers or raw rows. Report only matching
   booleans/count-integrity results. If a consistent comparison cannot be obtained
   because another process is writing, abort before HTTP.

## Response budget and redaction

No retry/backoff; one total call is below documented other-endpoint pacing.
Existing 10s connect/30s read timeout and 64 KiB compressed/decoded size bounds;
strict UTF-8/JSON and duplicate/trailing rejection. Allowlisted extraction:
`enabled`, `net`; available `adhoc_margin`, `cash`, `opening_balance`,
`live_balance`, `collateral`, `intraday_payin`; utilised `debits`, `exposure`,
`m2m_realised`, `m2m_unrealised`, `option_premium`, `payout`, `span`, `holding_sales`,
`turnover`, `liquid_collateral`, `stock_collateral`, `delivery`.

Keep numeric values only in private volatile memory. Persist/report only field
presence/type validity, local observation time, source/method, bounded category,
unverified term statuses and before/after integrity booleans. Ignore unknown
fields. No raw payloads, balances, account IDs, authorization headers, API keys,
tokens, session IDs, cookies or token fingerprints in console/Git/artifacts.
Disable request/body/header debug logs and tracing. No persistent HTTP cache.

## Stop conditions and final disposition

Stop on 401/403/429/5xx, timeout/connectivity loss, redirect, unexpected route,
malformed/oversized/missing fields, changed session/HALT, unverifiable integrity
or redaction failure. No token deletion/refresh/reset and no repair call.
Dispose the isolated process and private memory; leave durable stores and HALT
unchanged. Report NOT_PERFORMED if aborted before HTTP, otherwise the bounded
outcome without readiness promotion. Any write/integrity violation blocks work.

**HARD STOP: explicit user approval is absent. Execute zero real requests.**
Synthetic engineering may finish independently. Approval would authorize only
the request above after all preconditions pass, never an order or funding permit.
