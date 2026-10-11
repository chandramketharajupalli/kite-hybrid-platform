# Phase 13.8 validation and remaining assurance gaps

Result: **SYNTHETIC_ISOLATION_RECOVERY_PARTIAL**. The bounded implementation fixes
two synthetic HALT recovery defects and adds reproducible process-kill, rollback,
containment and isolation counterexamples. It does not close privileged whole-store
rollback, OS confinement, replicated failover or physical-storage guarantees. Several
exact socket/ticket/parse crash windows remain injected faults rather than externally
terminated instruction-level execution. No operational certification is implied.

## Baseline and frozen criteria

Initial develop, HEAD and local origin/develop all verified at
`2c54d9bd581139e26fff1775fcaeba7259b4591e`; clean tracked/untracked worktree and
diff check. No fetch or remote refresh. History inspected: 2c54d9b, 34cc3e5, 6c2a31c.
An ignored before-work inventory contains raw hashes of 817 tracked files; the new
manifest retains that inventory and the authorized changed-source exceptions.

Frozen Phase 13.8 plan raw SHA-256:
`560cabd3a14dc02da5d4a45623fc122f22ab2209141a375e7a02556080a18c31`.
No plan amendment or criteria relaxation. The failure cases and minimal runtime fix
were explicitly within the frozen plan. Existing public/package API signatures,
runtime dependency boundary, RSA-3072 profile and root reactor remain unchanged.

Registered Phase 13.7 identities verified before work:

- Plan raw: `D79F7251482A1A8529C47CFD9B877DA607AEFDDC163BE3C82179E541FE274E81`.
- Amendment raw: `66AD85AAEE27B0205C5F941C2E5D49684F4B7924CAE25DDB6E65392FDE52C30F`.
- Manifest canonical body: `cb87e01cfccf84698f5290ef293b4d5893ea774b9e608f7d131365503e7b2c5d`.

The canonical fingerprint was recomputed from sorted compact ASCII-escaped JSON,
not confused with the raw manifest SHA. All 17 Phase 13.7 current source/doc inputs
matched before edits. Historical source hashes are not claimed as the current
versions of the three intentionally changed files.

## Failure history, correction and actual tests

Installed JDK21.0.12, wrapper Maven3.9.11 and Docker server29.8.0. Cached PG17.6 and
Ryuk0.12.0 inspected; no image pull or dependency download. All Maven runs offline;
test JVMs/subprocesses use UTC. Raw logs/XML retained locally under ignored
tmp/phase138, not added to Git or quoted with system-property/environment payloads.

| Run | Actual result |
|---|---|
| Initial IsolationSecurityTest | 16 tests, 2 failures, 0 errors/skips; native exit1 |
| Corrected focused unit/security/packaging | 94 tests, 0 failures/errors/skips; exit0 |
| Ledger gate before HTTP | 94 unit + 31 integration; all pass, exit0 |
| Loopback HTTP gate | 94 unit + 54 integration; all pass, exit0 |
| Initial full standalone | 94 unit + 85 integration; all pass, exit0 |
| Existing root Java unit/architecture | 1,508 tests, 0 failures/errors/skips; exit0 |
| Added control-pipe harness negative check | IsolationSecurityTest 17 tests, 0 failures/errors/skips; exit0 |
| Final full standalone | 95 unit + 85 integration, 0 failures/errors/skips; exit0 |

Initial failures proved availability(true) could clear clock-reversal and conflicting
key-ID denial. The correction adds an irreversible, synchronized verifier-incarnation
quarantine independent of availability. Both original negative assertions are retained
unchanged and pass after the fix. No assertion/timeout was loosened. One subsequent
test-only addition checks EOF of the control pipe: exit29, empty spent marker and no
DB continuation. The root suite tested the final runtime source; that last addition
changed only a standalone test, so unrelated root tests need not be repeated.

Final per-class inventory is tied to actual XML in the manifest, not prior
phase counts: IsolationSecurityTest17, PackagingTest3, RecoverySecurityTest9,
SyntheticSecurityTest66; IsolationLedgerIT13, IsolationOwnerIT3, LedgerIT8, OwnerIT36,
RecoveryLedgerIT10, RecoveryOwnerIT15. Repeated invocations are not unique tests.
Surefire XML has no timestamp attribute in this environment; freshness is supported
by captured command runs/report hashes, not invented XML timestamps.

Read-only tooling corrections: bare python was unavailable; repository uv offline
Python was used. An ad hoc XML summary initially assumed a timestamp attribute and
failed, then used nullable metadata. PowerShell logs are UTF-16; their summaries use
the recorded encoding. These did not alter tests or evidence. Native stderr SLF4J
missing-provider warnings are captured; explicit Maven exit codes distinguish them
from failures. No Python application source or executable validator was introduced:
Python suites/Ruff/mypy NOT_APPLICABLE. Broad trading integration NOT_RUN because
no shared trading/auth/persistence/runtime code changed. All standalone disposable
integration is required and reported separately.

An initial artifact-audit assertion assumed no dumpstream files and failed. Inspection
identified 11 new Surefire/Failsafe dumpstream diagnostics, each 290 bytes, describing
the Windows manifest-JAR absolute test-classpath/different-root warning. They contain
only timestamp, test-classpath and tooling hint, no canary/credential/response data.
No hinted JVM bypass flag was applied. Older dumpstreams were preserved, not deleted
or attributed to this phase. These are build diagnostics, not native crash/heap dumps.
Their raw hashes and content classification are retained in the manifest. The audit
now explicitly accounts for them rather than claiming the target directories empty.

## Evidence strength and limits

