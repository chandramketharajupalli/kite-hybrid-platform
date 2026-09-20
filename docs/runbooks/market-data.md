# Market-data runbook

Phase 4 is market data only. No command below places, modifies or cancels orders,
or enables strategies, signals, risk decisions, position management, P&L or
automated trading. Automated tests use deterministic fixtures, fake transports
and a loopback WebSocket server; they never connect to Kite. Live steps below
are manual and require your explicit action.

The operating sequence is:

```text
Authentication -> Instrument Registry -> Market Data Connection -> Subscriptions
               -> Normalized Ticks -> Latest Market State -> Health
```

## Enable the local diagnostic deliberately

Complete [Kite authentication setup](../../README.md#kite-authentication) first.
Reuse the existing API key, encrypted token storage and encryption key. Do not
copy an access token or populate `KITE_ACCESS_TOKEN` for streaming. Select an
existing JDK 21 and start the existing development PostgreSQL/Redis services as
described in the [local infrastructure runbook](local-development-infrastructure.md).

In the application PowerShell window, run the helper and then explicitly set
market-data flags. Spring does not read `.env`, and the helper does not load the
new market-data variables, even if they are present in `.env`.

```powershell
.\scripts\Use-DevelopmentInfrastructure.ps1
$env:KITE_MARKET_DATA_ENABLED = 'true'
$env:KITE_MARKET_DATA_DIAGNOSTIC_ENABLED = 'true'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE = 'health,info,prometheus,marketdatastatus'
.\mvnw.cmd -pl apps/trading-core '-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC' spring-boot:run
```

The helper selects the development profile and safe trading flags. The UTC JVM
argument follows the existing Windows/PostgreSQL startup workaround. Enabling
these flags creates the diagnostic endpoints but does not connect a stream.
They are unavailable without the development profile and both enabled flags.
These local controls have no multi-user authorization layer; keep loopback binding.

In a second PowerShell window, confirm the existing authentication status:

```powershell
$kiteAuth = Invoke-RestMethod 'http://localhost:8080/api/broker/kite/auth/status'
$kiteAuth | Select-Object code, authenticated
if ($kiteAuth.code -ne 'KITE_AUTHENTICATED') {
    throw 'Complete the existing official browser login and instrument initialization first.'
}
```

If necessary, open `http://localhost:8080/api/broker/kite/auth/login` in your
browser, complete official interactive authorization, and repeat the status
check. `KITE_INITIALIZATION_PENDING` is not ready: retry the existing login
endpoint's initialization path. Do not bypass the instrument registry.

## Subscribe to one instrument and inspect safe data

The diagnostic resolves NSE/INFY from the current registry; it never accepts a
raw Kite token. Start in the minimum LTP mode:

```powershell
$marketData = 'http://127.0.0.1:8080/api/development/market-data'
Invoke-RestMethod -Method Post "$marketData/start?exchange=NSE&symbol=INFY&mode=LTP"
Invoke-RestMethod "$marketData/status"
Invoke-RestMethod 'http://127.0.0.1:8080/actuator/marketdatastatus'
```

Start is asynchronous: a response may show STARTING before CONNECTED. Actual
ticks are needed for FRESH. GET latest returns HTTP 404 until the store has a
tick for the selected instrument. During market activity, inspect it explicitly:

```powershell
$marketTick = Invoke-RestMethod "$marketData/latest?exchange=NSE&symbol=INFY"
$marketTick | Select-Object instrumentId, lastPrice, receivedAt, exchangeTimestamp
Invoke-RestMethod "$marketData/status"
```

Output contains normalized values and a platform UUID, never credentials or an
authenticated WebSocket URL. LTP has no exchange timestamp, OHLC or depth when
the broker does not provide them. GET latest can retain historical data after
stop or disconnect: always inspect current status and timestamps with the value.
Repeated identical subscription/start requests are idempotent.

To test mode restoration, explicitly request QUOTE or FULL only for this one
instrument. A mode change requires a fresh tick before health becomes FRESH:

```powershell
Invoke-RestMethod -Method Post "$marketData/subscriptions?exchange=NSE&symbol=INFY&mode=QUOTE"
```

Optional reconnect check: while the app is running, deliberately interrupt only
its broker network connection using your existing local network tooling. Observe
RECONNECTING and bounded retry logs, then restore connectivity within the retry
budget. CONNECTED should return with one desired/active subscription and the
requested mode restored; a new matching tick establishes FRESH. No network
interruption is performed automatically by the application or tests. If retry
exhaustion occurs, restore connectivity, stop, and explicitly start again.

## Unsubscribe and stop

When a tick is available, use its platform UUID to remove the subscription:

```powershell
$instrumentId = $marketTick.instrumentId.value
Invoke-RestMethod -Method Delete "$marketData/subscriptions/$instrumentId"
Invoke-RestMethod -Method Post "$marketData/stop"
Invoke-RestMethod "$marketData/status"
```

If there has been no tick, call `/stop` directly. Stop cancels reconnect and closes
the socket. It preserves desired subscriptions for an explicit restart, so
unsubscribe first when you want to forget the instrument. With no subscriptions
the connected status is NO_DATA. Stopping does not erase historical latest ticks.
Ctrl+C cleanly shuts down the application and its market-data executors.

After stopping Java, remove the opt-ins from its PowerShell process (and from an
IDE run configuration if used):

```powershell
Remove-Item Env:KITE_MARKET_DATA_ENABLED -ErrorAction SilentlyContinue
Remove-Item Env:KITE_MARKET_DATA_DIAGNOSTIC_ENABLED -ErrorAction SilentlyContinue
Remove-Item Env:MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE -ErrorAction SilentlyContinue
```

## Troubleshooting

| Observation | Meaning and recovery |
| --- | --- |
| Diagnostic endpoint HTTP 404 | Verify development profile and both explicit flags in the application process. Actuator exposure is a separate setting. Restart Java after changing configuration. |
| AUTH_REQUIRED / KITE_AUTH_REQUIRED | Missing/expired/invalidated runtime session. Streaming makes no repeated authentication attempts. Complete the existing official browser flow, wait for KITE_AUTHENTICATED, then stop/start market data explicitly. |
| WebSocket HTTP 401 or 403 | The transport classifies authorization rejection and invalidates that session generation; it does not retry forever. Check the app's API key, successful login and account/API market-data entitlement in Kite's developer console. A 403 does not by itself prove which credential or entitlement failed. Do not log the full connection URL or token. |
| RECONNECTING | Network failure, idle message timeout, connection timeout or failed subscription send. Check connectivity/proxy/firewall and message timestamps; retries use capped exponential jitter. Stop cancels retries. |
| DEGRADED / RECONNECT_EXHAUSTED | Consecutive retry budget is exhausted. Restore connectivity and explicitly stop/start. Successful reconnection resets the consecutive attempt counter; the cumulative metric remains. |
| CONNECTED + NO_DATA | No desired subscriptions, a subscription/mode change has not completed, or at least one desired instrument has not produced an accepted tick on this connection. Confirm desired/active counts and wait for activity. |
| CONNECTED + STALE | At least one desired tick exceeds `stale-after` by receive time or known exchange time. Heartbeats alone cannot freshen data. Inspect message/tick timestamps and instrument activity before restarting. |
| No ticks outside market activity | The gateway does not assume an exchange calendar or fabricate prices. NO_DATA/STALE is expected when subscribed instruments do not update. Verify again when that instrument is active. |
| UNRESOLVED_INSTRUMENT / INVALID_BROKER_MAPPING | Resolve the exchange/symbol against the initialized instrument registry. Correct spelling/segment or reinitialize reference data. Do not substitute a guessed raw token. Failed subscription validation leaves desired state unchanged. |
| SUBSCRIPTION_LIMIT | The complete desired set would exceed `max-subscriptions` (maximum 3000). Unsubscribe unneeded instruments or reduce the request. The gateway does not open extra sockets to bypass the limit. |
| INSTRUMENT_REGISTRY_CHANGED | Mapping was pinned for the connection. Stop/start against the new registry and resolve any changed IDs. Old queued callbacks cannot silently remap reused tokens. |
| SESSION_CHANGED | The session was replaced/reset while streaming. Confirm authentication status, then stop/start explicitly. Old connection failures cannot invalidate a newer session. |
| MALFORMED_DATA / decode failures | A frame or normalized tick failed validation. The frame is discarded and quality stays degraded. Inspect only counters/safe reasons, compare supported protocol fixtures, then stop/start after investigating. Never dump raw frames with session details. |
| BACKPRESSURE / dropped events | The bounded queue filled and newest events were rejected. Check processing load and queue/counter metrics; reduce subscriptions/modes or tune capacity after measurement. Quality remains degraded until explicit stop/start, even after newer ticks arrive. There is no backfill. |
| BROKER_ERROR / PROCESSING_FAILURE | Safe health reason records an unexpected error without exposing broker text. Inspect bounded structured logs, fix the cause and stop/start. Do not interpret an open socket as healthy data. |

Health and metrics are operational evidence only. `/actuator/health/readiness`
continues to describe the existing application/database readiness and is not a
market-data freshness check or permission to trade. See
[configuration defaults](../../config/README.md#market-data) and
[architecture](../architecture/kite-market-data.md).
