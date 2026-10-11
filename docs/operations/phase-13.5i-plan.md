# Phase 13.5I frozen coordination protocol

ReviewProtocol.13.5I.v1, prepared 2026-10-11. Parent clean develop, HEAD and
origin/develop `2ea8d50cf776b83584ef5cc4f38954a434ae1ad7`.
All required Git preflight commands passed before edits. This file is created
before collecting/processing decision evidence or deriving the Phase I result.
Its exact UTF-8 byte SHA-256 is frozen separately in validation before processing.
Any criteria change requires a new protocol version, rationale and new digest;
never rewrite this protocol after reviewing outcomes.

## Controlling identity, sources and roles

DiagnosticAssuranceContract.v2, draft 1, exact artifact bytes (no normalization):
`5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7`.
Working-tree and committed HEAD bytes both match. Do not alter the proposal.

Parent documents, all under docs:

- architecture/kite-db-free-assurance-contract-v2-proposal.md
- architecture/kite-credential-owner-service-boundary.md
- architecture/kite-credential-owner-security-review.md
- architecture/kite-authenticated-session-ownership.md
- operations/phase-13.5g-security-decision-matrix.md
- operations/phase-13.5g-validation.md
- operations/phase-13.5h-approval-matrix.md
- operations/phase-13.5h-implementation-readiness.md
- operations/phase-13.5h-review-request-draft.md
- operations/phase-13.5h-validation.md
- operations/phase-13.5-real-read-approval.md
- operations/phase-13.5-validation.md

Exact role order: Security/authentication; Runtime/DB; Packaging/logging;
User/operator. Duties/acceptance requirements come from Phase G D1-D10 and Phase H
role/evidence tables, not inferred from names. The Phase H plan is an additional
referenced parent; retain all Phase H files and prior generations unchanged.

Evidence hierarchy: current phase instruction controls allowed actions; committed
V2 controls contract scope; G/H control roles and existing evidence policy; original
approval/Stage B continuation control historical authority. Only attributable,
independently verified reviewer records can supply human decisions. Documentation,
tests, commits, unsigned drafts, AI recommendations and synthetic/local attestations
cannot approve. Missing/inconsistent controlling files stop closure determination.

## Decision admissibility and timestamps

Preserve source decision text verbatim in a protected evidence system; ledger stores
only safe references/digests and bounded rationale. H permits APPROVED,
APPROVED_WITH_CONDITIONS, REJECTED, PENDING, NOT_REVIEWED. For coordination only,
explicit mappings are APPROVED -> APPROVE, APPROVED_WITH_CONDITIONS -> CONDITIONAL,
REJECTED -> REJECT, PENDING -> PENDING, NOT_REVIEWED -> PENDING with raw state retained.
New reviewer responses may use the Phase I-requested APPROVE/REJECT/CONDITIONAL/PENDING
vocabulary. Unknown wording is not mapped by guesswork. No mapping changes V2.

Require exact role; independently attributable identity or organizational authority;
authority/delegation evidence for that role and conflict-of-interest disclosure;
exact version/full hash; rationale/risk acceptance; actual UTC decision and receipt
timestamps and their provenance; evidence URI/path plus exact original SHA-256;
authenticity verification method/status/verifier reference; conditions and scope.
No inference from Git authors, machine users or request sender. Missing fields do
not become empty unconditional approval. For absent decisions use null/UNKNOWN and
PENDING; no invented names, evidence IDs, receipt times or reviewer dates.

Preserve source timezone evidence, then convert a known offset to UTC Z without
changing the instant. Unknown timezone, future/impossible chronology, missing
timestamp or unverified receipt provenance prevents acceptance. No arbitrary
30-second runtime TTL is applied to human decisions. Respect explicit decision
expiry/review-validity and current authority/revocation evidence. Unknown freshness
or changed scope requires reviewer reconfirmation; do not fabricate a deadline.
Assessment date and replay execution time are not decision/receipt timestamps.

## Conditions, conflicts, rejection and aggregate result

Reviewer conditions require explicit text, accountable owner, closure evidence and
digest, verification and any confirmation the reviewer requires. CONDITIONAL is
not unconditional approval. Unknown/open conditions or absent required confirmation
cannot be promoted. Even satisfied conditions require an attributable effective
unconditional decision or explicit original conversion terms and confirmation.
Engineering gaps are not fabricated reviewer-issued conditions.

Keep all historical evidence. Withdrawal removes the decision from active approval
count; record attributable withdrawal evidence. Supersession must reference the
specific prior decision and come from verified authority for the same role/version;
timestamp ordering alone never resolves a conflict. Duplicate evidence counts once;
duplicate role rows are not extra approvals. Conflicts without a rejection remain
PENDING with a CONFLICTING reason; a verified active rejection blocks approval.
Wrong-version/stale/withdrawn/unverifiable/unauthoritative evidence stays inadmissible.
Changed proposal requires a new draft/hash and renewed decisions, never inheritance.

