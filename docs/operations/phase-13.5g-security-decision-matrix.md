# Phase 13.5G security decision matrix

DecisionMatrix.13.5G.v1, 2026-10-11; baseline a191b9c455c5bc916519846e431ba8341bc799dc.
Applies only to the v2 proposal and OwnerBoundary.v1. Operational NO_GO.
Evidence hierarchy and fixed criteria: [review plan](phase-13.5g-review-plan.md).
Actual run counts: [validation](phase-13.5g-validation.md).

## Decisions and independent approval roles

| ID / decision | Evidence and rationale | Current classification | Required evidence owner / approval |
|---|---|---|---|
| D1 Narrow DB assurance | Old integrity observer cannot prove absence of intervening writers; v2 explicitly limits claim to own diagnostic | PROPOSED; global NOT_PROVEN; own-process DESIGNED | Runtime/DB and user/operator must explicitly accept removal of global claim; PENDING |
| D2 Prefer C | No requester credential transfer; isolated owner can have narrower authority than trading JVM; A shares authority and B copies bearer | Design selected, not approved implementation | Security/authentication; PENDING |
| D3 Legitimate authentication | Existing complete/restore lifecycle has store/profile/instrument effects; no independent isolated account binding | AUTHENTICATED_OWNERSHIP_NOT_ESTABLISHED | Security/authentication: broker-compliant origin, consent, app/account binding, custody/revocation and trusted clock; PENDING |
| D4 Durable attempt ledger | Current static AtomicBoolean is local; proposed atomic claim and immutable incarnation survive restart through independent persistence | DESIGNED; no distributed validation | Security/authentication + runtime/DB: linearizability, durable acknowledgments, rollback fencing, partition/crash tests; PENDING |
| D5 HALT authority | knownHaltedAt is local; proposed independent signed all-instance assertion with epoch/replay/revocation | Live NOT_ESTABLISHED | Runtime/DB: issuer competence, actual scope, keys, trusted clock and epoch continuity; PENDING |
| D6 Minimal package/egress | POM includes Boot/JDBC/Redis/Flyway; general transport has order methods | NOT_DEPLOYED; own-process DESIGNED | Packaging/logging + runtime/DB: transitive closure and synthetic runtime reachability, then separately authorized live certification; PENDING |
| D7 Logging/custody | Existing marker and wire-level tests are narrower than sink inventory | Live NOT_VERIFIED | Packaging/logging: startup/dispatch drift, dump/agent/proxy/container/OS controls and retention ownership; PENDING |
| D8 Response evidence | Snapshot is owner/session-bound, enum derived from transport origin; no independently attested real session | Synthetic only; real response NOT_COLLECTED | Security/authentication: authenticated source chain and scoped receipt; PENDING |
| D9 Funding and safeguards | Funds observation resolves none of four authoritative collateral/cash terms automatically | MIS NOT_READY; all risk safeguards unchanged | User/operator and separately qualified broker-evidence review; PENDING, no trading scope |
| D10 Contract/read authority | Old one-GET approval depended on old invariants and aborted | v2 APPROVAL_PENDING; real-read authorization NONE | All four roles for contract; later explicit user/operator read authorization only after independent certification; PENDING |

Approval register (no signatures, timestamps or identities invented):

| Role | Contract decision | Implementation/security certification | Live certification | Real-read authorization |
|---|---|---|---|---|
| Security/authentication | PENDING | PENDING | PENDING | PENDING |
| Runtime/DB | PENDING | PENDING | PENDING | PENDING |
| Packaging/logging | PENDING | PENDING | PENDING | PENDING |
| User/operator | PENDING | PENDING | PENDING | PENDING |

Design completeness and green synthetic tests are engineering results, not entries
in this approval register. No trading authorization is requested or implied.

## Synthetic negative-case coverage and gaps

U = freshly rerun existing unit/architecture test. I = freshly rerun selected
disposable integration test. D = specified future test, NOT_EXECUTED because the
new boundary is not implemented. A U/I pass covers only the stated existing scope.
No newly proposed distributed or deployed guarantee is upgraded by local tests.
All fault outcomes require denial before dispatch or spent-budget result rejection.

Test base U: apps/trading-core/src/test/java/com/kitehybrid/platform/;
KiteEquityRead*Test classes are under broker/infrastructure/kite/.
I: apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadHarnessIntegrationTest.java.

