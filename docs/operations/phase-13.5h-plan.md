# Phase 13.5H frozen review plan

ReviewPlan.13.5H.v1, 2026-10-11. Created before approval-outcome evaluation.
Parent: clean develop, HEAD = origin/develop =
`6ac8b9dda6c0613d8fd3405f27d58acb83c471bc`.
All six required Git preflight commands passed before edits.

Controlling proposal: DiagnosticAssuranceContract.v2, draft 1,
docs/architecture/kite-db-free-assurance-contract-v2-proposal.md.
Verified exact-byte SHA-256:
`5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7`.
Fingerprint this plan with SHA-256 over exact UTF-8 file bytes and record it in
validation, outside this file, before evaluating decisions. Never revise criteria
after outcomes; a change needs a new version, rationale and old/new digests.

## Exact source-defined roles and acceptance questions

Source: committed phase-13.5g-security-decision-matrix.md, Approval register and
D1-D10. These are role labels, not identities of assigned human reviewers.

| Exact role | Source duties and acceptance questions |
|---|---|
| Security/authentication | D2-D4, D8, D10: accept C's trust model? Establish legitimate broker-compliant origin, consent, account/app binding, custody, revocation, trusted time, durable authorization and authenticated response chain? |
| Runtime/DB | D1, D4-D6, D10: explicitly accept removed global invariance? Require durable claim/failover/rollback proof, competent all-runtime HALT authority and independently isolated own-process reachability? |
| Packaging/logging | D6-D7, D10: require minimal transitive artifact, startup/egress confinement, all sinks/dumps/agents/proxies/OS controls, drift denial and retention ownership? |
| User/operator | D1, D9-D10: explicitly accept narrower claim and residual external-writer risk, preserve funding safeguards and separate contract/read/trading authority? |

For each role require an attributable decision against this exact proposal hash:
documented reviewer identity and authority for role, actual decision timestamp,
decision text/rationale, conditions, source artifact and independent verification.
This phase request is not an approval. Do not impersonate a reviewer or create
names, signatures, decisions or decision dates. Review dates are not approval dates.

## Evidence precedence and determination rules

Explicit current scope controls actions. Committed V2 and Phase G matrix control
proposal/role definitions; immutable original approval and Stage B abort control
historical authority. Actual baseline source and scoped test evidence control
implementation claims. Human approval requires attributable authenticated evidence;
tests, local signatures/UUIDs, summaries and author assertions cannot substitute.
Search relevant tracked documentation for decisions; report search scope and absence
without asserting that no off-repository human decision could exist.

The source matrix presently defines PENDING entries without an exhaustive status
vocabulary. For evaluating later supplied records use APPROVED,
APPROVED_WITH_CONDITIONS, REJECTED, PENDING, NOT_REVIEWED. Preserve source PENDING
unless attributable evidence changes it. Record missing identity/time/conditions
as NOT_DOCUMENTED, never invented dates or unconditional acceptance.

Approval requires all four attributable role decisions, exact version binding,
verified role authority, every condition satisfied and no conflicts. Unknown
condition closure is unsatisfied. A rejection yields CONTRACT_REJECTED. Missing
approval/evidence or unresolved conflict yields CONTRACT_APPROVAL_PENDING, unless
source/baseline/proposal identity is unverifiable, which yields REVIEW_BLOCKED.
Unambiguous four-role definition is mandatory; otherwise report
REVIEW_DEFINITION_UNRESOLVED and stop approval determination. Conflicting decisions
must be resolved by the responsible reviewer with explicit attributable supersession;
never use latest timestamp alone. Revised proposal invalidates old hash binding.
Only fully verified approvals permit CONTRACT_APPROVED_IMPLEMENTATION_PENDING;
even that does not authorize implementation, authentication, deployment or a GET.

## Review and validation scope

Compare seven requested assurance clauses with RETAINED/NARROWED/REPLACED/NEW
labels, distinguishing requested old guarantees from limitations of old evidence.
Review C, legitimate owner origin, durable ISSUED -> CONSUMED_BEFORE_DISPATCH ->
terminal outcomes, HALT, minimal packaging/egress/sinks and recovery. Separately
classify DESIGNED, SYNTHETICALLY_VALIDATED, IMPLEMENTED, INDEPENDENTLY_VERIFIED,
APPROVED and LIVE_VERIFIED; none implies the next. Report design gaps honestly.

Documentation only. Inspect actual Java paths/signatures and existing negative
tests read-only. No Java/Python or integration suite rerun is planned: unchanged
implementation and historical scoped tests can explain limits but cannot establish
human decisions or unimplemented service readiness. If a concrete review claim
needs execution, record its narrow rationale/selector before that focused run;
never count old 1508/55 results as new. Do not run broad suites for count increases.
Run project verifier, secret scanner, Git audit and explicit untracked-content audit.

Initial G1 exact SHA matches
`56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`.
All 18 hashes match run_phase116.sources UTF-8 read_text normalization. All 63
protected research files match normalized HEAD blobs; raw byte inventory saved
under ignored tmp/phase135h/protected.json for final comparison. Five-member Phase
12 acquisition/evaluation remains blocked; G1 test_state SEALED_NOT_EVALUATED.
Recheck at end. No sealed data opened, research evaluation or acquisition.

## Stop conditions, artifacts and safety

STOP on unexpected shared production/source changes, baseline/freeze mismatch,
unverifiable proposal identity, or need for a secret/live resource/guard bypass.
No .env/config/migration or historical proposal/approval edits, real Kite HTTP/WS,
token/login/callback/restore, development DB access, Spring startup, deployment,
HALT resume/arming/permits/orders, commit or push. No review message/ticket sent.
Preserve Stage B ZERO_CALL_ABORT, cash reserve/CNC, INR 10,000 buffered-notional
ceiling, Phase 12 BLOCK, H1/H2 and July seal. Operational NO_GO; MIS NOT_READY.

Only six new documents are planned:

- docs/operations/phase-13.5h-plan.md
- docs/operations/phase-13.5h-approval-matrix.md
- docs/operations/phase-13.5h-implementation-readiness.md
- docs/architecture/kite-credential-owner-security-review.md
- docs/operations/phase-13.5h-review-request-draft.md (DRAFT / NOT_SENT)
- docs/operations/phase-13.5h-validation.md

Present the human checkpoint as evidence needed, not a fabricated approval or a
request to launch anything. Complete the review package, report and STOP.
