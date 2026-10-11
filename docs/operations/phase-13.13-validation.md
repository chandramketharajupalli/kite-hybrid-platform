# Phase 13.13 validation

Result: **SYNTHETIC_PLATFORM_SECURITY_PARTIAL**. All fresh required Java suites pass,
but authenticated OCI provenance, effective LSM enforcement, user-namespace remapping
and complete external-sink assurance remain unresolved mandatory controls. This is
not certification or operational authorization.

## Baseline and frozen scope

Initial tree clean; develop HEAD and local origin/develop both
`d20ba52958111c2837ea4419a283798e7ee9ffed`. No fetch/pull; remote freshness unknown.
870 tracked raw hashes captured before work. Frozen plan SHA-256:
`59b74cd376d61531ff06f4b5b6446c335887c5a899e938aaf53860b24c3cce76`.
No plan amendment or acceptance relaxation. Four test-only corrections and two
test-only guard files are within its explicit scope. No production source, POM,
security profile, configuration, migration or prior document/evidence changed.

Source review found missing pre-creation budget checks and ownership labels in the
actual-owner fixture, and name-based removal in the recovered diagnostic fixture.
The shared test coordinator checks active/created workloads before creation, uses a
cooperative cross-JVM lock, labels ownership and removes raw Docker containers by
verified immutable ID. Actual-owner cleanup now runs in finally; witness-constructor
failure cleanup and owned-volume checks are explicit. Disposable PostgreSQL and
shell containment fixtures check the same pre-create budget. Runtime APIs are unchanged.

## Fresh execution and failure history

All Maven runs used JDK21.0.12, wrapper3.9.11, offline dependencies and UTC test JVMs.
Linux image Java reports 21.0.12.1+1-LTS. Docker engine29.8.0 uses kernel
6.18.33.2-microsoft-standard-WSL2. Exact selected XML was archived per run.

| Fresh run | Unit | Integration | Failures/errors/skips | Exit / elapsed |
|---|---:|---:|---|---|
| Focused diagnostics/resource gate | 97 | 9 | 0/0/0 | 0 / 1:58 |
| Actual owner + fence/local rollback/root recovery | 97 | 55 | 0/0/0 | 0 / 5:23 |
| Complete standalone | 97 | 138 | 0/0/0 | 0 / 9:59 |
| Root Java unit/architecture | 1,508 | NOT_RUN | 0/0/0 | 0 / 1:12 |

Counts are separate executions, not summed as unique tests. Two new unit cases cover
the budget's allowed and rejected counts. The existing nine diagnostics retain live
fourth-workload denial, two-container injected-assertion cleanup, dump containment
and exact parser negatives. No Maven failure or corrective test rerun occurred here.
Root broad integration and Python/Ruff/mypy were NOT_RUN: no shared production or
Python source changed. The required root unit/architecture suite did run.

One read-only sink-audit script initially included image-inspect.json among container
inspections and raised KeyError HostConfig. The corrected selector excludes that exact
image file. It then succeeded; retained output and the correction are in ignored
evidence and the manifest. This was not a Maven test or fixture failure. Historical
Phase13.12 overlap, teardown and Windows-query failures remain immutable; no prior
failure is relabeled a pass. SLF4J's no-provider warning is retained, not a test failure.

## Certification matrix

| Mandatory control | Classification | Fresh evidence and limits |
|---|---|---|
| Image digest identity | VERIFIED | Cached platform/local ID and five rootfs layers match registered manifest/config |
| OCI authenticated provenance | NOT_VERIFIED | No authenticated signer/root/subject signature chain; unsigned SLSA only |
| Effective AppArmor/SELinux | UNAVAILABLE | Java AppArmor=N; SELinux/securityfs unavailable; label kernel; owner OTHER_OR_UNCONFINED |
| Userns remapping | NOT_VERIFIED | uid_map/gid_map identity range 0 to 0, 4294967295; actual owner NOT_REMAPPED |
| Seccomp / no-new-privileges / caps | VERIFIED | Actual owner Seccomp2, NoNewPrivs1, CapEff0 and non-root65534 |
| Filesystem / process | VERIFIED, bounded | Actual Java root-write denial, scratch success, absent host/private-key paths, ProcessBuilder EPERM |
| Network / one-use | VERIFIED, bounded | No external route; TCP ENETUNREACH and invalid DNS denial; numeric-loopback one-use fixture |
| JVM dump containment | VERIFIED, bounded | Canary heap stays in private tmpfs; forbidden sinks denied; no host export |
| External logging sinks | NOT_VERIFIED | Local json-file/capture/test sinks inspected; external/privileged collectors not certified |
| Budget and cleanup | VERIFIED, tested cases | Maximum3; fourth denied; assertion cleanup; zero final owned resources |
| Actual Java owner | VERIFIED, synthetic only | Fresh six-case sandbox suite passed twice; same runtime/RSA path |
| Surviving-root recovery | VERIFIED, fixture only | Local dual-store restoration denied; loss/uncertainty and quarantine fail closed |
| All-authority rollback | VERIFIED negative control | Two grants remain possible when every authority is restored; no HTTP in counterexample |

