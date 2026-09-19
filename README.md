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

On this machine the host JVM timezone is `Asia/Calcutta`, which the pinned
PostgreSQL 17.6 image rejects during connection setup. Before PostgreSQL integration
tests or database-backed application startup, select UTC for Java processes in
this PowerShell session:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
```

If you already use `JAVA_TOOL_OPTIONS`, retain those options when adding the UTC
setting. This is an environment workaround for rejected timezone aliases; hosts
whose default timezone PostgreSQL accepts do not need it. Maven does not apply
the override automatically. No global environment or application configuration
is changed; closing the shell discards this process-local setting. See the
[infrastructure troubleshooting notes](docs/runbooks/local-development-infrastructure.md#troubleshooting-and-networking).

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
The `integration` profile compiles the separate `src/integrationTest/java` sources and
runs both unit tests and PostgreSQL integration tests during `verify`. To run
only the integration suite, use `.\mvnw.cmd -Pintegration '-DskipUnitTests=true' verify`.
Integration reports are in apps/trading-core/target/failsafe-reports.
There is **one** PostgreSQL integration test, covering migration, validation,
repeat migration and SQL connectivity. Do not add `--volumes` to `compose down`
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
database/Redis connection settings and safe trading/Kite flags. It does not
start services, persist credentials or modify global environment variables.
Alternatively configure the same variables in an IntelliJ run configuration;
an already running IDE does not inherit a different PowerShell process's values.
Host Java connects to `localhost:5432` and `localhost:6379`; future containerized
Java would use `postgres:5432` and `redis:6379` on the Compose network.

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
