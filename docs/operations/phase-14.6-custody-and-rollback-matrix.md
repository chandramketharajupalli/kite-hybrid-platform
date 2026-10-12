# Custody and one-use assessment

VERIFIED means the explicitly named fixture/document fact; PARTIAL means bounded
evidence with remaining gaps; DESIGNED means proposal only; NOT_ESTABLISHED means
missing operational proof. Historical results are not freshly executed attestations.

## Request and rollback controls

| Control | Classification | Evidence / limit |
|---|---|---|
| Reservation before send | VERIFIED fixture | Phase14.5 GateABoundary.java -> AttemptJournal.retire; fresh GateALedgerIT |
| Uncertain acknowledgement | VERIFIED fixture | GateABoundaryTest.uncertainOutcomeNeverRefundsOrRetries; GateALedgerIT.uncertainForceAcknowledgmentConsumesWithoutGranting |
| Process termination | VERIFIED fixture | GateALedgerIT crashBeforeSendNeverReplaysAfterMarkerCreation; before-create test allows one later attempt with no prior send |
| Restart/replay | VERIFIED intact local authority | GateALedgerIT.restartAndFailoverNeverRefundSuccessfulAttempt; not cross-host consensus |
| Two processes | VERIFIED shared journal | GateALedgerIT.twoProcessesCannotBothConsumeSameAuthority |
| Witness persistence | PARTIAL historical | docs/operations/phase-13.10-validation.md and phase-13.11-validation.md retain witness/root recovery; no real independent authority |
| Local rollback with surviving witness | VERIFIED historical fixture | docs/operations/phase-13.9-validation.md, measured local journal/DB restoration cases; not present in GateA local-only composition |
| Complete authority rollback | VERIFIED negative | Phase13.9 all-root counterexample and fresh GateALedgerIT.allAuthorityRollbackCounterexampleRemainsExplicit show another grant |
| Physical power loss | NOT_ESTABLISHED | Phase13.7/13.8 validation limits; force does not prove directory/drive power-loss behavior |
| Multi-host failover | NOT_ESTABLISHED | Phase13.9/13.10 retained limits; new JVMs on one host are not quorum/failover proof |
| Independent root recovery | DESIGNED operationally | docs/operations/phase-13.15-security-acceptance-matrix.md G12/G13 and phase-13.16-security-gate-matrix.md |

No universal exactly-once-delivery claim. Tested at-most-once admission depends on
retaining the same authority/store. Missing/partial/uncertain state denies; restoring
every authority defeats local histories. Signatures alone cannot detect that restore.

## Solo operator and custody

| Dimension | Assessment | Needed before production |
|---|---|---|
| Declared authority | User-declared sole developer/operator preserved | Do not convert declaration into independent verification |
| Verified authority | NOT_ESTABLISHED | Attributable account/session and authorization provenance |
| Technical role separation | DESIGNED | Distinct process/service identities, ACLs and scoped capabilities can constrain ordinary processes |
| Independent administration | NOT_ESTABLISHED | Another VM under the same administrator does not survive administrator-wide rollback |
| Credential ownership | NOT_ESTABLISHED | Legitimate owner-held session, no restore; separate approved custody workflow |
| Witness checkpoint ownership | NOT_ESTABLISHED | Independently retained checkpoint/root and separate restore authority or externally attested service |
| External logging custody | NOT_ESTABLISHED | Named sink custodians, access/retention/export controls and independent evidence |
| Backup/rollback authority | NOT_ESTABLISHED | Actor/restore matrix covering host,VM,ledger,witness,keys,checkpoints and logs |

Separate OS users improve role isolation but do not remove common root/administrator
power. Independence requires a separately controlled custodian/service with attributable
restore and access policies; none is supplied or provisioned here. Self-review remains
self-review. Existing V2/governance second review/four technical reviews stay PENDING.
No credential, witness key, account or personal information was accessed for this review.
