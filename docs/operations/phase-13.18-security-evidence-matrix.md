# Phase 13.18 candidate security evidence matrix

Exact names and semantics retained from [Phase13.15](phase-13.15-security-acceptance-matrix.md)
and [Phase13.17](phase-13.17-security-gate-matrix.md). Five records x15 gates=75cells.
C0 is an excluded upgrade path, retained explicitly rather than silently omitted.
MP=MEASURED_PASS; DC=DOCUMENTED_CAPABILITY_NOT_TESTED; U=UNKNOWN. No cell is waived.
G01 is repository preservation only, never candidate-host certification. G02 DC
means documented nominal allocation/options only, not measured headroom or licensing.
G05/G06 DC refers to proposed Ubuntu/Docker mechanisms, not a certified product feature.
C0 upgraded G02 is UNKNOWN; its existing unupgraded8GiB-host target is historical FAIL.

| Exact source gate | A1 | A2 | B1 | B2 | C0 |
|---|---|---|---|---|---|
| G01 baseline/protected records | MP | MP | MP | MP | MP |
| G02 host capacity/hypervisor/license | DC | DC | DC | DC | U |
| G03 exact OS image and patches | U | U | U | U | U |
| G04 boot/time | U | U | U | U | U |
| G05 enforcing AppArmor | DC | DC | DC | DC | U |
| G06 user namespaces | DC | DC | DC | DC | U |
| G07 privilege/resource isolation | U | U | U | U | U |
| G08 OCI/JDK authenticity | U | U | U | U | U |
| G09 network | U | U | U | U | U |
| G10 diagnostic/external sinks | U | U | U | U | U |
| G11 actual owner and HALT | U | U | U | U | U |
| G12 witness authority | U | U | U | U | U |
| G13 recovery/rollback | U | U | U | U | U |
| G14 budget/cleanup | U | U | U | U | U |
| G15 evidence and review | U | U | U | U | U |

## Evidence, accountable authority and future tests for every cell

Sources S01-S20 are in the [shortlist](phase-13.18-candidate-shortlist.md). E15 is
the source matrix, E17 the retained previous assessment; neither is a candidate test.
Each row below applies separately to all five candidate cells. Named humans are
not assigned; role ownership must be explicitly established. Failure or missing
evidence blocks future acceptance for that candidate; no score overrides a gate.

| Exact gate | Evidence now | Required future proof | Proposed accountable authority |
|---|---|---|---|
| G01 baseline/protected records | Before/after repository hashes only | Exact Git/G1/18/63/K/L/prior envelopes and before/after hashes | Evidence observer; repeat at next phase entry |
| G02 host capacity/hypervisor/license | A1 S01-S03; A2 S04-S06; B1 S07-S10; B2 S11-S12; C0 CIM/E16/S16 incomplete | Read-only capacity, installed supported hypervisor, reserve and license assessment | User/operator plus host admin; authorize inventory only |
| G03 exact OS image and patches | E15/E17 design only; no new candidate execution | Vendor signed checksum/key provenance, image hash, package/kernel inventory and supported lifecycle | Security/authentication plus platform custodian; approve exact acquisition separately |
| G04 boot/time | E15/E17 design only; no new candidate execution | Guest and host boot state, measurement provenance if claimed, time source/uncertainty; clock-reversal denial | Runtime/DB plus host admin; no vTPM monotonicity assumption |
| G05 enforcing AppArmor | S14 generic Ubuntu mechanism; C0 unqualified | Loaded profile/hash/enforce, actual Java label, authorized positive, file/process/network denials and audit attribution after restart | Packaging/logging plus guest policy admin; no unconfined fallback |
| G06 user namespaces | S15 generic Docker mechanism; C0 unqualified | Host/container uid_map/gid_map/namespace ownership, subordinate IDs, nonidentity ACL probe; physical-host boundary separately documented | Runtime/DB plus host observer; reject identity map |
| G07 privilege/resource isolation | E15/E17 design only; no new candidate execution | CapEff0/NNP1/seccomp/non-root/read-only/private mounts/cgroup/PID measurements and negative probes | Packaging/logging; actual Java execution required |
| G08 OCI/JDK authenticity | E15/E17 design only; no new candidate execution | Authorized signer/root, exact digest/predicate, validity/revocation and verification output; runtime/module/package inventory | Security/authentication; OCI_SIGNATURE_NOT_VERIFIED until evidence |
| G09 network | E15/E17 design only; no new candidate execution | Host/guest/container topology, counters and controlled denied IPv4/IPv6/DNS/proxy/metadata/gateway/redirect attempts; positive loopback | Runtime/DB plus network custodian; no real broker probe |
| G10 diagnostic/external sinks | E15/E17 design only; no new candidate execution | All destinations/custodians/ACL/encryption/retention/audit, bounded canary positives/negatives, no exported dump | Packaging/logging plus sink owners; unknown collector blocks |
| G11 actual owner and HALT | E15/E17 design only; no new candidate execution | Same Java request path, RSA/epoch/revocation/scope negatives, one attempted send and <=1 receipt; replay0 | Security/authentication plus test observer; synthetic only |
| G12 witness authority | E15/E17 design only; no new candidate execution | Actor/restore matrix, independently retained root, signed advance, key custody and quarantine recovery proof | Runtime/DB plus distinct root custodian; common-admin limit explicit |
| G13 recovery/rollback | E15/E17 design only; no new candidate execution | Races, uncertain ACK, kill/restart, local rollback surviving root, full-root two-grant/no-HTTP counterexample | Runtime/DB; no physical power-loss or universal claim |
| G14 budget/cleanup | E15/E17 design only; no new candidate execution | Max3 pre-create admission, helper separately counted, labels/IDs/finally/abnormal-exit cleanup; final zero-owned inventory | User/operator/test custodian; breach stops broader tests |
| G15 evidence and review | E15/E17 design only; no new candidate execution | Exact commands/versions/exits/XML, raw/canonical hashes, retained failures and attributable review | Four separate domain roles; no AI signature |

Per-candidate blockers and custody differences are in the shortlist and architecture.
The manifest expands all75 cells with status, source, scope, future test, authority
and consequence. Summary:5 repository-only MP,12 documented-not-tested,58 UNKNOWN.
Actual candidate LSM/userns/boot/network/sinks/Java/HALT/witness/recovery remain
unverified. OCI_SIGNATURE_NOT_VERIFIED for all. No four-domain decision completed.
