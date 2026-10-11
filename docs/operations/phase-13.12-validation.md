# Phase 13.12 validation and safety stop

Result: **BLOCKED**. The initial diagnostic suite exposed a teardown defect and
exceeded the frozen concurrent-workload budget. Further fault tests and regression
suites were stopped. This is not a sandbox security certification or a passing suite.
Only cleanup, preservation and evidence audits followed the stop.

## Baseline and frozen scope

Clean develop; HEAD and local origin/develop both
`a56256c24ee67d8022909e80654b29a1495ed179`. No fetch/pull; remote freshness unknown.
859 tracked files were hashed before work. Plan SHA-256:
`f61f0bd304070df3d9cde99aa892e235ac5a4a43a7c93c076a1d1ecabe1ed6df`.
The original plan is unchanged; no retrospective budget/acceptance amendment.
No existing tracked source or historical document was edited.

## Progress and failure record

1. Git and registered evidence passed: parent canonical envelopes, plans/amendments,
   G1, 18 normalized sources, 63 protected research files, 25 K/L files and 11 K envelopes.
2. Frozen plan created before network/code/fault tests. Cached image identity and
   rootfs/config chain verified. All 13 retained official-source receipts rehashed.
3. Official-domain documentation search found JDK GPG guidance, not a verified
   OCI signature chain. Direct documentation retrieval returned HTTP 403; no retry.
   Registry SBOM retrieval succeeded, SHA matched, 203 SPDX 2.3 packages inventoried.
4. Added two test-only diagnostic files. Initial focused run: 95 unit tests passed;
   12 integration cases, 6 failures, 0 errors, 0 skips. Actual-owner JavaSandboxIT
   passed 6; SandboxDiagnosticsIT failed all 6. Maven exit 1, total 2:51.
5. Teardown asserted docker diff was empty. Docker reported exactly `A /scratch`,
   the tmpfs mountpoint. That assertion ran before removal; subsequent tests retained
   earlier containers. Five HOLD containers overlapped, above the frozen maximum of
   three workload containers. Each had bounded memory/CPU/PIDs and a 55-second JVM
   lifetime, but that does not excuse the concurrency budget breach.
6. The jcmd test also failed its expected AttachNotSupportedException assertion:
   the actual command failed earlier with IOException: Read-only file system while
   creating the attach marker. This is a measured filesystem denial, not proof of
   the claimed Attach-mechanism rejection. No assertion was relaxed.
7. Stopped additional experiments. Captured final container state and removed exactly
   six owned diagnostic containers after checking original ID/image/no mounts.
   Confirmed each absent. No dump was copied out; tmpfs was disposed with containers.
   This does not claim physical secure erasure or protection from a privileged host.
8. Retained initial failed XML/logs and continued only project, secrets, canary,
   packaging/read-only inventory, preservation and deterministic evidence audits.

The diagnostic source remains an explicitly failing, uncommitted candidate for review.
Do not run it again unchanged. A future authorized correction must make cleanup run
in finally even when assertions fail, distinguish the exact empty mountpoint from
file export, verify tmpfs mount identity, and assert the actual attachment-denial
boundary. Preserve these initial failures and the unchanged frozen plan.

## Fresh measured results and limitations

| Assurance | Fresh finding | Status |
|---|---|---|
| Image identity | Platform/local ID 743b453c...37b5cf, Linux amd64, five rootfs layers bound to retained config | PASS |
| Image authenticity | No verified signer/root/subject signature chain; cosign/notation/gpg not found on host command path | NOT_VERIFIED |
| LSM | Java label kernel, AppArmor enabled=N, LSM list and SELinux enforce unavailable; daemon lists seccomp/cgroupns | UNAVAILABLE |
| User namespaces | uid_map and gid_map both 0 to 0, range 4294967295 | NOT_VERIFIED; identity mapping |
| Actual Java owner | Six unchanged sandbox cases passed: one send/receipt, replay/restart denied, race one winner, lost ACK zero sends, invalid HALT denied, uncaught exception redacted | PASS in tested scope |
| JVM diagnostic suite | Six failures, including teardown/resource breach and wrong attach-denial expectation | FAIL |
| Heap positive observation | 24,429,204-byte tmpfs heap contains canary; scanned in container, metadata only exported | Observed, not a suite pass |
| Heap forbidden destination | Read-only root denied, zero scratch artifacts | Observed, not a suite pass |
| Native-crash observation | SIGSEGV in disposable JVM; 46,672-byte scratch hs_err.log with no scanned canary | Observed, not a suite pass |
| Native forbidden destination | No scratch artifact; root remained read-only, bounded fallback output captured | Observed, not a suite pass |
| JFR | Absent from java.base-only diagnostic module layer | PASS for absence only; general JFR containment NOT_TESTED |
| Surviving-root rollback | Historical passing regression retained unchanged; not rerun after safety stop | NOT_TESTED this phase |
| All-authority rollback | Historical no-HTTP two-grant negative control retained unchanged | KNOWN_LIMITATION, not newly executed |

