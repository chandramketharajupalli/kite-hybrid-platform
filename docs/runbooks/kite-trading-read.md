# Phase 5A controlled Kite trading reads

These commands perform one explicit read per invocation. They use the existing
interactive login and encrypted token store. No order execution, modification,
cancellation, position conversion, strategy, risk approval or polling is enabled.
Automated tests use fake responses; real Kite validation is a separate manual step.

## Start the local application

Use the existing configured `.env`, encryption key, registered callback and local
PostgreSQL/Redis setup from [Kite authentication](../../README.md#kite-authentication).
Do not populate `KITE_ACCESS_TOKEN` for this feature. If another development Java
instance is running on port 8080, stop that instance before starting this one.

In PowerShell:

```powershell
Set-Location 'D:\Yogendra\kite-hybrid-platform'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
docker compose up -d --wait --wait-timeout 120
.\scripts\Use-DevelopmentInfrastructure.ps1
$env:KITE_REST_ENABLED = 'true'
$env:KITE_TRADING_READ_ENABLED = 'true'
$env:KITE_TRADING_READ_DIAGNOSTIC_ENABLED = 'true'
$env:KITE_MARKET_DATA_ENABLED = 'false'
$env:KITE_MARKET_DATA_DIAGNOSTIC_ENABLED = 'false'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SPRING_PROFILES_ACTIVE = 'development'
.\mvnw.cmd -pl apps/trading-core '-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC' spring-boot:run
```

The JDK path is the existing installation validated on this workspace; select an
already installed JDK 21 if using another machine. All variables above are local
to this PowerShell process. Existing authentication restoration may validate the
profile and initialize instruments on startup; the five new reads never run then.

In a second PowerShell window, check authentication and registry initialization:

```powershell
$kiteAuth = Invoke-RestMethod 'http://localhost:8080/api/broker/kite/auth/status'
$kiteAuth | Select-Object code, authenticated, initializationReady
if ($kiteAuth.code -ne 'KITE_AUTHENTICATED') {
    throw 'Complete the existing browser login and instrument initialization first.'
}
```

If needed, open `http://localhost:8080/api/broker/kite/auth/login` in your browser,
complete the official interactive login, and rerun the status check. For
`KITE_INITIALIZATION_PENDING`, retry the existing initialization/login path. Do not
copy credentials or create a second session. Do not run browser JavaScript against
these diagnostic routes; use PowerShell without an Origin header.

## Read each snapshot once

```powershell
$tradingRead = 'http://127.0.0.1:8080/api/development/trading-read'
Invoke-RestMethod -Method Get "$tradingRead/positions" | ConvertTo-Json -Depth 15
Invoke-RestMethod -Method Get "$tradingRead/holdings" | ConvertTo-Json -Depth 15
Invoke-RestMethod -Method Get "$tradingRead/margins" | ConvertTo-Json -Depth 15
Invoke-RestMethod -Method Get "$tradingRead/orders" | ConvertTo-Json -Depth 15
Invoke-RestMethod -Method Get "$tradingRead/trades" | ConvertTo-Json -Depth 15
```

Each route returns normalized immutable read models. Order and trade identifiers
and account values are intentionally visible locally for inspection; credentials,
broker messages, user identity, tags and raw payloads are not returned. Responses
have `Cache-Control: no-store`. Keep terminal output private. No request records
these snapshots in the authoritative ledger, updates order state, computes P&L,
or approves trading. P&L values are broker-reported observations.

Metrics can be inspected through the existing Prometheus exposure:

```powershell
(Invoke-WebRequest 'http://127.0.0.1:8080/actuator/prometheus').Content -split "`n" |
    Select-String '^kite_rest_(operations|duration)'
```

Success/failure counters and durations use operation/result labels only. A failed
request is not retried automatically. Inspect its safe category before retrying.

| Result | Meaning |
| --- | --- |
| 404 | Diagnostic profile or either explicit flag is missing |
| 403 | Request failed the loopback/Host/browser-origin guard or supplied proxy headers |
| 405 | Diagnostics accept GET only |
| 401 / `AUTHENTICATION` | Session absent, unverified, expired or rejected; complete existing login |
| 502 / `BROKER_API` | Broker returned an error; safe upstream HTTP status is included |
| 502 / `TRANSPORT` | Network read failed; no credentials or raw cause are returned |
| 502 / `INVALID_RESPONSE` | Invalid/incomplete/oversized data or unresolved/conflicting instrument mapping; no partial result |

An empty orders/trades/holdings list or empty position lists is valid. Empty or
missing margin segments are invalid; disabled segments must be explicitly supplied.
Unknown enum values appear as `UNKNOWN` and do not assert a known lifecycle state.
An instrument absent from the current registry rejects the response instead of
creating an identity. An expired/delisted instrument may require future historical
reference-data support. The five requests are separate observations and do not
form an atomic cross-account snapshot. Kite orders and trades cover the current
trading day, not a historical ledger.

## Stop

Stop Java with Ctrl+C, then remove the diagnostic opt-ins in that same shell:

```powershell
Remove-Item Env:KITE_TRADING_READ_ENABLED -ErrorAction SilentlyContinue
Remove-Item Env:KITE_TRADING_READ_DIAGNOSTIC_ENABLED -ErrorAction SilentlyContinue
```

No new broker connection or polling worker needs shutdown. Existing database and
Redis services can remain running for other development work.

Documentation checked for this implementation: [orders and trades](https://kite.trade/docs/connect/v3/orders/),
[positions and holdings](https://kite.trade/docs/connect/v3/portfolio/),
[account margins](https://kite.trade/docs/connect/v3/user/), and
[broker errors](https://kite.trade/docs/connect/v3/exceptions/).
