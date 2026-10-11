# Phase 13.12 recovery validation

Result: **SYNTHETIC_SANDBOX_SECURITY_PARTIAL**. The narrowly authorized fixture
recovery passes; the independent security-closure gaps below are not waived.

This is the additive recovery record. The original Phase 13.12 validation and
generation-1 manifest retain the mandatory safety stop and initial failures. No
historical result is rewritten as a pass. Final measured totals and canonical
fingerprint are bound in generation 2; synthetic progress grants no operational approval.

## Baseline and incident review

Branch develop; HEAD and local origin/develop both
`a56256c24ee67d8022909e80654b29a1495ed179`. No fetch/pull or remote-freshness claim.
Exactly seven expected untracked files were present; no tracked changes. Their raw
hashes, generation-1 canonical fingerprint and 100 referenced raw evidence files
matched. Docker confirmed the six original owned diagnostic containers were absent.
The original 859 tracked files, protected research and governance records are preserved.

The original incident was six diagnostic failures. A teardown assertion rejected
`A /scratch` before removal, leaving five bounded HOLD containers overlapping against
the three-workload maximum. That remains a resource-budget violation. The jcmd
assertion also expected a different exception from the measured filesystem denial.

User authorization explicitly permits this bounded recovery. Frozen plan SHA:
`f61f0bd304070df3d9cde99aa892e235ac5a4a43a7c93c076a1d1ecabe1ed6df`.
Amendment 1: `d5920d5e0f7aa2102689a8272e2b0c9af562f7c11e8c3b6b0b84504e5ba692de`.
Amendment 2: `bc0a9ce947440d0c7d9aa7db6eeffd5ffa20d6945a9fa3e367e4468822371ccf`.

## Narrow fixture corrections

Only SandboxDiagnosticsIT.java changes relative to the seven-file recovery baseline.
Cleanup processes every owned fixture, executes removal in finally, aggregates audit
failures and requires a successful absence listing. Uncertain ownership/cleanup
quarantines new fixture creation. Per-run names and labels restrict removal scope.

A cross-JVM file lock serializes diagnostic coordinators. Reserved slots and current
Docker workload counts are checked before create/start; three is the maximum. Other
workloads consume the budget without being removed. Only the pinned Ryuk cleanup
image is excluded. This cannot control an unrelated actor starting a container after
inspection and is not a production resource scheduler.