Base repository docker.io/library/eclipse-temurin; platform/local image ID:
`sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf`.
Index `sha256:b468c3fc688b14450571494f588bd939378e7fd542ed5a73f8efc13f17872a87`;
config `sha256:5b87e2cf305796b0082d40718341f9b75c2754e9cf81d228262b6902cae4e0b1`.
Java observed 21.0.12.1+1-LTS. No image pull, host installation or global policy change.
Acquisition time remains the prior receipt; current inspection is not a new acquisition.
SBOM hash `9be2f81a4df1ee28bbab5bd3699ac01926ee397989e0258c15980db097425b62`.
Unsigned metadata and JDK archive signatures do not authenticate the delivered image.

Effective actual-owner restrictions were rerun by JavaSandboxIT: UID/GID65534,
CapEff0, NoNewPrivs1, Seccomp2, root-write denial, process EPERM, TCP ENETUNREACH,
invalid-name DNS denial, no route, memory256MiB, CPU1, PIDs64, no owner mounts.
These do not establish an enforcing LSM, UID remapping or production OS certification.

## Commands and exact test scope

Fresh command:
`.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=SandboxDiagnosticsIT,JavaSandboxIT' verify`.
Host Oracle JDK21.0.12, Maven wrapper3.9.11, Docker29.8.0, Linux backend
6.18.33.2-microsoft-standard-WSL2. POM sets test JVM UTC.
Selected XML was copied before any later test run; no corrective test rerun occurred.
95 unit, 12 integration (6 failed), not 135 passing integration tests.

Focused ledger/root recovery, complete standalone integration and root Java suite:
NOT_RUN after safety stop. Python/Ruff/mypy and broad root integration: NOT_RUN;
no corresponding source change. Prior 95/129/1508 counts are historical only.
The unchanged runtime JAR is inspected separately for class inventory and java.base
closure; no diagnostic class, launcher or test adapter is included in it.

Project/secrets/diff, preservation, canary counts, diagnostic/command/XML hashes and
two canonical replay results are recorded in the generation-1 manifest. Raw logs,
failed XML and before inventories remain ignored. Existing dumpstreams are preserved
and classified narrowly; none is deleted to manufacture a clean result. The direct
HTTP403 and an initial Windows rg wildcard-path error are retained as inspection
failures, distinct from the six JUnit failures; rg was corrected to a directory glob.

## Exact additive Git inventory

- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/DiagnosticsProbe.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/SandboxDiagnosticsIT.java
- docs/operations/phase-13.12-plan.md
- docs/operations/phase-13.12-runbook.md
- docs/operations/phase-13.12-validation.md
- docs/architecture/kite-synthetic-owner-sandbox-security-closure.md
- research/phase-13.12/generation-1/evidence-manifest.json

No tracked modifications or staging; no binaries, dumps, logs or credentials in the
change set. Commit/push NONE. Test fixture correction and a new bounded rerun require
human review of the failure and resource boundary first.

## Retained safety status

Real Kite calls/tokens/account reads/WebSockets/orders/trading DB mutations and
operational HALT resume/arm/execute by this phase: 0. This is an action inventory,
not global telemetry. V2/governance second review/four reviews PENDING; real access
NOT_AUTHORIZED; operational NO_GO; MIS NOT_READY. Phase12 BLOCK; H1/H2 FROZEN;
July TEST SEALED; INR10,000 ceiling unchanged; ZERO_CALL_ABORT preserved.
Physical power-loss durability, privileged all-root rollback, multi-host consensus,
live authentication/HALT/external sinks remain unproven. No automatic next phase.
