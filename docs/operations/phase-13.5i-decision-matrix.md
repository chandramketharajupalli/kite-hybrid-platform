# Phase 13.5I contract decision record

Result: **CONTRACT_APPROVAL_PENDING**. Assessment date 2026-10-11 is not a reviewer
decision or receipt timestamp. Contract: DiagnosticAssuranceContract.v2 draft 1,
SHA-256 `5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7`.
Apply the unchanged [frozen protocol](phase-13.5i-plan.md), fingerprint
`028F28839FD554581550BDA0F82EB0127C6B1E4DAA187B4A17E5E79E3FA89B41`.

| Exact role | Source duties | Present evidence | Missing decision evidence | Actual state |
|---|---|---|---|---|
| Security/authentication | G D2-D4, D8, D10: origin, custody, capability/replay and provenance | C design; G/H pending register | Attributable authorized identity, V2 decision, timestamps, rationale and authenticity verification | PENDING |
| Runtime/DB | G D1, D4-D6, D10: narrowed claim, writer threats, ledger and external HALT | Own-process design; global limitation explicit | Same decision fields; explicit narrower-claim acceptance | PENDING |
| Packaging/logging | G D6-D7, D10: artifact, startup, sinks and deployment isolation | Proposed closure/sink inventory | Same decision fields; reviewed boundary and containment requirements | PENDING |
| User/operator | G D1, D9-D10: residual risk, funding safeguards and separate authority | Old aborted scope and new pending proposal | Same decision fields; explicit V2 residual-risk acceptance | PENDING |

Source: [G matrix](phase-13.5g-security-decision-matrix.md) and
[H matrix](phase-13.5h-approval-matrix.md). Exact source hashes and null fields are in
the [generation-1 ledger](../../research/phase-13.5i/generation-1/approval-ledger.json).
For every row reviewer identity, authority evidence, decision/receipt timestamps,
rationale, risk acceptance, conditions, evidence URI/digest and supersession are
null; authenticity NOT_VERIFIED, authority/conflict disclosure UNKNOWN. PENDING is
the source register state, not a reviewer response. No evidence was received: 0
approvals, 0 rejections, 0 conditional decisions. No external inbox was inspected.

Conditions/conflicts: none documented; their absence does not establish satisfaction
or a verified absence of conflicts. Four decisions and their attribution remain
outstanding. Engineering gaps are not invented reviewer conditions. The replay
derives pending from absent evidence; it does not approve/reject on anyone's behalf.

The [replay](../../research/phase-13.5i/generation-1/decision-replay.json) pins the
ledger semantic and file fingerprints. A future generation must preserve originals.
Verified rejection blocks approval; missing/inadmissible evidence stays pending;
otherwise open conditional terms prevent unconditional closure. Duplicates count
once, conflicts require attributable supersession, withdrawn/stale/wrong-version
decisions cannot qualify. New contract bytes require renewed version-bound decisions.
The exact precedence and timestamp/identity policy are frozen in the plan.

## Validation boundary and future decision cases

No general approval engine is added. Offline checks validate this empty-evidence
artifact and deterministic replay only. The following future engine cases remain
NOT_RUN, not implied by current integrity probes:

| Synthetic case | Required future behavior |
|---|---|
| Four pending roles | Pending, no authority |
| Wrong hash/changed contract or wrong role | Inadmissible; never reuse approval |
| Missing timestamp/identity or unverifiable authority | Pending |
| Conditional with open conditions/confirmation | Conditional if all other decisions qualify; otherwise pending |
| Verified rejection | Rejected |
| Conflicting or superseded records | Explicit attributable resolution; never latest-wins |
| Duplicate approval | Count once; reject duplicate role schema |
| Stale/withdrawn decision | Exclude; pending without replacement |
| Tampered evidence digest | Block trustworthy determination |
| Four valid synthetic approvals | Synthetic closure only; never operational evidence |

The actual pending replay and structural tamper checks are recorded separately in
[validation](phase-13.5i-validation.md). No historical test totals are current runs.
Contract approval PENDING; implementation unauthorized; operational NO_GO;
old contract/approval unchanged; Stage B ZERO_CALL_ABORT; real-read authority NONE.
