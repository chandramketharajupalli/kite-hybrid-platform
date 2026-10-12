# Phase 13.20 security and custody matrix

Exact labels/criteria copied from [Phase13.19](phase-13.19-security-gate-matrix.md). The [manifest](../../research/phase-13.20/generation-1/evidence-manifest.json) records75 candidate rows with source, authority, scope, transition and failure consequence.

| Gate | Exact label | Exact required evidence |
|---|---|---|
| G01 | baseline/protected records | Exact Git/G1/18/63/K/L/prior envelopes and before/after hashes |
| G02 | host capacity/hypervisor/license | Read-only capacity, installed supported hypervisor, reserve and license assessment |
| G03 | exact OS image and patches | Vendor signed checksum/key provenance, image hash, package/kernel inventory and supported lifecycle |
| G04 | boot/time | Guest and host boot state, measurement provenance if claimed, time source/uncertainty; clock-reversal denial |
| G05 | enforcing AppArmor | Loaded profile/hash/enforce, actual Java label, authorized positive, file/process/network denials and audit attribution after restart |
| G06 | user namespaces | Host/container uid_map/gid_map/namespace ownership, subordinate IDs, nonidentity ACL probe; physical-host boundary separately documented |
| G07 | privilege/resource isolation | CapEff0/NNP1/seccomp/non-root/read-only/private mounts/cgroup/PID measurements and negative probes |
| G08 | OCI/JDK authenticity | Authorized signer/root, exact digest/predicate, validity/revocation and verification output; runtime/module/package inventory |
| G09 | network | Host/guest/container topology, counters and controlled denied IPv4/IPv6/DNS/proxy/metadata/gateway/redirect attempts; positive loopback |
| G10 | diagnostic/external sinks | All destinations/custodians/ACL/encryption/retention/audit, bounded canary positives/negatives, no exported dump |
| G11 | actual owner and HALT | Same Java request path, RSA/epoch/revocation/scope negatives, one attempted send and <=1 receipt; replay0 |
| G12 | witness authority | Actor/restore matrix, independently retained root, signed advance, key custody and quarantine recovery proof |
| G13 | recovery/rollback | Races, uncertain ACK, kill/restart, local rollback surviving root, full-root two-grant/no-HTTP counterexample |
| G14 | budget/cleanup | Max3 pre-create admission, helper separately counted, labels/IDs/finally/abnormal-exit cleanup; final zero-owned inventory |
| G15 | evidence and review | Exact commands/versions/exits/XML, raw/canonical hashes, retained failures and attributable review |


| Gate | A1 | A2 | B1 | B2 | C0 |
|---|---|---|---|---|---|
| G01 baseline/protected records | MEASURED_PASS | MEASURED_PASS | MEASURED_PASS | MEASURED_PASS | MEASURED_PASS |
| G02 host capacity/hypervisor/license | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| G03 exact OS image and patches | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| G04 boot/time | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| G05 enforcing AppArmor | DESIGN_FEASIBLE_NOT_TESTED | DESIGN_FEASIBLE_NOT_TESTED | DESIGN_FEASIBLE_NOT_TESTED | DESIGN_FEASIBLE_NOT_TESTED | UNKNOWN |
| G06 user namespaces | DESIGN_FEASIBLE_NOT_TESTED | DESIGN_FEASIBLE_NOT_TESTED | DESIGN_FEASIBLE_NOT_TESTED | DESIGN_FEASIBLE_NOT_TESTED | UNKNOWN |
| G07 privilege/resource isolation | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| G08 OCI/JDK authenticity | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| G09 network | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| G10 diagnostic/external sinks | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| G11 actual owner and HALT | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| G12 witness authority | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| G13 recovery/rollback | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| G14 budget/cleanup | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |
| G15 evidence and review | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |


Five MEASURED_PASS cells are repository G01 only. Eight DESIGN_FEASIBLE_NOT_TESTED cells retain generic G05/G06 mechanisms for A1/A2/B1/B2, not enforcement. Sixty-two UNKNOWN cells include combined G02 capacity/hypervisor/license: catalog RAM cannot satisfy whole requirement. Vocabulary mapping was frozen before research; no historical enum or acceptance criterion changed. All70 non-G01 whole candidate gates remain NOT_VERIFIED. No waiver or procurement authority.

## Responsibility

| Function | Status | Assignment | Required evidence |
|---|---|---|---|
| owner/operator | USER_ASSERTED | USER_DECLARED_ONLY | Attributable bounded acquisition decision; identity/authority independently unverified |
| infrastructure administration | PROPOSED | UNFILLED | Admin/MFA/snapshot rights and least privilege |
| OCI signing verification | PROPOSED | UNFILLED | Publisher-authorized roots and exact digest signature |
| external log storage | PROPOSED | UNFILLED | Destination/ACL/retention/deletion/access audit and canary proof |
| witness-root custody | PROPOSED | UNFILLED | Separate restore authority and surviving checkpoint |
| backups | PROPOSED | UNFILLED | Store/root/log backups and restore credentials |
| recovery | PROPOSED | UNFILLED | Quarantine, uncertain acknowledgment and restore tests |
| audit | PROPOSED | UNFILLED | Attributable review and conflict disclosure |


Zero VERIFIED_ACCESS and zero INDEPENDENTLY_ENFORCED assignments. No people/organizations appointed. Sole-owner assertion is from retained K/L declarations, not verified authority. Backups and recovery are separate duties, not claimed independent people. Access tests, authority and informed decisions remain required.

A1/A2 physical administrators may restore owner/witness disks, keys, backups and logs together. B1/B2 account administrators may control snapshots/IAM/keys/exports; another VM/account under the same person is not independent. C0 host/hypervisor admin may roll back every guest authority. Witness independence NOT_ESTABLISHED for all five. Require separate restore control and surviving checkpoint, signature/epoch checks, fail-closed quarantine and no retry after uncertain acknowledgment. Preserve all-authority rollback counterexample: second grant remains possible; software witness is not hardware monotonicity.

Enumerate stdout/stderr,JVM heap/native/error dumps,Docker json-file/journald,cloud/host agents,support bundles,backups and external collectors. Require custodian,destination,ACL,retention/deletion/access audit and bounded canary non-export proof. OCI_SIGNATURE_NOT_VERIFIED; candidate LSM/userns,boot/time,actual owner/HALT,network and sinks remain unverified. No candidate host/account inspection performed.

V2 contract, governance second review and four technical reviews PENDING. Operational NO_GO; MIS NOT_READY; Phase12 BLOCK; H1/H2 FROZEN; July TEST SEALED; INR10,000 ceiling unchanged; StageB ZERO_CALL_ABORT preserved. Acquisition, provisioning and real access NOT_AUTHORIZED.
