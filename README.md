# kite-hybrid-platform

Java Kite integration includes official interactive authentication, encrypted
access-token persistence, read-only profile and instrument-master retrieval, and
opt-in Kite WebSocket market data normalized behind broker-independent Java ports.
Phase 5A adds opt-in, on-demand order-book, trade, position, holding and account
margin reads through the same authenticated session. See the
[trading-read runbook](docs/runbooks/kite-trading-read.md).
Phase 5B.1 adds broker-independent order commands and durable idempotency with
real execution disabled by default; see the [local order-command runbook](docs/runbooks/order-command-local.md).
The project has a modular Java control plane and lightweight Python strategy/quant plane.
No strategies, runtime messaging or frontend exist. Real broker execution remains
disabled by default.
Clearing emergency stop or changing live flags cannot enable orders.

Java owns signal validation → risk → order intents → OMS → execution → broker →
lifecycle → positions → reconciliation. Python emits signals only.
PostgreSQL stores encrypted Kite tokens and will own the future durable trading
ledger; Redis is optional ephemeral state.

## Selected toolchain

| Tool | Selected version |
| --- | --- |
| Java | JDK 21 (validated locally with existing Temurin 21.0.8) |
| Spring Boot | 3.5.16 |
| Maven | 3.9.11, Wrapper included with SHA-256 distribution verification |
| Python | 3.12 (validated locally with 3.12.14) |
| PostgreSQL local image | 17.6 |
| Redis local image | 7.4.5 |

Spring Boot 3.5.16 and Maven preserve the requested JUnit 5 baseline and Java 21
compatibility. Boot's BOM pins managed Java dependency versions; ArchUnit 1.4.1
and JSON Schema validator 1.5.9 are explicitly pinned test dependencies.
The migration preserves the previous test dependency resolution: Byte Buddy
1.18.3 (its agent remains BOM-managed 1.17.8) and SnakeYAML 2.5 at test runtime;
production retains BOM-managed SnakeYAML 2.4. See the
[migration report](docs/operations/maven-migration-report.md) for dependency
comparison and the test-compilation scope difference.
Phase 2 adds only Jackson CSV, aligned to the existing Boot-managed Jackson 2.21.4.
The official Kite SDK was reviewed; a narrow Spring HTTP adapter preserves exact
decimal parsing and read-only scope (see ADR-011).
Python uses uv.lock; version constraints express supported dependency families.
Review security/support and image patches before any deployment.

