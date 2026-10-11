# Phase 13.10 validation

Result: **SYNTHETIC_JAVA_SANDBOX_ROOT_RECOVERY_PARTIAL**.
**JAVA_OWNER_SANDBOX_NOT_EXECUTED**. Recovery evidence uses actual owner runtime
classes in host JVMs; this is not sandbox execution or operational certification.

## Baseline, environment and preservation

Initial clean develop, HEAD and local origin/develop both
`07d94f5055f74d26cb8f4ac5cc96e76b417bca34`. Porcelain inventory empty and diff check
passed. No fetch/pull; remote freshness not established. Before-work inventory:
837 tracked raw hashes. No tracked source or historical evidence is modified.

JDK21.0.12, Maven wrapper3.9.11, Docker29.8.0, Windows11. Maven offline, test JVMs UTC.
Cached images: postgres17.6, redis7.4.5, Ryuk0.12.0, hello-world; no Java image.
Only docker-desktop WSL discovered. No Linux JDK archive established in inspected
repository/JDK locations; user .jdks absent. Installed java.exe starts with MZ and
cannot form a Linux image. A read-only, network-none, unprivileged probe of the cached
PostgreSQL image confirmed no java command or standard JVM directory (exit 0).
No image download, package installation, host policy change or operational deployment.

Plan raw SHA-256:
`eb7ff542afd3a80284a5473301f631957d09122fadc68a3ab1811f0177f4e82d`.
Amendment 1 raw SHA-256:
`11cab58cb9a64e863433648487734ab7ee4335cc4b73d8d03b792420a93f01e8`.
Original plan remained unchanged. Parent 13.9 canonical fingerprint:
`906a484a9ee5cc6f5dc341e304956f5387c7f55873ec48524ce6214dc7156229`.
Phase13.6-13.9 canonical envelopes, registered plans/amendments, G1 exact hash,
18 normalized source hashes, 63 research files, 162 historical files and 25 K/L
files passed the entry gate. Existing CRLF source normalization is preserved.

## Commands, failures and actual counts

Module prefix: `.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml`.
Native exit codes and XML, not PowerShell's SLF4J NativeCommandError presentation,
determine test outcomes. Logs remain ignored under tmp/phase1310; manifest binds
hashes and report summaries. Repeated runs are not summed as unique coverage.

| Run | Command suffix | Result |
|---|---|---|
| Ledger gate | `-Pintegration '-Dit.test=FenceLedgerIT,IsolationLedgerIT' verify` | Exit0; 95 unit +27 integration, zero failures/errors/skips |
| Focused initial | `'-Dtest=PackagingTest,IsolationSecurityTest' -Pintegration '-Dit.test=RootRecoveryIT' verify` | Exit1; 20 unit passed; 9 integration, 4 failures, 0 errors/skips |
| Focused rerun1 | Same | Exit0; 20 unit +9 integration, zero failures/errors/skips |
| Full standalone | `-Pintegration verify` | Exit0; 95 unit +123 integration, zero failures/errors/skips |
| Root Java | `.\mvnw.cmd -o test` | Exit0; 1,508 tests, zero failures/errors/skips; 87 XML report classes |

All four initial failures were the new fixture's empty-stderr assumption: the child
emitted the known three-line SLF4J missing-provider/NOP diagnostic. Amendment 1 was
written before correction. The test now allows exactly those lines or empty stderr,
checks the generated witness canary, rejects any extra output, and uses fixed failure
messages. No arbitrary stderr suppression or weakened retirement/count assertions.
Failed log and XML retained; XML raw SHA-256
`649c9728044d8a77a28a5253d90561ca19cd7c85791e25d4cc23a17027b77991`.

Inspection-only command errors: optional .jdks path absent; a PowerShell brace-path
rg expression failed and was rerun with explicit paths. A malformed patch submission
was rejected before mutation and reapplied correctly. These are not test failures
or preservation mismatches. No test failure was hidden or historical count reused.

Python/Ruff/mypy NOT_RUN: no Python source/executable validator changes. Root broad
integration NOT_RUN: no shared production changes. Standalone disposable integration
and root unit/architecture checks are required and separately reported above.

Full integration XML breakdown: ContainmentIT2, FenceLedgerIT14, FenceOwnerIT13,
IsolationLedgerIT13, IsolationOwnerIT3, LedgerIT8, OwnerIT36, RecoveryLedgerIT10,
RecoveryOwnerIT15, RootRecoveryIT9. The nine new cases passed twice after correction
(focused and full); no claim of statistical flake elimination follows from two runs.

## Recovery matrix and limits

| Case | State/evidence | Peer/dispatch outcome | Classification |
|---|---|---|---|
| Kill after witness ACK | Witness retired, local ABSENT | 0 receipts; replacement sends0 | PROVEN_IN_FIXTURE |
| Kill before local consume commit | Witness retired, local RESERVED | 0 receipts; replacement sends0 | PROVEN_IN_FIXTURE |
| Kill after local consume commit | Witness retired, local CONSUMED | 0 receipts; replacement sends0 | PROVEN_IN_FIXTURE |
| Kill after full request receipt | CONSUMED; parent held response | 1 receipt before/after replacement; replacement sends0 | PROVEN_IN_FIXTURE |
| Kill before outcome persistence | CONSUMED; parsed response reached | 1 receipt before/after replacement; replacement sends0 | PROVEN_IN_FIXTURE |
| Lost child control pipe after witness ACK | Exit29, local ABSENT, witness spent | 0 receipts; replay denied | PROVEN_IN_FIXTURE |
| Lost witness ACK then retained-root reconstruction | Local ABSENT, witness ID still retired | No HTTP; replay denied | PROVEN_IN_FIXTURE |
| Conflicting pinned signing key | No usable signed grant, local ABSENT, witness spent | No HTTP; replay denied | PROVEN_IN_FIXTURE |
| Missing checkpoint / fresh root against old PG | Denied and quarantined; toggle/reconstruction cannot clear | No HTTP/local consume | PROVEN_IN_FIXTURE |
| All authorities restored | Retained old counterexample: second grant possible | Ledger only, no HTTP | NEGATIVE_DEMONSTRATED / UNRESOLVED |

