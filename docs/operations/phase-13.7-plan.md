# Phase 13.7 bounded hardening plan v1

Frozen before code edits. Baseline: develop, HEAD and local origin/develop
34cc3e5b242547774a5225d30aa460699db2b2d9; tracked/untracked tree clean; diff check
passed. No fetch/remote refresh. Compute raw SHA-256 of this file and retain in
validation/evidence; changes require an additive plan amendment.

## Scope and source findings

Read all nine existing Java sources/tests, module POM, Phase13.6 architecture,
plan/runbook/validation and manifest. Existing runtime API is package-private:
SyntheticOwner.observe(Request,String,Assertion,Fault), Ledger.consume/terminal,
SyntheticHalt.admit/recheck and LoopbackPeer.get. No public service API or launcher.
LedgerWorker.main exists in test sources only. PostgreSQL adapter is test-only.
State: ABSENT -> durable RESERVED -> CONSUMED -> immutable terminal observation.
Gaps: uncertain reservation before durable tombstone; no transport boundary count or
delivery classification; HALT epoch rollback; terminal update silently accepts no-op.

May modify the existing three runtime files and six test files under
apps/synthetic-credential-owner/src/{main,test}/java/com/kitehybrid/synthetic/.
May add only test AttemptJournal.java, RecoveryLedgerIT.java, RecoveryOwnerIT.java,
OwnerWorker.java and RecoverySecurityTest.java in that test package. POM may change
only test execution controls if necessary, never runtime dependencies/root wiring.
New docs: this plan, docs/architecture/kite-synthetic-owner-failure-recovery.md,
docs/operations/phase-13.7-{runbook,validation}.md and
research/phase-13.7/generation-1/evidence-manifest.json. No other tracked changes.
Ignored tmp/phase137 holds execution evidence; target outputs never added to Git.

Historical 13.6 docs/manifest and K/L generations remain exact bytes, even as current
prototype source evolves. Verified old manifest canonical fingerprint, 25 K/L raw
files, 132 historical parents, 14 Phase13.6 source/doc inputs, G1 exact identity,
18 registered text hashes and 63 original research hashes before edits. New evidence
will record current source versions separately rather than rewriting old evidence.

## Failure model and changes

Trusted synthetic fixtures only: generated fake entropy, signing keys, fixed UTC
clock, numeric loopback peer and Testcontainers-owned PostgreSQL with fsync,
full_page_writes/synchronous_commit ON. JDK21 java.base-only runtime. No broker
client, credentials, real endpoint, application startup, order code or live launcher.

Add a test-only, fixture-owned durable attempt journal before any database action.
CREATE_NEW plus FileChannel.force(true) creates one immutable tombstone per UUID;
partial/empty/existing markers deny permanently. A separate forced fixture identity
prevents silently recreating a lost journal. Share the same directory across workers
and restarts; fail closed if it is missing/replaced. No repair/delete/recovery grant.
This closes DB connection/reservation uncertainty under process-crash semantics,
not physical disk power loss, hostile directory deletion, replicated FS or rollback.
The journal is not a production adapter or an in-memory durability claim.

States: UNSEEN -> JOURNAL_RETIRED -> DB_RESERVED -> DB_CONSUMED -> OUTCOME_RECORDED.
Every state after JOURNAL_RETIRED is spent regardless of outcome. DB consumption
must be durably acknowledged before creating a private one-use transport ticket.
Bind request fingerprint, exact synthetic HALT evidence digest and owner incarnation.
Unknown reservation/consume acknowledgment grants nothing and leaves journal spent.
Validate exact legal outcome transitions; no success label grants another attempt.

