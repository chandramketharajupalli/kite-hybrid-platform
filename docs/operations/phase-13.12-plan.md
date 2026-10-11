# Phase 13.12 frozen plan v1

Baseline: clean develop; HEAD and local origin/develop
`a56256c24ee67d8022909e80654b29a1495ed179`. No fetch/pull; remote freshness unknown.
Before-work raw inventory: 859 tracked files. Parent Phase 13.11 canonical fingerprint
`90e95db0ef6d9318f4cd86478bfec4f740f7196e715b854a6a3c4667e30e2dc0`.
Registered parent plans, 18 text-normalized frozen sources, 63 raw protected research
files, 162 protected historical files, 25 K/L files and 11 K envelopes verified.
The source-defined sorted compact ASCII JSON convention applies to envelopes;
raw SHA-256 applies to plans and exact-byte inventories. Preserve CRLF distinctions.

## Scope and immutable criteria

Add only DiagnosticsProbe.java and SandboxDiagnosticsIT.java under the standalone
synthetic module's test package, this plan, numbered additive amendments, Phase 13.12
runbook/validation, architecture/kite-synthetic-owner-sandbox-security-closure.md,
and research/phase-13.12/generation-1 evidence. No existing tracked file edits.
Ignored tmp/phase1312 and module target/phase1312 retain bounded logs and measurements.
No production changes without a reproduced defect and a separately hashed amendment.
No generic approval engine, launcher, real credential source or broker transport.

Every mandatory control is classified PASS, FAIL, UNAVAILABLE, NOT_TESTED or
NOT_APPLICABLE with evidence. Mandatory image authenticity, LSM, user remapping,
diagnostic containment, actual Java-owner isolation and surviving-root recovery
must all pass for VALIDATED. Unavailable controls produce PARTIAL, never a waiver.
Integrity/safety gate failure stops work. All-authority rollback remains an explicitly
excluded, permanently retained negative control, not a universal guarantee.

## Image identity and bounded public provenance retrieval

Use only cached docker.io/library/eclipse-temurin Linux amd64 platform digest
`sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf`.
No pull or new base image authorized by this plan; missing cache stops execution.
Verify local image identity, platform/config/layers against retained authenticated-by-
digest metadata. Recheck retained official source receipts against parent hashes.
Digest equality and unsigned SLSA metadata establish identity/linkage, not signatures.

Permit at most 12 HTTPS metadata requests, 30 seconds each, no retry, 8 MiB per
response and 32 MiB total, only official Adoptium/Eclipse documentation, Docker
registry/auth, docker-library/official-images and adoptium GitHub sources. Registry
bearer authorization remains in memory, never logged. No unrelated destinations,
Kite, unofficial mirrors, host installs, trust-key invention or networked builds.
Inspect available signature tools and authoritative publisher verification guidance.
If no independently trusted signer/root and digest-bound signature chain can be
verified, record IMAGE_SIGNATURE_NOT_VERIFIED. Cached unsigned metadata may support
linkage but cannot satisfy authenticity. Optional official linked SBOM is bounded
metadata; packages are inventoried without a vulnerability-free claim.

## Disposable enforcement and diagnostic experiment

Reuse unchanged Phase 13.11 seccomp and actual-owner tests. New diagnostic test image
uses the exact base, COPY-only build, --pull=false and --network=none, no RUN steps.
Diagnostics are test fixtures, separate from the runtime JAR. Run UID/GID 65534,
cap-drop ALL, no-new-privileges, read-only root, private PID/IPC, network none,
one CPU, <=256 MiB memory, <=64 PIDs and <=48 MiB tmpfs. No host binds/socket/secrets.
Inspect effective security options, /proc status, uid_map/gid_map and LSM interfaces.
Do not load a host policy or change daemon settings. If no supported enforcing LSM
or remapping is available, measure the gap and do not invent a negative LSM pass.

Diagnostic canaries are internally derived SYNTHETIC-only values, never argv/env or
printed. Heap <=24 MiB; bounded OOM allocation <=40 MiB. Heap dump positive goes only
to container tmpfs; scan in that same running container for canary presence, emit
only size/hash/boolean. Negative dump destination is read-only root. Never copy a
dump to host or mount. Removal of owned containers disposes tmpfs; no physical secure-
erase claim. Inspect stopped container diff and logs for exported artifacts.

Native crash is permitted only in the disposable test JVM using a narrowly scoped
test-only java.base Unsafe reflection probe with explicit --add-opens. Do not add
that flag to owner execution. Disable core dumps and bound ErrorFile to scratch;
also test denied root destination. No host signal, host OOM, disk exhaustion or
kernel crash. Keep error-report output, scan for canaries, and record fallback paths.
Attachment is disabled for a waiting diagnostic fixture; invoke image jcmd via Docker
exec and require explicit denial (not timeout alone). JFR absence in the restricted
java.base owner is distinct from a general JVM prohibition. Unsafe reflective crash
and JDK tools are separate diagnostic tests, never runtime artifact dependencies.
Uncaught exception redaction remains covered by actual-owner regression.

## Test sequence and resource budget

1. Baseline, preservation and plan fingerprint before retrieval/code/fault tests.
2. Image identity, provenance assessment, available tool inventory and package metadata.
3. Focused SandboxDiagnosticsIT and unchanged JavaSandboxIT: effective controls,
   bounded dumps/errors, attachment, actual owner one-use success and negative probes.
4. FenceLedgerIT, IsolationLedgerIT, FenceOwnerIT and RootRecoveryIT: surviving-root
   rollback denial, all-authority rollback two grants/no HTTP, crashes and quarantine.
5. Complete standalone unit/integration suite, then root Java unit/architecture.
6. JAR inventory, jdeps, module-limited RSA/owner, project verifier, secrets/canary
   audit, diff, all baseline hashes, two disk-based canonical manifest replays.

Use JDK21 and offline Maven wrapper, test JVM UTC, disposable PostgreSQL only.
Total <=120 minutes; commands <=45 seconds except Maven suites <=15 minutes;
<=3 workload containers plus one Testcontainers cleanup container. Diagnostic
JVM/container lifetime <=60 seconds, allocation/file count bounded. Two corrective
reruns per failed gate maximum; preserve original XML/logs and explain corrections.
No unrelated Python code or suites; no broad root integration absent shared changes.
No assertion weakening to force green. Observe explicit failures, not timeout-only
inferences. Scan current/prior dumpstreams; retain known benign diagnostics precisely.

## Evidence, limits and stop rules

Manifest includes parent/input hashes, exact new paths, image identity/provenance,
effective LSM/namespace findings, diagnostic artifact hashes/counts without payloads,
commands/XML counts/failures, canary audit and limitations. Canonical SHA-256 over
sorted compact ASCII JSON payload, UTF-8 without newline, allow_nan=False; exclude
self-digest and compare two disk replays. Do not edit old generations or raw logs.

Stop on protected modifications, real secret requirement, unexpected external runtime
egress, unsafe privileges, unverifiable consumption, or work outside bounded scope.
No real Kite/account/token/WebSocket/order/trading DB/HALT operations. No operational
startup/deployment, .env/config/risk/migration edits, stage/commit/push. Preserve
INR10,000 ceiling, Phase12 BLOCK, H1/H2 FROZEN, July TEST SEALED, ZERO_CALL_ABORT.
V2, governance second review and four reviews PENDING; real access NOT_AUTHORIZED;
operational NO_GO, MIS NOT_READY. Physical power loss, privileged all-root rollback,
multi-host consensus, live provenance/HALT/external sinks remain unproven.
