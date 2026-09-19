# PostgreSQL integration-test timezone — 2026-09-19

`PostgresMigrationTest` passes on the existing Windows/JDK 21 environment without
`JAVA_TOOL_OPTIONS`, a machine timezone change, or a user
`~/.testcontainers.properties` file. Production settings, dependency versions,
the PostgreSQL 17.6 image and the development database volume are unchanged.

## Root cause

pgJDBC 42.7.11 unconditionally adds `TimeZone` to its startup packet, using
`TimeZone.getDefault().getID()`. Its conversion only adjusts GMT offset signs;
it does not canonicalize the host's legacy `Asia/Calcutta` identifier. This
PostgreSQL image rejects that identifier during connection establishment, before
Flyway can run migration SQL. Docker discovery and container startup had already
succeeded. See the pinned [driver startup implementation](https://github.com/pgjdbc/pgjdbc/blob/REL42.7.11/pgjdbc/src/main/java/org/postgresql/core/v3/ConnectionFactoryImpl.java#L421)
and [timezone conversion](https://github.com/pgjdbc/pgjdbc/blob/REL42.7.11/pgjdbc/src/main/java/org/postgresql/core/v3/ConnectionFactoryImpl.java#L474).

A JDBC `options=-c TimeZone=UTC` setting alone cannot fix this driver version:
PostgreSQL processes `options` before the separate startup parameters, then
attempts to apply the driver's invalid timezone. Changing the server default
alone also leaves that startup parameter unchanged. See
[PostgreSQL 17.6 startup processing](https://github.com/postgres/postgres/blob/REL_17_6/src/backend/utils/init/postinit.c#L1180).

## Test-scoped fix

- The disposable PostgreSQL server explicitly starts with `timezone=UTC`, while
  retaining Testcontainers' existing `fsync=off` setting.
- `@BeforeAll` saves the test JVM's timezone and temporarily selects UTC, so
  pgJDBC sends a supported value for every Flyway/JDBC connection in this class.
  `@AfterAll` restores the original timezone, including after test failure.
- JUnit `@Isolated` prevents other Jupiter tests from running concurrently with
  this temporary default. Unit-test and application JVM settings are untouched.
- The existing migration test also asserts that the connected JDBC session's
  `current_setting('TimeZone')` equals `UTC`.

The existing PostgreSQL container readiness check uses log output; the test's
JDBC connections are opened after `@BeforeAll`. No driver replacement, version
upgrade, POM override, production timezone setting or database migration change
was needed.

## Validation

Validation ran from the repository root using the existing Temurin JDK 21.0.8,
Maven Wrapper 3.9.11 and Git Bash's shell wrapper. `JAVA_TOOL_OPTIONS` was unset;
`_JAVA_OPTIONS`, `JDK_JAVA_OPTIONS` and `MAVEN_OPTS` were also unset in these child
command processes to exclude inherited JVM overrides. No global environment
variable was changed.

Before the fix, the integration-only command below reproduced the original
`Asia/Calcutta` startup failure, after successful automatic Windows named-pipe
Docker discovery and PostgreSQL container startup:

```sh
./mvnw -Pintegration -DskipUnitTests=true verify
```

After the fix, these exact Maven arguments passed:

```sh
./mvnw clean verify
./mvnw -Pintegration verify
```

Both final commands ran with the user Testcontainers properties file temporarily
moved aside. The unit build did not create it; integration discovery worked
without it. Testcontainers may subsequently cache the discovered strategy in
that file. The original user file was restored afterward and its SHA-256 matched
the pre-run value; no file contents were printed. Thus the file is supported but
not a discovery prerequisite.

| Check | Result |
| --- | --- |
| `clean verify` | Reactor `BUILD SUCCESS`; 148 unit tests, 0 failures/errors/skips |
| `-Pintegration verify` | Reactor `BUILD SUCCESS`; 148 unit tests plus 1 integration test, 0 failures/errors/skips |
| Flyway | V1 applied once; validation succeeded; second migrate executed 0 migrations |
| SQL assertions | `trading` namespace exists; JDBC session timezone is UTC |
| Default environment | Failsafe report retains `user.timezone=Asia/Calcutta`; no launch override required |
| Python | 14 tests passed |
| Ruff / mypy | Passed, including repository scripts; mypy checked 6 source files |
| Repository artifacts/safety/JSON | Passed |
| Secret scan | Zero findings |
| `git diff --check` | Passed |

Additional validation commands:

```powershell
Push-Location apps/strategy-engine
uv run --locked pytest
uv run --locked ruff check . ../../scripts
uv run --locked mypy
Pop-Location
uv run --project apps/strategy-engine --locked python scripts/verify-project.py
uv run --project apps/strategy-engine --locked python scripts/check-secrets.py
docker volume inspect kite-hybrid-platform_postgres-data --format '{{.Name}} {{.CreatedAt}}'
git diff --check
git status --short
```

The named volume remains `kite-hybrid-platform_postgres-data`, created
`2026-09-19T14:16:55Z`. The pre-existing stopped development PostgreSQL/Redis
containers were left unchanged; the tests used only disposable Testcontainers
databases. No volume deletion, development SQL writes, Docker reset or credential
disclosure occurred. Generated Python cache changes were restored to their
initial clean state. No commit or push was performed.

The IDE did not expose a runnable configuration for this integration source set,
so a debugger launch could not start. Runtime evidence came from the before/after
Maven runs and database assertions; the temporary agent logpoint was removed.
Logs and the properties-file preservation harness are under ignored
`tmp/timezone-integration/`.
