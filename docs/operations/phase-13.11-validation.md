# Phase 13.11 validation

Result: **SYNTHETIC_JAVA_OWNER_SANDBOX_PARTIAL**. Actual synthetic Java-owner execution
under the measured disposable controls is established. This report does not claim
complete sandbox certification: enforcing AppArmor/SELinux was not established,
user namespaces were not remapped, image signatures/build attestations were not
independently authenticated, and deliberate native-crash/heap-dump containment was
not exercised. Official publisher/source linkage was independently cross-checked;
digest identity alone was not accepted as provenance.

## Baseline, plan and preservation

Initial branch develop, clean tracked/untracked status; HEAD and locally known
origin/develop both `e4ff258f6dd6bbb3d52195e1359e7de192194699`. Commit message:
Validate synthetic Kite owner root recovery boundaries. No fetch/pull; remote
freshness is not claimed. Baseline inventory contains 845 tracked raw-byte hashes.

Frozen plan SHA-256: `b913dba5cfadc1f8d432f3fa356b55fbbee2a17cb463ebcc94030042d1279807`.
Additive amendments:

- 1: `ffbb9060a3c9dc6edaca1b3aec1640b6b4800f85331f24c6bc646864231964b9`.
- 2: `a4a5c4b973f5d55547fa788185d14c1313d753d7c2cc2d4bc733e68e85bc4fba`.
- 3: `0dc05f39ca66bb281bd729d5d78e3fb0c6d68cceccdada02a6ca6dc220635226`.

G1 raw SHA remains `56a1bd05bbbec36f639450f2f52c491c8c5436c16094f86bcf0c2818d4f63656`.
All 18 registered text-normalized sources and 63 protected research files match.
Three frozen Python files have raw CRLF hashes different from registered normalized
hashes; the registered convention is preserved, with no rewriting. All 25 K/L files,
11 K canonical envelopes and Phase 13.6–13.10 canonical manifests match. All 845 prior
tracked files remain byte-identical. V2 and the solo-governance proposal are unchanged.
Phase 12's five-member gate still prohibits acquisition/evaluation; G1 July TEST state
remains SEALED_NOT_EVALUATED. No trading/risk/configuration or historical source edits.

## Environment and acquisition

Windows 11 amd64; host Oracle JDK 21.0.12+7-LTS-205; Maven wrapper 3.9.11; Docker
client/server 29.8.0, Linux amd64 engine. Fresh initial image listing had no JDK image.
Prior phase archive/cache findings were retained as historical evidence, not claimed
as a new exhaustive host-filesystem search. No host JDK installation occurred.

One approved bounded pull, exit 0 in 29.05 seconds, retrieved official
`eclipse-temurin:21.0.12.1_1-jdk-noble` by immutable Linux amd64 manifest:
`sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf`.
Java observed 21.0.12.1+1-LTS. Compressed layers 213,248,504 bytes; image reported
684,697,895 bytes. Index/config/layer digests, official source URLs, raw metadata
hashes, receipt times and the exact local fixture-image identity are in the manifest.
No alternate image, retry, unofficial mirror or networked Docker build was used.
The locally built test image is resolved to an immutable ID before container creation;
each container's actual Image field must match it. The image is test-only, not deployed.

The immutable vendor recipe includes archive GPG and checksum commands; published
release checksum agrees. Neither the JDK archive signature nor an OCI image signature
was freshly verified here. This limits supply-chain assurance despite official HTTPS
source/catalog agreement. See the architecture document for exact provenance links.

## Fresh test history and corrections

Counts below are individual runs, not a sum of unique tests. Every Maven verify run
also executed the unchanged 95 unit/security/packaging cases. XML totals corroborate
the final suite counts. All Maven dependency resolution was offline.

