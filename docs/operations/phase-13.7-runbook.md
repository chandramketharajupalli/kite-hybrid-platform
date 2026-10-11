# Phase 13.7 offline recovery validation runbook

No operational launcher exists. These commands run synthetic fixtures only. Use the
installed JDK21, repository wrapper and cached Docker images; do not supply secrets,
real sessions, .env, trading DB URLs or real endpoints. Maven offline mode prevents
dependency fetching. PostgreSQL fixtures forbid image pulling; inspect the cached
PostgreSQL/Ryuk images first. Missing cache or unexpected network requirement => stop.

Before edits require clean develop with HEAD and local origin/develop both
34cc3e5b242547774a5225d30aa460699db2b2d9. Do not fetch or repair the checkout. Verify
G1/18/63 and exact historical K/L/Phase13.6 artifacts; current source hardening has a
new generation, never rewritten historical evidence. Freeze the Phase13.7 plan hash.

Run in this order from the repository root; capture LASTEXITCODE explicitly because
PowerShell can format native stderr warnings as NativeCommandError:

```powershell
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml test
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=LedgerIT,RecoveryLedgerIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=OwnerIT,RecoveryOwnerIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration verify
.\mvnw.cmd -o test
```

Each gate must pass before the next. Retain any failure XML/logs locally and summarize
the cause/correction in new evidence. Never disable a check to pass a gate. A ledger
failure prevents HTTP integration. The root reactor intentionally excludes this
standalone module; its test success cannot substitute for standalone tests.
Maven verify executes unit tests again before the selected integration classes. The
standalone POM does not honor trading-core's custom skipUnitTests property; an early
invocation supplied that inert property and still ran units. Do not use skipped-test
counts or add repeated invocations together as distinct coverage.

PostgreSQL test JVMs already use UTC. Durability settings are all ON, not the fsync-off
trading fixture configuration. The retirement journal is created once in a unique
system-temp directory `synthetic-owner-ledger-<fixture UUID>`. Its forced identity
and spent markers must survive adapter/worker restart. Missing/corrupt/partial store
means deny; never delete or reconstruct it to get a grant. All processes share this
fixture store. Cleanup belongs only to fixture teardown after the disposable DB and
workers stop. This is not operational recovery or a production file-store design.

The crash test SIGKILLs only its own PostgreSQL container and re-inspects host port
mapping after restart. Do not substitute a trading container or cached port. Owner
workers are test-only subprocesses, not real service launchers. They generate all
synthetic credentials/keys internally and print only a bounded count. Do not put
signed assertions, credentials or accounts in process arguments or test artifacts.

After suites pass:

```powershell
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml dependency:tree '-Dscope=runtime'
jdeps --print-module-deps apps/synthetic-credential-owner/target/synthetic-credential-owner-0.1.0-SNAPSHOT.jar
jar tf apps/synthetic-credential-owner/target/synthetic-credential-owner-0.1.0-SNAPSHOT.jar
uv run --offline python scripts/verify-project.py
uv run --offline python scripts/check-secrets.py
git diff --check
git diff --name-status
git diff --cached --name-status
git status --short --untracked-files=all
```

Require java.base only, no runtime dependencies, main entry point, service loader,
test journal/JDBC/Testcontainers or trading classes in the JAR. Static guards must
not be weakened. Static jdeps alone misses cryptographic providers: require the
RecoverySecurityTest actual-provider checks and --limit-modules java.base subprocess
to pass for the RSA-3072/SHA256withRSA synthetic profile. See plan amendment 1 for the
retained Ed25519 provider finding. Project/secrets scans are complementary heuristics, not live
containment proof. Recheck all protected raw/text hashes using registered methods.
Inspect untracked files and their whitespace too; git diff omits them. No target,
JAR/class, log or temporary journal may enter the changed/new Git inventory.

Manifest replay: SHA-256 of sorted compact JSON manifest body (ASCII escapes,
allow_nan=False, UTF-8/no newline), compare envelope fingerprint; verify raw current
source and protected inventory hashes and registered normalized source hashes; repeat
twice and require identical fingerprint. Do not treat old source fingerprints as
current after authorized source changes; retain before/after identities separately.
Do not copy raw Surefire system properties, environment or account-like payloads into
the public manifest. Counts, hashes, bounded outcomes and limitations suffice.

Report actual fresh results, crashes versus injections, attempted sends versus proven
fixture receipt, and limitations of local filesystem/single-primary durability.
Existing trading integration need not rerun when shared trading code is untouched;
Python tests/Ruff/mypy are not applicable without Python source changes. No live
session/HALT/logging/OS certification follows. Keep all governance/technical approvals
pending, operational NO_GO and real access NOT_AUTHORIZED. Stop; no commit or push.