The first five are actual parent process terminations, not just exceptions. Killed
workers cannot report a final local result; classify pre-send barriers NOT_DISPATCHED,
receipt-before-response MAY_HAVE_DISPATCHED from owner perspective, and before-outcome
as having reached response observation. Peer counts are independently observed; no
unsupported claim about bytes follows merely from a timeout or death.

Existing suites cover unavailable witness, reservation/ACK injection, duplicate IDs,
stale checkpoint/expiry/epoch, two owner JVMs (one send/receipt), replacement denial,
witness PG restart with mapping reinspection, local rollback and persistent quarantine.
Existing HALT tests cover key conflict, signature/revocation, epoch and clock reversal.
These are fresh regressions when included in the completed full run, not new claims
of distributed quorum or universal signed-message replay coverage.

The coordinator Root is deliberately retained memory in selected reconstruction
tests. No independently durable hardware root, witness-process power-loss recovery,
privileged full-snapshot resistance or multi-host failover is established. Signed
checkpoints alone are not monotonic. At-most-once attempted dispatch is conditional
on surviving authority and is not exactly-once delivery.

## Sandbox, packaging and logs

Actual Java sandbox filesystem/process/DNS/TCP/seccomp/AppArmor/user-namespace
measurements: **NOT_TESTED**. Docker's builtin seccomp/cgroupns capability listing
does not prove effective Java policy. Shell ContainmentIT regression is not a
substitute. JVM-compatible process restrictions and allowed loopback inside a
default-deny sandbox remain open, as do container Java failure/dump canary tests.

Runtime JAR freshly inspected: 15 classes, 25 entries, SHA-256
`4d5798ca017e09a916d07f5a397be9912a4b8e9e044049e7e7bd7dc33e8a6305`.
No Main-Class/service providers or test/witness/JDBC/trading dependencies in the
artifact. Runtime dependency tree empty; jdeps java.base; isolated existing RSA
probe executed with built JAR plus only its probe class under --limit-modules
java.base, exit0. Runtime and POM/reactor unchanged. New child main is test-only.

Child stderr is captured and strictly checked, not discarded. Generated authentication
uses private stdin; no credential arguments/environment. Existing fake credential,
signature, payload, nested-exception and serialization checks remain. Dumpstreams
are retained and inventoried in the manifest; inspect their exact diagnostic format
without copying credentials/payloads. The module snapshot was captured after the
initial ledger gate began, so it is not a complete pre-turn ignored-output inventory.
External log sinks, JVM heap/core/agents, IDE/CI capture and live sink retention remain
unverified. Local scans do not certify them.

## Final audit and exact inventory

Final project/secrets/diff, deterministic replay and preservation outcomes are bound
in the evidence manifest. Canonical replay verifies sorted compact ASCII-escaped
JSON body hashes, all raw parents/current files, normalized frozen sources and
denied operational statuses twice. It proves consistency, not approvals.

Project verifier passed. Initial source/evidence secret scan: 1,880 text files,
zero potential secret locations, exit0. Final artifact scan: 1,883 text files,
zero potential secret locations, exit0; final project verification also exit0.
Git diff --check passed; untracked files also receive an explicit UTF-8/trailing-space/
final-newline check. All 837 pre-existing tracked hashes remain unchanged. G1/18/63,
162 historical files, 25 K/L files and 11 K canonical envelopes are reverified at
replay. Three frozen Python sources retain their registered CRLF-vs-LF distinction;
no normalization is written back. No staged files or generated outputs in Git inventory.

182 report dumpstreams passed the exact bounded four-line diagnostic-format check;
all 44 files in the module snapshot remain unchanged. No .dump/.hprof/hs_err_pid
files were found in the scoped module target and phase1310 scratch directories.
This scoped absence is not host-wide dump or live logging certification.

Only these eight new files are in scope; no tracked modifications or staged files:

- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/RootRecoveryIT.java
- apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/RootRecoveryWorker.java
- docs/architecture/kite-synthetic-owner-sandbox-and-root-recovery.md
- docs/operations/phase-13.10-plan.md
- docs/operations/phase-13.10-plan-amendment-1.md
- docs/operations/phase-13.10-runbook.md
- docs/operations/phase-13.10-validation.md
- research/phase-13.10/generation-1/evidence-manifest.json

No target, class, JAR, log, dump or secret is staged/added. All previous tracked
source/evidence stays byte-identical. Zero real broker/token/WebSocket/orders,
development/production DB mutations, operational HALT resume/arm/execute or deployment.
Root test fixtures are not operational application startup or account access.
No commit/push. V2/governance second review/four reviews PENDING, real access
NOT_AUTHORIZED, NO_GO, MIS NOT_READY; INR10,000/cash/CNC unchanged, Phase12 BLOCK,
H1/H2 FROZEN, July SEALED, Stage B ZERO_CALL_ABORT.

Next bounded prerequisite: establish a reviewed immutable Linux JDK21 artifact and
measure actual owner confinement with JVM-compatible policy. Separately design
surviving-root recovery and independently establish any live/governance prerequisites.
No automatic operational transition follows.
