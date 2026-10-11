# Phase 13.19 exact security gate matrix

Exactly75 records in the evidence manifest, one per candidate/gate. Fifteen names
and acceptance criteria below are copied from [Phase13.15](phase-13.15-security-acceptance-matrix.md),
with [Phase13.16](phase-13.16-security-gate-matrix.md) and
[Phase13.17](phase-13.17-security-gate-matrix.md) mappings preserved.
MP=MEASURED_PASS (repository only); DC=DOCUMENTED_CAPABILITY_NOT_TESTED;
U=UNKNOWN. DC is an inherited generic mechanism/catalog sub-observation, not a
whole candidate gate pass. All70 non-G01 whole candidate gates NOT_VERIFIED.
No candidate was provisioned or tested. No N/A waiver. Historical C0 as-is RAM
failure is not a new upgraded-host test. E18 is preserved Phase13.18; E15 source criteria.

| Exact gate | A1 | A2 | B1 | B2 | C0 |
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

## Exact acceptance criteria and proposed authorities

Each row applies separately to each candidate. The manifest expands source, scope,
status, exact criteria, authority, consequence and no-promotion transition for75cells.

| Exact gate | Exact required evidence | Exact proposed authority / next action |
|---|---|---|
| G01 baseline/protected records | Exact Git/G1/18/63/K/L/prior envelopes and before/after hashes | Evidence observer; repeat at next phase entry |
| G02 host capacity/hypervisor/license | Read-only capacity, installed supported hypervisor, reserve and license assessment | User/operator plus host admin; authorize inventory only |
| G03 exact OS image and patches | Vendor signed checksum/key provenance, image hash, package/kernel inventory and supported lifecycle | Security/authentication plus platform custodian; approve exact acquisition separately |
| G04 boot/time | Guest and host boot state, measurement provenance if claimed, time source/uncertainty; clock-reversal denial | Runtime/DB plus host admin; no vTPM monotonicity assumption |
| G05 enforcing AppArmor | Loaded profile/hash/enforce, actual Java label, authorized positive, file/process/network denials and audit attribution after restart | Packaging/logging plus guest policy admin; no unconfined fallback |
| G06 user namespaces | Host/container uid_map/gid_map/namespace ownership, subordinate IDs, nonidentity ACL probe; physical-host boundary separately documented | Runtime/DB plus host observer; reject identity map |
| G07 privilege/resource isolation | CapEff0/NNP1/seccomp/non-root/read-only/private mounts/cgroup/PID measurements and negative probes | Packaging/logging; actual Java execution required |
| G08 OCI/JDK authenticity | Authorized signer/root, exact digest/predicate, validity/revocation and verification output; runtime/module/package inventory | Security/authentication; OCI_SIGNATURE_NOT_VERIFIED until evidence |
| G09 network | Host/guest/container topology, counters and controlled denied IPv4/IPv6/DNS/proxy/metadata/gateway/redirect attempts; positive loopback | Runtime/DB plus network custodian; no real broker probe |
| G10 diagnostic/external sinks | All destinations/custodians/ACL/encryption/retention/audit, bounded canary positives/negatives, no exported dump | Packaging/logging plus sink owners; unknown collector blocks |
| G11 actual owner and HALT | Same Java request path, RSA/epoch/revocation/scope negatives, one attempted send and <=1 receipt; replay0 | Security/authentication plus test observer; synthetic only |
| G12 witness authority | Actor/restore matrix, independently retained root, signed advance, key custody and quarantine recovery proof | Runtime/DB plus distinct root custodian; common-admin limit explicit |
| G13 recovery/rollback | Races, uncertain ACK, kill/restart, local rollback surviving root, full-root two-grant/no-HTTP counterexample | Runtime/DB; no physical power-loss or universal claim |
| G14 budget/cleanup | Max3 pre-create admission, helper separately counted, labels/IDs/finally/abnormal-exit cleanup; final zero-owned inventory | User/operator/test custodian; breach stops broader tests |
| G15 evidence and review | Exact commands/versions/exits/XML, raw/canonical hashes, retained failures and attributable review | Four separate domain roles; no AI signature |

G02 fresh catalog sources: A1 P01/P02; A2 P03/P04; B1 P07/P13; B2 P08/P19;
C0 E18 only. G05/G06 generic capability evidence remains historical E18, not fresh
candidate measurements. G01 fresh before/after repository hashes. Other gates
retain E15/E18 requirements and absent candidate proof. Every failed or missing
mandatory requirement blocks future security acceptance; procurement hard blockers
also include quote/terms/custody and cannot be offset by a score.

Counts:5 repository-only MP,12 DC,58 U;0 candidate-specific measured security passes.
OCI_SIGNATURE_NOT_VERIFIED, LSM/userns/sinks and witness independence unverified.
G15 stays unknown despite document checks because human review is still pending.
Seven proposed custody functions are not replacements for the four review domains.
