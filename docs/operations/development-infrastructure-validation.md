# Development infrastructure validation — 2026-09-19

Historical record of the earlier infrastructure attempt. For subsequent Docker
reachability and Maven workflow validation, see the
[Maven migration validation record](maven-migration-report.md).
The commands and blocked results below are retained as evidence of this attempt.

**Overall: BLOCKED — Docker Desktop is installed, but its Linux engine cannot
start.** This runtime attempt supersedes the earlier report that Docker was not
installed. Docker's backend reports `Virtual Machine Platform not enabled`;
`wsl --status` and `wsl --list --verbose` report that WSL is not installed.
The ordinary Java build and Python checks pass. The explicitly attempted
PostgreSQL Testcontainers suite fails during Docker discovery, before its test
body executes. Infrastructure runtime validation is not complete.

## Scope and initial state

Read `AGENTS.md`, `docker-compose.yml`, `.env.example`, the local infrastructure
runbook, the previous validation record, the PowerShell environment helper and
Git status before execution. Also inspected the existing Gradle configuration,
Spring configuration, baseline migration, integration test and verification
scripts. Work remains on `develop`, tracking `origin/develop`.

The initial working tree already had eight modified tracked files and three
untracked infrastructure documentation/helper files, listed under Git status
below. These prior changes were preserved. No fetch, merge, commit or push was
performed during this attempt.

The local `.env` file was missing, and neither required password had a process
environment override. Initial Compose validation failed on the required password
checks. Created the ignored `.env` from `.env.example`, with independent
cryptographically random 32-byte local-only PostgreSQL and Redis passwords.
Passwords were never displayed. Database/user remain `trading`; the safe
PAPER/live-disabled/emergency-stop/Kite-disabled defaults remain in place.
`.env` is ignored and untracked. No existing credentials were overwritten.

## Actual results

`PASS` means the check executed successfully. `FAIL` means an attempted check
failed. `BLOCKED` identifies the prerequisite preventing completion.
`NOT EXECUTED` means no runtime verification took place; configuration inspection
does not substitute for execution.

