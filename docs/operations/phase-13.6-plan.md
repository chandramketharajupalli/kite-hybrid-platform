# Phase 13.6 frozen synthetic implementation plan v1

Baseline develop / fc473eab7b2c83a9543cda769972e4928a418495, matching local
origin/develop. Twenty-five pre-existing untracked K/L artifacts inventoried in
ignored tmp/phase136/baseline.json before edits; no tracked change. Exact V2,
amendment and G1 plus 18 normalized sources, 63 original research hashes and 132
historical inputs verified. Freeze this file's raw SHA-256 before source edits.
No criteria relaxation; a change needs a new version and explicit rationale.

## Allowed files and architecture

Add only apps/synthetic-credential-owner/pom.xml and src/main/java/com/kitehybrid/
synthetic/{SyntheticOwner,SyntheticHalt,LoopbackPeer}.java; src/test/java/com/
kitehybrid/synthetic/{SyntheticSecurityTest,PackagingTest,DisposableLedger,LedgerIT,
LedgerWorker,OwnerIT}.java. Test-only PostgreSQL backend and worker cannot enter
the runtime JAR. A standalone Maven child inherits version/plugin management, but
is not added to root modules, trading-core dependencies or Spring wiring. Main code
uses only JDK; no Main-Class, launcher, CLI, environment credential source or loader.
Allowed documentation: this plan, phase-13.6-validation.md, phase-13.6-runbook.md,
architecture/kite-synthetic-credential-owner.md. Add a redacted fingerprinted evidence
manifest under research/phase-13.6/generation-1/. No pre-existing file edits.

Inspect current read/handoff/isolation/integrity/request-factory/transport/session/
auth/HALT imports and actual signatures; retain their code unchanged. Root reactor
contains trading-core only. Existing request factory imports Spring HTTP/Apache and
has official transport; harness/handoff import trading HALT/session; integrity uses
JDBC; general transport exposes order scope; auth restore/complete has token-store,
profile and instrument effects. None qualifies for isolated prototype reuse.

## Threat model and frozen acceptance criteria

Untrusted request fields, forged/missing/replayed attestation, wrong caller/account/
owner/recipient/route, expiry, epoch/key revocation, host configuration drift, duplicate
delivery, process crash, ambiguous commit and network outcome. Trusted test harness
generates all synthetic identities/keys/secret material and explicitly passes fixed
clock and pinned loopback port. No input from .env, JVM properties or environment
may select credentials, network endpoints or database. OS administrator/JVM agent
compromise remains outside enforceable Java boundary and cannot be certified here.

Owner APIs package-private, fixture-created synthetic credential only, no bearer
import/export. Redacted capability/attestation/credential rendering and no native
serialization; response contains outcome and fixed shape-presence booleans only.
Signed synthetic HALT binds request fingerprint (all scope fields), owner, audience,
operation, epoch, nonce, issued/expiry/key ID. Check before claim, before dispatch
and before accepting result. Rotation/revocation/unavailable issuer fail closed.
Same attestation may be rechecked only for its in-flight invocation; other uses deny.

Ledger must be validated first on a Testcontainers-owned disposable PostgreSQL
with fsync/full_page_writes/synchronous_commit ON. Test-only factory is instantiated
only from its owned container coordinates; no development DB variables. Persist a
unique irreversible reservation first, then CONSUMED transaction before acknowledgment.
Reservation is already spent: failure before consume commit cannot reopen it. Return
send grant only after certain consume acknowledgment; any ambiguity means no dispatch.
No TTL deletion/reset or recovery grant. Bind complete request digest and immutable
ID, version, incarnation, scope, expiry and HALT epoch through that digest.
Concurrent workers/processes have one winner; new process/restarted DB cannot replay.
Verify rollback/connection failure, before-commit, after-commit-before-ack and process
death after ack; keep all reservation/terminal rows spent. DB crash/restart proof
assumes storage honors fsync, not power-loss/host-rollback certification.
Stop simulated-dispatch integration if ledger validation fails (LEDGER_NOT_VALIDATED).

Dedicated fixed-loopback peer uses raw bounded HTTP/1.1 over one Socket to numeric
127.0.0.1 at the fixture-pinned port; no DNS, proxy, redirect, retry, cookies or auth.
Exact GET /user/margins/equity only; fixture-only small JSON grammar/schema and
bounded response, never official API compatibility. Request URI must equal pinned
literal. Deny alternate methods/paths/ports/aliases/query/userinfo and injected settings.
Timeout/connection loss after claim stays spent and unknown. Test HTTP success,
401/403/429/5xx, redirects, malformed/oversized body, timeout/loss. Results always
SYNTHETIC_* or PROTOTYPE_PRECONDITION_FAILED, never authoritative funds evidence.

Sink policy fixture must affirm closed console/file/wire/trace/dump/agent/proxy/test-
artifact capture; unknown/unsafe policy denies construction/dispatch/result. Inspect
effective known JVM/env hazard names without values; no property-selected URL.
No logging framework, Actuator or trace dependency. Synthetic capture tests prove
only exercised stdout/stderr/exception/object/report paths, not OS/container/IDE sinks.
Credential entropy never logged or persisted, even synthetic. Database rows contain
request metadata digests, never credential values or raw balances.

## Tests, gates and bounded execution

Use installed JDK21, repo mvnw.cmd, standalone -f module path. Unit/security/package
tests independent of Docker. First run -Pintegration -Dit.test=LedgerIT verify;
only after success run OwnerIT loopback integration. Integration helpers are test
classes with no production wiring. Up to 8 concurrent workers, one isolated DB per
class, bounded socket/query/process waits (<=30 seconds), bounded body <=4096 bytes,
synthetic clock-based expiry <=30 seconds. No deliberate real-network probe.
Run full existing Java unit/architecture suite once for regressions; shared trading
production unchanged, so existing broad trading DB integration need not run. Run all
new disposable integration and package dependency/jdeps/JAR scan. No Python changes,
Python tests/Ruff/mypy NOT_APPLICABLE. Record failures and focused reruns honestly.
If artifact scan fails report MINIMAL_ARTIFACT_NOT_VALIDATED, never waive exclusions.

Threat evidence includes static forbidden dependency/class scan and runtime class
resolution failure, egress/environment/system-property injection, serialization/log
marker checks and no funded/trading classification. These prove synthetic behavior
only, not process/host network isolation or independent live authentication/HALT.
No OS firewall rule change or real deployment; document required external enforcement.

Canonical evidence JSON uses sorted keys, compact comma/colon, ensure_ascii=True,
allow_nan=False UTF-8 with no newline for body SHA-256; envelope sorted indent2 LF.
Pin exact new-file hashes, prior inventory, commands and actual XML counts; distinguish
initial failures/reruns. Keep secret-bearing logs out of artifacts. Run project/secrets/
diff and full untracked audit, reverify protected evidence and K/L raw hashes.

V2 PENDING, four reviews PENDING, K acceptance user-declared, effective adoption/
second review PENDING, original/new checkpoint time unverified, no live read authority.
Real ownership/HALT NOT_ESTABLISHED, live logging NOT_VERIFIED, operational NO_GO,
MIS NOT_READY. Preserve ZERO_CALL_ABORT, cash/CNC/INR10000, Phase12 BLOCK, H1/H2 freeze
and July seal. Zero real broker/token/trading DB/startup/order/WebSocket/HALT actions,
config/migration/risk edits, commit/push or destructive Git. Report and STOP.
