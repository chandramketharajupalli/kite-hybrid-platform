# Phase 14.13 validation

PHASE_14_13_PARTIAL. Baseline develop HEAD/local origin/develop:
0e3f347e401e34e92c34101b56f714a36568a5d4. Initially clean; all sixteen Phase14.12
additions committed. Baseline1131 tracked files with raw inventory fingerprint
864b9fb3e0bd974eb69e2a407dc1f63c0433db2ce83acdadf78d6756425cb3b0.
Plan SHA256:7881ccb4d38ac82564edbc4c9aa62b3fd731f060a72d7502fa241a9dab619e54.

Eleven additions, no existing modifications: two runtime classes, three test classes,
five Phase14.13 documents and one evidence manifest. Exact paths/hashes in manifest.
No source/POM edits or historical evidence rewrites. No staging,commit or push.

## Fresh tests

| Suite | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| AdapterTest |47|0|0|0|
| AuthorityRecoveryTest |16|0|0|0|
| ParserTest |25|0|0|0|
| AuthorityRecoveryIT |8|0|0|0|
| StoreProcessIT |8|0|0|0|
| GateAOfflinePolicyTest |40|0|0|0|

Total144 fresh tests:128unit+16process/filesystem integration.24 newly added recovery
tests;120 retained tests freshly rerun, not inherited historical totals. Both commands
exited0 on their first run; no failed tests,build attempts or corrective reruns this phase.
Large initial read-only audit output was truncated; relevant recovery/host portions
were read separately afterward. No historical failure reclassified or erased.

Commands, sequential and offline:
mvnw.cmd -o -f apps/gate-a-offline-adapters/pom.xml -Pintegration verify
and mvnw.cmd -o -f apps/gate-a-offline-policy/pom.xml verify.
Generated XML hashes bind fresh reports; runtime JAR hash and source allowlist recorded.
No Python/full trading-core/DB/container suites: their sources/contracts unchanged.
No real network dispatch or genuine signing/owner evidence used.

## Recovery observations

| Case | Fresh observed result |
|---|---|
| Restart after synthetic observation | CONSUMED reconstructed; replay denied |
| Revocation reconstruction | REVOKED persists across child JVMs; no availability/reinitialize rearm |
| Signed revoked state | Conservatively QUARANTINED; fresh wrapper denied |
| Higher epoch / stale permit | Highest epoch survives; old permit denied and quarantined |
| Clock reversal | Stored last observation rejects reversal; quarantine survives reconstruction |
| Conflicting key identity | Binding mismatch denies and quarantines |
| Two-process compare-and-advance | Exactly one expected-sequence winner; consumed check denied afterward |
| Crash before reservation | No transition; later explicitly invoked fixture may observe |
| Crash after witness / after local | Restart denied: RECOVERY_PENDING / CONSUMED respectively |
| Lost ACK after witness / both writes | False result; mismatch or consumed state retained, no grant |
| Corrupted/missing checkpoint | RECOVERY_PENDING; no automatic repair |
| Local rollback, witness survives | Mismatch denies in unit and child-process checks |
| Witness rollback, local survives | Mismatch denies in unit test |
| All histories plus attempt store restored | Two synthetic observations possible: negative control retained |
| Uncertain observation outcome | Attempt remains consumed; restarted wrapper denied |

The journal is bounded to256 records,8192bytes per record and uses a witness lock,
CREATE_NEW immutable records and force(true). Same-admin integrity hashes do not establish
an independent monotonic authority. Physical power loss, OS/container restart, controller
cache behavior, directory metadata durability and replicated failover NOT_TESTED.
Fresh tests use process termination; no destructive experiments. Old Phase13.9-13.11
signed-root/witness evidence remains historical and conditionally trusted, not production.

Runtime inspection:53class files,20 selected main source files,eight test sources,
no Main-Class/test probe in JAR; no packaged HTTP/socket/SQL/Spring/trading transport/
environment-reader references. Runtime dependencies remain Jackson CSV/core/databind/
annotations; JUnit test-only. No new dependencies or trading application startup.
Owned child JVMs128MiB,30-second timeout,maximum two concurrent; parent256MiB. Tests
completed and fixture temporary directories are JUnit-owned. No infrastructure created.

## Preservation, host and permissions

Preflight and final checks preserve Phase14.0-14.12 canonical envelopes/raw files,
Phase14.0A13 newline reconciliations,original978 entries,G1/18sources/63research/25K-L,
17plans/11governance envelopes and all baseline raw bytes. Universe1140-25=1115 unique
enabled NSE symbols/order preserved.0genuine mappings;1115MASTER_UNAVAILABLE.
Final secret/canary,new-file whitespace,LF/Git-byte checks and two manifest replays
bind the additive package; generated outputs remain ignored and index empty.

Host decision DEFERRED. Historical7.65GiB/0.76-0.85GiB memory observations are not current
measurements. No selected host/authority has production acceptance. Remote Linux with
genuinely separate authority custody is a conditional design option only, not purchase
or deployment approval. Phase13.12 failure and Phase13.14-13.19 custody/host gaps retained.

INDEPENDENT_ROLLBACK_AUTHORITY_NOT_ESTABLISHED; PHYSICAL_POWER_LOSS_NOT_VERIFIED;
PRODUCTION_EGRESS_NOT_VERIFIED; AUTHENTICATED_SESSION_NOT_ESTABLISHED. External logs,
diagnostics,credential/key/backup ownership and independent administrative separation
remain unverified. This is why the phase remains PARTIAL despite passing bounded tests.

Compliance submission unchanged: USER_REPORTED_SENT_WITH_SCREENSHOT; eleven unresolved
topics,no original screenshot/body hash,no confirmed delivery/ticket/reply or legal
approval. ZERODHA_TERMS_UNRESOLVED. Gate A/B NOT_AUTHORIZED. Agent real Kite requests,
credential accesses,vendor messages,orders,deployment,trading-DB and operational HALT
changes all zero. NO_GO,ZERO_CALL_ABORT,Phase12BLOCK,H1/H2FROZEN,JulySEALED,INR10000
and V2/governance/four technical reviews pending unchanged. Stop without acquisition.
