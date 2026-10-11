# Phase 13.9 validation

Result: **SYNTHETIC_ANTI_ROLLBACK_CONTAINMENT_PARTIAL**. The bounded surviving-witness
tests pass. Universal rollback resistance remains ANTI_ROLLBACK_UNRESOLVED, and the
Java owner has not been executed inside the enforced container sandbox. Neither a
passing negative-control test nor shell policy evidence removes these limitations.

## Baseline and frozen scope

Initial develop HEAD and local origin/develop:
`66c7dfec1fb9a835b32545e3f48cf62312c349d4`. Initial porcelain inventory empty. Branch,
refs, status and log were inspected; no fetch or remote refresh was performed.
Local tracking-ref equality cannot establish the remote's current state.

Plan raw SHA-256:
`ed7a29a7a1365a338b8bbdd5206636799f560198f5393919b0f52d9956f665fc`.
The plan preceded executable edits and remains unchanged; no scope amendment.
JDK 21.0.12, Maven wrapper 3.9.11, Docker 29.8.0, Windows 11. Maven was offline;
test processes used UTC. Only cached images and disposable owned infrastructure
were used. No image pull, JDK installation, global setting or host policy change.

## Actual test runs and failure history

Commands use the repository wrapper. For module runs the common prefix is
`.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml`. Logs and the failed
ContainmentIT XML are retained under ignored `tmp/phase139`, not committed evidence.
The manifest records their hashes and summaries; tests are not added across reruns.

| Run / suffix | Actual result |
|---|---|
| ledger-initial: `-Pintegration '-Dit.test=FenceLedgerIT,IsolationLedgerIT' verify` | Exit 0; 95 unit + 27 integration; zero failures/errors/skips |
| containment-owner-initial: `-Pintegration '-Dit.test=ContainmentIT,FenceOwnerIT' verify` | Exit 1; test compilation failed, no tests: Docker Mount API has no getType |
| containment-owner-rerun1: same | Exit 1; 95 unit passed; 14 integration, 0 failures/1 error/0 skips. PID=1 shell's expected fork denial was treated as startup failure |
| containment-owner-rerun2: same | Exit 1; test compilation failed, no tests: startup enum is SUCCESSFUL, not SUCCESS |
| containment-owner-rerun3: same | Exit 0; 95 unit + 15 integration, zero failures/errors/skips |
| ledger-final: `'-Dtest=PackagingTest' -Pintegration '-Dit.test=FenceLedgerIT,IsolationLedgerIT' verify` | Exit 0; 3 packaging unit + 27 integration, zero failures/errors/skips |
| full-standalone: `-Pintegration verify` | Exit 0; **95 unit + 114 integration**, zero failures/errors/skips |
| root: `.\mvnw.cmd -o test` | Exit 0; **1,508 tests**, zero failures/errors/skips; 87 report classes |

Corrections used inspected Docker APIs: empty mount source/name plus exact destination
allowlist replaces the nonexistent accessor. The PID probe now waits for terminal
completion, then requires nonzero exit, no OOM, and specific fork/resource-denial
diagnostics. An arbitrary startup error does not pass. PID/time limits were not raised.
The enum spelling was corrected from the actual API. Static review also retained
quarantine in the witness Root snapshot and tested reconstruction refusal; this was
not an observed failing test. Both focused gates and the full suite passed afterward.

Full integration breakdown: ContainmentIT 2, FenceLedgerIT 14, FenceOwnerIT 13,
IsolationLedgerIT 13, IsolationOwnerIT 3, LedgerIT 8, OwnerIT 36, RecoveryLedgerIT 10,
RecoveryOwnerIT 15. The manifest binds individual XML report hashes and counts.
Python/Ruff/mypy: NOT_RUN, no Python source or executable validator added. Root
integration: NOT_RUN, no shared production source changes; standalone disposable
integration ran. Historical counts are not substituted for these fresh results.

## Fencing evidence and observed counts

