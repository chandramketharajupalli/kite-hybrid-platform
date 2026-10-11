# Phase 13.10 synthetic recovery runbook

Reproduction starts from clean develop at 07d94f5055f74d26cb8f4ac5cc96e76b417bca34,
with matching locally known origin/develop. Do not fetch or repair mismatches. Verify
G1/18/63/KL and committed parent fingerprints, then freeze the plan and any amendment.

JDK21/wrapper, offline Maven, cached Testcontainers PostgreSQL only; UTC configured
in the module and child JVMs. Never supply real credentials, .env, trading database
routes or a broker endpoint. Never run network-denial probes on the host against
external addresses. A missing verified Linux Java image means sandbox NOT_EXECUTED.

```powershell
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=FenceLedgerIT,IsolationLedgerIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml '-Dtest=PackagingTest,IsolationSecurityTest' -Pintegration '-Dit.test=RootRecoveryIT' verify
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

Ledger gate must pass before owner HTTP tests. Capture native exit and XML counts,
preserve failed reports before reruns. Do not suppress arbitrary child output: only
the exact Amendment 1 tooling diagnostic is allowed alongside empty stderr. Generated
witness authentication uses private stdin; keys/receipts/credentials never go into
process arguments, environment, logs or evidence. Kill only the owned child object.
Do not convert fixture state deletion or a fresh Root into a recovery procedure.

Inspect JAR entries and runtime dependencies; copy only the existing nested
RecoverySecurityTest$BaseOnlySignatureProbe class to an isolated ignored probe directory
and run it with the runtime JAR under --limit-modules java.base. Do not include test
dependency JARs in this probe. Module restriction is not OS isolation.

Recompute every pre-change tracked raw hash, registered normalized frozen source,
parent canonical envelope and protected artifact. Replay the final evidence body
twice with sorted compact ASCII-escaped JSON, UTF-8/no newline, allow_nan=False.
Verify its hash, file inventory, unchanged parents and operational denial statuses.
No test rerun or human approval is implied by canonical replay.

To close the sandbox gap in a separately scoped continuation, first establish a
reviewed immutable Linux JDK21 source/image digest and provenance. Then freeze the
JVM-compatible seccomp/process/mount/network policy and test actual owner success,
failure, DNS/TCP denial and canary containment inside it. No pull, install or live
deployment follows automatically from this runbook. Stop after reporting; no commit,
push, token acquisition, real read, HALT change or trading.