Transport requires the owner-issued private ticket, rechecks authorization before
socket and send, consumes ticket once and counts attempted sends before first byte.
Result adds only a bounded delivery classification: NOT_DISPATCHED before send,
MAY_HAVE_DISPATCHED on any uncertain send/response, OBSERVED_COMPLETED only after a
complete bounded response. Unknown outcomes never become reusable. Receipt assertions
require independent fake-peer observations; attempted send does not prove receipt.
Keep exact numeric host/port/GET/path, zero retries/redirects/proxy/DNS, bounded wire.

HALT epochs never decrease, conflicting key-ID replacement denies, clock reversal
fails closed. Test owner-incarnation, evidence binding, expiry/skew/revocation and
pre-send/post-result changes. Existing native-serialization/redaction rules retained;
allowlist the new result enum, never signed evidence, account-like payload or entropy.

## Frozen validation matrix and order

1. Static dependency/source review and focused unit/security/packaging tests.
2. Disposable ledger tests only: journal partial write/missing identity, connection
   failure, before/during reservation rollback, lost reservation and consume ACK,
   after-consume worker death, illegal outcome transitions, duplicate/different
   digests, eight threads/two processes, same-store restart/container port change.
   No HTTP integration until this gate passes. Cannot prove durable pre-dispatch
   consumption => STOP; never bypass the gate.
3. Loopback tests: before socket, socket before send, partial send, sent but response
   lost, fixture-confirmed remote receipt, parse failure, before outcome persistence;
   restart/replay for each, two competing owner JVMs with at most one received request,
   changed incarnation cannot revive consumed or old-scope approval; timeout/reset,
   malformed/oversized/truncated/non-2xx/delayed response, no redirect or retry.
4. Full standalone suite, then existing root Java unit/architecture. Existing trading
   integration not required absent shared trading source changes. No Python source
   changes or general approval engine. Record exact fresh XML totals and failures.
5. Project verifier, secret scan, JAR/jdeps/dependency inventory, diff/untracked audit,
   exact protected hashes and two identical canonical evidence replays.

Failure injection is explicit synthetic evidence, not claimed physical process death.
Use actual subprocess termination after consumption and two-owner subprocess race;
label other crash windows injected. Re-inspect mapped ports after Docker restart.
Do not claim replicated failover/OS sandbox/live sink or independent HALT evidence.

Budgets: max eight threads/two competing owner workers, per socket <=3s, wire header/
body <=4096 bytes, each subprocess <=25s and container recovery <=25s; max one owned
PostgreSQL container per integration class. Maven offline (-o); cached images only,
no public-egress probes or dependency fetching. Stop if required artifacts unavailable.
Use UTC test JVMs, scoped resources and cleanup only of fixture-owned resources.
Retain failed reports before reruns; correct causes without relaxing assertions.

## Preservation and decision boundaries

Protected-source mismatch, unexpected Git state/change, real credential/network
requirement, accidental external egress or weakened safeguards => stop and report.
No config/.env/migration/risk edits, trading DB access, normal app startup, real tokens,
Kite/WebSocket/order action, HALT resume/arm/execute, stage/commit/push or destructive Git.
No historical evidence edits. G1 exact SHA 56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656.
18 sources use registered UTF-8 text/newline normalization; 63 research hashes raw.
V2 and amendment hashes remain those bound in preserved Phase13.6 manifest.
Phase12 BLOCK; H1/H2 FROZEN; July SEALED; INR10000/cash/CNC unchanged; StageB ZERO_CALL_ABORT.
Operational NO_GO, V2/four reviews/governance second review PENDING, real Kite access
NOT_AUTHORIZED, MIS NOT_READY. Passing tests changes none of these decisions.

Evidence canonicalization: sorted keys, compact comma/colon JSON, ensure_ascii=True,
allow_nan=False, UTF-8/no body newline; SHA-256 body inside fingerprint/manifest
envelope. Raw file hashes separate. Exclude manifest's raw self-reference. Report
SYNTHETIC_FAILURE_RECOVERY_VALIDATED only if all required synthetic suites pass;
otherwise PARTIAL/BLOCKED with missing evidence. Stop after report.
