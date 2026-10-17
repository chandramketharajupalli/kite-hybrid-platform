# Phase 14.33A additive remediation plan

Baseline: e2ca61a652cd0b6e0e5c753823bd7f74b1ed7f80. PREPARATION_ONLY.
Owner authorization permits preparation and disposable-location ACL testing only; no acquisition retry.

Preserve all historical evidence, existing quarantine and receipt ACLs, and Phase 14.22 plan.
Original failure: Set-Acl on receipt custody raised SeSecurityPrivilege exception; numeric exit code and exact command were not retained. Do not rewrite that finding.

Proposed quarantine: `D:/Yogendra/offline-artifact-quarantine/phase-14.33A/74b40b6723fd6bfc5401a48e76fd8e94f2cd68919b5c532fa4b271c7f59db65c-retry-01`
Proposed receipt custody: `D:/Yogendra/offline-artifact-receipts/phase-14.33A/74b40b6723fd6bfc5401a48e76fd8e94f2cd68919b5c532fa4b271c7f59db65c-retry-01`
Both must remain absent until a new explicit acquisition decision authorizes creation.

Use native SetNamedSecurityInfoW with security-information mask 0x80000004 (DACL and protected DACL only), null owner/group/SACL pointers. Do not change privileges, execution policy, owner, group or audit settings. Reject wrong owner, nonempty directory, checkout path, traversal and reparse-point ancestry. Require current operator ownership; retain only operator, SYSTEM and Administrators FullControl with object/container inheritance. Read back protected DACL and unchanged owner/group. Audit settings are excluded from the write by API flags; privileged SACL readback is not claimed.

Initial disposable test attempt: script-file execution was denied by existing PowerShell execution policy, exit 1; ACL function never ran. Retain failure. Corrective test uses the reviewed script as an inline ScriptBlock with no policy changes. Future execution must bind the reviewed procedure hash and require explicit owner authorization; sourcing this file defines functions only and does not authorize mutation.

Validation budget: local bounded synthetic directories, 11 ACL/path checks and deterministic packet/preservation checks; no network, dependency execution or full trading suite. Retain disposable evidence outside repository. Stop on any unexplained failure or protected hash change. No global configuration changes or elevation.

Permitted additions: docs/operations/phase-14.33A-remediation-plan.md, scripts/research/phase1433a-dacl-only.ps1, research/phase-14.33A/generation-1/retry-authorization.json, docs/operations/phase-14.33A-validation.md, research/phase-14.33A/generation-1/evidence-manifest.json.
Future acquisition requires new packet-bound approval including both destinations, eight exact artifacts, original manifest/attestation, eight requests, 3,029,164 bytes, 20 seconds, no redirects/retries. Installation remains separately NOT_AUTHORIZED. Concurrent hostile filesystem replacement and physical durability are not proven by these checks.