Image repository is docker.io/library/eclipse-temurin, Linux amd64. Platform digest
and local image ID:
`sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf`.
Config digest:
`sha256:5b87e2cf305796b0082d40718341f9b75c2754e9cf81d228262b6902cae4e0b1`.
All 13 retained official metadata receipts rehash correctly. Four bounded official
documentation reads were made; no pull, signature-tool installation, new base image,
profile or host/daemon setting change. Recipe/archive GPG guidance is not OCI proof.
SBOM is retained supporting metadata, not a fresh vulnerability assessment.

The actual-owner fixture observes exactly one send/receipt for a successful attempt
and competing owners, zero sends for spent IDs, invalid HALT and lost acknowledgment.
Witness restart retains retirement. PostgreSQL/coordinator tests independently retain
local rollback denial and all-authority two-grant counterexamples. No hardware-root,
physical power-loss, privileged rollback or multi-host consensus claim is made.

## Sinks, resources and packaging

46 fresh actual-owner/witness container inspections and 36 bounded owner outputs are
retained across the two sandbox runs. Configured Docker logging is json-file; owner
mounts are empty, witness storage is an owned named volume. No fixture external
forwarder is configured. Raw dumps are never copied to host.

Two positive heaps of 24,432,275 and 24,427,055 bytes intentionally contain the fake
canary, confined to tmpfs. Native error logs of 48,239 and 48,283 bytes have no scanned
canary. Forbidden heap/native destinations and attachment produce no scratch artifact.
The diagnostic positive heap is not described as clean. Privileged host access, swap,
physical erase and arbitrary future diagnostic contents remain outside this evidence.

Both diagnostic runs created and verified removal of eleven containers: 22/22 total.
Across raw-Docker diagnostic and actual-owner fixtures, 68 immutable-ID removals were
verified. Admission records contain at most two other workloads before admitting a
third. Testcontainers cleanup is additionally checked by final label/volume absence.
Final owned containers0, owned volumes0; three unrelated stopped containers and two
pre-existing volumes remain unchanged. Nothing unrelated was removed. No manual
cleanup was required. Cooperative locking does not constrain an unrelated Docker
operator; ambiguous creation still requires exact-ID reconciliation before another suite.

Runtime JAR remains byte-identical:
`4d5798ca017e09a916d07f5a397be9912a4b8e9e044049e7e7bd7dc33e8a6305`.
15 classes, jdeps java.base, no Main-Class or test adapters. Actual Linux owner/RSA
executes with --limit-modules java.base. This is not an OS-package or sandbox proof.

The scoped canary scan examined 5,521 files with zero hits. All 206 historical
dumpstreams match; seven new dumpstreams match only the exact four-line Windows
Manifest-JAR diagnostic, for 213 total. No scoped exported heap/native dump was found.
Project verifier and Git whitespace checks exit0. Secret scanning inspected 2,233
text files with zero findings. Final source audit and exact counts are recorded in
the manifest. Raw logs/reports stay ignored; no generated artifact is staged.

## Preservation and replay

G1 raw SHA remains 56a1bd05bbbec36f639450f2f52c491c8c5436c16094f86bcf0c2818d4f63656.
18 registered normalized source hashes, 63 protected research files, 25 K/L files,
11 K envelopes and Phase13.6-13.12 canonical manifests match. The three registered
CRLF/LF differences are reported without rewriting. All 100 original failed and
804 recovery evidence files remain byte-identical. 866/870 tracked baseline files
are unchanged; the other four are intentional test corrections with exact original
source snapshots. Historical plans and approval/failure ledgers remain unchanged.

The new manifest binds source snapshots, current hashes, test XML, raw evidence,
plan and parent fingerprints. Canonical replay is sorted compact ASCII JSON,
UTF-8/no newline, allow_nan=False, excluding its own fingerprint. Two disk replays
and hash verification precede the final report; the envelope holds the non-circular
canonical fingerprint. Evidence consistency is not human approval or live proof.

## Exact changed/new inventory

Modified test files:

- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/ContainmentIT.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/DisposableLedger.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/JavaSandboxIT.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/SandboxDiagnosticsIT.java

New files:

- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/PlatformResources.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/PlatformResourcesTest.java
- docs/operations/phase-13.13-plan.md
- docs/architecture/kite-synthetic-sandbox-platform-security.md
- docs/operations/phase-13.13-runbook.md
- docs/operations/phase-13.13-validation.md
- research/phase-13.13/generation-1/evidence-manifest.json

## Remaining prerequisites and operational denial

Separately authorize a disposable host with measured enforcing LSM and genuine
user-namespace mapping, and establish a publisher-backed OCI signer/root/subject
verification procedure. Independently inventory external collectors. Do not weaken
these requirements or change this host automatically. Future tests must retain the
all-root rollback limitation and resource/failure controls.

Real Kite/account/token/WebSocket/order/trading DB/operational HALT actions by this
phase: zero, an action inventory rather than global telemetry. Unit-test mock control
transitions are not operational HALT changes. V2/governance second review/four reviews
PENDING; implementation under V2 and real access NOT_AUTHORIZED; NO_GO; MIS NOT_READY.
Phase12 BLOCK; H1/H2 FROZEN; July TEST SEALED; INR10,000 unchanged; ZERO_CALL_ABORT
preserved. No stage, commit, push, deployment or automatic next phase.