The [architecture report](../architecture/kite-synthetic-owner-isolation-assurance.md)
contains the complete taxonomy, threat actors and crash matrix. Important outcomes:

- Eight parent-terminated JVM persistence windows: before journal create, after
  create/write/force, before/after reservation commit, before/after consumption commit.
  After retirement, restarted adapters deny; before first retirement no grant existed.
- Three parent-terminated owner windows: after durable claim before acknowledgment
  return, after fixture-confirmed full receipt before response, before terminal write.
  Replacements cannot send. A received request and observed client completion are
  distinct facts; no exactly-once claim or renewal from unknown outcome.
- Actual file truncation/changed content denies reuse; create obstruction and injected
  write exception deny. These are not sudden-power-loss or full-disk tests.
- Single-store logical restoration is fenced by the surviving store. Restoring both
  to pre-attempt state permits a second grant. That counterexample uses no HTTP;
  privileged rollback remains open and requires an external non-rollbackable authority.
- Existing two-JVM races, changed incarnation/replay denial and actual owned PG
  SIGKILL/restart rerun. Docker's port changed and was re-inspected. This is one host
  and one primary, not replicated split-brain/quorum certification.
- New address-form denials never resolve/connect; inherited redirect/proxy/env,
  partial-write/reset/timeout/oversized/malformed and post-response-revocation tests
  rerun. No public-network egress probe.
- Nested exception cause/suppressed canaries remain absent from bounded results,
  console and thread names. Existing credentials/signature/wire/serialization tests
  rerun. External sinks, JVM diagnostics, heap/crash dumps, hostile reflection and
  CI/IDE retention remain UNVERIFIED; no complete sink containment claim.
- A java.base-only child can read its own canary and spawn a child. This is executable
  evidence that module restriction is not OS confinement. Separate OS identity/ACL,
  network policy, read-only root, environment allowlist and capability controls remain
  proposed/unverified. No host policy changed or owner deployment created.

## Independent packaging check

Runtime dependency tree contains only the standalone artifact. JAR inspected:
25 entries, 15 runtime classes; no Main-Class, META-INF/services, test workers,
journal/JDBC/Testcontainers/Spring/trading packages or real broker hostname. jdeps
returns java.base. Actual provider checks and a restricted-JVM signature test pass.
An additional independent invocation used only the built JAR plus a directory with
the single nested test probe class: `java --limit-modules java.base`; exit0. Thus
test-scope libraries were not available to that probe. No production launcher added.
JAR hash and entry inventory are in the manifest; generated JAR/classes remain ignored.

## Preservation and final audit

G1 raw: `56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`.
V2 raw: `5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7`.
K amendment raw: `24F3FBD86E6FB662EDB6890C5150AB03C3E9FCE23D7B7B5DA709F43B1F636EDB`.
18 registered text hashes match G1. Fifteen also match raw; candidates.py,
diagnostics.py and opening_features.py retain their original CRLF differences, using
the registered UTF-8/newline normalization only. No protected content rewritten.
63 original research hashes, 162 historical raw inputs and 25 K/L raw files match;
all 11 fingerprinted K envelopes replay. K Generations1-4 and L docs remain unchanged.
Phase12 acquisition/evaluation flags false with five reasons; July SEALED_NOT_EVALUATED.

Project verifier PASS; secret scanner PASS (1,853 text files, zero potential secret
locations); git diff --check PASS; no staged changes. Raw/current/initial and protected
inventories pass. Final audit details are recorded in the manifest.
Canonical replay verifies raw/current/initial inventories, registered normalized
hashes, unchanged parent evidence and fail-closed statuses twice. Equal fingerprints
prove evidence consistency only, not a rerun or independent approval. No generated
target/JAR/class/log/dump/.env file is staged; staged inventory must remain empty.

## Exact changed/new paths

Modified:

```text
apps/synthetic-credential-owner/src/main/java/com/kitehybrid/synthetic/SyntheticHalt.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/AttemptJournal.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/DisposableLedger.java
```

The first fixes contradiction quarantine; the other two expose test-only crash
barriers while preserving default adapter behavior. No POM/config/trading change.

Added:

```text
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/IsolationLedgerIT.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/IsolationOwnerIT.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/IsolationSecurityTest.java
apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/IsolationWorker.java
docs/architecture/kite-synthetic-owner-isolation-assurance.md
docs/operations/phase-13.8-plan.md
docs/operations/phase-13.8-runbook.md
docs/operations/phase-13.8-validation.md
research/phase-13.8/generation-1/evidence-manifest.json
```

New tests/probes cover actual process termination, modeled storage restoration,
quarantine/containment and negative isolation/harness behavior. New docs freeze and
explain the evidence; the additive manifest binds actual results and retained parents.

V2/four technical reviews/governance second review PENDING. Owner acceptance remains
USER_DECLARED_ACCEPTANCE_RECORDED; original/checkpoint timestamp UNVERIFIED; no fresh
owner decision or 24-hour timing proof inferred. Operational implementation, real
Kite access, credential acquisition, deployment and trading NOT_AUTHORIZED; NO_GO;
collateral-assisted MIS NOT_READY. Real session/live HALT NOT_ESTABLISHED; live logs
NOT_VERIFIED. INR10,000/cash/CNC unchanged; Phase12 BLOCK, H1/H2 FROZEN, July SEALED,
Stage B ZERO_CALL_ABORT preserved. Real Kite calls/WebSockets/orders, real token
loads/restoration, development/production DB mutations, trading startup and operational
HALT resume/arm/execute: 0. No stage/commit/push or destructive Git action.

Next: separately review an anti-rollback/fencing design and OS/logging assurance
requirements; obtain genuine governance and four-domain evidence. No further live
action follows. Stop after reporting.