[Docker diff](https://docs.docker.com/reference/cli/docker/container/diff/) includes
added directories; [tmpfs writes](https://docs.docker.com/engine/storage/tmpfs/) occur
outside the writable layer. The corrected check accepts only empty diff or the exact
mountpoint entry `A /scratch`, with the exact tmpfs configuration, read-only root and
no host mounts verified. Additional paths, child files and changed/deleted entries
fail. This is not a generic diff exclusion or evidence that every dump is clean.

The jcmd check requires exit1 and the exact pinned-JDK IOException/attach-marker
stack. Successful attach, timeout, incomplete stack or extra/unrelated errors fail.
It proves filesystem denial at that boundary; it does not independently prove the
DisableAttachMechanism flag caused the denial. Canary checks are retained, including
raw-byte scanning of command logs. No production runtime or diagnostic probe changed.

## Recovery failure retained

First recovery command selected only SandboxDiagnosticsIT. 95 unit tests passed;
Failsafe recorded ten failures (nine tests plus after-all), zero errors/skips.
Windows native argument handling removed quotes in a Go-template ownership-label
lookup. Docker returned exit64. The new quarantine denied all later fixture creation:
one stopped container remained, with no new overlap. Its exact ID/label was inspected
against the retained creation record and it was removed by ID; a successful listing
confirmed absence. Source, XML and logs remain retained.

Amendment 2 replaced the quoted lookup with exact Docker label/name filters. No
acceptance or resource threshold changed. The fresh focused rerun passed 95 unit and
nine diagnostic cases, zero failures/errors/skips. It created eleven containers and
verified removal of all eleven. Peak workload count was three; fourth creation was
denied before Docker create. Two-container injected-assertion cleanup passed and the
injected failure was propagated, not suppressed. Final owned inventory was empty.

Only after that gate was independently checked were actual-owner, ledger/recovery
and complete regression runs permitted. Each run is counted separately, not summed
as unique tests. Selected XML is archived before later Maven runs overwrite reports.

| Fresh recovery run | Unit | Integration | Failures/errors/skips | Exit / duration |
|---|---:|---:|---|---|
| Initial focused recovery | 95 | 10 reported, including after-all | IT 10/0/0 | 1; 1:13; retained |
| Focused correction | 95 | 9 | 0/0/0 | 0; 1:44 |
| Actual owner + fenced/rollback/root recovery | 95 | 55 | 0/0/0 | 0; 4:11 |
| Complete standalone | 95 | 138 | 0/0/0 | 0; 7:11 |
| Existing root Java unit/architecture | 1,508 | NOT_RUN | 0/0/0 | 0; 1:20 |

The full standalone run independently repeated all nine diagnostic cases: eleven
containers created, eleven removals verified, maximum three workload containers,
fourth denied before creation, final owned set empty. Across the two passing
diagnostic runs: 22/22 automatic removals. The first recovery failure's one stopped
container was separately reconciled and removed; that manual cleanup is not called
automatic cleanup success. None of the six original incident containers reappeared.

All mandatory test runs for the bounded recovery have completed successfully after
the correction. This supersedes the execution stop for this user-authorized recovery
only, not any operational restriction. Root broad integration and Python/Ruff/mypy
were not run: no shared production or Python source changed. Runtime packaging is
unchanged: 15 classes, java.base from jdeps, no Main-Class or test adapters, JAR SHA
`4d5798ca017e09a916d07f5a397be9912a4b8e9e044049e7e7bd7dc33e8a6305`.
The actual Linux owner/RSA path was exercised by the unchanged sandbox tests.

## Reproduction and evidence

Offline JDK21/Maven wrapper, test JVM UTC, unchanged disposable PostgreSQL fixtures:

```powershell
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=SandboxDiagnosticsIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=JavaSandboxIT,FenceLedgerIT,IsolationLedgerIT,FenceOwnerIT,RootRecoveryIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration verify
.\mvnw.cmd -o test
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
git status --short --untracked-files=all
```

Fresh diagnostic records use unique target/phase1312-recovery directories. Original
target/phase1312 and tmp/phase1312 records remain intact. No dump payload is exported:
the positive heap deliberately contains the canary, is scanned within bounded tmpfs,
and emits only metadata. Container removal disposes that mount. Docker notes that
tmpfs can be swapped; physical secure erasure and privileged-host confidentiality
are not established. Native crash and changed-sink negatives remain bounded fixtures.

Final audits: project verifier and secrets scanner exit0; zero secret findings.
The scoped canary audit checked 3,564 files with zero hits and no exported host heap
or native dump. All 197 prior dumpstreams were preserved; nine new files match only
the known four-line Windows Manifest-JAR diagnostic. None was removed. The one-off
focused-gate audit emitted a Python invalid-escape SyntaxWarning; its assertions
passed and the later audit uses a raw regex. This was not a Java test failure.

G1 raw hash, all 18 registered normalized sources, 63 protected research files,
25 K/L files, 11 K envelopes and parent manifests remain unchanged. All 859 tracked
baseline files match. The six initial untracked artifacts other than the diagnostic
fixture, including the frozen plan, remain byte-identical. Source
snapshots preserve the original fixture digest in generation 2. No prior approval,
failure report or evidence generation is overwritten. Canonical replay follows the
registered sorted compact ASCII JSON payload convention; both disk replays must
match the generation-2 envelope fingerprint and its input hashes.

Generation 2 links generation 1 and preserves exact UTF-8 source snapshots for both
the original failing fixture and first recovery failure, with hashes. Canonical
replay checks those snapshots, current source inventory and immutable parents rather
than pretending an intentionally corrected current source still has its old digest.

## Exact Git inventory

All eleven paths below are untracked relative to the committed baseline. No tracked
file is modified or staged. Seven existed at recovery entry; only
SandboxDiagnosticsIT.java was corrected. Four paths are additive recovery artifacts.

- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/DiagnosticsProbe.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/SandboxDiagnosticsIT.java
- docs/architecture/kite-synthetic-owner-sandbox-security-closure.md
- docs/operations/phase-13.12-plan.md
- docs/operations/phase-13.12-runbook.md
- docs/operations/phase-13.12-validation.md
- research/phase-13.12/generation-1/evidence-manifest.json
- docs/operations/phase-13.12-plan-amendment-1.md
- docs/operations/phase-13.12-plan-amendment-2.md
- docs/operations/phase-13.12-recovery-validation.md
- research/phase-13.12/generation-2/evidence-manifest.json

Ignored logs, XML, dumps' metadata, compiled output and local test images are not
staged or included as raw binaries. Next work requires separate scope for the open
provenance/LSM/user-namespace/production-containment evidence. No operational transition.

## Assurance limits and retained policy

Even successful recovery cannot produce full security closure: OCI signature
authentication, enforcing AppArmor/SELinux, remapped user namespaces and production
containment remain unverified. The original pinned Linux image is reused; no pull,
host installation, host security-policy change or real credential source is added.
All-authority rollback remains a permanent negative control. Physical power-loss
durability, privileged rollback, multi-host consensus and live external sinks remain
unproven. An at-most-once attempted dispatch is not exactly-once delivery.

V2, governance second review and four technical reviews remain PENDING; real access
NOT_AUTHORIZED; operational NO_GO; MIS NOT_READY. Phase12 BLOCK, H1/H2 FROZEN,
July TEST SEALED, INR10,000 ceiling unchanged and ZERO_CALL_ABORT preserved.
Real Kite/token/account/order/WebSocket/trading DB/operational HALT actions performed
by this recovery: zero; this is not a global telemetry claim. No stage/commit/push.
