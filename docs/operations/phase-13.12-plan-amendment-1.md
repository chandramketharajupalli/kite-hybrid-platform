# Phase 13.12 amendment 1: authorized recovery of diagnostic fixtures

Parent frozen plan SHA-256:
`f61f0bd304070df3d9cde99aa892e235ac5a4a43a7c93c076a1d1ecabe1ed6df`.
Generation-1 canonical fingerprint:
`fb37b6ed592d81f73dbddfefb966cb61deb8a676efdd0f4695aadc2b3a943c92`.
Baseline remains develop / a56256c24ee67d8022909e80654b29a1495ed179;
local origin/develop agrees, no fetch. Seven expected untracked files inspected and
matched generation-1 hashes. All six previous owned containers are absent. No
unrelated work or containers may be removed. User explicitly authorized this recovery.

The initial six diagnostic failures and five-HOLD-container overlap remain historical
failures, not retroactively accepted behavior. Preserve the original plan, reports,
generation-1 manifest and all referenced raw evidence. Before correcting the fixture,
retain its original source in ignored recovery evidence with its generation-1 digest.

## Bounded corrections

Change only the existing untracked SandboxDiagnosticsIT.java. DiagnosticsProbe,
runtime, previous JavaSandboxIT and security policy remain unchanged. Add this
amendment, phase-13.12-recovery-validation.md, and generation-2 evidence. New runs use
unique target/phase1312-recovery subdirectories; never overwrite failed raw evidence.

Cleanup must attempt every owned container even when audit/assertions fail, remove
each in a finally block, verify absence with a successful Docker listing, aggregate
failures and deny new fixtures if cleanup is uncertain. Test injected assertion
failure with two actual owned containers; both must disappear and the failure must
remain visible. Do not infer absence from a failed daemon/inspect command alone.

Before create and start, enforce <=3 workload containers. Reserve known owned slots,
serialize this fixture across JVMs using an OS file lock, inspect running containers
conservatively (unrelated workloads consume budget but are never removed), and reject
a fourth fixture before creation. Only the standard Testcontainers Ryuk cleanup
image may be excluded from workload accounting. Verify three permitted concurrent
fixtures and fourth denied, capture bounded event records and final absence. This
does not control an unrelated actor launching containers after inspection.

Docker documents `A` as an added file or directory, and tmpfs writes as outside the
writable layer. The observed `A /scratch` is a mountpoint, not sufficient evidence of
a dump export. Accept only empty diff or exactly that one entry after checking the
exact tmpfs destination/options, read-only root, no host binds and a pre-start diff
without other entries. Reject children under /scratch, changed/deleted entries,
additional paths or absent/altered tmpfs configuration. Retain dump/canary checks.
Sources reviewed at the user's request:
https://docs.docker.com/reference/cli/docker/container/diff/
https://docs.docker.com/engine/storage/tmpfs/
These two official documentation reads add to the allowed source scope; no image
acquisition, host changes or runtime external egress. tmpfs can use host swap;
container removal is not physical secure erasure or privileged-host confidentiality.

Match the measured jcmd denial narrowly: nonzero exit and the expected IOException
Read-only file system stack at attach-marker creation, with only the known stack
frames for this pinned JDK. Unknown errors, timeout, extra output and successful
attachment fail. This establishes filesystem denial, not independent proof of
DisableAttachMechanism. Add negative parser cases rather than suppress stderr.

## Recovery gate and continuation

First run ONLY SandboxDiagnosticsIT through offline Maven verify (its unchanged unit
prerequisites also execute). Include injected cleanup failure, budget and parser/diff
negative tests. No broader suites until exit0, selected XML, cleanup absence and
max-three event records agree. At most two corrective reruns, preserving failures.
Then run actual-owner and fenced/rollback/root-recovery focused suites, full standalone
unit/integration and root Java unit/architecture. No shared production changes;
root broad integration/Python suites remain unnecessary.

Keep original memory/CPU/PID/scratch/process/time bounds and <=3 workload plus one
cleanup container. Maximum recovery work 120 minutes. Stop again on resource breach,
uncertain cleanup, protected modification, real credential need or unsafe execution.
Project/secrets/canary/diff audits and two deterministic generation-2 replays follow.
Never overwrite generation 1; generation 2 links parent digests and before/after source.
All controls not independently verified remain gaps: OCI image signatures, LSM,
user remapping, privileged all-root rollback, physical power loss and production
containment. At best SYNTHETIC_SANDBOX_SECURITY_PARTIAL on this evidence.
No real Kite/token/order/trading DB/HALT actions, stage/commit/push or live permission
changes. Governance/V2/technical reviews stay PENDING and operational NO_GO.
