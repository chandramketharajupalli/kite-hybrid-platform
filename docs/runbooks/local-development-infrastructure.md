# Local development infrastructure (Windows PowerShell)

Docker was confirmed reachable during the Maven migration on 2026-09-19. See the
[Maven migration validation record](../operations/maven-migration-report.md) for
executed container, connectivity, Flyway and integration-test results. Run the
checks below to establish the current state of your own development environment.
No software, Windows/WSL settings or global Java settings are changed by this setup.

This Compose project contains one PostgreSQL 17.6 container and one Redis 7.4.5
container, using official images. Java and Python run on the Windows host.
PostgreSQL is the authoritative future durable trading store. Redis is ephemeral,
requires a local password, disables snapshot/AOF persistence, and mounts `/data`
as tmpfs. PostgreSQL retains its data in the named `postgres-data` volume.
This workflow performs no Kite requests and adds no application containers.

## Prerequisites and environment checks

Use an installed Docker Desktop with Linux containers, or a compatible local
Docker Engine and Compose v2. Installation and startup are developer actions.
JDK 21 and the existing uv/Python 3.12 environment are separate prerequisites.

```powershell
Set-Location D:\Yogendra\kite-hybrid-platform
docker --version
docker compose version
docker info
```

- If `docker` is not recognized, report **DOCKER EXECUTION BLOCKED**: install Docker
  Desktop or configure the already installed CLI on PATH, then open a new shell.
- If the CLI works but `docker info` cannot reach the local daemon, report
  **DOCKER ENGINE NOT RUNNING**. Start Docker Desktop and wait for the engine.
- If `docker compose version` fails, Compose v2 is missing or unavailable. Fix that
  prerequisite before trying Compose commands.
- If using multiple Docker contexts, inspect `docker context show` and
  `docker context ls`. Verify that the selected endpoint is the intended local
  development engine; do not start this stack against a remote deployment.

Stop Docker execution until these checks succeed. A working CLI alone does not
prove that containers or Testcontainers can run.

## Local credentials

Create the local environment file only if it is missing:

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}
notepad .env
```

Keep `DB_NAME=trading` and `DB_USER=trading` unless a different local database/user
is needed. Set nonempty, local-only `DB_PASSWORD` and `REDIS_PASSWORD` values in
`.env`. Blank example values intentionally prevent Compose startup. Do not use
production passwords or paste passwords into shell commands, issues or logs.
`.env` is ignored; `.env.example` remains versioned without real credentials.

Compose maps `DB_NAME`, `DB_USER` and `DB_PASSWORD` to the official PostgreSQL
image's `POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD` settings. These are
the same local credentials subsequently supplied to Spring, not a second set.
PostgreSQL's image-created user has administrative privileges; this is a local
development setup, not a production role/permission design.

Compose supports single-quoted `.env` values when a password contains literal
characters such as `$` or `#`. Consult the interpolation rules below for escaping
quotes. Existing PowerShell environment variables take precedence over `.env`.
Check for stale `DB_NAME`, `DB_USER`, `DB_PASSWORD` or `REDIS_PASSWORD` variables
without printing their values. The helper below deliberately uses the exact
configuration resolved by Compose so Java does not get a different password.

```powershell
docker compose config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose configuration is invalid.' }
```

Use `config --quiet`, not plain `config` or `config --environment`: resolved
configuration contains credentials. Avoid dumping container environment or the
helper's internal configuration. Local Docker administrators can inspect
container environment; this is not a production secret-management system.

## Start and wait for health

```powershell
docker compose up -d
if ($LASTEXITCODE -ne 0) { throw 'Infrastructure startup failed.' }
docker compose up -d --wait --wait-timeout 120
if ($LASTEXITCODE -ne 0) { throw 'Infrastructure did not become healthy.' }
docker compose ps
```

The second `up` is idempotent and waits for both configured healthchecks. Expect
one healthy `postgres` and one healthy `redis` service. Image downloads may take
longer than later starts; inspect logs if startup fails instead of increasing
timeouts indefinitely. PostgreSQL uses `pg_isready`; Redis uses an authenticated
PING. There is no `depends_on` relationship because neither service depends on
the other and Java is not a Compose service.

Container health is infrastructure availability, not trading readiness.
`pg_isready` does not establish that supplied credentials work; verify that
separately below.

## Verify connectivity without exposing passwords

The PostgreSQL PowerShell literal here-strings send scripts into the container.
The trailing shell comments also tolerate Windows CRLF line endings. These
diagnostics read passwords from the container environment, never interpolate
them into host command arguments, and do not print them.

PostgreSQL: use the Compose service hostname so the check uses network
authentication, rather than the image's trusted local Unix socket connection.

