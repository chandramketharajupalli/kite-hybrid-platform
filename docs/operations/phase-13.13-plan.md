# Phase 13.13 frozen plan v1

Baseline: clean develop, HEAD and local origin/develop
`d20ba52958111c2837ea4419a283798e7ee9ffed`. No fetch; remote freshness unknown.
The before-work inventory records raw hashes of 870 tracked files. G1, 18 registered
text-normalized sources, 63 protected research artifacts, 25 K/L files, 11 K
envelopes, 162 historical protected files and Phase 13.6-13.12 canonical manifests
match. Phase 13.12 recovery's 100 prior and 804 recovery evidence files also match.
Keep all historical plans, failures, governance and generations byte-identical.

## Scope and acceptance

This phase assesses mandatory image authenticity, effective LSM, user namespaces,
actual Java-owner containment, diagnostic sinks, resource cleanup and surviving-root
recovery. Each receives VERIFIED, NOT_VERIFIED, UNAVAILABLE, FAILED or NOT_RUN.
Every mandatory control and fresh regression must pass for VALIDATED. Missing
signature/LSM/remapping/external-sink proof yields PARTIAL; a safety or focused-test
failure yields BLOCKED and stops broader tests. No criterion becomes optional.
All-authority rollback remains a permanent negative control, not a claimed guarantee.

Allowed additions: this plan and numbered amendments; phase-13.13 runbook/validation;
architecture/kite-synthetic-sandbox-platform-security.md; research/phase-13.13/
generation-1 evidence. Ignored tmp/phase1313 and unique target subdirectories retain
raw logs, reports and before/source snapshots. No generated binaries/dumps staged.

Source review found test-fixture resource gaps: JavaSandboxIT lacks pre-create budget
checks and ownership labels; SandboxDiagnosticsIT checks labels but removes by name.
Permit narrowly scoped test-only resource guard and regression tests, plus changes
to JavaSandboxIT, SandboxDiagnosticsIT, DisposableLedger and ContainmentIT to enforce
pre-creation budgets, exact ownership, immutable container-ID cleanup and finally
cleanup. Preserve exact original source snapshots in evidence. No runtime, POM,
seccomp profile, trading source, configuration, migration or historical document edits.

## Environment, image and provenance

Use JDK21, offline Maven wrapper and the existing cached Linux amd64 image only:
docker.io/library/eclipse-temurin@sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf.
Rehash retained official index/platform/config/source/SLSA/SBOM receipts and compare
local image ID, layers and platform. Digest identity is not authenticated provenance.
No image pull, host software installation, Docker/WSL configuration, userns setup,
new security profile or host policy change. COPY-only test-image builds may reuse
the cached base with --pull=false --network=none. No implicit acquisition fallback.

Allow at most six public documentation requests, no retries, to official Adoptium,
Eclipse or Docker documentation only; no artifact retrieval. Establish a trusted OCI
signer/root/subject procedure only from attributable publisher evidence. If absent,
record IMAGE_SIGNATURE_NOT_VERIFIED. Unsigned SLSA, SBOM and archive GPG guidance
cannot establish an OCI signature. No vulnerability-free claim. No Git fetch.

## Resource and failure boundaries

Maximum three workload containers, plus the existing pinned Ryuk cleanup container.
Count existing workloads before creation/start; reserve concurrent owner slots and
serialize cooperative coordinators. Label owned resources per run; inspect immutable
IDs before removal. Never remove unrelated resources. Cleanup must execute after
assertion/startup failure and verify absence; uncertain cleanup denies further creates.
Retain fourth-workload denial and injected-assertion cleanup regressions. This is a
cooperative single-host test budget, not a scheduler controlling unrelated actors.
Record containers, volumes and ownership before/after every suite. Any overlap,
leak or ambiguous ownership stops broader tests; only verified exact-ID cleanup follows.

Keep existing non-root, cap-drop, no-new-privileges, seccomp, read-only root, private
namespaces, bounded tmpfs, CPU/memory/PID limits and network-none controls. Observe
Java /proc state, uid/gid maps and LSM interfaces; no shell-only substitution for
owner execution. Unsupported enforcing LSM or remapping remains a mandatory gap.
Numeric loopback peers only; documentation-address/invalid-DNS denial probes remain
inside an inspected network-none namespace, never against a real external service.

Diagnostics retain synthetic canaries, <=24 MiB heap, <=48 MiB tmpfs, <=256 MiB
container memory, <=64 PIDs, one CPU and bounded lifetime. Heap canary presence is
expected in private tmpfs; never export a dump. Keep exact attach-denial and scratch
diff assertions, native-fault confinement and changed-sink negatives. Inventory
Docker logging driver, stdout/stderr, command logs, test XML/dumpstreams, mounts and
diagnostic destinations. Unknown external collection remains NOT_VERIFIED. No
physical secure-erasure claim for tmpfs; no raw canary/private key in evidence.

## Execution gates and reproducibility

1. Baseline/preservation, frozen plan hash, static fixture review, cached image and
   bounded provenance/capability inspection.
2. Focused resource/security/diagnostic tests; inspect cleanup and budget before
   permitting any broader suite. Mandatory focus failure stops this execution.
3. Actual Java-owner, fenced ledger/local rollback and root recovery integration.
4. Complete standalone unit/integration; then root Java unit/architecture.
5. JAR inventory/jdeps and actual java.base-limited owner/RSA evidence; project
   verifier, secrets/canary scan, diff/staging audit, preservation and replay twice.

Use existing test clocks/UTC and disposable PostgreSQL only. Total <=120 minutes;
Maven suite <=15 minutes; Docker command <=45 seconds; no retry to erase a failure.
Archive selected XML before reruns overwrite reports. Retain every failed attempt
and diagnostic. No Python/full root integration suites absent corresponding changes.

Canonical envelopes use SHA-256 of sorted compact ASCII JSON, UTF-8/no newline,
allow_nan=False, excluding their own fingerprint. Raw-byte hashes protect exact
files; only the 18 registered sources use the established universal-newline text
method. Replay from disk twice, check input hashes and compare fingerprints. Store
plan hash externally to avoid self-reference; amendments are additive and separately
hashed. Historical source claims replay against retained snapshots when a fixture
is intentionally corrected, not against a fabricated unchanged current hash.

## Permanent exclusions and stop conditions

Stop on baseline/protected mismatch, unsafe overlap, cleanup uncertainty, mandatory
focused failure, unauthorized acquisition, real credential need or external runtime
egress. No real Kite/token/account/WebSocket/order/trading DB/HALT operations, normal
application startup, deployment, .env/risk edits, staging, commit or push.
Preserve INR10,000 ceiling, Phase12 BLOCK, H1/H2 FROZEN, July TEST SEALED and
ZERO_CALL_ABORT. V2/governance second review/four reviews remain PENDING; real access
and implementation under V2 NOT_AUTHORIZED; operational NO_GO and MIS NOT_READY.
Physical power loss, privileged complete-root rollback, multi-host failover, live
authentication/HALT/logging and production isolation remain unproven. Stop after report.