References: [Boot compatibility](https://docs.spring.io/spring-boot/3.5/system-requirements.html),
[Boot dependency management](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html),
[Maven Wrapper](https://maven.apache.org/wrapper/).

## Windows PowerShell setup

Run commands from this repository root. Prerequisites are user-managed JDK 21,
uv with Python 3.12, and Docker only for services/integration tests.
No script installs a JDK/Docker or changes global JAVA_HOME. Maven requires an
existing JDK 21 and rejects other Java major versions.

Select an already installed JDK 21 for the current PowerShell process only:

```powershell
$env:JAVA_HOME = 'C:\path\to\existing\jdk-21'
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\javac.exe" -version
```

Stop if those commands do not identify Java 21 and javac 21. Do not use the
machine's default Java 8. The implementation report identifies the existing
Spring Tools Java 21 installation used here; no machine-specific path is built
into Maven configuration.

Integration tests configure their own PostgreSQL/JDBC timezone as UTC; no
`JAVA_TOOL_OPTIONS` override or machine timezone change is required. See the
[integration timezone note](docs/operations/postgres-integration-timezone.md)
for the JDBC startup behavior and test scope.

## Java build and tests (no Docker)

```powershell
.\mvnw.cmd compile
.\mvnw.cmd verify
.\mvnw.cmd -pl apps/trading-core '-Dtest=SignalContractTest' test
```

The ordinary build runs unit, architecture, configuration, application and
contract tests. JUnit XML and text reports: apps/trading-core/target/surefire-reports.
The bootable JAR is apps/trading-core/target/trading-core-0.1.0-SNAPSHOT.jar.
First execution downloads the pinned Maven distribution and dependencies;
globally installed Maven is not required. On POSIX shells, use `./mvnw` with the
same goals and options. The root POM builds the `apps/trading-core` Java module;
the Python application retains its independent uv build.
Ordinary test/build commands use mock HTTP responses, deterministic binary fixtures,
and fake or loopback WebSocket infrastructure. They never contact Zerodha.

## Python environment, tests, lint and types

```powershell
Push-Location apps/strategy-engine
$env:UV_PYTHON_DOWNLOADS = 'never'
uv sync --locked --python 3.12
uv run --locked pytest
uv run --locked ruff check .
uv run --locked mypy
uv run --locked pytest tests/test_contracts.py
Pop-Location
```

uv creates a project virtual environment, not a bundled Python distribution.
If hardlinking across drives is unavailable, uv falls back to copying; optionally
set process-local UV_LINK_MODE=copy. The engine has no executable strategy or
broker client.

## Contract and repository validation

```powershell
.\mvnw.cmd -pl apps/trading-core '-Dtest=SignalContractTest' test
uv run --project apps/strategy-engine --locked pytest apps/strategy-engine/tests/test_contracts.py
uv run --project apps/strategy-engine --locked python scripts/verify-project.py
uv run --project apps/strategy-engine --locked python scripts/check-secrets.py
```

Shared fixtures prove schema/model compatibility, not runtime delivery.
The secret scan is heuristic and prints locations only; it is not a security audit.

## Local services and integration tests (Docker required)

Docker Desktop with a running Linux-container engine and Compose v2 is required.
It is user-managed; no script installs or starts Docker Desktop. Run from the
repository root. Docker was confirmed reachable during the Maven migration on
2026-09-19. See the [Maven migration validation record](docs/operations/maven-migration-report.md)
for current container, connectivity, Flyway and Testcontainers results. The
[earlier infrastructure validation record](docs/operations/development-infrastructure-validation.md)
retains the historical Docker startup failure.

```powershell
cd D:\Yogendra\kite-hybrid-platform
docker --version
docker compose version
docker info
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Edit .env: set your own local DB_PASSWORD and REDIS_PASSWORD; never commit it.
docker compose config --quiet
docker compose up -d
docker compose ps
# Wait for both services to be healthy before starting Java.
docker compose up -d --wait --wait-timeout 120
.\mvnw.cmd -Pintegration verify
docker compose down
```

Official images remain pinned to PostgreSQL 17.6 and Redis 7.4.5. Only loopback
ports 5432/6379 are published. PostgreSQL uses a named persistent volume; Redis
requires authentication and disables persistence, with `/data` in tmpfs.
Do not display resolved Compose configuration or inspect container environments:
they contain local passwords. `config --quiet` validates without printing them.

Testcontainers starts its own disposable PostgreSQL; Compose need not be running
for integration tests, but Docker must work. Tests are explicit and do not silently
skip when Docker is missing. Missing Docker means BLOCKED / NOT EXECUTED.
Normal Docker discovery does not require a user `~/.testcontainers.properties`
file. `PostgresMigrationTest` explicitly sets the disposable server and JDBC
session to UTC, restores the test JVM's previous timezone afterward, and runs in
isolation from other JUnit tests. Application timezone settings are unchanged.
The `integration` profile compiles the separate `src/integrationTest/java` sources and
runs both unit tests and PostgreSQL integration tests during `verify`. To run
only the integration suite, use `.\mvnw.cmd -Pintegration '-DskipUnitTests=true' verify`.
Integration reports are in apps/trading-core/target/failsafe-reports.
PostgreSQL integration coverage includes migration, validation, repeat migration,
SQL connectivity and encrypted Kite token storage. Do not add `--volumes` to `compose down`
unless intentionally discarding the local database.

See the [local infrastructure runbook](docs/runbooks/local-development-infrastructure.md)
for authenticated SQL/PING checks, Flyway history, logs, restart, port conflicts,
data deletion and the complete PowerShell workflow. Container health, application
readiness and trading readiness remain separate concepts.

## Run the application locally

Compose reads `.env`; Spring Boot does not automatically read it. After services
are healthy, load the resolved local credentials into the current PowerShell
process without printing them:

```powershell
.\scripts\Use-DevelopmentInfrastructure.ps1
.\mvnw.cmd -pl apps/trading-core spring-boot:run
```

The helper only sets this process's environment: development profile, matching
database/Redis connection settings, configured Kite authentication variables,
and safe trading flags. It does not
start services, persist credentials or modify global environment variables.
Alternatively configure the same variables in an IntelliJ run configuration;
an already running IDE does not inherit a different PowerShell process's values.
Host Java connects to `localhost:5432` and `localhost:6379`; future containerized
Java would use `postgres:5432` and `redis:6379` on the Compose network.

If database-backed application startup fails because PostgreSQL rejects the
host JVM timezone alias `Asia/Calcutta`, use the application-only process-local
workaround in the [infrastructure runbook](docs/runbooks/local-development-infrastructure.md#host-java-flyway-and-health).
The integration-test timezone setup does not change application startup behavior.

A reachable PostgreSQL and matching password are required; Flyway creates the
trading namespace and encrypted Kite token table. Development, test, paper and
production profiles are provided.
Production requires explicit database configuration and is not deployment-ready.
Do not run the isolated test profile as an operational trading deployment.

The application binds to 127.0.0.1 by default. Read-only endpoints:

- /actuator/health
- /actuator/health/liveness
- /actuator/health/readiness
- /actuator/prometheus

Application health/readiness is separate from trading readiness, which is always
false in Phase 1. No API enables orders. The internal tradingstatus endpoint is
not exposed over HTTP by default. Runtime kill-switch controls are future work.

## Design and scope

See [system overview](docs/architecture/system-overview.md), [ADRs](docs/adr/),
[contracts](contracts/README.md), [operations](docs/operations/README.md) and
[halt/recovery runbook](docs/runbooks/trading-halt-and-recovery.md).

Only populated packages exist. Future module boundaries and event types are
documented rather than represented by empty classes or speculative schemas.
The pipeline is an architectural boundary, not a functioning OMS in this phase.

Remaining work includes durable deduplication/ledger transactions, approved intent
orchestration, production risk rules, paper execution, recovery/reconciliation,
authenticated trading control APIs and measured transport design.
No throughput or 1,000-instrument performance claim is made.

Paper execution and durable order processing remain separate future phases.
Phase 4 implements market-data ingestion through the existing gateway, with a
bounded queue, explicit subscriptions, reconnect/resubscription, normalized ticks,
an in-process latest-value store, freshness health, and Micrometer metrics. It
does not implement strategies, signals, risk decisions, orders, positions, P&L
or automated trading.

## Kite Authentication

Use the official browser authorization flow. There is no manual request-token
or access-token copying, and no automated Zerodha credentials/TOTP entry,
undocumented login endpoint, browser scraping or Selenium.

One-time setup:

1. In the Kite Connect developer console, register this exact redirect URL for
   your API key: `http://localhost:8080/api/broker/kite/auth/callback`.
2. In your ignored `.env`, set `KITE_REST_ENABLED=true`, `KITE_API_KEY`,
   `KITE_API_SECRET`, and
   `KITE_REDIRECT_URL=http://localhost:8080/api/broker/kite/auth/callback`.
   Keep the existing local database settings. Leave `KITE_ACCESS_TOKEN` empty;
   it is only for the optional legacy standalone diagnostics below.
3. Generate the encryption key once, using the PowerShell commands below.
   Retain the same key and PostgreSQL volume across application restarts.

This creates a cryptographically random 32-byte key and writes its Base64 value
to the ignored `.env` without printing it. Run from the repository root. It
refuses to overwrite a populated key:

```powershell
$kiteEnvText = Get-Content -Raw -LiteralPath .env
if ($kiteEnvText -match '(?m)^KITE_TOKEN_ENCRYPTION_KEY=[ \t]*\S') {
    throw 'A token encryption key already exists; reuse it.'
}
$kiteKeyBytes = New-Object byte[] 32
$kiteRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $kiteRandom.GetBytes($kiteKeyBytes) } finally { $kiteRandom.Dispose() }
$kiteKeySetting = 'KITE_TOKEN_ENCRYPTION_KEY=' + [Convert]::ToBase64String($kiteKeyBytes)
if ($kiteEnvText -match '(?m)^KITE_TOKEN_ENCRYPTION_KEY=\s*$') {
    $kiteEnvText = $kiteEnvText -replace '(?m)^KITE_TOKEN_ENCRYPTION_KEY=[ \t]*\r?$', $kiteKeySetting
} else {
    $kiteEnvText += "`r`n" + $kiteKeySetting + "`r`n"
}
Set-Content -LiteralPath .env -Value $kiteEnvText -Encoding UTF8
[Array]::Clear($kiteKeyBytes, 0, $kiteKeyBytes.Length)
Remove-Variable kiteEnvText, kiteKeyBytes, kiteKeySetting, kiteRandom
```

Protect `.env` and database backups. Encryption uses AES-256-GCM; the encryption
key belongs outside PostgreSQL. Changing or losing it prevents reuse of existing
encrypted tokens. Restore the original key or reset local authentication and
authenticate again. Do not regenerate the key for every start.

Daily workflow:

1. Start the existing local infrastructure and application with an installed JDK
   21 selected in this shell:

   ```powershell
   docker compose up -d --wait --wait-timeout 120
   .\scripts\Use-DevelopmentInfrastructure.ps1
   .\mvnw.cmd -pl apps/trading-core '-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC' spring-boot:run
   ```

   The application JVM argument avoids PostgreSQL's rejection of the host's
   legacy `Asia/Calcutta` timezone alias without changing global Java settings.
   Authentication expiry still uses `Asia/Kolkata`.
   If Windows blocks the helper script, run
   `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` in this shell first;
   this applies only to the current PowerShell process.
2. Check `http://localhost:8080/api/broker/kite/auth/status`.
3. If it reports `KITE_AUTH_REQUIRED`, open
   `http://localhost:8080/api/broker/kite/auth/login` in your browser.
4. Complete Zerodha's mandatory interactive login/authorization, including any
   required second factor. Use the same browser and `localhost` host throughout
   so the callback retains the dedicated `KITE_LOGIN_NONCE` cookie.
5. Zerodha redirects to the configured callback. The backend checks the returned
   state against that cookie and atomically consumes the unexpired PostgreSQL
   login attempt. It then captures the
   request token, exchanges it, validates the resulting session with a profile
   call, and stores the access token encrypted in PostgreSQL.
6. The existing `KiteSession` is updated immediately and instrument initialization
   continues automatically. Broker reads can use that session; PAPER mode,
   emergency stop and the absence of a live order adapter remain unchanged.
7. Application restarts load and validate the stored token. No new interactive
   login is needed while that token remains usable.

Kite tokens expire at the next daily 06:00 Asia/Kolkata cutoff and can be
invalidated earlier by the broker. The application persists issue/expiry metadata,
rejects expired tokens and verifies restored sessions. Authentication being
required does not crash startup. See the
[official Kite authentication documentation](https://kite.trade/docs/connect/v3/user/).

Status and callback responses never return access tokens or API secrets. Opening
the login endpoint while already authenticated returns safe authenticated status.
The callback must follow a login started by this application. A 256-bit random
nonce travels as the documented `redirect_params=state%3D...` value and in a
host-only, HttpOnly, SameSite=Lax cookie (Secure on HTTPS). PostgreSQL stores only
its SHA-256 digest and creation/expiry timestamps, scoped to the configured API
key's digest. An atomic delete consumes a matching attempt once, before token
exchange. Attempts expire after ten minutes; cleanup deletes at most 100 expired
rows per login. No password, TOTP, request token, API secret, access token or
checksum is stored in the login-attempt table.

Authentication no longer uses `HttpSession` or `JSESSIONID`. A restart can complete
an outstanding attempt within its original lifetime if the same database, API
key, callback origin and browser nonce cookie remain available. A consumed attempt
cannot replay after a restart. Duplicate callbacks now return
`KITE_CALLBACK_STATE_INVALID`; check `/api/broker/kite/auth/status` for the result
of an already completed login. If exchange fails after consumption, start a fresh
login. Run one application process for this local workflow; the shared active
broker session and trading initialization remain process-local.

The official Kite documentation supports returning `redirect_params` as callback
query parameters, and our URL uses its single-encoded format. A real callback was
observed without `state`; neither database persistence nor encoding unit tests
establish why the broker omitted it. Missing state **still fails closed**, even
with a valid nonce cookie. We deliberately do not substitute cookie-only
acceptance: an unrelated request token could otherwise be injected into a pending
login. No less-protected fallback is enabled. See the
[callback troubleshooting procedure](docs/runbooks/kite-auth-callback.md) for safe
checks and the distinction between a lost servlet session and missing broker state.

If status reports `KITE_AUTH_UNAVAILABLE` or `KITE_INITIALIZATION_PENDING`, opening
the same login endpoint retries stored-session validation or instrument
initialization before requesting another interactive login.

To forget the local session and encrypted token:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/broker/kite/auth/reset -Headers @{'X-Kite-Auth-Reset'='true'}
```

This is a local reset, not Zerodha account logout or broker-side token revocation.
Open the login endpoint again when needed. Keep the application bound to loopback;
these local control endpoints have no multi-user authorization layer.

## Phase 4: market data

The flow is authentication -> instrument registry -> explicit connection and
subscriptions -> normalized ticks -> latest market state -> market-data health.
The existing `KiteSession` supplies WebSocket credentials. `KITE_ACCESS_TOKEN` is
not a second token source for streaming.

Market data defaults to disabled, and enabling configuration does not start a
connection automatically. Java callers use `MarketDataGateway` with platform
`InstrumentId` values and typed `LTP`, `QUOTE` or `FULL` modes. LTP is the default;
quote/depth are optional, and prices use `BigDecimal`. Latest ticks stay in-process.
There are no Redis writes or database writes on the receive/processing path.

For an explicit development-only live check with one registry-resolved instrument,
follow the [market-data runbook](docs/runbooks/market-data.md). It requires
`KITE_AUTHENTICATED`, `KITE_MARKET_DATA_ENABLED=true`, the development profile,
and `KITE_MARKET_DATA_DIAGNOSTIC_ENABLED=true`. The runbook shows start, safe tick
inspection, optional reconnect verification, unsubscribe and clean stop. Do not
run it as part of automated tests. Keep the application on loopback.

Connection state alone is not health: `FRESH` requires every desired subscription
to have current data from the active connection. Heartbeats keep the socket alive
without making prices fresh. `/actuator/marketdatastatus` is available only when
explicitly added to Actuator exposure; database/process readiness is separate.

See [market-data architecture](docs/architecture/kite-market-data.md) and
[configuration reference](config/README.md#market-data).

## Optional legacy read-only Kite diagnostics

KITE_REST_ENABLED defaults to false. Enabling it opts into the authentication
flow above and automatic profile/instrument initialization. The optional standalone
diagnostic commands retain their legacy API-key/access-token environment input;
they do not share the application's PostgreSQL token store. They are unnecessary
for daily browser authentication. These standalone commands implement neither
order mutations nor WebSocket streaming; Phase 4 streaming uses the running
application's authenticated session.

See [safe PowerShell credential/diagnostic instructions](docs/runbooks/kite-rest-diagnostic.md).
After setting process-local environment variables as described there:

```powershell
.\mvnw.cmd -pl apps/trading-core compile exec:exec '-Dkite.diagnostic=profile'
.\mvnw.cmd -pl apps/trading-core compile exec:exec '-Dkite.diagnostic=instruments'
```

These commands make real read-only requests only when invoked with explicit
KITE_REST_ENABLED=true. Each runs without a server/database. The instruments
command creates a temporary process-local registry; it does not refresh another
running application's registry. Failed refreshes preserve its prior valid snapshot.

See [REST/session/registry architecture](docs/architecture/kite-rest-and-instruments.md),
[HTTP decision](docs/adr/ADR-011-read-only-kite-rest-adapter.md) and
[identity/snapshot decision](docs/adr/ADR-012-instrument-identity-and-atomic-snapshots.md).
Real connectivity must be reported separately from unit/mock validation.
