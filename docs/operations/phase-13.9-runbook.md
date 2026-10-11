# Phase 13.9 synthetic fencing and containment runbook

Require clean develop at 66c7dfec1fb9a835b32545e3f48cf62312c349d4 before reproducing
the starting review. Check local refs without fetching; mismatches require stopping,
not repairing. Verify registered 13.7/13.8 raw/canonical hashes, G1/18/63 and K/L
history. Freeze phase-13.9-plan.md before executable changes. Preserve negative controls.

Use installed JDK21/wrapper, offline Maven, cached images and UTC. Do not inject any
real credential, .env, trading URL or broker endpoint. All Java networking binds
numeric loopback. Container egress-denial probes execute only after verifying network
mode none and no routable interface; do not reproduce those probes on the host.

```powershell
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=FenceLedgerIT,IsolationLedgerIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=ContainmentIT,FenceOwnerIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration verify
.\mvnw.cmd -o test
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml dependency:tree '-Dscope=runtime'
jdeps --print-module-deps apps/synthetic-credential-owner/target/synthetic-credential-owner-0.1.0-SNAPSHOT.jar
uv run --offline python scripts/verify-project.py
uv run --offline python scripts/check-secrets.py
git diff --check
git diff --cached --name-status
git status --short --untracked-files=all
```

Capture native exit codes and retain failed logs/XML before reruns. Maven verify
also runs unit tests; do not add repeated runs as unique coverage. Ledger gate must
pass before owner HTTP. Witness/local containers are separate and owned by the test;
never substitute an existing database. The root snapshot and signing key exist only
at the fixture coordinator. Loss of that root is denial, not permission to bootstrap
from old DB data. The all-root-reset negative control is never a recovery recipe.

Child FenceWorker uses private stdin for generated fixture bootstrap. Do not move
authentication material to arguments, environment, reports or evidence. Roots/IDs
and receipts never represent broker authority. Only selected FencedLedger fixtures
obtain witness grants; retained legacy tests remain explicitly unfenced.

OS policy probes use the cached PostgreSQL image with overridden shell/sleep entrypoint,
not a running PostgreSQL or Java-owner service. Inspect UID, network none, root-readonly,
capabilities, no-new-privileges, tmpfs-only mounts and resource limits. The PID=1
negative process is expected to terminate on denied fork. Require actual denial
diagnostics; do not raise PID limits or treat any unrelated launch error as proof.
Do not apply a suggested Surefire classpath-check bypass from dumpstream diagnostics.

Inspect final JAR entries/dependency tree and execute the existing nested RSA probe
with --limit-modules java.base and a classpath containing only the built JAR plus
the isolated test probe class. No operational Main-Class is added. Recheck report
canaries and inventory new dumpstreams without deleting earlier outputs.

Replay the new evidence manifest twice: sorted compact ASCII-escaped JSON body,
allow_nan=False, UTF-8/no newline, SHA-256 envelope fingerprint. Verify current file
hashes, full initial tracked inventory, historical fingerprints, normalized frozen
sources and denied operational statuses. Raw manifest hash is separate; exclude its
self-reference. Replay is artifact consistency, not test rerun or approval.

Report PARTIAL whenever universal rollback or required Java-owner OS containment
remains unresolved. This host's cached shell image is not Java-owner confinement
evidence. Stop after reporting; no commit/push, activation, real request, governance
signature, V2 approval or trading follows.