```powershell
@'
export PGPASSWORD="$POSTGRES_PASSWORD" # Client authentication from container environment.
psql -h postgres -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c 'SELECT 1;' # Read-only connectivity check.
'@ | docker compose exec -T postgres sh
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL connectivity failed.' }
```

Expected result: one row containing `1`. This proves the container's PostgreSQL
service connection; starting Java below additionally validates connectivity
through the port published to Windows.

Redis:

```powershell
docker compose exec -T redis redis-cli -e ping
if ($LASTEXITCODE -ne 0) { throw 'Redis connectivity failed.' }
```

Compose supplies `REDISCLI_AUTH` inside the Redis container from the local
`REDIS_PASSWORD` setting; `redis-cli` uses it automatically. Expected result:
`PONG`. This is infrastructure validation only. The application
does not yet implement cache behavior, and Redis is not required for application
readiness. No order, fill, position, trade or reconciliation state belongs here.

## Host Java, Flyway and health

Select an existing JDK 21 for this PowerShell process; do not use the machine's
Java 8 default or install a JDK implicitly:

```powershell
$env:JAVA_HOME = 'C:\path\to\existing\jdk-21'
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\javac.exe" -version
```

Stop if both commands do not identify version 21. On this machine the host JVM
timezone `Asia/Calcutta` is rejected by PostgreSQL 17.6 during connection setup.
For integration tests and database-backed Java startup on affected hosts, set a
process-local UTC override before launching Maven:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
```

Retain any existing Java options when adding this setting. Hosts whose default
timezone PostgreSQL accepts do not need the override. The setting is inherited
by Maven and its forked test/application JVMs until this PowerShell session ends;
no global environment or application configuration is changed. Maven does not
set the timezone automatically.

Spring does not automatically read `.env`. The small helper captures Compose
configuration in memory, exports only the local datasource/Redis settings to
this PowerShell process, and sets
the safe development profile and trading defaults. It does not start services
or make broker requests.

```powershell
.\scripts\Use-DevelopmentInfrastructure.ps1
if (-not $?) { throw 'Development configuration was not loaded.' }
.\mvnw.cmd -pl apps/trading-core spring-boot:run
```

The helper sets `SPRING_PROFILES_ACTIVE=development`, `TRADING_MODE=PAPER`,
`ENABLE_LIVE_TRADING=false`, `EMERGENCY_STOP=true`, and `KITE_REST_ENABLED=false`.
It supplies `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT` and
`REDIS_PASSWORD` from the local Compose setup. Run it again after changing the
resolved configuration. Settings apply to this shell and child processes only;
IntelliJ run configurations need the equivalent environment separately.

Flyway runs on Java startup. The only existing migration,
`V1__platform_baseline.sql`, creates the `trading` namespace. No new trading tables
or data imports are part of this task. After successful startup, use a second
PowerShell window in the repository root to inspect application health:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:8080/actuator/health/liveness
Invoke-RestMethod http://127.0.0.1:8080/actuator/health/readiness
```

Expect `UP` after the database and application are ready. Readiness includes
database availability. Neither successful health endpoints nor healthy
containers authorize trading: trading remains halted/unavailable and there is
no live execution adapter. Use Ctrl+C in the application terminal to stop Java.

Inspect migration status through a read-only SQL query:

```powershell
@'
set -e # Stop if either SQL verification fails.
export PGPASSWORD="$POSTGRES_PASSWORD" # Client authentication from container environment.
psql -h postgres -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c 'SELECT installed_rank, version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;' # Read-only Flyway status.
psql -h postgres -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'trading';" # Verify namespace.
'@ | docker compose exec -T postgres sh
if ($LASTEXITCODE -ne 0) { throw 'Flyway verification failed.' }
```

Expect version `1`, `success=true` and the `trading` schema. If the history table
does not exist, investigate Java startup/Flyway failure; do not create it by hand.
Flyway validates and leaves the migration unchanged on subsequent startup.

## Tests

Use the JDK 21 and, on affected hosts, UTC setup above in this PowerShell session.

```powershell
.\mvnw.cmd verify
if ($LASTEXITCODE -ne 0) { throw 'Java build failed.' }
.\mvnw.cmd -Pintegration '-DskipUnitTests=true' verify
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL integration tests failed.' }
```

The ordinary build runs unit/architecture/contract tests without Docker and
makes no real Kite calls. The separate integration suite currently contains
**one** `PostgresMigrationTest` test. Testcontainers starts its own isolated,
disposable `postgres:17.6` database, applies the migration, validates it, verifies
a second migration run performs no work, and checks the `trading` namespace.
It does not use or delete the Compose database. Testcontainers manages shutdown;
check for unexpectedly remaining test containers if a run is interrupted.
Compose does not need to be running for this test, but Docker Engine does.
Missing Docker means **BLOCKED / NOT EXECUTED**, never passed.
Run `.\mvnw.cmd -Pintegration verify` for the complete lifecycle with both unit
and integration suites. Unit reports are in `apps/trading-core/target/surefire-reports`;
integration reports are in `apps/trading-core/target/failsafe-reports`.

