# Safe Kite REST diagnostic

Prerequisites: selected existing JDK 21, network access, and an externally obtained
Kite access token for your API key. This command neither performs login nor refreshes
tokens. API secret is not required for read-only profile/instrument GETs.

From the repository root in Windows PowerShell:

```powershell
# Choose an existing JDK 21 for this process only.
$env:JAVA_HOME = 'C:\path\to\existing\jdk-21'
$env:KITE_API_KEY = [System.Net.NetworkCredential]::new('', (Read-Host 'Kite API key' -AsSecureString)).Password
$env:KITE_ACCESS_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'Kite access token' -AsSecureString)).Password
$env:KITE_REST_ENABLED = 'true'
$env:TRADING_MODE = 'PAPER'
$env:ENABLE_LIVE_TRADING = 'false'
$env:EMERGENCY_STOP = 'true'

# These are separate, explicit real calls. Do not run them in automated unit tests.
.\mvnw.cmd -pl apps/trading-core compile exec:exec '-Dkite.diagnostic=profile'
.\mvnw.cmd -pl apps/trading-core compile exec:exec '-Dkite.diagnostic=instruments'
```

Do not place tokens in command arguments, commit them, or paste diagnostic request
headers into issues/logs. Spring Boot/this command do not automatically load .env.
The API secret placeholder remains for a later controlled exchange flow.

A successful profile command prints CONNECTED, without account identity.
An instrument command prints retrieved/accepted/rejected counts, snapshot version
and UTC timestamp. This is a process-local snapshot; it is discarded on exit.
The command returns nonzero for failures. Maven may then report a failed
`exec:exec` goal; the safe category printed above it is the diagnostic result.

| Category | Action |
| --- | --- |
| CONFIGURATION | Set explicit opt-in plus key/token, check syntax without printing values |
| AUTHENTICATION | Obtain a fresh valid token through the broker's supported interactive lifecycle |
| BROKER_API | Inspect broker service availability/rate limits; no automatic retry is performed |
| TRANSPORT | Check network connectivity/proxy; credentials must remain secret |
| INVALID_RESPONSE | Review sanitized failure and adapter/schema coverage; do not dump the raw response |

Clear process-local credentials when finished:

```powershell
Remove-Item Env:KITE_API_KEY -ErrorAction SilentlyContinue
Remove-Item Env:KITE_ACCESS_TOKEN -ErrorAction SilentlyContinue
Remove-Item Env:KITE_API_SECRET -ErrorAction SilentlyContinue
$env:KITE_REST_ENABLED = 'false'
```

Normal build/test commands make no Kite network calls. A diagnostic success is
read-only connectivity evidence and never trading authorization.
