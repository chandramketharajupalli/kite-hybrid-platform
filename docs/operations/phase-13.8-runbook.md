# Phase 13.8 synthetic isolation/recovery runbook

No launcher, real credentials, broker calls or operational approval are involved.
Use the installed JDK21, Maven wrapper, UTC test JVM settings and cached images only.
Require clean develop at 2c54d9bd581139e26fff1775fcaeba7259b4591e before reproducing
the starting review. Do not fetch or repair a mismatch. Preserve all earlier evidence.

Verify the registered raw/canonical 13.7 fingerprints, G1/18 normalized sources/63
raw research files and K/L inputs. Read and hash phase-13.8-plan.md before fault
tests. Freeze changes to criteria as an additive amendment. Current source hashes
belong to new evidence, never overwrite prior manifests or pretend old XML is fresh.

Run from repository root, in order; capture LASTEXITCODE immediately for each Maven
command and preserve failed XML/logs under ignored tmp/phase138 before rerunning:

```powershell
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml test
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=LedgerIT,RecoveryLedgerIT,IsolationLedgerIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=OwnerIT,RecoveryOwnerIT,IsolationOwnerIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration verify
.\mvnw.cmd -o test
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

Do not run HTTP integration until the honest-store ledger gate passes. The logical
whole-store rollback counterexample deliberately demonstrates a second ledger grant;
it performs no HTTP and is not permission to retry an operational request. It removes
only UUID-specific disposable fixture state. Never substitute a trading database,
host path or real approval identifier. No test installs a production recovery path.

IsolationWorker is test-only and absent from the JAR. Stage signals contain only
fixed labels. The parent waits <=25s and terminates its own process at the barrier;
closed control input exits without continuing. Fixture-owned processes/containers
must terminate before journal cleanup. Existing container restart tests re-inspect
mapped ports. No registry pull, host storage failure, external egress probe, host
firewall modification or global environment change is required or authorized.

RecoverySecurityTest runs actual RSA signing/verification in a separate JVM limited
to java.base. For independent built-JAR verification, copy only its nested test
BaseOnlySignatureProbe class into a new ignored probe directory; run java with
--limit-modules java.base and classpath consisting only of that directory plus the
runtime JAR. No test dependency should be available to that probe. Inspect JAR entries,
service descriptors, Main-Class, class count, forbidden strings and runtime dependency
tree in addition to jdeps. No normal application startup is part of validation.

The permissions probe reads its own temporary canary and launches one bounded child;
success is a counterexample to treating module limits as OS isolation. No access to
trading stores, secrets or unrelated host data is involved. Record OS policy, external
logging, physical storage and multi-host replication as unverified rather than
converting their absence into passing deployment evidence.

Manifest replay: canonicalize manifest body with sorted keys, compact separators,
ASCII escapes, allow_nan=False, UTF-8 and no newline; SHA-256 must equal fingerprint.
Verify current raw inventories, preserved raw inputs, registered normalized sources,
retained statuses, exact inventory and plan identity. Repeat twice; equal fingerprints
prove deterministic evidence consistency only. Raw manifest hash is separate.
Exclude the manifest itself from its raw self-inventory. Never publish raw test
system properties, credentials, signed assertions or account-like responses.

After audit report all failures/corrections, actual counts, current hashes and limits.
Keep V2/four reviews/governance second review PENDING, NO_GO, MIS NOT_READY and real
access NOT_AUTHORIZED. Stop without commit/push or any activation.