| Negative case | Existing executable evidence / exact test anchor | New-boundary validation still required |
|---|---|---|
| Wrong owner/requester | U Handoff.everyFailedCheckBurnsCapabilityBeforeHttp(owner,recipient,restart) | D authenticated RPC principal/incarnation mismatch; no network |
| Wrong account/application | U Handoff locallyAuthenticatedKiteSessionIsSyntheticScopeNotIndependentBrokerProof; session replacement denies | D independently signed wrong account/app binding; local session identity is not account proof |
| Replay, expiry, revocation, clock reversal | U Handoff.inspectThenConsumeDoesNotExportSessionAndReplayIsDenied; everyFailedCheckBurnsCapabilityBeforeHttp; witnessLeaseFailClosedAndCannotRecover | D signed envelope replay, durable epoch floor, uncertainty bounds and revocation races |
| Competing replicas | U Handoff.concurrentConsumeHasExactlyOneWinner (two threads only) | D two processes/hosts claim same durable approval concurrently; exactly one claim, at most one attempt |
| Crash after one-use claim | U Isolation.independentChildCannotTakeHeldFixtureLockAndCrashReleasesIt is ONLY lock behavior, not durable budget | D kill claimant at commit/ack/pre-send boundaries; restarted owner denied; uncertain ack never grants send |
| Lost/uncertain response | U RequestFactory.lostResponseDoesNotRepeatGetOnAnotherConnection; Isolation.sameRequestObjectCannotExecuteAgainAfterResponseLoss; I response-loss | D process crash after dispatch plus ledger failover remains consumed; no receipt reconstruction grants retry |
| Forged/stale/conflicting HALT | U Handoff.unknownHaltEvidenceNeverProducesAnObservation; concurrentInvalidationDuringResponseDiscardsReceiptAndStillChecksIntegrity; RuntimeTradingHaltTest | D signature tampering, wrong issuer/audience, expired assertion, incomplete instance set, key revocation, replay and epoch rollback |
| Wrong method/endpoint | U RequestFactory.wrongMethodOrExactUriDeniedBeforeDispatch; syntheticOriginCannotEscapeLiteralLoopback | D service rejects envelope/body disagreement before claim and before send; no arbitrary URL |
| Redirect/retry | U RequestFactory.oneLoopbackRequestNoRedirectOrRetry; I 301/302/307/308, errors/timeouts | D certify client AND intermediary retry policy; no TLS-intercepting proxy |
| Unauthorized DB dependency | U ControlledEquityReadArchitectureTest direct dependency/call exclusions; I observerCannotWriteEvenIfItAttemptsDirectSql | D reject transitive DB/Redis/writer-RPC dependencies and runtime access; current observer itself uses JDBC |
| Startup side effects | U ControlledEquityReadArchitectureTest.isolatedHarnessCannotReachExecutionAuthenticationLifecycleOrStartup | D minimal entrypoint process under target policy makes no auth/DB/order/background calls; full artifact not certified |
| Logging leakage/config drift | U Handoff.capturedLogsDoNotContainSyntheticCredentialsOrBalances; verboseLoggerAbortsBeforeAnySensitiveRequest; RequestFactory.loggingChangeAfterRequestCreationBurnsAttemptWithoutDispatch | D complete sink inventory, OS/container/IDE/proxy capture, dynamic appender/agent injection and fail-closed sink loss |
| Capability/session serialization | U Handoff.serializationFailsWithoutExposingCapabilityOrCredentials; full adapter tests | D RPC codec must reject objects/imports and unknown fields; encrypted bearer export also forbidden |
| Credential transfer | U Handoff.officialWireCannotReceiveSyntheticHandoff; architecture handoffAndIsolationCannotAcquireNetworkCredentialsOrPersistCapabilities | D no API/file/env/CLI/IPC token ingress/export in service; independently inspect enrollment seal |
| Global nonmutation counterexample | I committedWriteAndRestorationBetweenSnapshotsCannotCertifyWriterExclusion; disablingFutureLoginsDoesNotExcludeAlreadyConnectedWriter; drainingOwnedDisposableWriterAndDenyingReconnectExcludesOnlyThatRole; permissionFingerprintDetectsDriftButNotRestorationBetweenCaptures | No global claim can follow; these counterexamples deliberately allow external fixture writes |
| Noncooperative writer | U Isolation.heldCooperativeLockCannotPreventAnIndependentNonParticipantFromActing | Cooperative locks do not certify global exclusion |
| Post-read drift | U Handoff.postReadIntegrityIsAttemptedEvenWhenCapabilityIsRevoked; I externalStateChangeIsDetectedAndNeverReportedAsVerified | D independently recheck HALT/provenance/ledger/sinks; result discarded and budget remains spent |

No redundant broad suite or toy ledger was added: tests of a model would not prove
the required durable store and deployed owner. New controls are explicitly DESIGNED
and their tests remain D. Existing disposable PostgreSQL uses fsync=off for fixture
speed; its passing results are not durable-ledger crash-consistency evidence.

## Historical and current evidence provenance

Phase A introduced synthetic local handoff; B added positive known-HALT and
immediate wire-log checks; C clarified synthetic provenance, concurrency and
noncooperating writers; D added scoped drain/privilege restoration counterexamples;
E selected minimal deployment direction; F proposed narrower assurance and C.
Phase F's two standalone full-suite errors remain historical and unchanged; its
separate WebSocket follow-up corrected Optional polling only. Fresh Phase G runs
are listed independently in validation and do not overwrite any historical result.

All references resolve to baseline a191b9c. No external broker protocol/account
evidence, human signature or live certification was acquired. Proposed auth flow
requires future independent verification against then-current authoritative broker
requirements. No source label or locally minted timestamp fills that gap.