| Check | Result | Actual observation |
| --- | --- | --- |
| Docker CLI | PASS | `Docker version 29.8.0, build 88096ef` |
| Docker Compose | PASS | `Docker Compose version v5.5.1` |
| Docker Engine | BLOCKED | `docker info` failed: initially the Linux-engine named pipe was missing; after a Desktop start attempt it returned HTTP 500, exit 1. No server version was obtained. |
| Local Docker context | PASS | `desktop-linux`, endpoint `npipe:////./pipe/dockerDesktopLinuxEngine`; no remote engine selected |
| Docker Desktop start | FAIL | `docker desktop start --timeout 45` exited 1 with `Docker Desktop is still starting: context deadline exceeded` |
| Host prerequisite diagnosis | BLOCKED | Current Docker backend startup log: `engine linux/wsl failed to start: checking preconditions: Virtual Machine Platform not enabled`; WSL commands report not installed |
| Required environment settings | PASS after local setup | Both required passwords are configured; database and user resolve to `trading`; no stale password/name overrides were present |
| Compose configuration | PASS after local setup | `docker compose config --quiet`, exit 0; resolved JSON captured only in memory for safe assertions |
| Compose service/storage assertions | PASS | Exactly `postgres` and `redis`; ports bind only to `127.0.0.1`; PostgreSQL named volume retained; Redis `/data` tmpfs and snapshot/AOF persistence disabled |
| PostgreSQL version | NOT EXECUTED | Configured image is `postgres:17.6`; no running server version could be queried |
| Redis version | NOT EXECUTED | Configured image is `redis:7.4.5`; no running server version could be queried |
| PostgreSQL startup/health | NOT EXECUTED | Blocked by unavailable engine |
| Redis startup/health | NOT EXECUTED | Blocked by unavailable engine |
| `docker compose ps` | NOT EXECUTED | Engine prerequisite failed; no services were started by this attempt |
| PostgreSQL authenticated connection, database and user | NOT EXECUTED | Configured names were checked, but database existence and authenticated connectivity were not established |
| Redis authenticated PING | NOT EXECUTED | No Redis service available |
| Spring/Flyway against development PostgreSQL | NOT EXECUTED | `bootRun` was not started without a working database; no migration or schema-history query executed |
| PowerShell helper syntax | PASS | PowerShell AST parser reported no syntax errors |
| PowerShell helper invocation | BLOCKED | Direct invocation was rejected because script execution is disabled on this machine; no execution policy was changed or bypassed |
| Existing JDK | PASS | Existing Temurin Java and compiler `21.0.8`; `JAVA_HOME` set only in command processes |
| Ordinary Java build | PASS | `build --no-daemon --rerun-tasks --console=plain`, exit 0; 7 actionable tasks executed |
| Java unit/application/architecture/contract tests | PASS | **148 tests in 15 suites; 0 failures, 0 errors, 0 skipped**, from fresh JUnit XML |
| Integration-test compilation | PASS | Existing integration source compiled successfully |
| PostgreSQL Testcontainers task | FAIL; runtime validation BLOCKED | Explicit Gradle invocation exited 1. JUnit XML: **1 suite, 1 reported result, 1 failure, 0 errors, 0 skipped**. Result is `initializationError`: `Could not find a valid Docker environment`. |
| Testcontainers migration test body | NOT EXECUTED | **0 migration test bodies executed, 0 passed**; the one existing test was blocked during class initialization |
| Testcontainers PostgreSQL startup/connection/Flyway/shutdown | NOT EXECUTED | Docker discovery failed before a PostgreSQL test container could start; normal container cleanup could not be verified |
| Complete Java verification | BLOCKED | Ordinary build passes; the required integration suite does not pass. Combined reports contain 148 passing tests plus 1 initialization failure, not 149 passing tests. |
| Python pytest | PASS | **14 collected, 14 passed**, 0 failed/skipped; Python 3.12.14, pytest 8.4.2, uv 0.12.10 |
| Ruff | PASS | Ruff 0.16.8, `All checks passed!`, exit 0 |
| Strict mypy | PASS | mypy 1.20.2, no issues in **6 source files**, exit 0 |
| Repository verification | PASS | Existing artifact/profile/safety/contract JSON verification script, exit 0 |
| Secret scan | PASS after runtime fix | Initial scan flagged the required ignored local `.env`; final scan reports zero potential secret locations, exit 0 |
| Secret-check regression checks | PASS | Seven isolated Git-fixture checks, described below |
| Git whitespace check | PASS | `git diff --check` after build completion and generated-artifact restoration |
| Service log inspection | NOT EXECUTED | No PostgreSQL/Redis containers started; Docker Desktop host logs were inspected instead |
| Normal `docker compose down` | NOT EXECUTED | No working engine/services to stop; clean shutdown cannot be claimed |
| Restart and PostgreSQL persistence | NOT EXECUTED | No runtime data/volume identity could be compared; named-volume configuration alone does not prove persistence |

## Runtime fix and unchanged infrastructure

The actual secret-check failure was caused by scanning the local `.env` that the
documented runtime workflow requires. Updated `scripts/check-secrets.py` to skip
environment files only when Git identifies them as ignored and untracked.
`.env.example` is always scanned. Tracked environment files, unignored/untracked
source and other ignored files retain their existing scanning behavior. Git
inspection failures stop the check instead of allowing it to pass. Existing
PostgreSQL/Redis credential detection patterns were preserved.

Seven isolated temporary repository checks covered: ignored local environment
files, `.env.example` even when ignored, tracked ignored `.env`, unignored
environment files, untracked source, other ignored files, and Git failure.
All seven passed using synthetic values, without printing credentials.

Files changed by this continuation:

- `scripts/check-secrets.py`: the demonstrated local-environment scan fix above.
- `docs/operations/development-infrastructure-validation.md`: this actual record.
- `.env`: new ignored local configuration; not tracked or staged.

