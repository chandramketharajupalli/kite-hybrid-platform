# Maven migration and validation — 2026-09-19

The Java build now uses Maven 3.9.11 through Maven Wrapper 3.3.4, with the root
`kite-hybrid-platform` parent and `apps/trading-core` child. JDK 21 and Spring Boot
3.5.16 are unchanged. Both required post-removal commands, `mvnw.cmd clean verify`
and `mvnw.cmd -Pintegration verify`, reported reactor `BUILD SUCCESS`.

All 148 unit tests and the one PostgreSQL integration test passed, with zero
failures, errors or skips. Gradle and Maven results match suite by suite. Python's
14 tests and repository static/secret/whitespace checks also passed.

No commit or push was performed. Pre-existing infrastructure changes were
preserved. No application execution/trading logic changed; the sole Java source
edit replaces the obsolete Gradle diagnostic command in its usage message.

This report preserves the migration's historical validation, which used a
process-local UTC override. That override is no longer an integration-test
prerequisite: see the [subsequent scoped timezone fix](postgres-integration-timezone.md).
The original commands and results below remain historical evidence.

## Configuration translated

| Gradle configuration | Maven equivalent |
| --- | --- |
| Root project, `com.kitehybrid:0.1.0-SNAPSHOT`, `trading-core` mapped to `apps/trading-core` | Parent POM with the same group/version and child module location |
| Spring Boot plugin/BOM 3.5.16, Maven Central | Spring Boot starter parent 3.5.16; Maven Central default repository |
| Java toolchain 21; automatic JDK download disabled | Compiler release 21, Enforcer requires Maven to execute on JDK 21 with an existing `javac`; no JDK downloader |
| UTF-8 and Boot's compiler parameter metadata | UTF-8 source/report encoding and `parameters=true` |
| Production resources copied unchanged | Unfiltered Maven resources; all six files verified byte-for-byte |
| JUnit Platform unit source set and `contracts.dir` | Surefire with class discovery and module-relative repository contracts path |
| Explicit `integrationTest` source set/task | Opt-in `integration` profile, separate compilation/output and Failsafe; Testcontainers dependencies profile-only |
| `src/integrationTest/resources` convention | Profile-only resource copy to `target/integration-test-classes` |
| Ordinary build/check/test do not require Docker | Ordinary Maven `test`/`verify` do not compile or execute integration tests |
| `bootJar` and plain JAR | Boot repackage plus a separate `plain` classifier JAR |
| Explicit `bootRun` | Child `spring-boot:run` |
| Explicit standalone `kiteDiagnostic` Java process | Child `compile exec:exec -Dkite.diagnostic=profile` or `instruments`, forking the selected JDK |
| ArchUnit and repository Python/static checks | Existing test/check sources retained; Maven artifacts substituted in repository verification |

No Java lint/coverage plugin or CI configuration existed to translate. No CI
provider/configuration was added. Python project structure and dependency lock
remain unchanged.

## Dependencies and plugins

All production declarations were translated: web, validation, JDBC, actuator and
Redis Spring Boot starters; Jackson CSV; Flyway core; runtime PostgreSQL Flyway
support, PostgreSQL JDBC driver and Prometheus registry. Test declarations retain
Spring Boot starter-test, ArchUnit, JSON Schema validator, JUnit Platform launcher,
and the explicit integration-profile Testcontainers JUnit/PostgreSQL modules.

| Dependency | Preserved resolved version |
| --- | --- |
| Spring Boot parent, starters and packaging plugin | 3.5.16 |
| Jackson CSV / Jackson BOM | 2.21.4 |
| Flyway core / PostgreSQL support | 11.7.2 |
| PostgreSQL JDBC | 42.7.11 |
| Micrometer Prometheus | 1.15.12 |
| JUnit Jupiter / Platform | 5.12.2 / 1.12.2 |
| Testcontainers | 1.21.4 |
| ArchUnit | 1.4.1 |
| JSON Schema validator | 1.5.9 |
| Test Byte Buddy / agent | 1.18.3 / 1.17.8 |
| SnakeYAML production / test runtime | 2.4 / 2.5 |

Maven and Gradle resolve all 81 production runtime coordinates to identical
versions. All 116 Gradle unit-runtime and 130 integration-runtime coordinates are
also present at identical versions in the corresponding Maven test JVMs.
Byte Buddy core is explicitly pinned as a test dependency. SnakeYAML is replaced
only in Surefire/Failsafe runtime classpaths, retaining the production BOM's 2.4
in the application. These preserve Gradle's higher test-transitive selections
without upgrading or removing a production dependency.

