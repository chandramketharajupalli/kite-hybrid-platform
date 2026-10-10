# Phase 13.5 Stage B proposal — CONDITIONALLY APPROVED; ZERO_CALL_ABORT

Continuation status (2026-10-10): the user explicitly approved this exact
one-GET/no-write scope. Mandatory live preconditions could not be independently
verified, beginning with an existing authenticated in-memory session and reviewed
handoff. No request was sent. See phase-13.5-validation.md, Stage B continuation.
The original proposal below is retained as the approval scope; its original
NOT APPROVED/awaiting language describes the state before that user approval.
Approval does not waive any precondition or authorize token restoration/startup.

Proposal version: Phase13.5/v1, 2026-10-10. Default decision **DENY**.
Stage A: 1447 unit/architecture and 546 disposable integration tests passed,
zero failures/errors/skips in the final full build. Tests are synthetic evidence,
not approval, real schema confirmation or broker funding eligibility.

## Exact request submitted for separate approval

- **Method and endpoint:** one `GET https://api.kite.trade/user/margins/equity`.
  [Official Kite route documentation](https://kite.trade/docs/connect/v3/user/#funds-and-margins),
  accessed 2026-10-10; fixed official origin is also enforced in code.
- **Maximum:** one request attempt total, zero retries, no redirects, pagination,
  polling, supplementary endpoints or second process/run to renew the budget.
- **Purpose:** observe the live equity funds response shape and minimal field
  presence/type validity; not authorize MIS funding or trading.
- **Excluded:** profile, all-segment funds, positions, holdings, orders, trades,
  margin calculations, historical data, WebSockets, login, exchange and refresh.
  Order placement/modification/cancellation is forbidden under all outcomes.

Approval must explicitly name this endpoint, maximum count and no-write scope.
This file and the Phase13.5 instruction are not approval. Do not select the real
mode enum until separate approval is verified and every precondition below holds.

## Preconditions immediately before dispatch

1. Existing authenticated, unexpired in-memory KiteSession with independently
   established intended account/token-row binding. No token installation,
   profile-validation shortcut, restore/reset, login or credential exchange.
   The internal harness has no credential loader. A safe handoff must already
   exist and be independently reviewed; if unavailable, abort with zero calls.
2. Active startup HALT and runtime HALTED, stable epoch, live execution and arming
   disabled. The isolated composition constructs no execution/permit/strategy/
   order/market-data component. No main Spring startup, Flyway or background task.
3. Independently verified exclusive observation window: other trading processes
   stopped/quiesced and unable to reconnect or dispatch. The harness verifies no
   other database clients/prepared transactions, but cannot prevent a future
   external process. Unknown external isolation means abort, not assumed safety.
4. Existing SELECT-only connection/role, server-enforced transaction_read_only,
   autocommit READ COMMITTED, pg_read_all_stats visibility, no elevated/table-write/
   sequence/schema-create rights or RLS-hidden rows. Required trading tables,
   exactly one encrypted token row, no allowed authorization rows. Do not provision
   roles, clear audit history, change .env/configuration or stop other services
   under this approval. If such work is needed, stop for a separate scoped plan.
5. Capture private before fingerprints/counts for every trading table, including
   encrypted token, order, risk, authorization and reconciliation rows. Preserve
   HALT epoch and session identity privately. No writable connection or store in
   the harness; no state-restoring cleanup. Recheck immediately before HTTP.
6. Confirm TLS verification and all HTTP/TLS/JDBC debug logging, tracing and dumps
   disabled. Harness rejects enabled Apache wire/header DEBUG. No credentials in
   CLI arguments, stdout/stderr, exception text, logs or artifacts.

The one-shot request uses the existing adapter/strict mapper with a dedicated
exact-route factory, retries/redirects/auth challenges/cookies disabled and a
non-resettable process-wide real budget. TCP connect/request acquisition limit
10s, HTTP response timeout 30s, existing 64 KiB wire/decoded parsing bound. Do not
change TLS trust or endpoint. An attempt is spent even on timeout/response loss.

## Minimal extraction and retention

Allowlist: enabled/net; available adhoc_margin/cash/opening_balance/live_balance/
collateral/intraday_payin; utilised debits/exposure/m2m_realised/m2m_unrealised/
option_premium/payout/span/holding_sales/turnover/liquid_collateral/stock_collateral/
delivery. Values are transient and local; unknown fields are ignored, missing/
null/type-invalid/ambiguous or oversized fields fail. The direct single-equity
data shape is currently a synthetic assumption, not verified account evidence.

No raw response, raw balances, identifiers, headers, tokens, session IDs, encrypted
token bytes or token-row fingerprints in Git/logs/reports. Retain only bounded
outcome, conservative attempt count, local receipt time/source, field validation
status, integrity-match/HALT booleans and the four-question evidence matrix.
Private hashes/counts are compared in memory and released with the process.

## Stop and postconditions

Stop before HTTP for missing approval/authentication or any unverifiable
precondition. Stop after the single attempt for 401/403/429/5xx, redirect,
unexpected endpoint/response, malformed/duplicate/trailing JSON, invalid decimals,
timeout, changed session/HALT or any database integrity discrepancy. No retry,
repair call, token cleanup or DB write. Broker rejection may invalidate only
in-memory authentication. All error outcomes still attempt independent post-checks.

Compare all before/after private hashes/counts; verify HALT and its epoch unchanged.
Discard observation if checks fail or receipt is stale/future. Close only isolated
HTTP resources and caller-owned read-only connection, leaving durable state and
all safety defaults unchanged. Do not resume HALT, arm, consume/grant permits,
select a candidate/quantity or dispatch.

Four default results remain eligible adjusted collateral UNKNOWN, actually free
collateral UNKNOWN, exact cash rule UNKNOWN/CONFLICTING and qualifying cash field
UNKNOWN/CONFLICTING. A successful GET may close none of them. Preserve existing
V1 request/reference/freshness semantics without inventing missing real request
context. Collateral-assisted MIS stays NOT_READY unless separately established;
even complete evidence would grant no trading authority here.

**HARD STOP: separate explicit user approval is required. Until received,
REAL_READ_AWAITING_APPROVAL; real Kite requests = 0.**
Approval is conditional on every control above, not a waiver of missing evidence.