| Case | Measured evidence | Classification |
|---|---|---|
| Local journal, DB, or both restored; witness survives | First grant true; second false in all three ledger cases | PROVEN_IN_FIXTURE |
| Both local stores restored after successful owner observation | Peer receipts 1 before and 1 after; attempted sends 1 | PROVEN_IN_FIXTURE |
| Existing unfenced dual-store rollback | Retained IsolationLedgerIT grants twice; no HTTP in negative control | NEGATIVE_DEMONSTRATED |
| All local/witness stores and trusted Root restored | New negative control grants twice; no HTTP dispatch | NEGATIVE_DEMONSTRATED / UNRESOLVED |
| Two independent ledger JVMs | One grant exit 17, one denial exit 18; replacement denied | PROVEN_IN_FIXTURE |
| Two owner JVMs, distinct synthetic incarnations | Sum of attempted sends 1; peer receipts 1; post-rollback replacement sends 0, peer remains 1 | PROVEN_IN_FIXTURE |
| Witness PG KILL/restart | Docker mapping re-inspected; consumed ID denied, new ID granted with retained coordinator | PROVEN_IN_FIXTURE |
| Witness DB sequence regresses | Denied; quarantine persists after DB repair and coordinator reconstruction from retained Root | PROVEN_IN_FIXTURE |
| Missing root / unavailable / wrong pinned root / auth / signature / stale epoch or checkpoint / expiry | Denied; no usable fence | PROVEN_IN_FIXTURE |
| Real witness-JVM loss without retained trusted root, physical power loss, multi-host quorum | No implementation/evidence of recoverable trusted root | NOT_TESTED / UNRESOLVED |

The witness runs outside owner JVMs, in the test coordinator, backed by a separate
PostgreSQL container. Its in-memory trusted root survives selected fixture failures,
not loss/restoration of the host. No third table/file is presented as physical
independence. Signatures and matching hashes cannot solve complete trusted-state rollback.

## Failure windows

| Window | Fenced evidence / delivery classification |
|---|---|
| Before/during witness reservation | Injected denial, local row absent, fresh-client replay denied; no owner HTTP |
| Lost witness ACK, or ACK lost before local write | No usable grant; owner attempted sends 0 and peer receipts 0; local row absent |
| Local partial journal write after fence | Denied, witness retains retirement; replay denied; no HTTP |
| After local ACK / before ticket or socket / socket created before send | Three injected windows: NOT_DISPATCHED, attempted sends 0; replay denied |
| During send / after send / parsing | Three injected windows: MAY_HAVE_DISPATCHED, attempted sends 1; replay denied. No exact peer-byte-receipt claim |
| After parsed response / before outcome persistence | Two injected windows: OBSERVED_COMPLETED, attempted sends 1; outcome remains UNKNOWN, replay denied |
| Stale owner or delayed ACK past expiry | No send; stale owner does not advance checkpoint; delayed case retains local consumption |
| Recovery | Retained-root restart/replacement tests above; existing actual child-kill tests rerun unchanged |

Injected faults are not physical process-kill or power-loss evidence at every witness
transition. Full witness-process kill at each new transition and replicated split-brain
are NOT_TESTED. Root substitution is a bounded split-brain-like pinning test only.
At-most-once attempt can deliver zero times; terminal labels never refund authorization.

## OS, logs and runtime packaging

ContainmentIT passed both probes in a cached image with overridden entrypoint:
UID/GID 65534, read-only root, explicit tmpfs scratch, no host/anonymous mounts,
ALL capabilities dropped, no-new-privileges, network none, 64 MiB, half CPU, PID bounds.
Root writes denied, scratch write allowed, selected host paths/socket absent, effective
capabilities zero. With network-none inspected first, the documentation-IP connection
and invalid-name DNS probes failed. No host external egress probe was run. PID=1
denied child creation with the expected diagnostic. The short-lived PID configuration
was inspected after exit; the network probe configuration before attempts.

These are PROVEN_IN_FIXTURE shell/kernel controls. Java-owner confinement, JVM-compatible
process policy, allowed loopback owner/witness traffic inside that sandbox, Windows
service ACLs, privileged escape and multi-host isolation remain NOT_TESTED/UNRESOLVED.
Only Docker Desktop WSL and no cached Linux JDK image were available; no pull/install
was authorized or attempted. `java.base` is not an OS sandbox.

Generated witness authentication is passed through private stdin, never arguments or
environment. Tests check captured owner stdout/stderr, redacted results, peer requests,
child output/arguments and synthetic container logs. Existing credential/signature/
serialization/error guards reran. This establishes bounded synthetic sink checks,
not full heap/core/JVM-agent/IDE/CI/proxy or live external log certification.