Use the existing Python project working directory so pytest and mypy read their
normal project settings:

```powershell
Push-Location apps/strategy-engine
uv run --locked pytest
uv run --locked ruff check .
uv run --locked mypy
Pop-Location
uv run --project apps/strategy-engine --locked python scripts/verify-project.py
uv run --project apps/strategy-engine --locked python scripts/check-secrets.py
```

Inspect every command's result; a later successful command does not erase an
earlier failure. Record exact executed test counts and distinguish static checks
from container execution.

## Routine service management

Run these from the repository root. Logs should be reviewed locally before
sharing; do not publish resolved environment/configuration or passwords.

```powershell
docker compose ps
docker compose logs --tail 100 postgres redis
docker compose logs --follow postgres redis
# Ctrl+C stops log following; it does not stop the services.
docker compose restart postgres redis
docker compose stop
docker compose start
```

`restart` and `start` reuse existing container configuration. After editing `.env`
or Compose, use `docker compose up -d --wait --wait-timeout 120` to reconcile
configuration, then rerun the Java helper. PostgreSQL initialization settings
have the persistence caveat described below.

Remove containers/network while preserving PostgreSQL data:

```powershell
docker compose down
```

Only when deliberately resetting **all local development database data**, use:

```powershell
docker compose down --volumes
```

The latter command destroys the named database volume; it is not routine
shutdown and must never be used against data to retain. Redis loses ephemeral
state whenever it stops. No Docker prune or unrelated-container cleanup is part
of this workflow.

## Troubleshooting and networking

Both host ports bind only to `127.0.0.1`: PostgreSQL `5432`, Redis `6379`. Java on
Windows uses `localhost:5432` and `localhost:6379`. A future application on the
same Compose network would use `postgres:5432` and `redis:6379`; its own
`localhost` would be the application container. Do not hard-code container IPs.
There is no application container in the current project.

Before changing a port, identify any listener:

```powershell
Get-NetTCPConnection -State Listen -LocalPort 5432,6379 -ErrorAction SilentlyContinue |
    Select-Object LocalAddress, LocalPort, OwningProcess
# Replace the number with an OwningProcess value above; inspect, do not kill blindly.
Get-Process -Id 1234
```

Resolve an existing PostgreSQL/Redis/container conflict deliberately. Do not
silently select random ports: published ports, Java configuration and the helper
must agree. A startup failure due to a reserved Windows port range also requires
developer investigation; this project does not modify Windows/WSL networking.

If PostgreSQL reports password or database mismatch after editing `.env`, its
initialization variables only apply to an empty data directory. Recreating the
container preserves the named volume and does not rotate the existing password
or rename its database/user. Restore matching local configuration, or plan an
explicit local credential change. Use the destructive reset above only when the
existing development data is intentionally disposable. `pg_isready` may still
report healthy while application authentication fails.

If Redis fails authentication, check for stale shell environment overrides and
whether the container was recreated after `.env` changed. A nonempty
`REDIS_PASSWORD` must match Java's resolved setting. Do not disable authentication
to bypass a mismatch. Redis readiness remains separate from trading readiness.

If PostgreSQL reports `FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"`,
the JDBC connection is supplying a JVM timezone alias that the server rejects.
Docker can be reachable and containers healthy while this connection fails.
Apply the process-local `JAVA_TOOL_OPTIONS` UTC setting described above, then
rerun the integration tests or restart Java from that shell. This issue also
occurred in the previous build's integration baseline; changing the build system
does not fix it. Keep the pinned database image and existing database volume.

If a script is blocked by local PowerShell policy, inspect the file and follow
the machine's approved script-execution process; do not change system policy
automatically. Closing the PowerShell process clears the helper's environment.

## Official references

- [Docker Compose networking](https://docs.docker.com/compose/how-tos/networking/)
  explains service-name discovery and published host ports.
- [Compose interpolation](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)
  documents `.env` syntax and shell-variable precedence.
- [Official PostgreSQL image](https://hub.docker.com/_/postgres) documents image
  variables, initialization behavior and persistent data.
- [PostgreSQL pg_isready](https://www.postgresql.org/docs/17/app-pg-isready.html)
  documents why server readiness does not prove correct credentials.
- [Official Redis image](https://hub.docker.com/_/redis) describes Redis
  configuration, security and its `/data` volume.
- [Compose up](https://docs.docker.com/reference/cli/docker/compose/up/) and
  [Compose down](https://docs.docker.com/reference/cli/docker/compose/down/)
  document health waiting and volume-removal behavior.
