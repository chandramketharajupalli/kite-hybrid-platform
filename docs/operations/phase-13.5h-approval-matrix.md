# Phase 13.5H four-role approval matrix

ApprovalEvidence.13.5H.v1, prepared 2026-10-11. This preparation date is not a
reviewer decision timestamp. Determination: **CONTRACT_APPROVAL_PENDING**.
Contract approval PENDING; operational NO_GO. No reviewer is represented or signed
for by this package. This is an attributable-evidence register with missing fields
explicitly recorded, not four invented decisions.

Controlling source: [Phase G decision matrix](phase-13.5g-security-decision-matrix.md),
Approval register and D1-D10, at commit
`6ac8b9dda6c0613d8fd3405f27d58acb83c471bc`.
Proposal: DiagnosticAssuranceContract.v2, draft 1. Exact-byte SHA-256:
`5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7`.
The four role names below are copied exactly from the source approval register.

## Role responsibilities and evidence

| Exact role | Responsibility from committed matrix | Present evidence | Missing evidence required to close the review |
|---|---|---|---|
| Security/authentication | D2-D4, D8, D10: C trust model; compliant auth origin/consent/account/app binding, custody, expiry/revocation/time; durable claims; response provenance; contract authority | V2 admission/lifecycle specification; OwnerBoundary.v1; local session/handoff source; historical synthetic tests | Identified authorized reviewer and explicit digest-bound decision; acceptance of assumptions and implementation obligations; independently verified legitimate authentication origin and owner/caller evidence before operational certification; reviewed ledger/signature/key protocol |
| Runtime/DB | D1, D4-D6, D10: accept removed global-invariance claim; durable consumption; all-runtime HALT coverage; own-process isolation | Old observer and abort evidence; V2 narrower claim; disposable snapshot counterexample source; ledger/HALT design | Attributable explicit acceptance or rejection of narrower guarantee; durable store/fencing specification and verification obligations; runtime authority/coverage ownership; future independent HALT/reachability certification |
| Packaging/logging | D6-D7, D10: transitive package/runtime reachability, egress, startup/dispatch sink drift, dump/agent/proxy/container/OS controls and retention | Current POM confirms broad artifact; owner dependency/sink allowlists; existing local redaction tests | Attributable decision; chosen minimal artifact/build and enforcement approach, full sink ownership/retention and proof obligations; future SBOM/confinement/runtime capture evidence |
| User/operator | D1, D9-D10: accept own-process-only assurance and external-writer residuals; preserve funding safeguards; separate contract and read authority | Old conditional one-GET approval with ZERO_CALL_ABORT; V2 clearly distinguishes contract/read/trading gates; Phase G PENDING record | Identified authorized reviewer and explicit V2 digest-bound decision acknowledging global nonmutation NOT_PROVEN and MIS NOT_READY; later separately scoped implementation/auth/deployment/read permissions remain absent |

Design approval can accept a specification with explicit future certification
obligations; missing live certification alone does not fabricate a rejection.
No design approval is present here either. If a reviewer makes an obligation a
condition of their approval, it must be independently closed before the combined
contract status can become APPROVED. Future-phase prerequisites remain separate
and must still prevent operational use after contract approval.

## Actual decision records

Each PENDING below preserves a source-recorded state. It is not an affirmative
decision made by an identified reviewer. NOT_DOCUMENTED means no attributable
record was supplied or found within the reviewed evidence scope.

| Field | Security/authentication |
|---|---|
| Proposal version / SHA | DiagnosticAssuranceContract.v2 draft 1 / 5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7 |
| Actual reviewer identity / role authority | NOT_DOCUMENTED / NOT_VERIFIED |
| Actual decision status | PENDING (source register); no attributable approval or rejection |
| Actual decision timestamp | NOT_DOCUMENTED |
| Reviewer conditions | NOT_DOCUMENTED; no unconditional approval inferred |
| Reviewer rationale | NOT_DOCUMENTED |
| Engineering reason to remain pending | No independently attributable digest-bound decision; design/local tests cannot establish broker-authoritative provenance |
| Source artifact | phase-13.5g-security-decision-matrix.md, Approval register Security/authentication row; duties D2-D4, D8, D10 |
| Decision verification status | NOT_VERIFIED; only source definition and PENDING entry verified |

