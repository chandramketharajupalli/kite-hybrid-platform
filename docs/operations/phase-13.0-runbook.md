# Phase 13.0 safe local runtime runbook

This runbook authorizes synthetic testing only. No real Kite account, candle,
calculator or WebSocket call is part of Phase 13.0. No live/paper orders, HALT
resume, arm, execution permit, strategy execution or production startup.
Read the [plan](phase-13.0-plan.md), [contracts](../architecture/kite-connect-runtime-hardening.md)
and [validation](phase-13.0-validation.md) before interpreting results.

## Synthetic startup and diagnostics

Use the repository Maven wrapper with the installed JDK 21 and Docker engine.
Never source `.env`, a diagnostics helper, or saved real account settings.
Open a disposable child PowerShell in `D:\Yogendra\kite-hybrid-platform` and
clear ambient deployment overrides in that child only, without printing values:

```powershell
$phase13Names = @([Environment]::GetEnvironmentVariables('Process').Keys | Where-Object {
    $_ -match '^(KITE_|RISK_|SPRING_|DB_|REDIS_|TRADING_|SERVER_|MANAGEMENT_|ENABLE_LIVE_TRADING$|EMERGENCY_STOP$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$|MAVEN_OPTS$)'
})
foreach ($phase13Name in $phase13Names) {
    [Environment]::SetEnvironmentVariable($phase13Name, $null, 'Process')
}
$env:PYTHONDONTWRITEBYTECODE = '1'
.\mvnw.cmd '-Dtest=KiteRestReadTest,KiteAuthenticationUseCaseTest,JdkKiteWebSocketTransportTest,KiteHistoricalAdapterTest,IntradayFundingEvidenceTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

The focused tests use fake HTTP and literal loopback WebSockets. Full unit tests
start isolated Spring test contexts; this profile excludes datasource, Flyway
and Redis auto-configuration. Disposable integration starts real Spring wiring
against Testcontainers PostgreSQL with synthetic credentials and injected fake
broker services. Do not replace its datasource URL or transport with deployment
settings. Tests are the safe local startup path for this phase; do not run the
normal development server, which can migrate a configured trading database and
restore a real credential. Redis is optional and not used as readiness truth;
no Redis connection is needed by these changed read paths.

Use test assertions and bounded categories as diagnostics. Existing operator
and reconciliation suites contain simulated execution fixtures to test safety
boundaries; those fixtures are not instructions to run an operator console.
The new status regression keeps HALT effective and asserts zero order and
execution-authorization rows while preserving the encrypted credential row.

Actual routes verified from source, for interpreting isolated tests:

* `/actuator/health/liveness`: process state. `/actuator/health/readiness`:
  application/DB availability outside the test profile. Neither means trading
  ready. Missing infrastructure can prevent startup or fail readiness.
* `/actuator/tradingstatus`, `/actuator/kitestatus`,
  `/actuator/marketdatastatus`: default HTTP 404 because not exposed. When
  explicitly exposed in a future isolated diagnostic context, bind management
  and application to loopback and expose only the selected names; never `*`.
  Trading status remains false while HALT/emergency stop is effective and in
  fact remains hard false in this implementation. Do not resume it for a check.
* `/api/broker/kite/auth/status`: passive status, no broker request or durable
  token cleanup. Login/callback/reset are lifecycle actions, not passive probes.
* `/api/development/trading-read/{orders,trades,positions,holdings,margins}`:
  requires read/diagnostic flags, development but not production, and local
  request checks. These detailed account routes make broker calls when backed
  by a real transport; do not call them in Phase 13.0 outside injected fakes.
* `/api/development/market-data/{start,subscriptions,stop,status,latest}`:
  guarded development diagnostics. Start/subscription controls can open a real
  socket under real configuration; use only the loopback test fixture here.

`IntradayFundingEvidence.inspect` is a Java-only diagnostic projection. Invoke
it with current synthetic normalized observations for testing; there is no
new HTTP endpoint or real-fetch command. Its labels distinguish observed fields,
derived application cash and calculator margin/charges from UNKNOWN eligibility.
An APPROVED cash-only arithmetic field is not overall readiness; `readiness`
is always NOT_READY. Output omits account values and identity.

## Error triage

| Symptom | Interpretation and safe next step |
| --- | --- |
| Missing/expired token | AUTH_REQUIRED; no broker read is possible. Test fake expiry/missing-token cases. Real interactive login is a separately authorized future action; no automatic refresh/password/MFA flow. |
| 401/403 | Ordinary transport invalidates only in-memory session. Status reports denial without deleting durable tokens. Do not invoke restore/reset from a read-only harness; deliberate restore retains existing cleanup semantics. Historical errors preserve the shared session but fail the read. |
| Valid TokenException with other status | Retained compatibility behavior treats a strict error envelope as auth rejection, including HTTP 200/400/500. Duplicate/trailing JSON is not trusted. |
| 429 | Stop the read; no automatic retry. Check the separately approved call budget/pacing before any later request. |
| 5xx/network loss/timeout | Fail the observation; no order fallback, retry, or readiness from stale snapshots. Without a valid TokenException, durable and in-memory credentials survive an ordinary read. Restore itself remains fail-closed on transient profile validation. |
| Malformed JSON/UTF-8/gzip or payload limit | INVALID_RESPONSE; no partial normalization. Keep only bounded category/status evidence, not a raw response or exception cause. |
| WebSocket reconnect/stale/idle | Inspect bounded reconnect attempts, active/desired counts, tick age and queue/decode/drop counters. Heartbeats are not ticks. Do not bypass preflight; test subscription restoration and exhaustion locally. |
| Queue overflow/backpressure | Quality degrades and dropped events are counted. Do not treat a connected socket as healthy or increase production limits to force PASS. |
| MIS evidence insufficient | Keep collateral/cash-field eligibility UNKNOWN and NOT_READY. Preserve cash-only reserve, separate charges and full buffered notional ceiling; no net/commodity/utilised-collateral credit. |
| Corporate-action certification blocked | Phase 12 stays BLOCK; zero provider calls/writes. Fix evidence in a separately reviewed generation; do not bypass the gate or download a corpus. |
| Diagnostic 404/profile mismatch | Expected with flags disabled or an incompatible profile. Verify source mappings and the test fixture; do not enable broad actuator exposure or production diagnostics. |
| Wrong encryption key | Bounded storage/decryption error. Never print keys, ciphertext, tokens or raw rows; do not overwrite a real token or change `.env`. |

## Shutdown and recovery

Wait for Maven to finish and inspect its exit code plus Surefire/Failsafe XML
counts. Tests close Spring contexts, loopback peers, executors and containers.
On interruption, inspect only the test process/container ownership before
stopping anything; do not stop or delete unrelated development services/volumes.
Do not use recursive cleanup/reset/restore against the repository. Keep ignored
test logs for diagnosis and audit tracked/untracked files separately.

Recovery from test failure means fixing the narrow defect and rerunning affected
checks. Never alter acceptance, risk limits, defaults, certificates, HALT or
frozen evidence to force a successful result. Do not commit or push this phase.

## Optional future real reads: separate authorization required

There is intentionally no executable real-read command here. Before any such
read, draft `phase-13.0-real-read-plan.md` listing the exact endpoint, maximum
calls, necessity, timeout, independent HALT/stop guard, redaction and token-
preserving harness. It must use read-only DB transactions and before/after
row-count/token-fingerprint evidence without emitting the token. Obtain explicit
operator authorization for that exact plan. Development continuation is not
authorization. Real order mutation remains forbidden regardless of read approval.
