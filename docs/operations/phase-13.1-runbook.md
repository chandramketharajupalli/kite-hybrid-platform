# Phase 13.1 synthetic MIS evidence runbook

No real/paper order authorization. No operational resume, arm, permit, execute,
login, token exchange, account call or WebSocket connection. Keep HALT effective.
Read the [plan](phase-13.1-plan.md), [contract and official sources](../architecture/kite-mis-funding-evidence.md)
and [validation](phase-13.1-validation.md).

## Interpret the three different questions

1. **Cash-only policy sufficiency:** current synthetic equity cash lower bound
   covers required MIS margin, charges and cash-only reserve under the existing
   application equations. `APPROVED`/PASS here is only arithmetic evidence.
2. **Broker collateral eligibility:** requires authoritative current eligible
   adjusted collateral, actually available collateral, applicable cash component
   and cash-field eligibility mapping. All four are UNKNOWN in this phase;
   collateral-assisted readiness is NOT_READY.
3. **Trading readiness:** diagnostic success grants nothing. Authorization is
   BLOCKED; summary readiness is always NOT_READY. HALT, independent execution
   gates and the INR 10,000 full buffered-notional ceiling remain unchanged.

## Safe synthetic startup and validation

Use installed JDK 21, repository Maven wrapper and Docker. Do not source `.env`
or start the regular development application; it may restore real credentials
and migrate a configured database. Use a disposable child PowerShell:

```powershell
cd D:\Yogendra\kite-hybrid-platform
$phase131Names = @([Environment]::GetEnvironmentVariables('Process').Keys | Where-Object {
    $_ -match '^(KITE_|RISK_|SPRING_|DB_|REDIS_|TRADING_|SERVER_|MANAGEMENT_|ENABLE_LIVE_TRADING$|EMERGENCY_STOP$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$|MAVEN_OPTS$)'
})
foreach ($phase131Name in $phase131Names) {
    [Environment]::SetEnvironmentVariable($phase131Name, $null, 'Process')
}
$env:PYTHONDONTWRITEBYTECODE = '1'
.\mvnw.cmd '-Dtest=IntradayFundingEvidenceTest,IntradayFundingDiagnosticsTest,IntradayAccountCapacityTest,KiteOrderMarginAdapterTest,ConservativeValuationArchitectureTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

Run Maven sequentially. Unit tests use normalized synthetic inputs and fake
HTTP/loopback services. Integration owns Testcontainers PostgreSQL and creates
disposable databases, with fake broker adapters. Never replace these URLs or
services with development configuration. No Redis truth or Redis deployment
connection is required for this evidence path.

`IntradayFundingDiagnosticsTest` demonstrates `detail` and
`summarizeSynthetic`. Output has stable enum/field ordering, local timestamps,
request checksum and no raw balances/identities. Use only the returned view for
diagnostics; do not serialize the input account records or exceptions. The new
disposable runtime test checks unchanged operator status, HALT, zero order and
execution-authorization rows and zero gateway/HTTP mutation calls.

Existing full regression suites intentionally exercise isolated simulated
execution/permit fixtures to verify denial, one-shot behavior and reconciliation.
They do not authorize an operator action or constitute paper/live trading.
Changed-evidence denial cases must have zero fake broker mutations; diagnostic
calls must never consume a permit. Do not make HALT false to fix a diagnostic.

No new HTTP route exists. `/api/broker/kite/auth/status` is passive in source,
but this runbook uses isolated test assertions instead of contacting a running
development application. `/actuator/tradingstatus`, `/actuator/kitestatus` and
`/actuator/marketdatastatus` remain unexposed by default. Their absence is not
a reason to broaden actuator exposure. Infrastructure liveness/readiness never
means permission to trade. Detailed development account routes can call Kite;
do not invoke them against real configuration.

## Exact denial triage

| Evidence/status | Interpretation and safe action |
| --- | --- |
| AUTH_REQUIRED / missing or expired token | No authenticated observation. Keep NOT_READY; test fake missing/expiry cases. No automatic login/refresh. |
| HTTP 401/403 | Ordinary read invalidates memory; passive auth status preserves encrypted durable token. Do not call explicit restore/reset as a passive probe. |
| HTTP 429 | Single failed read; no retry. Synthetic test only. Future authorized reads require separately budgeted pacing. |
| HTTP 5xx, timeout, connectivity loss | Bounded read failure; no cached success or order fallback. Non-auth transient failures preserve tokens. A strict explicit TokenException retains Phase 13.0 auth-rejection semantics even under another status. |
| Malformed REST/WS error envelope | Duplicate/trailing/invalid JSON is not authentication proof. Read/stream fails or degrades under existing contracts; no eligibility promotion or durable cleanup. |
| POLICY_UNAVAILABLE | Required positive reserve/caps/freshness configuration absent or disabled. Do not change production defaults to get PASS. |
| ACCOUNT_UNAVAILABLE / INVALID_EVIDENCE | Missing capacity/segment, disabled equity, invalid components or uncomputable account state. No commodity substitution. |
| ACCOUNT_STALE / ACCOUNT_FUTURE | Local observation falls outside the accepted clock window; do not reuse an earlier success. |
| QUOTE_MISSING / QUOTE_STALE / QUOTE_FUTURE | A current exact-request quote is absent. Local receipt is not a broker snapshot timestamp. |
| REQUEST_CONFLICT / CONFLICTING binding | Instrument, symbol, shape or quantity does not match. Reject; do not silently resize/rebind a quote. |
| CASH_POLICY_INSUFFICIENT | Margin + charges + cash reserve exceeds accepted cash/capacity. A one-paisa deficit denies; collateral/net/utilised fields cannot repair cash-only sufficiency. |
| ELIGIBLE_ADJUSTED_COLLATERAL_UNKNOWN | No authoritative current eligibility/haircut-adjusted amount for the account/product. |
| AVAILABLE_COLLATERAL_UNKNOWN | Observed aggregate and utilised collateral do not prove an actually available eligible amount. |
| CASH_COMPONENT_UNKNOWN | General positive-cash guidance is known; the complete applicable request/category/debit rule is not established. |
| CASH_FIELD_ELIGIBILITY_UNKNOWN | Broker-authoritative mapping from the relevant cash prerequisite to API/ledger fields is missing. |
| ORDERS_CLEAR blocked | OPEN, partial, pending or UNKNOWN normalized order exists. FILLED/CANCELLED/REJECTED are terminal. Preserve exact reconciliation identity. |
| POSITIONS_CLEAR / ACCOUNT_CLEAN blocked | Nonzero net/day position or unresolved order. Generic production exposure policy remains separate. |
| MARKET_HEALTHY / MARKET_FRESH blocked | Reconnect, stale/idle feed, queue/decode loss or noncurrent price. Heartbeat/connected socket alone is insufficient; use loopback regressions. |
| INSTRUMENT_IDENTITY / FULL_NOTIONAL blocked | Reference/lot mismatch, invalid review ceiling, excessive full buffered notional, quantity/exposure limit, or prerequisite failure. No margin-based sizing. |
| HALT_CLEAR blocked / AUTHORIZATION blocked | Expected safety state; do not resume or obtain a permit. A diagnostic is never an execution grant. |
| Any UNKNOWN check | Evidence could not be computed; never interpret as PASS. Independent PASS checks do not override it. |
| Phase 12 certification BLOCK | All five corporate-action certifications remain unresolved. Zero uncertified provider calls/writes; no acquisition or sealed July access. |

## Shutdown, audit and recovery

Wait for Maven's exit code and inspect Surefire/Failsafe counts, including
errors/skips. Fixtures close their contexts, peers and disposable containers.
If interrupted, identify test-owned resources before stopping them; never stop
unrelated development services or delete volumes. Do not reset/restore/clean
the working tree. Fix a demonstrated narrow defect and rerun affected checks;
record every failure and rerun rather than overwriting the result narrative.

Audit `git status --short`, `git diff --check`, `git diff --stat`,
`git diff --name-status` and untracked paths. Inspect all production diffs.
Recheck G1 SHA/18 sources, protected research hashes and Phase 12 gate without
running research evaluation. No `.env`, migrations, configuration, safety
defaults, reserve or risk limits may change. No commit/push.

## Future real-read proposal: separate authorization required

**REAL_EVIDENCE_NOT_COLLECTED.** No real read is needed to validate this phase's
conservative UNKNOWN behavior, so no executable proposal is activated here.
If future work needs account-specific evidence, first create
`docs/operations/phase-13.1-real-read-proposal.md` and stop for explicit operator
authorization. It must list exact endpoint/method, necessity, maximum calls,
pacing, connect/read timeout, allowed fields, redaction, independent HALT/no-write
checks and stop conditions. A token-preserving harness must avoid restore/reset,
compare durable token/DB fingerprints before/after using read-only access, and
never emit rows or secrets. State current primary rate-limit sources in that
proposal; this phase adds no retry/pacing change.

Account aggregate reads alone may still leave authority gaps. Prefer broker
clarification of the precise cash mapping and eligibility contract before
requesting account access. Continuation of development is not real-read consent.
No real order mutation, streaming, historical download or live promotion follows
from a read-only approval.
