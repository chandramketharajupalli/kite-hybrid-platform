# kite-hybrid-platform

Phase 2 adds read-only Java Kite REST profile and instrument-master integration
to the modular Java control plane and lightweight Python strategy/quant plane.
No strategies, broker execution, runtime messaging or frontend exist.
Clearing emergency stop or changing live flags cannot enable orders.

Java owns signal validation → risk → order intents → OMS → execution → broker →
lifecycle → positions → reconciliation. Python emits signals only.
PostgreSQL is the future durable ledger; Redis is optional ephemeral state.

## Selected toolchain

| Tool | Selected version |
| --- | --- |
| Java | JDK 21 (validated locally with existing Temurin 21.0.8) |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.3, Wrapper included with SHA-256 distribution verification |
| Python | 3.12 (validated locally with 3.12.14) |
| PostgreSQL local image | 17.6 |
| Redis local image | 7.4.5 |

Spring Boot 3.5 and Gradle 8 preserve the requested JUnit 5 baseline and Java 21
compatibility. Boot's BOM pins managed Java dependency versions; ArchUnit 1.4.1
and JSON Schema validator 1.5.9 are explicitly pinned test dependencies.
Phase 2 adds only Jackson CSV, aligned to the existing Boot-managed Jackson 2.21.4.
The official Kite SDK was reviewed; a narrow Spring HTTP adapter preserves exact
decimal parsing and read-only scope (see ADR-011).
Python uses uv.lock; version constraints express supported dependency families.
Review security/support and image patches before any deployment.

References: [Boot compatibility](https://docs.spring.io/spring-boot/3.5/system-requirements.html),
[Boot dependency management](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html),
[Gradle 8.14.3](https://docs.gradle.org/8.14.3/release-notes.html).

## Windows PowerShell setup

Run commands from this repository root. Prerequisites are user-managed JDK 21,
uv with Python 3.12, and Docker only for services/integration tests.
No script installs a JDK/Docker or changes global JAVA_HOME. Gradle toolchain
automatic downloads are disabled.

Select an already installed JDK 21 for the current PowerShell process only:

```powershell
$env:JAVA_HOME = 'C:\path\to\existing\jdk-21'
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\javac.exe" -version
```

Stop if those commands do not identify Java 21 and javac 21. Do not use the
machine's default Java 8. The implementation report identifies the existing
Spring Tools Java 21 installation used here; no machine-specific path is built
into Gradle configuration.

## Java build and tests (no Docker)

```powershell
.\gradlew.bat build --no-daemon
.\gradlew.bat :trading-core:test --tests '*SignalContractTest'
```

The ordinary build runs unit, architecture, configuration, application and
contract tests. Test reports: apps/trading-core/build/reports/tests/test/index.html.
The bootable JAR is under apps/trading-core/build/libs.
First execution downloads the pinned Gradle distribution and dependencies.
Ordinary test/build commands use mock HTTP responses and never contact Zerodha.

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
.\gradlew.bat :trading-core:test --tests '*SignalContractTest'
uv run --project apps/strategy-engine --locked pytest apps/strategy-engine/tests/test_contracts.py
uv run --project apps/strategy-engine --locked python scripts/verify-project.py
uv run --project apps/strategy-engine --locked python scripts/check-secrets.py
```

Shared fixtures prove schema/model compatibility, not runtime delivery.
The secret scan is heuristic and prints locations only; it is not a security audit.

## Local services and integration tests (Docker required)

```powershell
Copy-Item .env.example .env
# Edit .env locally and set a nonempty DB_PASSWORD; never commit it.
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d postgres redis
.\gradlew.bat :trading-core:integrationTest --no-daemon
docker compose down
```

Testcontainers starts its own disposable PostgreSQL; Compose need not be running
for integration tests, but Docker must work. Tests are explicit and do not silently
skip when Docker is missing. Missing Docker means BLOCKED / NOT EXECUTED.
Do not add -v to compose down unless intentionally discarding the local database.

## Run the application locally

Compose reads .env; Spring Boot does not automatically read it.
Supply Java's environment independently in the same PowerShell process:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'development'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/trading'
$env:DB_USER = 'trading'
# Set DB_PASSWORD in this process from your local secret source.
$env:TRADING_MODE = 'PAPER'
$env:ENABLE_LIVE_TRADING = 'false'
$env:EMERGENCY_STOP = 'true'
.\gradlew.bat :trading-core:bootRun
```

A reachable PostgreSQL and matching password are required; Flyway creates only a
trading namespace. Development, test, paper and production profiles are provided.
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
authenticated control APIs, market-data ingestion and measured transport design.
No throughput or 1,000-instrument performance claim is made.

Paper execution and durable order processing remain separate future phases.
Recommended Phase 3 objective: read-only WebSocket market-data ingestion through
the existing gateway boundary, with bounded handoff, freshness and reconnect tests.

## Phase 2: explicit read-only Kite diagnostics

Normal startup has KITE_REST_ENABLED=false and performs no Kite calls, even when
credentials are present. API key and an externally obtained access token are
required only for explicit REST use. API secret is reserved for future login
exchange; it is not used by the current GET endpoints. No automatic login,
refresh, broker mutation, WebSocket, PostgreSQL or Redis registry is implemented.

See [safe PowerShell credential/diagnostic instructions](docs/runbooks/kite-rest-diagnostic.md).
After setting process-local environment variables as described there:

```powershell
.\gradlew.bat :trading-core:kiteDiagnostic --args="profile" --no-daemon
.\gradlew.bat :trading-core:kiteDiagnostic --args="instruments" --no-daemon
```

These commands make real read-only requests only when invoked with explicit
KITE_REST_ENABLED=true. Each runs without a server/database. The instruments
command creates a temporary process-local registry; it does not refresh another
running application's registry. Failed refreshes preserve its prior valid snapshot.

See [REST/session/registry architecture](docs/architecture/kite-rest-and-instruments.md),
[HTTP decision](docs/adr/ADR-011-read-only-kite-rest-adapter.md) and
[identity/snapshot decision](docs/adr/ADR-012-instrument-identity-and-atomic-snapshots.md).
Real connectivity must be reported separately from unit/mock validation.