| Run | Unit | Integration | Failures/errors/skips | Outcome |
|---|---:|---:|---|---|
| Initial FenceLedgerIT + IsolationLedgerIT gate | 95 | 27 | 0/0/0 | PASS, 1:09 |
| New sandbox initial | 95 | 5 | IT 4/1/0 | FAIL, 1:21; retained |
| Sandbox correction 1 | 95 | 5 | IT 1/0/0 | FAIL, 2:22; retained |
| Sandbox + FenceOwnerIT + RootRecoveryIT | 95 | 28 | 0/0/0 | PASS, 4:07 |
| Complete standalone before final audit | 95 | 129 | 0/0/0 | PASS, 7:36 |
| Root Java unit/architecture | 1508 | NOT_RUN | 0/0/0 | PASS, 1:57 |
| Complete standalone after amendment 3 | 95 | 129 | 0/0/0 | PASS, 8:38 |

Initial failures: Docker normalizes shared network names to exact container IDs;
the empty image seed directory exists without a witness-volume mount. Assertions now
verify the resolved ID, no owner mounts and absent witness private key. The remaining
failure was errno formatting: this Linux JDK uses explicit EPERM wording. Exact
numeric EPERM or that explicit wording is required; an unknown error, timeout or
successful child process still fails. No kernel restriction was weakened.

A manual diagnostic first failed before owner execution because PowerShell split an
unquoted -D argument; the quoted retry exposed the formatting mismatch. Both outputs
remain in ignored evidence. A read-only inspection initially queried a nonexistent
`body` envelope key; it was corrected to the source-defined `manifest` key. No source
or fingerprint was rewritten. The combined run's Ryuk housekeeping overlap exceeded
the initial three-total-container budget; amendment 2 explicitly records that fact
and bounds subsequent runs to three workload containers plus one cleanup container.

Root broad integration and Python/Ruff/mypy: NOT_RUN; no shared production or Python
code changed. Repeated standalone verification after amendment 3 is justified by the
new immutable-image/errno guards, not by a desire to inflate test counts.

## Measured controls and recovery evidence

| Check | Observation / classification |
|---|---|
| Actual owner path | Unchanged SyntheticOwner.observe, SyntheticHalt and LoopbackPeer execute in Linux Java |
| One-use positive read | 1 attempted send, 1 peer receipt; OBSERVED_COMPLETED |
| Replacement owner | 0 additional sends/receipts for spent ID |
| Witness kill/restart | Same public key and retirement retained; replacement denied; restarted peer observes 0 new receipts |
| Two owner JVMs | Exactly 1 winner/send/receipt, 1 denial |
| Lost witness acknowledgment | 0 sends/receipts; spent ID denied on retry |
| Expired/revoked/conflicting HALT | 0 sends/receipts; DENIED |
| UID/GID, capabilities, privilege escalation | Java observed 65534/65534, CapEff=0, NoNewPrivs=1, Seccomp=2 |
| Filesystem | Root write explicitly denied; scratch succeeds; host paths/socket/private witness key absent; owner mounts empty |
| Process creation | Java ProcessBuilder denied with explicit EPERM |
| External TCP/DNS | Network-none namespace, no route; documentation-address TCP returns ENETUNREACH, invalid DNS returns UnknownHostException |
| Cgroups | Java observed 64 PIDs, 268435456-byte memory limit, cpu.max 100000/100000 |
| AppArmor/SELinux | NOT_ESTABLISHED; no verified enforcing label |
| User namespaces | NOT_REMAPPED |
| Uncaught synthetic canary | Redacted handler; exit 1; no canary in captured output/Docker logs |
| Local dual-store rollback, surviving independent witness | Inherited test: receipt count stays 1; replay denied |
| All-authority rollback | NEGATIVE_DEMONSTRATED: second grant possible; counterexample performs no HTTP |
| Root loss/conflicting key/quarantine | Existing RootRecoveryIT fails closed; reconstruction cannot clear quarantine |
| Physical power loss / privileged rollback / multi-host consensus | UNVERIFIED |

The test-only forced-file witness is independent of the owner's filesystem, not a
hardware monotonic root. PostgreSQL/coordinator tests remain separate host fixtures.
At-most-once attempted dispatch is not exactly-once delivery. Fixed synthetic clocks
and keys do not establish broker authentication, independent live HALT or human
governance timestamps. No governance second-review decision was created.

