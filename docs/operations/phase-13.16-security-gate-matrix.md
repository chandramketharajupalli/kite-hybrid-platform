# Phase 13.16 mapping of all fifteen acceptance gates

Source: [Phase13.15 matrix](phase-13.15-security-acceptance-matrix.md). Every G01-G15
row is retained. Proposed role owners are accountable functions, not named reviewers
or approvals. Current host observations do not verify a nonexistent guest.

| Gate / original requirement | Phase13.16 status and evidence | Proposed owner; pass criterion / next action |
|---|---|---|
| G01 baseline/protected records | VERIFIED; manifest preservation | Evidence observer; exact baseline/G1/18/63/K/L and parent hashes must match at next entry |
| G02 host capacity/hypervisor/license | PARTIAL evidence; target NOT_FEASIBLE; host-measurements and hypervisor-feasibility | User/operator + host admin; sufficient measured headroom and supported installed/licensed hypervisor; resolve RAM/edition before provisioning |
| G03 exact OS image and patches | NOT_VERIFIED; no acquisition; draft packet TBD | Security/authentication + platform custodian; signed vendor checksum/key and exact image/package/kernel/support inventory |
| G04 boot/time | NOT_VERIFIED; local uptime/time is not attestation | Runtime/DB + host admin; guest/host secure/measured boot evidence and trusted time uncertainty/reversal denial |
| G05 enforcing AppArmor | NOT_VERIFIED; no guest | Packaging/logging + policy admin; loaded enforcing hashed profile attached to actual Java, positive/negative/audit/restart evidence |
| G06 user namespaces | NOT_VERIFIED; no guest maps | Runtime/DB + host observer; nonidentity guest-host uid/gid map/namespace ownership and ACL tests; Windows boundary separately proved |
| G07 privilege/resource isolation | NOT_VERIFIED; Docker advertised features only | Packaging/logging; Java CapEff0/NNP1/seccomp/non-root/read-only/private mounts/cgroups/PIDs and negative probes |
| G08 OCI/JDK authenticity | NOT_VERIFIED; historical digest only | Security/authentication; publisher-authorized signer/root/exact subject/validity/revocation output plus package/module inventory |
| G09 network | NOT_VERIFIED; no switch/firewall changes or probes | Runtime/DB + network custodian; host/guest/container default deny, controlled IPv4/IPv6/DNS/proxy/gateway/metadata/redirect negatives and loopback positive |
| G10 diagnostic/external sinks | NOT_VERIFIED; json-file metadata, encryption/backup unknown | Packaging/logging + sink owners; all destinations/ACL/encryption/retention/audit and bounded canary containment, no host dump export |
| G11 actual owner and HALT | NOT_VERIFIED for new testbed; historical runs not repeated | Security/authentication + test observer; actual Java/RSA/epoch/revocation/scope negatives, one-use send<=1/receipt<=1/replay0 |
| G12 witness authority | NOT_VERIFIED; no distinct admin/root supplied | Runtime/DB + separate root custodian; actor/restore matrix, root/checkpoint survives owner rollback, quarantine proof |
| G13 recovery/rollback | NOT_VERIFIED for new testbed; prior counterexample preserved | Runtime/DB; races/lost ACK/kill/restart/local rollback denial and all-root two-grant/no-HTTP negative retained |
| G14 budget/cleanup | NOT_VERIFIED by execution; no resources created | User/operator + test custodian; max3 admission before creation plus separately counted helper, labels/IDs/finally/abnormal-exit cleanup and zero-owned final inventory |
| G15 evidence and review | PARTIAL; read-only receipts/replay complete, human/security reviews pending | Four separate domain roles; attributable decisions and future commands/exits/XML/failed evidence/hashes; no AI signature |

G02's negative capacity result is not a safety-stop violation by the inspection.
Unavailable fields remain UNKNOWN; no gate is merged, waived or promoted. Provisioning
authorization PENDING; security certification unavailable; operational NO_GO.

## Witness and network/logging boundaries

A single Windows administrator or snapshot operator with control of all VM disks,
local journal/PG ledger, witness storage/key and coordinator checkpoint can potentially
restore every authority together. Another VM/database under that control does not
establish independence. Require separately documented storage/restore administration,
authenticated checkpoint advancement, access audit and independently retained root.
No such authority is created or verified. Missing/ambiguous ACK, signature, root state
or clock evidence must quarantine and deny dispatch/retry; restart cannot clear it.
Future tests must retain surviving-witness replay denial and all-authority two-grant
counterexample without HTTP. No hardware monotonicity/power-loss/multi-host claim.

Future host/guest network proposal: private isolated switch, no public NAT/LAN bridge,
deny ingress/egress IPv4+IPv6/DNS/metadata/host gateways; permit only explicit numeric
loopback fixture traffic. Any external witness test link needs separately scoped exact
endpoints/identity and no internet route. No actual broker domain is probed. Need both
host-switch/firewall and guest/container observations, not guest-only assertions.

Future sink evidence must cover heap/native/hs_err/JFR/attach, stdout/stderr, daemon
json-file, Maven/CI, host mounts, Windows diagnostics/EDR/collectors and snapshots.
Identify custodians, exact destinations, ACLs, encryption, retention/deletion and
access audit without exporting keys or raw dumps. Canary positives stay in bounded
private tmpfs; negatives must show no canary at unauthorized host/daemon/CI/external
sinks. Unknown collectors remain a blocker; local scans do not certify them.
