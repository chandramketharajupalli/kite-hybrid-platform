# Phase 14.14 independent authority options

Assessment only. INDEPENDENT_ROLLBACK_AUTHORITY_NOT_ESTABLISHED.
Evidence: [Phase 14.13 recovery](phase-14.13-durable-authority-recovery.md),
[validation](../operations/phase-14.13-validation.md), and the source ledger in
[cost and custody](../operations/phase-14.14-cost-and-custody.md).

Current call graph: GateAOfflinePolicy -> RecoverableGateAAuthority -> SignedHaltAuthority
and AuthorityCheckpointJournal; FileAttemptAuthority supplies the separate spent marker.
Journal state binds the authorization, highest epoch, observation time, sequence, consumed
flag and previous digest. The witness file lock serializes compare-and-advance. Immutable
CREATE_NEW witness records are forced before local records; acknowledgment follows both.
Missing, unequal or corrupt histories deny via RECOVERY_PENDING. Ordinary recovery cannot
clear consumption, revocation or quarantine. SignedHaltAuthority alone holds verifier state
in memory; the wrapper supplies bounded restart evidence. No production signing authority
or network dispatcher is established by these components.

Historical Phase13.9 FenceWitness uses PostgreSQL and a retained signed root. Trust still
depends on that root surviving; it does not prove separate administrative ownership.
Phase14.13 recorded 144 passing tests, including a two-grant negative control when both
histories and the spent marker are restored. These are historical tests, not fresh results.
Restoring all authoritative state can permit reuse. Hashes detect inconsistency, not a
privileged administrator reconstructing a coherent old history. No universal exactly-once
network delivery, physical power-loss durability or multi-host failover claim is made.

| Authority | Trust, read/write/restore and key custody | Atomicity/persistence and recovery | Rollback, network and failure boundary | Complexity; monthly / annual complete cost | Acceptance evidence |
|---|---|---|---|---|---|
| A local dual-file journal | Same administrator owns both histories and keys; no independent root | Tested file-lock CAS, forced records; disagreement denies; no repair/rearm | Surviving newer copy detects rollback; restoring all copies defeats it; local-only | Low infrastructure, manual recovery; TBD / TBD | Filesystem guarantees, separate retained root and custody would still be needed |
| B separate PostgreSQL, same administrator | DB roles may separate processes; administrator/backup owner can restore both; key owner unverified | Transactional conditional update proposed; old synthetic witness evidence is bounded | Remote location alone adds no privileged-rollback defense; outage must deny | Medium operations and backup; TBD / TBD | Exact DB/version, isolation/durability tests, IAM and restore audit; independent root absent |
| C independently administered remote witness | Proposed separate witness and backup administrators; no named verified custodian; restricted signer | Authenticated atomic compare-and-advance, sticky revocation, append audit and recovery ceremony required | Can resist host rollback only if host cannot reset witness, key or trusted endpoint; outage denies | High custody/service operations; TBD / TBD | Named administrators, access-denial proofs, restoration policy, key rotation, outage and concurrency tests |
| D managed conditional-write service: DynamoDB candidate | Service conditional-write primitive; account/root/IAM/backup authority still decisive; no verified independent account | Documented conditional writes; monotonic epoch policy must be implemented and tested; PITR restores to a new table | Admin changing client target to restored state is a threat; fixed identity and independently controlled policies required | Medium/high IAM and recovery; TBD / TBD | Region/account eligibility, complete billing model, exact atomic protocol and independent restore/key custody |
| E hardware-backed authority | No actual supported product/ownership established | UNKNOWN; no TPM or secure-element guarantee inferred | Reset/replacement/firmware/admin behavior unverified | UNKNOWN; TBD / TBD | Product documentation for monotonic operation, endurance/reset/power semantics, custody and recovery before selection |

A/B are useful bounded recovery mechanisms but do not meet independent-authority acceptance.
C/D are design candidates only. E is INSUFFICIENT_EVIDENCE. None currently qualifies.

| Threat | Existing A/B boundary | Required C/D control; unresolved limit |
|---|---|---|
| Ordinary process crash | Surviving consumed evidence denies replay | Durable acknowledgment semantics; never reopen uncertain attempt |
| Container restart | Not freshly measured | Persistent independently retained state, identity revalidation |
| Host restart | Not freshly measured | Storage recovery test and fail-closed boot |
| Local disk corruption | Journal corruption/mismatch denies historically | Trusted checkpoint reconstruction without reauthorization |
| Filesystem snapshot rollback | Surviving newer witness detects mismatch | Witness outside host restore authority |
| Administrator backup restore | Both stores may be restored | Separate backup authority and fixed trusted service identity |
| Compromised host administrator | Coherent local rollback remains possible | Remote policy must reject epoch decrease; admin cannot impersonate witness |
| Signing-key compromise | Hash journal is not a remedy | Independent revocation/key rotation; stolen live signer remains a serious risk |
| Witness rollback | Surviving local history detects mismatch | Independent retained monotonic checkpoint; all-root rollback still excluded |
| Network loss | Local fixtures do not prove production behavior | Deny, no retry, consumed uncertainty retained |
| External outage | No service provisioned | Deny until reviewed recovery; no fallback to local authority |
| Concurrent owners | Historical one-winner file CAS | Service conditional operation and competing-client test |
| Multi-host failover | NOT_ESTABLISHED | Shared atomic authority, fencing and split-brain tests |
| Physical power loss | NOT_VERIFIED | Storage/provider evidence and approved durability validation; no destructive host experiment |

Authenticated communication, restricted keys, immutable audit custody, durable revocation
and independent backup ownership are separate acceptance gates. Physical separation alone
satisfies none of them. No hardware or external service is procured or provisioned.
