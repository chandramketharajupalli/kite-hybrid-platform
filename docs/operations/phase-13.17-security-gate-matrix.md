# Phase 13.17 exact fifteen-gate option mapping

Controlling [Phase13.15 matrix](phase-13.15-security-acceptance-matrix.md) and
[Phase13.16 mapping](phase-13.16-security-gate-matrix.md). Labels and acceptance
semantics retained verbatim. Proposed authority functions are not authenticated
people or decisions. Evidence IDs resolve in the [comparison](phase-13.17-option-comparison.md).

Each row's required test, authority and failure consequence applies separately to
all three option cells. A/B/C suffix adds the distinct inspection boundary. Thus
all45 cells have status, source, required test, authority and consequence. G01 is
repository-only measured evidence. No prospective machine is certified. UNKNOWN
includes missing price/identity/trust evidence, not a rejection vote.

| Exact gate / requirement | A | B | C | Required evidence/test for each option | Proposed accountable authority / next action | Failure consequence |
|---|---|---|---|---|---|---|
| G01 baseline/protected records | MEASURED_PASS; baseline/parent hashes; A boundary | MEASURED_PASS; baseline/parent hashes; B boundary | MEASURED_PASS; baseline/parent hashes; C boundary | Exact Git/G1/18/63/K/L/prior envelopes and before/after hashes | Evidence observer; repeat at next phase entry | Stop assessment on mismatch |
| G02 host capacity/hypervisor/license | UNKNOWN; E16,P04,P05,P06,U; A boundary | UNKNOWN; E16,P04,P05,P06,U; B boundary | UNKNOWN; E16,P04,P05,P06,U; C boundary | Read-only capacity, installed supported hypervisor, reserve and license assessment | User/operator plus host admin; authorize inventory only | No provisioning selection until capacity/license/cost resolved |
| G03 exact OS image and patches | DESIGN_FEASIBLE_NOT_TESTED; D15,P01,U; A boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,P01,U; B boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,P01,U; C boundary | Vendor signed checksum/key provenance, image hash, package/kernel inventory and supported lifecycle | Security/authentication plus platform custodian; approve exact acquisition separately | No security acceptance; keep relevant approval pending |
| G04 boot/time | UNKNOWN; D15,U; A boundary | UNKNOWN; D15,U; B boundary | UNKNOWN; D15,U; C boundary | Guest and host boot state, measurement provenance if claimed, time source/uncertainty; clock-reversal denial | Runtime/DB plus host admin; no vTPM monotonicity assumption | No security acceptance; keep relevant approval pending |
| G05 enforcing AppArmor | DESIGN_FEASIBLE_NOT_TESTED; D15,P02,A; A boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,P02,A; B boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,P02,A; C boundary | Loaded profile/hash/enforce, actual Java label, authorized positive, file/process/network denials and audit attribution after restart | Packaging/logging plus guest policy admin; no unconfined fallback | No security acceptance; keep relevant approval pending |
| G06 user namespaces | DESIGN_FEASIBLE_NOT_TESTED; D15,P03,P09,A; A boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,P03,P09,A; B boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,P03,P09,A; C boundary | Host/container uid_map/gid_map/namespace ownership, subordinate IDs, nonidentity ACL probe; physical-host boundary separately documented | Runtime/DB plus host observer; reject identity map | No security acceptance; keep relevant approval pending |
| G07 privilege/resource isolation | DESIGN_FEASIBLE_NOT_TESTED; D15,A; A boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,A; B boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,A; C boundary | CapEff0/NNP1/seccomp/non-root/read-only/private mounts/cgroup/PID measurements and negative probes | Packaging/logging; actual Java execution required | No security acceptance; keep relevant approval pending |
| G08 OCI/JDK authenticity | UNKNOWN; D15/D16,U; A boundary | UNKNOWN; D15/D16,U; B boundary | UNKNOWN; D15/D16,U; C boundary | Authorized signer/root, exact digest/predicate, validity/revocation and verification output; runtime/module/package inventory | Security/authentication; OCI_SIGNATURE_NOT_VERIFIED until evidence | No security acceptance; keep relevant approval pending |
| G09 network | DESIGN_FEASIBLE_NOT_TESTED; D15,P08,A; A boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,P08,A; B boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,P08,A; C boundary | Host/guest/container topology, counters and controlled denied IPv4/IPv6/DNS/proxy/metadata/gateway/redirect attempts; positive loopback | Runtime/DB plus network custodian; no real broker probe | No security acceptance; keep relevant approval pending |
| G10 diagnostic/external sinks | UNKNOWN; D15/D16,U; A boundary | UNKNOWN; D15/D16,U; B boundary | UNKNOWN; D15/D16,U; C boundary | All destinations/custodians/ACL/encryption/retention/audit, bounded canary positives/negatives, no exported dump | Packaging/logging plus sink owners; unknown collector blocks | No security acceptance; keep relevant approval pending |
| G11 actual owner and HALT | DESIGN_FEASIBLE_NOT_TESTED; D15,A; prior synthetic evidence historical; A boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,A; prior synthetic evidence historical; B boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,A; prior synthetic evidence historical; C boundary | Same Java request path, RSA/epoch/revocation/scope negatives, one attempted send and <=1 receipt; replay0 | Security/authentication plus test observer; synthetic only | No security acceptance; keep relevant approval pending |
| G12 witness authority | UNKNOWN; D15/D16,U; A boundary | UNKNOWN; D15/D16,U; B boundary | UNKNOWN; D15/D16,U; C boundary | Actor/restore matrix, independently retained root, signed advance, key custody and quarantine recovery proof | Runtime/DB plus distinct root custodian; common-admin limit explicit | No security acceptance; keep relevant approval pending |
| G13 recovery/rollback | DESIGN_FEASIBLE_NOT_TESTED; D15,A; permanent rollback counterexample historical; A boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,A; permanent rollback counterexample historical; B boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,A; permanent rollback counterexample historical; C boundary | Races, uncertain ACK, kill/restart, local rollback surviving root, full-root two-grant/no-HTTP counterexample | Runtime/DB; no physical power-loss or universal claim | No security acceptance; keep relevant approval pending |
| G14 budget/cleanup | DESIGN_FEASIBLE_NOT_TESTED; D15,A; A boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,A; B boundary | DESIGN_FEASIBLE_NOT_TESTED; D15,A; C boundary | Max3 pre-create admission, helper separately counted, labels/IDs/finally/abnormal-exit cleanup; final zero-owned inventory | User/operator/test custodian; breach stops broader tests | No security acceptance; keep relevant approval pending |
| G15 evidence and review | UNKNOWN; D15/D16,U; A boundary | UNKNOWN; D15/D16,U; B boundary | UNKNOWN; D15/D16,U; C boundary | Exact commands/versions/exits/XML, raw/canonical hashes, retained failures and attributable review | Four separate domain roles; no AI signature | No security acceptance; keep relevant approval pending |

A boundary = physical-host/root/backup observation; B = provider/account/guest/remote
sinks; C = upgraded Windows/hypervisor/guest. These are future required tests, NOT_RUN.
G02 for C is UNKNOWN for the proposed upgrade; its unchanged current-host subcase
is MEASURED_FAIL historically (E16 RAM), not a fresh failed test. No available A/B
machine or upgraded C exists from this phase. No gate is dropped or renamed.

Counts:3 MEASURED_PASS repository cells,24 DESIGN_FEASIBLE_NOT_TESTED cells,
18 UNKNOWN cells;0 prospective-host MEASURED_PASS. UNAVAILABLE/MEASURED_FAIL are
permitted states but not assigned to an untested future machine. D15 G05 remains
specifically enforcing AppArmor; changing it to SELinux requires separate design,
not an implicit equivalence. Historical Desktop unavailable LSM/identity maps do
not predetermine A/B/C future enforcement.

G15 remains UNKNOWN as a whole despite current document/replay checks: attributable
four-domain and governance decisions are pending. Gate owners must provide real
evidence; Codex does not assign identities or sign. Any missing mandatory evidence
keeps security acceptance pending. Provisioning remains NOT_AUTHORIZED.
