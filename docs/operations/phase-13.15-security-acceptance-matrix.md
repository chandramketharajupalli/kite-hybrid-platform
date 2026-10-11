# Phase 13.15 security acceptance matrix

Design specification complete does not mean control verified. All future control
implementations are UNVERIFIED; DESIGNED below describes the protocol only. VERIFIED
is restricted to source/hash/document checks performed here. Named human authorities
are not assigned; roles below are proposed responsibilities, not approvals.

| ID / requirement | Design / implementation state | Evidence required before future acceptance | Proposed authority / next action |
|---|---|---|---|
| G01 baseline/protected records | VERIFIED / preserved | Exact Git/G1/18/63/K/L/prior envelopes and before/after hashes | Evidence observer; repeat at next phase entry |
| G02 host capacity/hypervisor/license | DESIGNED / UNVERIFIED | Read-only capacity, installed supported hypervisor, reserve and license assessment | User/operator plus host admin; authorize inventory only |
| G03 exact OS image and patches | DESIGNED / UNVERIFIED | Vendor signed checksum/key provenance, image hash, package/kernel inventory and supported lifecycle | Security/authentication plus platform custodian; approve exact acquisition separately |
| G04 boot/time | DESIGNED / UNVERIFIED | Guest and host boot state, measurement provenance if claimed, time source/uncertainty; clock-reversal denial | Runtime/DB plus host admin; no vTPM monotonicity assumption |
| G05 enforcing AppArmor | DESIGNED / UNVERIFIED | Loaded profile/hash/enforce, actual Java label, authorized positive, file/process/network denials and audit attribution after restart | Packaging/logging plus guest policy admin; no unconfined fallback |
| G06 user namespaces | DESIGNED / UNVERIFIED | Host/container uid_map/gid_map/namespace ownership, subordinate IDs, nonidentity ACL probe; physical-host boundary separately documented | Runtime/DB plus host observer; reject identity map |
| G07 privilege/resource isolation | DESIGNED / UNVERIFIED | CapEff0/NNP1/seccomp/non-root/read-only/private mounts/cgroup/PID measurements and negative probes | Packaging/logging; actual Java execution required |
| G08 OCI/JDK authenticity | DESIGNED / UNVERIFIED | Authorized signer/root, exact digest/predicate, validity/revocation and verification output; runtime/module/package inventory | Security/authentication; OCI_SIGNATURE_NOT_VERIFIED until evidence |
| G09 network | DESIGNED / UNVERIFIED | Host/guest/container topology, counters and controlled denied IPv4/IPv6/DNS/proxy/metadata/gateway/redirect attempts; positive loopback | Runtime/DB plus network custodian; no real broker probe |
| G10 diagnostic/external sinks | DESIGNED / UNVERIFIED | All destinations/custodians/ACL/encryption/retention/audit, bounded canary positives/negatives, no exported dump | Packaging/logging plus sink owners; unknown collector blocks |
| G11 actual owner and HALT | DESIGNED / UNVERIFIED | Same Java request path, RSA/epoch/revocation/scope negatives, one attempted send and <=1 receipt; replay0 | Security/authentication plus test observer; synthetic only |
| G12 witness authority | DESIGNED / UNVERIFIED | Actor/restore matrix, independently retained root, signed advance, key custody and quarantine recovery proof | Runtime/DB plus distinct root custodian; common-admin limit explicit |
| G13 recovery/rollback | DESIGNED / UNVERIFIED | Races, uncertain ACK, kill/restart, local rollback surviving root, full-root two-grant/no-HTTP counterexample | Runtime/DB; no physical power-loss or universal claim |
| G14 budget/cleanup | DESIGNED / UNVERIFIED | Max3 pre-create admission, helper separately counted, labels/IDs/finally/abnormal-exit cleanup; final zero-owned inventory | User/operator/test custodian; breach stops broader tests |
| G15 evidence and review | DESIGNED / UNVERIFIED | Exact commands/versions/exits/XML, raw/canonical hashes, retained failures and attributable review | Four separate domain roles; no AI signature |

State vocabulary: DESIGNED, VERIFIED, UNVERIFIED, UNAVAILABLE, FAILED. Historical
Desktop LSM unavailability does not predetermine the proposed guest's outcome.
An unavailable mandatory control blocks security acceptance; it is not waived.

Decision dimensions:

- TESTBED_DESIGN_READY: scoped documentation, complete evidence gates and offline
  checks pass. No actual platform assurance is promoted.
- PROVISIONING_AUTHORIZATION_PENDING: exact hypervisor/image/network/cost/custodian
  proposal awaits separate user authorization; no VM exists from this phase.
- TESTBED_SECURITY_CERTIFIED: NOT_AVAILABLE_IN_THIS_PHASE; all mandatory deployed
  testbed gates need new evidence and review.
- OPERATIONAL_NO_GO: permanent for this phase; V2, second review and four domains PENDING.

Overall design result may be LINUX_VM_TESTBED_DESIGN_READY_PROVISIONING_PENDING while
all implementation controls remain UNVERIFIED. Unsupported host/provenance/root/sink
choices require revised additive proposal and explicit authorization, never auto-fallback.