Runtime JAR: 15 classes, SHA-256
`4d5798ca017e09a916d07f5a397be9912a4b8e9e044049e7e7bd7dc33e8a6305`.
jdeps reports java.base; actual Linux owner RSA execution uses --limit-modules
java.base. No Main-Class, trading/Spring/JDBC/Testcontainers classes or real broker
hostname appears in the runtime class scan. Three additional probe classes are
packaged separately in the test image, with no JUnit/PG adapters.

## Logging, project checks and replay

Scoped canary scans cover command/container logs, inspect records, Maven reports and
available dumpstreams. Historical dumpstreams are retained byte-for-byte. New
dumpstreams are checked against the exact four-line Windows Maven Manifest-JAR
absolute-path diagnostic, not ignored generically. No scoped native/heap dump was
observed; deliberate native-crash dump generation and future external sink certification
remain NOT_TESTED/NOT_VERIFIED. Exact final scan counts/hashes are in the manifest.
The scoped audit checked 1,844 files with zero canary hits. All 182 historical
dumpstreams remain unchanged; 13 new ones match the narrow diagnostic classification
(195 total). None was deleted. Zero scoped heap/native dump files were observed.

Project verifier passed; secret scan checked 1,923 text files with zero locations.
Git whitespace, staged/tracked inventory, untracked text whitespace and preservation
checks passed. Canonical evidence replay produced identical fingerprints twice;
the final envelope carries the fingerprint, avoiding a circular documentation hash.
Replay uses the
registered sorted compact ASCII JSON payload convention, excludes self-digest, and
checks the source and preserved-file hashes. Raw logs, compiled output and images stay
ignored/local; no generated binaries or logs are staged. Owned phase1311 containers
and volumes were removed; the verified base and test image remain in the local cache.

## Exact additive Git inventory

No existing tracked file modified or staged. New files only:

1. `apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/JavaSandboxIT.java`
2. `apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/SandboxOwnerProbe.java`
3. `apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/SandboxWitnessProbe.java`
4. `apps/synthetic-credential-owner/src/test/resources/phase1311/owner-seccomp.json`
5. `apps/synthetic-credential-owner/src/test/resources/phase1311/MOBY-LICENSE`
6. `apps/synthetic-credential-owner/src/test/resources/phase1311/README.md`
7. `docs/operations/phase-13.11-plan.md`
8. `docs/operations/phase-13.11-plan-amendment-1.md`
9. `docs/operations/phase-13.11-plan-amendment-2.md`
10. `docs/operations/phase-13.11-plan-amendment-3.md`
11. `docs/architecture/kite-synthetic-java-owner-sandbox.md`
12. `docs/operations/phase-13.11-runbook.md`
13. `docs/operations/phase-13.11-validation.md`
14. `research/phase-13.11/generation-1/evidence-manifest.json`

The first six files add isolated fixtures/policy and attribution; the remainder freeze
scope, document measured evidence and bind it deterministically. No existing production
runtime, test, POM, configuration or historical evidence file changes.

## Retained safety state and next boundary

Real Kite REST/WebSocket/historical requests: 0. Real token/account/order access: 0.
Trading DB and operational HALT resume/arm/execute mutations: 0. These are actions
performed by this phase, not a claim of globally complete runtime telemetry.
Phase 12 acquisition BLOCK; H1/H2 FROZEN; SBIN July TEST SEALED; INR10,000 ceiling
UNCHANGED; Stage B ZERO_CALL_ABORT preserved. V2, governance second review and four
technical reviews PENDING. Real access NOT_AUTHORIZED, operational readiness NO_GO,
collateral-assisted MIS NOT_READY. Commit/push NONE.

Next bounded work would require explicit scope for stronger image-attestation proof,
supported LSM/user-namespace enforcement and controlled diagnostic-dump containment.
It cannot waive all-authority rollback or promote live readiness. Stop here; no
automatic deployment, authentication, real request or next operational phase.