Deterministic aggregate precedence:

1. Unverifiable baseline/contract/source integrity or evidence corruption:
   CONTRACT_DECISION_BLOCKED; stop, no repair.
2. Any verified authorized active REJECT: CONTRACT_REJECTED.
3. Missing/inadmissible required decision or unresolved conflict without rejection:
   CONTRACT_APPROVAL_PENDING (even if another role is conditional).
4. Otherwise open conditions or required confirmation: CONTRACT_CONDITIONAL.
5. Only four verified attributable effective unconditional approvals, exact binding,
   satisfied conditions and no conflicts: CONTRACT_APPROVED.

This classification records reviewers' decisions; Codex makes none for them.
Contract closure is separate from implementation authorization, readiness, live
certification, real-read permission and trading. Operational NO_GO remains unless
separately established under a new scope; this phase performs no live verification.

## Distribution and privacy

Four role packets are DRAFT / NOT_SENT. Sending requires separate explicit user
authorization and confirmed recipients/content through an authorized tool. No
automatic emails/messages/tickets/invitations; do not ask for distribution approval
as a prerequisite to finishing the draft package. No private contact details,
credentials, account identifiers, tokens, keys or raw messages in Git. Sensitive
originals remain with authorized custodians; record only a safe access reference
and cryptographic digest. Hash proves byte identity, not authenticity or authority.

## Additive ledger, canonicalization and replay

Create research/phase-13.5i/generation-1/approval-ledger.json and decision-replay.json.
They are documentary review evidence, NOT the future runtime one-use request store.
Use versioned body/fingerprint envelopes like existing research artifacts.
Canonical semantic bytes: json.dumps(body, sort_keys=True, separators=(",", ":"),
ensure_ascii=True, allow_nan=False).encode("utf-8"), no trailing newline.
Fingerprint = lowercase SHA-256 of those bytes. Stored envelope bytes are sorted
indent=2 ASCII JSON, UTF-8, LF, exactly one final newline. Record exact file hashes
separately in validation. Reject duplicate JSON keys, non-finite values and extra
or missing role records. Arrays retain frozen source role order, not arbitrary
set iteration. No wall-clock output in semantic fingerprints.

Ledger pins parent commit, protocol byte hash, exact proposal identity, controlling
source byte hashes, role records, evidence count and no-authorization boundaries.
Decision records distinguish source PENDING status from absent reviewer decision;
identity/authority/time/evidence/conditions/supersession fields are explicit nulls
or unknown, never manufactured. Source documents are not decision evidence.
Replay binds ledger semantic and exact-byte hashes and repeats source/contract/
role checks before reproducing the absent-evidence result. It grants no authority.

No reusable approval verifier was found in the inspected scripts/contracts/Python
review paths. This coordination scope uses documentation plus strict offline
artifact validation; no general decision engine or repository Python code is
justified for empty evidence. Repeated canonicalization and independent disk replay
must reproduce identical fingerprints. Negative integrity checks on copies may
verify hash/role/canonical rejection, without inventing human records. Record the
full future synthetic decision-case matrix as NOT_RUN where no engine exists;
do not claim four synthetic approvals are operational approvals or tested closure.

## Protection, validation and artifacts

Initial G1 raw SHA verified as
`56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`.
All 18 registered UTF-8 read_text-normalized source hashes and all 63 original
protected research HEAD comparisons passed; raw inventory retained under ignored
tmp/phase135i/. New generation files are additive and excluded from the original
63-file count. Recheck original inventory, all controlling and Phase H docs, Phase
12 five-member acquisition/evaluation BLOCK and sealed July marker at completion.

Run project/secrets/Git checks and untracked UTF-8/whitespace/link/payload audit.
Java/Python suites NOT_RUN absent source changes; no historical totals reused.
If repository Python validator code becomes necessary, version this plan before
adding it and run focused tests, Ruff and strict mypy. Unexpected shared production
change, protected mismatch, secret/live-resource need or guard bypass means STOP.

Five docs: phase-13.5i-plan.md, phase-13.5i-review-requests.md,
phase-13.5i-decision-matrix.md, phase-13.5i-implementation-readiness.md,
phase-13.5i-validation.md, all in docs/operations; two generation-1 JSON files above.
No other deliverables or modifications intended. No broker requests, tokens,
authentication/browser, Spring startup, paper trading, development DB, HALT resume,
permits/orders, .env/config/migration/risk changes, deployment, commit or push.
Preserve Stage B ZERO_CALL_ABORT, INR 10,000 full buffered notional, cash/CNC,
Phase 12 BLOCK, frozen H1/H2 and sealed SBIN July. Finish package, report and STOP.
