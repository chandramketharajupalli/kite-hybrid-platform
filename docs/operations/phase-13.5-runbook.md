# Phase 13.5 controlled equity-read runbook

Stage A is synthetic. Stage B defaults DENY, zero real requests. Do not run a
trading application, CLI profile diagnostic, login/restore/reset, calculator,
WebSocket or historical command to satisfy a missing precondition.

## Official contract and limits

Accessed 2026-10-10: [Kite User / funds](https://kite.trade/docs/connect/v3/user/#funds-and-margins)
specifies GET `/user/margins/:segment`, equity or commodity, version 3 and token
authorization headers. Its example illustrates the combined body. The existing
mapper's direct `status=success,data=<equity segment>` shape remains a synthetic
assumption until separately approved observation. It rejects wrappers/extra
segments, duplicate/trailing JSON and missing/null fields rather than guessing.
No publication/effective date for this API page was stated in retrieved content.

Field allowlist: `enabled`, `net`; available `adhoc_margin`, `cash`,
`opening_balance`, `live_balance`, `collateral`, `intraday_payin`; utilised
`debits`, `exposure`, `m2m_realised`, `m2m_unrealised`, `option_premium`, `payout`,
`span`, `holding_sales`, `turnover`, `liquid_collateral`, `stock_collateral`,
`delivery`. Values remain exact bounded BigDecimal, transient/private only.
Unknown fields cannot supply eligibility. No commodity row is synthesized.

The [Apache HTTP builder](https://hc.apache.org/httpcomponents-client-5.6.x/current/httpclient5/apidocs/org/apache/hc/client5/http/impl/classic/HttpClientBuilder.html)
documents explicit retry/redirect/cookie/auth-cache disabling. The isolated wire
uses the existing Boot-managed HttpClient5 5.5.2 dependency and Spring request
factory; compilation and loopback response-loss tests validate the local API.
No global JVM retry property or normal transport permission is changed. TLS
verification remains default; no custom trust manager, proxy or hostname override.

## Internal composition and synthetic commands

No normal Spring bean, controller, public Actuator endpoint, credential-loader
CLI or arbitrary-URL client is added. The real wire also shares a non-resettable one-attempt budget across factories
in the JVM; no process restart is authorized to renew an exhausted approval.
An unexpected route poisons its factory. The package-local harness accepts an
already authenticated KiteSession, a SELECT-only connection guard, the HALT
latch, a clock and an explicit mode. Its default mode is DISABLED. The normal
adapter, strict mapper and session-bound snapshot are reused.

Run tests from the repository root with JDK21 and Maven wrapper. Remove only
child-process deployment/JVM overrides as in Phase13.4; never load/print .env.
Docker is used only for disposable Testcontainers PostgreSQL. Test credentials
are synthetic; fake account calls are intercepted or directed to literal loopback.

```powershell
.\mvnw.cmd '-Dtest=KiteEquityReadHarnessTest,KiteEquityReadRequestFactoryTest,ControlledEquityReadArchitectureTest,KiteEquityMarginReadAdapterTest,KiteTradingReadMapperTest,IntradayCollateralContractTest,TradingReadArchitectureTest,KitePhase2SafetyTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
$env:PYTHONDONTWRITEBYTECODE='1'
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

The focused disposable selector is standard Failsafe:
`./mvnw.cmd -Pintegration '-Dit.test=KiteEquityReadHarnessIntegrationTest' verify`.
It still runs the complete unit stage. The full integration command remains
mandatory; a selector does not replace it. Existing operator regressions use fake
execution/permit/HALT fixtures, never operational live/paper trading.

## No-write and isolation procedure

The harness cannot acquire credentials or create a connection. A separately
reviewed embedding must supply an existing valid authenticated session, scoped to
the intended account and token row, without a profile call or token installation.
The enum `SEPARATELY_APPROVED_REAL` expresses an operator-selected mode; it is not
proof of user approval. The operator must separately verify the approved proposal.
If safe authenticated handoff is unavailable, stop with zero calls. Never fake
profile validation on a real token or run the main application to obtain it.

Database checks are hardcoded SELECT only on the supplied connection: server
transaction_read_only on, autocommit and READ COMMITTED, restricted role without
write/sequence/schema-create privileges, membership in pg_read_all_stats so other
users cannot be hidden, no RLS-hidden trading rows, other client
connections or prepared transactions. Required token/order/risk/authorization/
reconciliation tables must exist; exactly one encrypted token row and no allowed
authorization rows. A conservative denial may occur for historical allowed audit
rows; do not delete them to make this pass. No role/configuration/transaction
changes are made by the harness. PostgreSQL statistics visibility follows the
[predefined-role contract](https://www.postgresql.org/docs/17/predefined-roles.html)
(accessed 2026-10-10); insufficient visibility denies rather than assumes isolation. Query timeout 10s, row/row-size bounds fail closed.

Before/after capture covers every trading table, sorted by row JSON, with row
count and SHA-256 of length-delimited UTF-8 row bytes. Only fingerprints/counts
remain in private memory; raw rows are neither logged nor persisted. Fresh
autocommit snapshots prevent a long transaction from hiding concurrent changes.
HALT must be startup-halted and runtime HALTED with unchanged epoch; the isolated
composition contains no arming, permit, strategy, order or dispatch component.
Debug wire/header logging denies the request. A rejection invalidating in-memory
authentication is permitted; durable token cleanup is not.

These checks are observations, not a lock against a future independent process.
Before real use, the operator must independently establish that all other trading
processes are stopped/quiesced and cannot reconnect for the entire window, with
live execution/arming disabled. No single read-only JDBC connection proves that.
If external inactivity/account binding cannot be established, Stage B is blocked
even after approval. No development role provisioning or process shutdown is
authorized by the phase instruction itself.

## Outcome and error triage

| Result | Required handling |
| --- | --- |
| DISABLED / ALREADY_USED | Zero additional calls; do not instantiate another run under the same approval. |
| PRECONDITION_DENIED | Missing isolation, HALT, role, token-row or mode evidence; stop, no repair writes. |
| AUTHENTICATION | Missing/unvalidated/expired/changed session, 401/403 or valid token error; no login/refresh/profile/cleanup. |
| BROKER_API | 429/5xx or other rejected status including redirect; stop, no retry. |
| TRANSPORT | Timeout/connectivity/response loss; attempt consumed even without usable response. |
| INVALID_RESPONSE | Malformed/oversized/ambiguous shape, bad decimals, stale/future observation; no fallback. |
| STATE_CHANGED | Before/after DB/HALT proof failed; discard observation, stop and report without repairing state. |
| OBSERVED | Only a redacted receipt/provenance is released after checks. No funding eligibility or execution authorization. |

`statePreserved` refers to durable table fingerprints and HALT, not continued
in-memory authentication after broker rejection. Null observation is never usable.
Only bounded outcomes, attempt count, local time and integrity booleans may appear
in general reports. No raw balances, account/token/session identifiers, encrypted
token bytes or token fingerprints in Git, logs or report artifacts.

Use the receipt time and source with existing
`IntradayFundingEvidence.equityObservation` for a separate pure V1 projection.
Preserve any existing exact request/reference fingerprints; absent real request
context remains absent. Do not invent a candidate/quantity. Source catalog IDs,
an authenticated response and caller-normalized data cannot create independent
broker authority. Cash-only arithmetic remains separate; standalone equity
projection is MIS_MARGIN_UNAVAILABLE and collateral-assisted readiness NOT_READY.

Public evidence remains Q1/Q2 UNKNOWN and Q3/Q4 unresolved source conflicts in
[Phase13.3](../architecture/kite-mis-authoritative-evidence.md). Do not derive
free collateral from net or utilised fields, select a positive cash field or
apply F&O ratios to NSE MIS. Preserve the unsent broker clarification draft.

## Stage B and shutdown

Only the exact Stage B approval document may be submitted for separate approval
after Stage A passes. No approval means REAL_READ_AWAITING_APPROVAL. A proposal
file or successful test is not authorization. Maximum proposed real budget:
one GET https://api.kite.trade/user/margins/equity; no profile, calculator, orders,
holdings, candles, WS, redirect, retry or supplemental request.

After the single attempt, close only the isolated wire/client and caller-owned
read-only connection and release transient memory. No DB cleanup, token reset,
HALT resume, policy edit or unrelated Docker cleanup. Retain only redacted
validation/approval/evidence documents. INR 10,000 full buffered notional, cash
reserve, CNC, Phase12 BLOCK and sealed/frozen research remain unchanged.
