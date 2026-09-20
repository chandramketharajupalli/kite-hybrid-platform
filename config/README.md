# Configuration ownership

Java application*.yml files define development, test, paper and production profiles.
Profiles do not override safety defaults. TRADING_MODE and profiles are distinct.
The production profile requires DB_URL, DB_USER and DB_PASSWORD environment values.

.env.example documents variables; populated .env is ignored. Compose loads .env
for its own substitution. Spring Boot does NOT automatically load it. Export the
required variables in PowerShell before Maven `spring-boot:run` (see root README).
Python settings use STRATEGY_ prefix and do not receive Kite credentials.

KITE_REST_ENABLED defaults to false. Set it to true for official browser
authentication and profile/instrument initialization. The application uses
KITE_API_KEY, KITE_API_SECRET,
KITE_REDIRECT_URL=http://localhost:8080/api/broker/kite/auth/callback, and
KITE_TOKEN_ENCRYPTION_KEY (Base64-encoded 32 random bytes retained across restarts).
The development helper loads these from Compose's resolved environment without
printing them; `.env` is authoritative for the variables managed by that helper.
Ordinary Compose commands retain shell-over-`.env` precedence.
KITE_ACCESS_TOKEN is retained only for optional legacy standalone diagnostics.
Missing interactive authentication does not break application startup.
See [Kite Authentication](../README.md#kite-authentication) for setup and the
browser flow. Never commit populated credentials or encryption keys.

## Market data

All properties below are under `kite.market-data`. Spring configuration maps the
listed environment variables explicitly. Durations accept values such as `500ms`,
`10s` and `1m` and must be positive and at most one day. Reconnect maximum delay
must be at least the initial delay. Configuration is validated at startup.

| Property | Environment variable | Default | Purpose |
| --- | --- | --- | --- |
| `enabled` | `KITE_MARKET_DATA_ENABLED` | `false` | Allows explicit gateway start; does not autoconnect |
| `diagnostic-enabled` | `KITE_MARKET_DATA_DIAGNOSTIC_ENABLED` | `false` | Enables local diagnostics only with development profile and market data enabled |
| `connect-timeout` | `KITE_MARKET_DATA_CONNECT_TIMEOUT` | `10s` | Connection and subscription command deadline |
| `stale-after` | `KITE_MARKET_DATA_STALE_AFTER` | `30s` | Maximum tick age for each desired instrument |
| `idle-timeout` | `KITE_MARKET_DATA_IDLE_TIMEOUT` | `30s` | No broker messages before reconnect |
| `queue-capacity` | `KITE_MARKET_DATA_QUEUE_CAPACITY` | `4096` | Pending ticks, 1..1,000,000; newest events dropped on saturation |
| `max-subscriptions` | `KITE_MARKET_DATA_MAX_SUBSCRIPTIONS` | `3000` | Desired instruments, 1..3000; unresolved/over-limit requests rejected atomically |
| `reconnect.initial-delay` | `KITE_MARKET_DATA_RECONNECT_INITIAL_DELAY` | `1s` | First exponential delay cap; equal jitter uses half to all of the cap |
| `reconnect.max-delay` | `KITE_MARKET_DATA_RECONNECT_MAX_DELAY` | `30s` | Upper bound for exponential backoff |
| `reconnect.max-attempts` | `KITE_MARKET_DATA_RECONNECT_MAX_ATTEMPTS` | `8` | Retries after failure, 0..100; zero disables reconnect |

Spring does not read `.env`, and the development infrastructure helper loads its
existing authentication/infrastructure variables only. Set market-data variables
explicitly in the shell after running the helper, or in the IDE run configuration.
The [manual runbook](../docs/runbooks/market-data.md) gives an exact example.

The runtime authenticated `KiteSession` is the only streaming credential source.
Do not set a separate market-data token. `KITE_ACCESS_TOKEN` remains limited to
legacy standalone REST diagnostics. Session expiry/reset/replacement stops the
stream and requires an explicit restart after authentication is restored.

For local health inspection, optionally set
`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,prometheus,marketdatastatus`.
This exposes safe status metadata, without instrument labels or credentials.
It does not add market data to database/process readiness and does not enable
trading. Keep `SERVER_ADDRESS=127.0.0.1` for unauthenticated development controls.
