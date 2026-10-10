# Phase 13.3 optional account observation proposal

Decision: **DENY / UNAPPROVED**. Real request budget currently ZERO.
Prepared 2026-10-10. This is a reviewable proposal, not an executable harness.
Phase 13.3 section 8 requires separate explicit approval; continuation or
silence is not approval. Recommend resolving the broker clarification first.

## Exact optional request and purpose

If separately approved, permit at most **one**
`GET https://api.kite.trade/user/margins/equity`, no query/body. This documented
segment route is verified in [Kite User API](https://kite.trade/docs/connect/v3/user/#funds-and-margins),
accessed 2026-10-10. Purpose: observe presence, type, sign and consistency of
current equity fields for later broker field-mapping review. It cannot itself
prove category eligibility, post-reservation free collateral or qualifying cash.

No profile call: it adds personal data without resolving these four questions.
No holdings, all-segment margins, orders/trades, instruments, calculation POST,
historical or WebSocket. `/margins/orders` budget is ZERO, even though it is
calculation-only. No fallback, redirect, pagination, retry or repeat on success.
One invocation only; at least one second of broker-request quiescence beforehand.
The official [other-endpoint limit](https://kite.trade/docs/connect/v3/exceptions/#api-rate-limit)
is 10/second on the access date; the one-call proposal is deliberately stricter.

Allowlisted response paths, under the single-segment `data` object:

```text
status
data.enabled
data.net
data.available.cash
data.available.opening_balance
data.available.live_balance
data.available.intraday_payin
data.available.adhoc_margin
data.available.collateral
data.utilised.debits
data.utilised.payout
data.utilised.holding_sales
data.utilised.m2m_realised
data.utilised.m2m_unrealised
data.utilised.option_premium
data.utilised.stock_collateral
data.utilised.liquid_collateral
```

Other fields are ignored in memory and never emitted. Error bodies may be
classified using a bounded error type; never print messages or payloads.
Emit fixed field names and presence/type/sign/consistency flags only. No raw
balances, account identities, headers, cookies, credentials, token fingerprints,
ciphertext, holdings or response snapshots in logs, docs or committed artifacts.
Numeric data may exist transiently in memory for flags; no eligibility arithmetic.

## Mandatory implementation and isolation checks before any call

The current `KiteRestTransport.Endpoint.MARGINS` permits `/user/margins`, not
the proposed segment path. `BrokerMargins` requires both normalized segments.
Do not call the broader route or invent a commodity row to bypass that contract.
An isolated one-shot allowlisted observation path and strict single-segment
parser must first be implemented and tested in the existing Java transport
architecture under the approved scope. Neither is added/activated in this phase.
Approval alone does not make the present application a safe harness.

1. No ordinary Spring application startup, Flyway, scheduler, token restore/reset,
   login exchange, profile validation, order gateway, operator execution, arm,
   permit or writable repository. A read-only transaction cannot protect against
   writes by a different startup connection; exclude those components entirely.
2. Use an existing valid credential with independently established private
   account binding. Read encrypted storage with a SELECT-only DB role and
   read-only transaction. Decrypt in memory without logging. Missing/expired
   token or inability to establish intended account binding aborts before HTTP;
   do not add an unapproved profile call. No save/delete/cleanup/refresh.
3. Independently verify effective HALT/emergency stop and live execution disabled
   immediately before the request, without starting or changing the trading app.
   Abort if safety state cannot be established read-only. Assert no operational
   permits, no arm/execute ability and no concurrent trading writer.
4. Before/after compare token-row ciphertext/nonce/time fingerprints and counts
   privately in memory. Also compare counts and content integrity for actual
   trading tables: auth attempts, orders/events, risk decisions, authorizations,
   admission and reconciliation state, using the inspected schema. Emit only
   unchanged/changed flags. No raw fingerprints. Concurrent change means abort
   and report an inconclusive integrity check; never repair or overwrite.
5. Fixed HTTPS host, exact GET/path allowlist, TLS verification, no redirects,
   no request/header/body logging. Connect timeout 10s, response timeout 30s,
   maximum wire and decoded body 64 KiB. Strict duplicate/trailing JSON rejection
   and bounded BigDecimal parsing. No dynamic endpoint or method input.
6. First pass loopback tests for success, malformed/oversized bodies, 401/403,
   429, 5xx, timeout, disconnected peer, expired token and changed safety/integrity
   state. Assert maximum one GET, zero other methods/routes and durable writes.
   Recheck HALT/token-write-free invariants before HTTP, not just at initialization.

Any authentication rejection invalidates isolated memory only; stop without
durable token changes or relogin. Stop on 429/5xx/timeout, any malformed response,
unknown safety/account binding, integrity drift or potential secret exposure.
Close resources, preserve HALT and report NOT_READY. Unused budget expires.

No approval or no safe harness means zero real calls and
**REAL_ACCOUNT_VERIFICATION_NOT_PERFORMED**. Even a successful single observation
is not a reservation, final-dispatch guarantee, authenticated support statement
or independent evidence for the four missing terms. A later real calculation
would require a different explicitly itemized proposal and separate approval.