173 existing Surefire/Failsafe dumpstreams were inventoried and hashed. Each has the
same bounded four-line Windows manifest-classpath diagnostic format; 11 registered
Phase 13.8 diagnostics remain unchanged. No diagnostics were deleted or bypass hints
applied. There was no initial ignored-report snapshot, so the other files are not all
attributed to this phase. These are tooling diagnostics, not proof about crash dumps.

JAR: 15 classes / 25 entries, raw SHA-256
`4d5798ca017e09a916d07f5a397be9912a4b8e9e044049e7e7bd7dc33e8a6305`.
Unchanged runtime source and artifact, no Main-Class/service providers, prohibited
trading/JDBC/Testcontainers/witness test classes absent. Runtime dependency tree has
no dependencies; jdeps reports java.base. Existing RSA probe executed with only the
built JAR and isolated probe class under `--limit-modules java.base`, exit 0. No root
reactor/POM change or operational launcher. Test fixtures remain outside the artifact.

## Preservation, replay and final checks

All 826 initial tracked files are byte-identical. Registered checks pass: G1 raw hash,
18 frozen sources (UTF-8 universal-newline convention), 63 protected research files,
162 historical protected files, 25 K/L files, 11 K canonical envelopes, Phase 13.7/13.8
plans and canonical manifests. Three frozen Python files differ raw-vs-normalized due
to existing CRLF: candidates.py, diagnostics.py, opening_features.py; none rewritten.
G1 remains SEALED_NOT_EVALUATED; Phase 12 gate has acquisition/evaluation false and
all five unresolved-member reasons. INR10,000, cash/CNC and operational defaults unchanged.

The evidence manifest records final project/secrets/diff checks and two canonical
replays. Canonicalization is sorted compact ASCII-escaped JSON, allow_nan=False,
UTF-8/no newline; hash the manifest body, not its fingerprint envelope. Replay also
checks file hashes, parent preservation, result/status constraints and exact inventory.
It proves artifact consistency, not reviewer authority or runtime behavior beyond tests.

Project verifier PASS (exit 0); secret scans PASS (1,869 files on the first manifest
scan, 1,868 on the final scan; zero potential secret locations in each; scanner scope
includes mutable ignored workspace files); git diff --check PASS; tracked/staged differences empty. All 11
new files also passed a separate trailing-whitespace, UTF-8 and final-newline check
because git diff does not cover untracked files. Two independent reads/replays of
the final manifest must produce its identical envelope fingerprint; that fingerprint
is reported with the final evidence rather than embedded in this hashed document.

Exact new inventory (no tracked modifications or staged files):

- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/FenceWitness.java — signed test witness and pinned client.
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/FencedLedger.java — witness-before-local-consumption composition.
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/FenceWorker.java — private-bootstrap child fixture.
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/FenceLedgerIT.java — rollback, uncertainty, race and restart controls.
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/FenceOwnerIT.java — bounded HTTP, counts and containment checks.
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/ContainmentIT.java — disposable kernel-policy probes.
- docs/operations/phase-13.9-plan.md — frozen scope and criteria.
- docs/architecture/kite-synthetic-owner-anti-rollback-containment.md — design and assurance limits.
- docs/operations/phase-13.9-runbook.md — reproduction and stop rules.
- docs/operations/phase-13.9-validation.md — this report, including failure history.
- research/phase-13.9/generation-1/evidence-manifest.json — additive fingerprints/results.

No target/JAR/class/log/dump/secret artifacts staged or added to this inventory.
No commit/push. Real Kite/token/WebSocket/order/dev-production DB/HALT resume/arm/
execute counts are zero. V2/four technical reviews/governance second review PENDING;
real access and operational implementation NOT_AUTHORIZED; NO_GO, MIS NOT_READY,
Phase12 BLOCK, H1/H2 FROZEN, July SEALED, Stage B ZERO_CALL_ABORT preserved.

Next work needs separately scoped JVM-in-sandbox evidence and a justified surviving
monotonic trust root/recovery design. Governance and live prerequisites remain separate;
no automatic deployment, real read, contract approval or Phase 14 follows.
