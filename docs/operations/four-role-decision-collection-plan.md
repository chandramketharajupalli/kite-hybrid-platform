# Four-role decision collection protocol v1

Parent: develop / 0c1495246b6bdf1ed265020f6fbd60e6f067b03a. Clean local HEAD
and origin/develop verified before edits. Governance/evidence only. This plan is
created and its raw-byte SHA-256 frozen before processing new decision evidence.
Changes require a new version and rationale, never retrospective relaxation.

Contract: DiagnosticAssuranceContract.v2 draft 1; raw SHA-256
5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7.
Controlling roles and duties: G security decision matrix D1-D10 and H approval
matrix; I plan, packets, decision/readiness/validation and generation-1 ledger/replay;
V2 proposal, credential-owner boundary and security review. Preserve these sources.

Exact roles, ordered: Security/authentication; Runtime/DB; Packaging/logging;
User/operator. One person may fill multiple roles only with independently documented
authority for each and four distinct decisions. Job title alone grants no authority.

## Admissibility and verification

Require stable record ID/generation, exact role/version/draft/hash, attributable
identity, independently established role authority and delegation/conflict disclosure,
explicit decision, timezone-bearing decision timestamp and evidence-capture timestamp,
timestamp provenance, rationale/risk acceptance, conditions/closure obligations,
safe evidence reference and original content SHA-256, authenticity method/verifier/
status/limitations, validity/revocation and supersession references. Null fields for
absent decisions; never generate reviewer names, signature strings or receipt times.

Source enum remains I's APPROVE/REJECT/CONDITIONAL/PENDING. Explicit input mappings:
APPROVED -> APPROVE; APPROVED_WITH_CONDITIONS or APPROVE_WITH_CONDITIONS -> CONDITIONAL;
REJECTED -> REJECT; NOT_REVIEWED -> PENDING. Preserve original source enum separately.
Unknown or ambiguous wording stays unverified. Do not silently translate assent.

Only locally committed or user-supplied evidence may be searched. External systems
require explicit source authorization. No current task instruction constitutes a
reviewer response. Accept authenticated original records with independent authority
verification; a signed artifact additionally needs trusted key ownership/authority
and revocation checks. Correspondence requires sender/recipient authenticity, not
copied text. Screenshots, forwarded text, self-declared titles, unsigned drafts,
JSON rows, Git authors, generated signatures, test passes and old GET approval
cannot alone establish authority. Missing independent verification means UNVERIFIED.
Digest equality proves bytes, not authorship. Record limitations honestly.

Preserve original timestamp/offset; UTC conversion must preserve its instant.
Missing timezone, impossible chronology, decision after capture, expired evidence,
unknown validity or current authority requires correction/reconfirmation. Do not
apply runtime 30-second request TTL to human contract decisions. A report preparation
date is not a reviewer or capture timestamp. Requests for missing details follow
actual submissions; no invented response or deadline.

## History, conditions and aggregation

Append new generations/records; never overwrite prior G/H/I or earlier decisions.
Deduplicate identical evidence by digest and identity; duplicate record IDs invalidate
structure. Preserve withdrawals/revocations and their authenticated references.
Supersession must be explicit, attributable, same role/version and chronological;
reject cycles/missing predecessors. No latest-wins rule. Conflicting purported
records require escalation and remain unresolved until authenticated resolution.
Conditions require enumerated owner, closure proof/digest, independent verifier and
any required reviewer confirmation. Closed checkboxes alone are not unconditional approval.

Aggregation follows I precedence: integrity corruption stops with
CONTRACT_DECISION_BLOCKED; verified active rejection -> CONTRACT_REJECTED even with
missing roles; unresolved conflicts without rejection -> CONTRACT_APPROVAL_PENDING
with CONFLICTING reason; missing/unverified roles -> CONTRACT_APPROVAL_PENDING;
otherwise open conditions/confirmation -> CONTRACT_CONDITIONAL; four verified
unconditional approvals with no conflicts -> CONTRACT_APPROVED. Presentation maps
these to REJECTED, CONFLICTING, APPROVAL_PENDING, CONDITIONAL_PENDING and APPROVED.
An unverified purported rejection is not a verified rejection. Missing is not a vote.

Implementation stays NOT_AUTHORIZED / GAPS_OPEN absent separately scoped authority
and evidence. Governance closure never establishes live readiness or permission.

## Drafts and private evidence

Four role packets remain DRAFT / NOT_SENT. Sending requires separate explicit user
authorization for exact recipient and message, then independently recorded sending
evidence. Do not contact anyone to complete this package. Sensitive originals remain
with authorized custodians under access control; Git receives only minimized safe
references/digests. No private contacts, account IDs, balances, credentials or raw
confidential correspondence. Custodian identity/access and retention schedule are
UNKNOWN until supplied; do not invent them or copy originals pending arrangements.
Retain non-secret append-only audit generations; disposal of originals needs an
approved retention policy and must not erase necessary verification provenance.

## Artifacts and offline validation

Five four-role Markdown documents under docs/operations and additive
research/phase-13.5j/generation-1/{evidence-index,approval-ledger,decision-replay}.json.
J is a namespace for this collection generation, not a runtime release. Envelopes
retain body/fingerprint convention. Canonical body = UTF-8 JSON with sorted keys,
separators comma/colon, ensure_ascii=True, allow_nan=False, no newline. Fingerprint
is lowercase SHA-256. Stored envelopes use sorted indent=2 ASCII JSON and one LF.
Plan raw-byte fingerprint is recorded in index/validation, not recursively in itself.

Index pins baseline, controlling sources and raw/HEAD/normalized comparisons plus
actual submission list. Ledger links index and predecessor I, four stable pending
slots and submission references. Slots are not reviewer decisions. Replay binds both
semantic and file digests. Verify duplicate keys, canonical bytes, hash/role/ID set,
evidence linkage, chronology, revocation/supersession/condition/conflict data before
aggregation. With no submissions, timestamps and all such references must be null
or empty as explicitly specified. Any populated decision data is outside the
empty-evidence replay and MUST stop for full independent adjudication.

Use bounded inline offline validation, not a generic approval engine. Run pending
replay twice from disk. In-memory SYNTHETIC_TEST_ONLY mutations test invalid hash,
missing/wrong role, duplicate records, broken evidence link, revoked/superseded
claims, conflicts, open/claimed-closed conditions, malformed timestamps and forged
approval promotion. These validate refusal to credit unsupported data, NOT successful
authentication/supersession or conditional approval adjudication. Save only case
names/outcomes, never synthetic identities in the real ledger. No production code.

Verify project/secrets, staged/unstaged/untracked changes, links and whitespace.
No unrelated Java/integration/Python suites; report NOT_RUN. Reverify raw V2/G1,
18 registered read_text-normalized hashes, 63 original research hashes and G/H/I
byte preservation. Record CRLF differences explicitly, never normalize the contract.
Unexpected source changes, fingerprint failure or live-resource need stops work.

No real APIs/tokens/login, trading startup/DB, WebSockets/orders, HALT resume/arm,
configuration/risk/migration edits, deployment, commit/push or destructive Git.
Preserve ZERO_CALL_ABORT, MIS NOT_READY, cash/CNC, INR 10,000 buffered ceiling,
Phase 12 BLOCK, frozen H1/H2 and sealed SBIN July. Report and STOP.