Maven plugins introduced: Compiler 3.14.1; Enforcer 3.6.2; Surefire and Failsafe
3.5.4; Exec 3.6.3; Spring Boot 3.5.16. Boot's parent manages JAR 3.4.2, Resources
3.3.1 and Clean 3.4.1, among other standard lifecycle plugins. Dependency Plugin
3.8.1 was used to inspect resolution. Wrapper 3.3.4 uses its official `only-script`
distribution; no wrapper JAR is required. Maven ZIP SHA-256 is pinned to
`0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb`.
The downloaded Maven ZIP was additionally checked against its published SHA-512.

References: [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/),
[compiler testCompile](https://maven.apache.org/plugins/maven-compiler-plugin/testCompile-mojo.html),
[Surefire classpaths](https://maven.apache.org/surefire/maven-surefire-plugin/examples/configuring-classpath.html),
[Spring Boot Gradle defaults](https://docs.spring.io/spring-boot/3.5/gradle-plugin/reacting.html).

## Execution environment and exact validation commands

Commands ran from `D:\Yogendra\kite-hybrid-platform`, except the explicitly
identified Python project commands. Each Java command process selected the
already installed Temurin JDK 21.0.8. No JDK installation or global environment
change occurred:

```powershell
$env:JAVA_HOME = 'D:\Softwares\spring-tools-for-eclipse-4.32.0.RELEASE-e4.37.0-win32.win32.x86_64\sts-4.32.0.RELEASE\plugins\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_21.0.8.v20250724-1412\jre'
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:JAVA_HOME\bin\javac.exe" -version
.\mvnw.cmd --version
```

The first Gradle integration run, before setting UTC, failed because PostgreSQL
rejected the host JVM timezone alias `Asia/Calcutta`. Both builds then used the
same process-local UTC setting. This is an existing environment prerequisite,
not a Maven application change. The original failure is retained in local
evidence; the rerun passed. In `cmd.exe`, the equivalent setting is
`set JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`.

```powershell
docker version --format '{{.Server.Version}}'
docker compose config --quiet
docker compose ps --format json

# Historical commands executed before the wrapper/build files were removed:
.\gradlew.bat build :trading-core:integrationTest --no-daemon --rerun-tasks --console=plain
.\gradlew.bat build :trading-core:integrationTest :trading-core:migrationDependencies -I tmp/maven-migration/dependency-baseline.gradle --no-daemon --rerun-tasks --console=plain

# Successful explicit compilation and dependency inspection:
.\mvnw.cmd -B -ntp -pl apps/trading-core compile dependency:list '-DoutputFile=../../tmp/maven-migration/maven-unit-dependencies.txt'

# Full validation with dependency-parity adjustments before removing Gradle:
.\mvnw.cmd -B -ntp -Pintegration verify

# Required final validation after removing all obsolete Gradle build files:
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration verify

# Shell wrapper syntax and execution on the available Git Bash:
& 'C:\Program Files\Git\bin\bash.exe' -n ./mvnw
& 'C:\Program Files\Git\bin\bash.exe' ./mvnw --version

# Explicit disabled diagnostic; expected exit 1 / CONFIGURATION, no broker call:
$env:KITE_REST_ENABLED = 'false'
.\mvnw.cmd -B -ntp -pl apps/trading-core compile exec:exec '-Dkite.diagnostic=profile'

Push-Location apps/strategy-engine
uv run --locked pytest
uv run --locked ruff check . ../../scripts
uv run --locked mypy
Pop-Location
uv run --project apps/strategy-engine --locked python scripts/verify-project.py
uv run --project apps/strategy-engine --locked python scripts/check-secrets.py
uv run --project apps/strategy-engine --locked python tmp/maven-migration/compare-evidence.py
git diff --check
git diff --cached --name-only
git check-ignore .env apps/trading-core/target tmp/maven-migration
git ls-files '*/target/*' 'target/*' '*.log'
git status --short
```

The local comparison helper verifies suite totals, actual test-JVM dependency
coordinates, executable/plain JAR structure, verbatim production resources and
unit/integration output isolation. It is ignored validation evidence, not a new
repository build prerequisite. The disabled-diagnostic assertion initially
expected the label `DISABLED`; inspection confirmed the existing application's
correct label is `CONFIGURATION`. No application logic was changed for this check.

| Validation | Result |
| --- | --- |
| Gradle UTC baseline | 15 unit suites / 148 tests; 1 integration suite / 1 test; all passed |
| Maven compile | BUILD SUCCESS; Java release 21 |
| Maven pre-removal integration verification | Reactor BUILD SUCCESS; 148 unit + 1 integration passed |
| Post-removal `clean verify` | Reactor BUILD SUCCESS; 148 passed, 0 failures/errors/skips; no integration execution |
| Post-removal `-Pintegration verify` | Reactor BUILD SUCCESS; 148 unit + 1 integration passed, 0 failures/errors/skips |
| `PostgresMigrationTest` | Isolated postgres:17.6; V1 applies once; validates; second migrate applies 0; trading schema query succeeds |
| Python | 14 passed; Python 3.12.14 / pytest 8.4.2 |
| Ruff including scripts | All checks passed |
| mypy | No issues in 6 source files |
| Repository artifact/safety/JSON check | Passed |
| Secret scan | 375 text files; zero potential secret locations after this report was written |
| Whitespace | `git diff --check` passed |
| Shell wrapper | Syntax passed; Maven 3.9.11 / Java 21.0.8 confirmed |
| Disabled diagnostic | Expected nonzero result; CONFIGURATION rejection; real Kite connectivity not executed |

## Development infrastructure and packaging

Docker Engine 29.8.0 was reachable. The existing development containers had
stopped during the session; `docker compose start --wait --wait-timeout 120`
started the same PostgreSQL/Redis containers, without recreating them or changing
their configuration. Their IDs remained `dafdfd0778a0` and `2c32cde5cccc`.
Both final health states are `healthy`.

The existing named volume `kite-hybrid-platform_postgres-data` remains present,
with creation time `2026-09-19T14:16:55Z`. No Docker volume deletion, prune,
`compose down`, Docker Desktop reset or global Docker change was performed.
Disposable Testcontainers databases are separate from the development volume.

Authenticated connectivity and Flyway checks used container environment values
without printing or placing passwords in host command arguments:

```powershell
docker compose start --wait --wait-timeout 120
docker compose exec -T redis redis-cli -e ping
@'
set -e # Fail on a failed SQL check.
export PGPASSWORD="$POSTGRES_PASSWORD" # Container environment only.
psql -h postgres -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c 'SELECT 1;' # Authenticated network check.
psql -h postgres -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c 'SELECT installed_rank, version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;' # Read-only migration history.
psql -h postgres -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'trading';" # Existing namespace.
'@ | docker compose exec -T postgres sh
Test-NetConnection -ComputerName 127.0.0.1 -Port 5432 -InformationLevel Quiet
Test-NetConnection -ComputerName 127.0.0.1 -Port 6379 -InformationLevel Quiet
docker volume inspect kite-hybrid-platform_postgres-data --format '{{.Name}} {{.CreatedAt}}'
```

Results: Redis `PONG`; SQL `1`; successful Flyway V1 history; `trading` schema;
both published host ports reachable. The built executable JAR was also started
against these services with the existing development helper, safe trading/Kite
flags and port 18080. Health, liveness and readiness all returned `UP`; only this
temporary application process was stopped afterward. The command was:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tmp/maven-migration/validate-runtime.ps1
```

That ignored validation script invokes the existing infrastructure helper,
launches the selected JDK with
`-jar apps/trading-core/target/trading-core-0.1.0-SNAPSHOT.jar --server.port=18080`,
checks `/actuator/health`, `/actuator/health/liveness` and
`/actuator/health/readiness`, and stops its own process. The execution-policy
override applies only to that child process. Credentials remain in process
environment/in-memory Compose configuration and are not included in this report.
A private exact-match audit of 11 saved logs/report files found no development
database/Redis password values; only the audit result was printed.

## Differences and limits

- Maven uses test scope for the JUnit launcher, so it is available during test
  compilation as well as execution. Maven also exposes runtime dependencies to
  test compilation. The test compiler sees production SnakeYAML 2.4, while both
  actual test JVMs use the preserved 2.5. Existing tests do not compile against
  the SnakeYAML API.
- Maven resolves API Guardian 1.1.2 onto the test runtime because of Maven/Gradle
  metadata differences. It is an annotation API; all existing baseline runtime
  coordinates and versions are otherwise preserved. With the integration profile,
  Testcontainers dependencies are available to both Maven test classpaths, but
  integration classes/output remain isolated and unit tests do not start Docker.
- Maven produces XML/text Surefire/Failsafe reports in `target`, replacing
  Gradle's HTML reports under `build`. Task syntax, incremental caches and
  warning formatting differ. No corresponding Gradle static-analysis plugin
  was omitted.
- Maven's Boot packager omits nine class-free starter JARs and includes Boot
  jarmode tools 3.5.16. All runtime-bearing dependencies keep their baseline
  versions; executable and plain JARs are both produced and inspected. Maven
  also retains its intermediate `.jar.original` under ignored `target`.
- Both wrapper scripts were exercised on Windows (the shell version through
  Git Bash); native Linux/macOS execution was not performed. The unmodified
  official shell script's checksum-verified ZIP path requires `unzip` on Unix.

## Exact file inventory and preservation

New migration files:

```text
.mvn/wrapper/maven-wrapper.properties
apps/trading-core/pom.xml
docs/operations/maven-migration-report.md
mvnw
mvnw.cmd
pom.xml
```

Existing files edited for migration (including files already modified before
this task):

```text
.gitattributes
.gitignore
README.md
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteRestDiagnostic.java
config/README.md
docs/api/README.md
docs/architecture/kite-rest-and-instruments.md
docs/operations/README.md
docs/operations/phase-1-implementation-report.md
docs/operations/phase-2-implementation-report.md
docs/runbooks/kite-rest-diagnostic.md
scripts/check-secrets.py
scripts/verify-project.py
```

Pre-existing untracked documents updated in place:

```text
docs/operations/development-infrastructure-validation.md
docs/runbooks/local-development-infrastructure.md
```

Pre-existing user changes preserved without migration edits:

```text
.env.example
apps/trading-core/src/main/resources/application.yml
docker-compose.yml
infrastructure/docker/README.md
infrastructure/postgres/README.md
infrastructure/redis/README.md
scripts/Use-DevelopmentInfrastructure.ps1
```

Deleted only after Maven validation passed:

```text
apps/trading-core/build.gradle.kts
build.gradle.kts
gradle.properties
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
settings.gradle.kts
```

There were already 90 tracked generated files under `.gradle/` and
`apps/trading-core/build/` when the task started. Exactly 39 changed during
baseline validation; only those changed tracked paths were restored to their
initial clean versions, after saving current test evidence. They remain
historically tracked, unchanged, as requested; they are not Maven inputs.
Five initially clean tracked Python cache files changed by pytest were similarly
restored. No broad Git clean/reset was used. Fresh Maven `target`, logs and local
evidence are ignored; no new generated build artifacts are tracked or staged.

The shell wrapper has executable mode 100755 recorded via
`git add --intent-to-add --chmod=+x -- mvnw` because this Windows repository has
`core.filemode=false`. This is an intent-to-add entry only: no file content is
staged, and `git diff --cached --name-only` is empty. No commit or push occurred.

## Remaining intentional Gradle references

- `.gitignore`: protects old `.gradle/` caches and `build/` outputs.
- `scripts/check-secrets.py`: excludes legacy generated cache/output directories.
- `apps/trading-core/pom.xml`: two explanatory comments describe preserved resource
  copying and JUnit discovery.
- Historical reports retain their original evidence and commands:
  `docs/operations/phase-1-implementation-report.md`,
  `docs/operations/phase-2-implementation-report.md`, and
  `docs/operations/development-infrastructure-validation.md`.
- This migration report records the old baseline and removed paths.
- Preserved historical generated artifacts and ignored local validation evidence
  can contain old paths. Active developer commands, scripts and POMs do not
  require any Gradle file or executable.

Final `git status --short` (the `.mvn/` entry contains only the wrapper properties
listed above):

```text
 M .env.example
 M .gitattributes
 M .gitignore
 M README.md
 D apps/trading-core/build.gradle.kts
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteRestDiagnostic.java
 M apps/trading-core/src/main/resources/application.yml
 D build.gradle.kts
 M config/README.md
 M docker-compose.yml
 M docs/api/README.md
 M docs/architecture/kite-rest-and-instruments.md
 M docs/operations/README.md
 M docs/operations/phase-1-implementation-report.md
 M docs/operations/phase-2-implementation-report.md
 M docs/runbooks/kite-rest-diagnostic.md
 D gradle.properties
 D gradle/wrapper/gradle-wrapper.jar
 D gradle/wrapper/gradle-wrapper.properties
 D gradlew
 D gradlew.bat
 M infrastructure/docker/README.md
 M infrastructure/postgres/README.md
 M infrastructure/redis/README.md
 A mvnw
 M scripts/check-secrets.py
 M scripts/verify-project.py
 D settings.gradle.kts
?? .mvn/
?? apps/trading-core/pom.xml
?? docs/operations/development-infrastructure-validation.md
?? docs/operations/maven-migration-report.md
?? docs/runbooks/local-development-infrastructure.md
?? mvnw.cmd
?? pom.xml
?? scripts/Use-DevelopmentInfrastructure.ps1
```
