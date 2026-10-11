# Phase 13.11 synthetic-only reproduction

Use develop baseline `e4ff258f6dd6bbb3d52195e1359e7de192194699` and the frozen plan plus
all three amendments. This runbook authorizes no real broker action, service deployment,
governance adoption, trading application startup or historical-evidence modification.

1. Check Git branch/refs/cleanliness and registered preservation hashes. Do not fetch,
   repair, discard work or substitute line-ending normalization conventions.
2. Review the manifest's official source linkage, image/config/layer digests and
   incomplete image-signature verification. Acquisition was one bounded pull; do not
   silently repeat it or fall back to another image. The test fails if the exact
   Linux image is not cached. A Windows JDK is not a Linux runtime.
3. Use the repository JDK 21/Maven wrapper. All Maven commands below are offline;
   PostgreSQL is disposable and the test JVM timezone is UTC in the unchanged POM.
4. Run ledger gates before any new owner HTTP simulation:

```powershell
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=FenceLedgerIT,IsolationLedgerIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=JavaSandboxIT,FenceOwnerIT,RootRecoveryIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration verify
.\mvnw.cmd -o test
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
git status --short --untracked-files=all
git diff --name-status
git diff --cached --name-status
```

JavaSandboxIT constructs a local test image with no RUN or networked build steps.
It copies only the existing runtime JAR and three probe classes. Its generated
Dockerfile, raw command logs and inspect records stay under ignored target/phase1311.
The upstream seccomp license and derived policy live in test resources. Containers
are named with unique phase1311 IDs; only owned containers and named volumes are
removed. Preserve failed reports, command logs and dumps; do not clean them to hide
failure history. Never mount Docker socket, host home, trading data or credentials.

The witness self-terminates after 120 seconds; each host command/owner invocation is
bounded to 30 seconds and each JUnit case to 120 seconds. At most three workload
containers plus the existing Testcontainers cleanup container may overlap. Empty
witness initialization is explicit; recovery must not recreate a missing authority.

Review XML counts and process exits, not console assertions alone. Inspect runtime
JAR entries and jdeps; verify the Linux probe actually executes with java.base only.
Rehash all baseline files and historical envelopes. Canonical evidence replay uses
sorted compact ASCII JSON, UTF-8 without a newline, allow_nan=False, over the manifest
payload only; compare its SHA-256 twice with the envelope fingerprint.

Expected negative controls include all-authority rollback permitting a second grant
with no HTTP. That is a retained limitation, not universal anti-rollback validation.
Unverified LSM/user-namespace isolation, external sinks, privileged rollback and
physical power loss prevent broader certification. Stop on real credential needs,
external runtime egress, protected-file changes or a request to weaken a guard.
No commit/push or automatic next operational phase follows.