No Compose, healthcheck, authentication, Spring, migration, Java/Python source or
test changes were needed or made during this attempt. No speculative migrations,
application containers or new dependencies were introduced. Redis persistence
remains disabled. No volume-removal, prune, Compose shutdown or volume-reset
command was run; no existing PostgreSQL development volume was deleted.

## Commands and evidence

The main commands below were actually attempted. Docker status and WSL diagnostics
failed as recorded above; the integration command failed rather than being skipped.

```powershell
docker --version
docker compose version
docker info
docker context show
docker context ls
docker context inspect --format '{{.Endpoints.docker.Host}}'
docker desktop status
wsl --status
wsl --list --verbose
docker desktop start --timeout 45
docker compose config --quiet
# JSON output captured in memory; never printed or written to evidence logs.
docker compose config --format json
.\scripts\Use-DevelopmentInfrastructure.ps1

# Existing JDK 21 selected for each command process only.
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\javac.exe" -version
.\gradlew.bat build --no-daemon --rerun-tasks --console=plain
.\gradlew.bat :trading-core:integrationTest --no-daemon --rerun-tasks --console=plain

Push-Location apps/strategy-engine
uv run --locked pytest
uv run --locked ruff check .
uv run --locked mypy
Pop-Location
uv run --project apps/strategy-engine --locked python scripts/verify-project.py
uv run --project apps/strategy-engine --locked python scripts/check-secrets.py
git check-ignore .env
git ls-files .env
git diff --check
git status --short --branch
```

JDK path used:
`D:/Softwares/spring-tools-for-eclipse-4.32.0.RELEASE-e4.37.0-win32.win32.x86_64/sts-4.32.0.RELEASE/plugins/org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_21.0.8.v20250724-1412/jre`.

Fresh build logs, JUnit totals/XML/HTML, Docker diagnostic output and Python
results were retained under ignored
`tmp/docker-infrastructure-validation/runtime-2026-09-19/`.
This repository already tracks ignored build/cache artifacts. Before validation,
snapshotted 90 tracked Java/Gradle artifacts and five Python bytecode files.
After saving fresh evidence, restored 39 changed Java/Gradle artifacts and the
five Python snapshots to their original bytes; verified snapshot hashes. Use the
saved runtime evidence for this run, not restored historical tracked reports.

## Warnings, outstanding work and Git status

- Docker's backend startup error is an operating-system prerequisite failure,
  not evidence of a defect in the Compose service design. Docker Desktop's GUI
  starting does not establish that its Linux engine is running. No Windows
  features, WSL installation or system settings were changed by this validation.
- The helper is additionally blocked by the existing PowerShell execution
  policy. Its AST syntax passes, but successful helper execution is unverified.
- The Java test JVM emitted the class-sharing/bootstrap-classpath warning; the
  ordinary build still exited 0. Python checks emitted no validation warnings.
- A Git whitespace check attempted while Gradle held its tracked cache lock
  failed to read that lock. The final check after Gradle exited and artifacts
  were restored passed.
- PostgreSQL/Redis service startup logs and runtime warnings are unknown, because
  those services did not run. No service health or persistence success is claimed.
- Remaining prerequisite: complete Docker Desktop's Linux-engine setup, including
  the reported Virtual Machine Platform/WSL prerequisites. Then rerun the pending
  service health/authentication, Spring/Flyway, Testcontainers, logs, normal
  shutdown and PostgreSQL persistence checks from the existing runbook.

Final Git status retains the same eight modified and three untracked paths as
the initial status; the scanner and this report include this continuation's edits:

```text
## develop...origin/develop
 M .env.example
 M README.md
 M apps/trading-core/src/main/resources/application.yml
 M docker-compose.yml
 M infrastructure/docker/README.md
 M infrastructure/postgres/README.md
 M infrastructure/redis/README.md
 M scripts/check-secrets.py
?? docs/operations/development-infrastructure-validation.md
?? docs/runbooks/local-development-infrastructure.md
?? scripts/Use-DevelopmentInfrastructure.ps1
```

No Kite diagnostic or real Kite request ran; normal Java tests use mock responses.
No Phase 3 work was started. Nothing was staged, committed or pushed.
