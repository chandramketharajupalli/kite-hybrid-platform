# Phase 14.13 frozen plan

Baseline develop HEAD/local origin/develop:
0e3f347e401e34e92c34101b56f714a36568a5d4. Clean index/worktree; sixteen Phase14.12
files committed. Baseline1131 tracked files; canonical raw-hash inventory fingerprint
864b9fb3e0bd974eb69e2a407dc1f63c0433db2ce83acdadf78d6756425cb3b0.
Phase14.0-14.12 evidence,13 newline reconciliations,G1/18/63/K-L and1115 universe passed.

Audit: SignedHaltAuthority holds epoch/revocation/quarantine/clock observations in
memory. FileAttemptAuthority retains attempt retirement but has no independent root.
GateAOfflinePolicy accepts both ports and remains fixture-only. SessionCustody has
no implementation. Earlier signed witness/process-kill evidence is test-only and
retains the complete-authority rollback counterexample. No production issuer exists.

Permitted additions only: AuthorityCheckpointJournal.java and RecoverableGateAAuthority.java
under apps/gate-a-offline-adapters/src/main/java/com/kitehybrid/gatea/adapters/;
AuthorityRecoveryTest.java, AuthorityRecoveryProbe.java and AuthorityRecoveryIT.java
under the matching test directory; this plan, architecture/phase-14.13-durable-authority-recovery.md,
operations/phase-14.13-host-and-authority-decision.md, operations/phase-14.13-runbook.md,
operations/phase-14.13-validation.md and research/phase-14.13/generation-1/evidence-manifest.json.
Existing source, POMs and historical evidence remain unchanged. Existing compiler allowlist
already includes the new adapter package. No new dependencies or launcher.

Implement bounded append-only local/checkpoint records with exact authorization binding,
current/highest epoch, sticky denial state, sequence, time, chain digest and synthetic
provenance. Serialize compare-and-advance using the witness file lock; force witness
before local ACK. Missing/corrupt/mismatched/partial history yields RECOVERY_PENDING
and never auto-repairs/arms. Two directories model separately retained authority only,
not independent administration. Integrate with signed HALT and existing attempt store.
Permit CONSUMED rechecks only inside the successful consuming wrapper incarnation;
restart never grants consumed authority. Local clock rollback/invalid signatures deny
and persist quarantine when storage is available. No retry after uncertainty.

Threats: stale epoch/key/scope, revoke replay, process loss, concurrent advance,
partial writes/lost ACK, local/checkpoint rollback, corruption and diagnostic leakage.
States: UNINITIALIZED,ACTIVE_SYNTHETIC,CONSUMED,REVOKED,QUARANTINED,RECOVERY_PENDING.
Only explicit new fixture initialization can enter ACTIVE_SYNTHETIC. Denial never
clears through restart/availability. No operational HALT action or approval is created.

Tests: sticky revocation/epoch/clock recovery; stale/conflicting identity; corruption,
missing checkpoint; two-process compare-and-advance; crash before/after reservation;
lost ACK; local rollback with witness surviving; witness rollback; complete rollback
two-grant negative control; integrated policy replay/uncertain outcome; redaction/Gate B.
Run full adapter unit and explicit filesystem/process integration suite, plus unchanged
policy suite as relevant. Retain failures and reruns. Offline Maven only, one build at a
time; at most two children,128MiB each,30-second timeout; parent256MiB, owned temp paths.
No containers/DB/images/host changes/power-loss tests. No credentials or broker/network I/O.

Non-guarantees: signature issuer/owner custody, independently fresh external checkpoints,
directory metadata/power-loss durability, replicated failover, admin rollback, production
OS/egress/diagnostic containment. Restoring all authority can permit reuse. Preserve this
counterexample. Production decision remains conditional, with historical host RAM clearly
dated and no procurement/provisioning. Terms and all eleven reply topics remain unresolved.

Final checks: baseline raw hashes, plans/manifests/protected evidence, universe1140/25/1115,
exact inventory,new hashes,packaged dependencies,secrets/canaries,LF/Git bytes,two replays.
Safety/preservation failure BLOCKED; incomplete mandatory recovery/isolation PARTIAL.
No stage/commit/push/fetch/reset/clean/config changes. Preserve Gate A/B NOT_AUTHORIZED,
NO_GO,ZERO_CALL_ABORT,Phase12BLOCK,H1/H2FROZEN,JulySEALED,INR10000 and pending reviews.
Hash plan before implementation; any correction additive. Stop after reporting.