| Field | Runtime/DB |
|---|---|
| Proposal version / SHA | DiagnosticAssuranceContract.v2 draft 1 / 5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7 |
| Actual reviewer identity / role authority | NOT_DOCUMENTED / NOT_VERIFIED |
| Actual decision status | PENDING (source register); no attributable approval or rejection |
| Actual decision timestamp | NOT_DOCUMENTED |
| Reviewer conditions | NOT_DOCUMENTED; no unconditional approval inferred |
| Reviewer rationale | NOT_DOCUMENTED |
| Engineering reason to remain pending | Removal of global assurance is material; no recorded acceptance, durable-ledger verification or independently verified live HALT |
| Source artifact | phase-13.5g-security-decision-matrix.md, Approval register Runtime/DB row; duties D1, D4-D6, D10 |
| Decision verification status | NOT_VERIFIED; only source definition and PENDING entry verified |

| Field | Packaging/logging |
|---|---|
| Proposal version / SHA | DiagnosticAssuranceContract.v2 draft 1 / 5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7 |
| Actual reviewer identity / role authority | NOT_DOCUMENTED / NOT_VERIFIED |
| Actual decision status | PENDING (source register); no attributable approval or rejection |
| Actual decision timestamp | NOT_DOCUMENTED |
| Reviewer conditions | NOT_DOCUMENTED; no unconditional approval inferred |
| Reviewer rationale | NOT_DOCUMENTED |
| Engineering reason to remain pending | No decision or minimal deployment/sink certification; source redaction is insufficient |
| Source artifact | phase-13.5g-security-decision-matrix.md, Approval register Packaging/logging row; duties D6-D7, D10 |
| Decision verification status | NOT_VERIFIED; only source definition and PENDING entry verified |

| Field | User/operator |
|---|---|
| Proposal version / SHA | DiagnosticAssuranceContract.v2 draft 1 / 5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7 |
| Actual reviewer identity / role authority | NOT_DOCUMENTED / NOT_VERIFIED for V2 |
| Actual decision status | PENDING (source register); no attributable V2 approval or rejection |
| Actual decision timestamp | NOT_DOCUMENTED for V2 |
| Reviewer conditions | NOT_DOCUMENTED for V2; old approval conditions are not imported |
| Reviewer rationale | NOT_DOCUMENTED for V2 |
| Engineering reason to remain pending | The current request authorizes review only; the historical one-GET approval binds the old contract and ended in ZERO_CALL_ABORT |
| Source artifact | phase-13.5g-security-decision-matrix.md, Approval register User/operator row and D1, D9-D10; old scope in phase-13.5-real-read-approval.md and phase-13.5-validation.md Stage B continuation |
| Decision verification status | NOT_VERIFIED for V2; historical scope and abort verified as documentary records only |

## Aggregation and conflict handling

Frozen rules: [Phase H plan](phase-13.5h-plan.md). The committed matrix supplies
PENDING, but no exhaustive decision vocabulary. Future attributable records may
use APPROVED, APPROVED_WITH_CONDITIONS, REJECTED, PENDING or NOT_REVIEWED.
Current records remain PENDING; no identity/date is inferred from Git author,
filesystem owner, environment username, assistant output or phase-request sender.

All four approvals must bind exactly this proposal version/hash and verified role
authority. Conditions need explicit scope, evidence, closure owner and verified
closure. Unknown/unsatisfied conditions cannot count as approval. A reviewer
rejection yields CONTRACT_REJECTED; unresolved approval conflicts or absent
records retain CONTRACT_APPROVAL_PENDING. Resolve conflicts only through explicit
attributable supersession, not last timestamp wins. Changed proposal content needs
a new version and new hash-bound decisions. Do not alter the committed proposal.

Evidence scope: all eleven requested committed documents read; approval-related
search over tracked docs/operations/phase-13.5* and docs/architecture/kite-* at
HEAD; current user instruction. No attributable V2 decisions found. No external
inbox/ticket system or private identity directory was consulted. This does not
assert that no off-repository decision exists; it cannot be credited without
supplied, authenticated evidence. No review request was sent.

Present tally: 0 verified attributable approvals, 0 documented attributable V2
rejections, 4 source PENDING entries. Result CONTRACT_APPROVAL_PENDING; approval
PENDING. Future implementation remains unauthorized; operational NO_GO.
