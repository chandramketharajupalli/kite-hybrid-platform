# Phase 13.14 validation

Result: **PLATFORM_ASSESSMENT_COMPLETE_GAPS_OPEN**. This is an assessment, not
certification. No runtime/test code or existing document was modified. Current
metadata inspection and offline consistency checks do not reproduce historical
Java-process enforcement or close OCI authenticity, LSM, remapping and external sinks.

## Baseline, plan and progress

Initial develop tree clean; HEAD and local origin/develop both
`19c91847a37bdc85020642e037e349caa7c4dd20`. No fetch/pull; remote freshness unknown.
877 tracked raw hashes captured before work. Frozen plan SHA-256:
`afde0729be5e3a5110e2e37518f88f05ea59c01a3de1a3a6d91c8a5dd10e0e48`.
No amendment, acquisition, host configuration change or acceptance relaxation.

1. Verified prior manifests, registered plans, G1/18/63, K/L and prior source snapshots.
2. Froze plan before host/image observations and eight official documentation opens.
3. Collected selected Windows/Docker/image/resource metadata; reviewed retained owner,
   diagnostics, rollback, initial safety-stop and recovery evidence.
4. Prepared host acceptance criteria, supply-chain verdict, sink inventory and matrix.
5. Ran project/secrets checks, preservation and current resource comparison. Generated
   canonical evidence and replayed twice with input checks; final results in envelope.

One observation failed: the planned path
`C:\Program Files\Docker\Docker\Docker Desktop.exe` was absent (Get-Item PathNotFound).
Desktop application version remains NOT_VERIFIED. PowerShell continued to successful
Docker commands; aggregate exit0 does not erase that failed lookup. No retry/search,
host installation or policy change. No Java fixture failed because none ran.

The first offline replay failed on registered_parent_plans for Phase13.13: the new
manifest generator nested that parent's path-to-hash map where a scalar hash was
required. Read-only comparison confirmed the source and before-work raw hash both
equal 59b74cd376d61531ff06f4b5b6446c335887c5a899e938aaf53860b24c3cce76.
Only the new manifest mapping was corrected; no protected source mismatch or plan
change occurred. The failed replay is retained in failure_history, followed by two
fresh complete replays. PowerShell's later successful Git commands did not make the
initial Python assertion a pass.

## Fresh observations versus historical tests

Observation time 2026-10-11T15:51:01.5295337Z is a local clock reading, not an owner
decision or independently authenticated timestamp. Windows11/build26200; Docker
client/server29.8.0/Linux amd64, kernel6.18.33.2-microsoft-standard-WSL2; cgroupfs/v2;
SecurityOptions seccomp builtin/cgroupns; json-file driver. No advertised rootless,
userns or LSM option proves either availability or absence of all possible host support.

Cached platform/local ID is
`sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf`.
Five rootfs layers agree with the cached config; platform/index/config and all 13
official metadata receipts rehash. Retained SBOM rehashes. No OCI signer/root/subject
chain verified: OCI_SIGNATURE_NOT_VERIFIED. No cosign/notation/gpg found on PATH.

AppArmor/SELinux remains UNAVAILABLE_ON_TESTED_BACKEND from prior actual-Java evidence;
user namespace remapping NOT_VERIFIED with historical identity maps. Current process
enforcement NOT_TESTED. Eight bounded official documentation opens succeeded; no
image/artifact pull or tools installed. Documents specify future tests, not host changes.

Fresh Java unit/architecture, standalone/integration, Python unit/Ruff/mypy: NOT_RUN;
documentation/data-only scope. Historical Phase13.13 full results remain 97 standalone
unit/security, 138 integration and 1508 root Java tests, zero failures/errors/skips.
Those counts are not Phase13.14 execution. Historical initial Phase13.12 six failures,
resource overlap and first-recovery ten reported failures remain preserved.

Project verifier exits0. Initial secrets scan: 2241 text files, zero findings; final
scan after all eight artifacts is recorded separately in the manifest/check log.
Git diff/check and explicit untracked-text whitespace checks cover the new documents.
Scoped canary audit checks all eight new artifacts using existing synthetic canary
derivations, including raw/hex/base64 forms; no values printed. No diagnostic outputs
or dumps were created. This scan is not external-sink certification.

## Resource and protection results

No containers created, started, exec'ed, stopped or removed; no volumes/images changed.
Before/after inventories match: three unrelated stopped containers, two unrelated
volumes, zero active/owned workloads and zero owned leftovers. No cleanup required.
Three-workload ceiling and exact-ID/finally controls remain unchanged in test source.
No new resource-budget test is claimed; the historical fourth-container denial is retained.

All 877 tracked baseline files remain byte-identical. G1 raw SHA:
`56a1bd05bbbec36f639450f2f52c491c8c5436c16094f86bcf0c2818d4f63656`.
18 registered universal-newline source hashes and 63 raw protected research files pass;
raw CRLF differences for candidates.py, diagnostics.py and opening_features.py are
recorded with both hashes. 25 K/L files and 11 canonical envelopes pass, including
Generations1-4. V2 and amendment unchanged. Phase12 gate denies acquisition/evaluation
for all five members; G1 test_state is SEALED_NOT_EVALUATED.

Phase13.6-13.13 canonical manifests and registered parent plans pass. Prior raw failure
and recovery evidence rehashed: 100 Phase13.12 initial, 804 recovery and 1741 Phase13.13
files. Counts describe separate registered maps and may overlap; not unique-file totals.
Historical source snapshots remain intact. No prior failure is rewritten as success.

Manifest canonicalization: sorted compact ASCII JSON, UTF-8/no newline, allow_nan=False,
manifest payload only. Two disk replays must match its envelope and linked raw hashes.
The manifest stores exact baseline/new-document hashes; its own fingerprint is outside
the canonical payload, with no circular self-hash. Ignored sanitized observations/check
logs remain under tmp/phase1314. No generated binaries, dumps or logs are staged.

## Exact new inventory

- docs/operations/phase-13.14-plan.md
- docs/architecture/kite-sandbox-alternative-linux-host-assessment.md
- docs/operations/phase-13.14-platform-security-matrix.md
- docs/operations/phase-13.14-oci-provenance-assessment.md
- docs/operations/phase-13.14-logging-and-dump-boundaries.md
- docs/operations/phase-13.14-runbook.md
- docs/operations/phase-13.14-validation.md
- research/phase-13.14/generation-1/evidence-manifest.json

No tracked modifications or staging. Commit/push NONE.

## Recommendation and retained restrictions

Separately authorize a controlled Linux VM acceptance assessment, conditional on
kernel LSM/audit access, real UID/GID translation and root/witness separation outside
its restore domain. Evaluate dedicated Linux hardware if those boundaries cannot
be established. Availability/cost and both candidate platforms remain NOT_TESTED.
No host choice supplies an OCI signature or authenticates unknown external sinks.

Historical surviving-root rollback denial is retained. All-authority restore still
permits two grants in a no-HTTP counterexample. Physical power loss, privileged restore,
hardware monotonicity, multi-host failover and production containment remain unproven.

Real Kite/account/token/WebSocket/order/trading DB/operational HALT actions performed
by this phase: zero; action inventory, not global telemetry. No real canaries/secrets
loaded. V2, governance second review and four technical reviews PENDING; real access
and implementation under V2 NOT_AUTHORIZED. NO_GO; MIS NOT_READY; Phase12 BLOCK;
H1/H2 FROZEN; SBIN July TEST SEALED; INR10,000 ceiling unchanged; ZERO_CALL_ABORT.
No provisioning, migration, deployment, commit/push or automatic next phase.
