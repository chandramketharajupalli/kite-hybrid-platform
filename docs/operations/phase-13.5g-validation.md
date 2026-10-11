# Phase 13.5G validation

2026-10-11. Documentation-only review of the proposed DB-free assurance contract
and credential-owner boundary. Operational NO_GO independently of engineering
results. Previous Stage B ZERO_CALL_ABORT remains unchanged. Final result and
completed checks appear in the final audit below.

## Baseline, toolchain and frozen review

Required preflight ran before any edit: git status --short, branch --show-current,
rev-parse HEAD, rev-parse origin/develop, log -8 --oneline and diff --check.
Initial tree clean, develop; both refs exactly
`a191b9c455c5bc916519846e431ba8341bc799dc`.
No pull/fetch/reset/stash/clean/restore/discard, commit or push.

Toolchain observed: Windows 11 amd64; Oracle JDK 21.0.12; repository Maven wrapper
3.9.11 (used for all builds); installed system Maven 3.9.16 (version inspection
only); uv 0.12.10; uv-managed Python 3.14.7; Git 2.55.0.windows.5;
Docker client/server 29.8.0. Bare python --version hit the Windows Store alias and
reported Python unavailable; uv run python --version succeeded. No installation,
global JAVA_HOME change or secret values were required.

ReviewPlan.13.5G.v1 was created and hashed before decision evaluation/test execution.
Exact-byte SHA-256:
`87280B462DE86036DBCCD537B8C09630E645A35F5165BD3DDBEE268467EB2BB2`.
Criteria remain unchanged; no plan revision. Proposal identity:

| Final design document | Version | Exact-byte SHA-256 |
|---|---|---|
| kite-db-free-assurance-contract-v2-proposal.md | DiagnosticAssuranceContract.v2 draft 1 | 5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7 |
| kite-credential-owner-service-boundary.md | OwnerBoundary.v1 | 16DEB0FCC4ACDF07AC2CF080E598345A668437B09EB06CD53064231905A7A2F1 |

Digests cover exact file bytes, with no self-referential digest in the target files.
Any future changed proposal requires a new draft/digest and renewed review.

## Evidence and design result

Inspected baseline source/signatures for harness, handoff, isolation, integrity,
request factory, equity adapter, session, authentication lifecycle and runner,
local HALT, application entry points, execution dependencies, packaging POM and
relevant tests. Read old Stage B approval and abort, Phase A-E validations, Phase F
plan/decision/validation/WebSocket follow-up and the four required architecture
documents. Concrete source/method inventory is in the owner boundary; exact
negative-case anchors and limitations are in the security decision matrix.

Preferred C retains credentials in a genuinely separate, minimal owner and exposes
only a scoped operation. A inherits trading authority; B receiving a transferred
credential expands custody and does not prove provenance. C's administrative and
authentication burden is explicit, not waived. No owner is implemented or deployed.
The proposed assurance is own-process noninterference only; global trading DB
nonmutation NOT_PROVEN. Removing the old global requirement is a material approval
change. No old guard or historical approval was altered or reused.

Cross-process protocol specifies atomic durable claim, immutable owner incarnation,
permanent consumption on crash/timeout/ambiguous acknowledgment, rollback epoch
fencing and no retry/automatic renewal. This is DESIGNED, not a tested ledger.
Authentication provenance and independent live HALT are NOT_ESTABLISHED. Packaging
NOT_DEPLOYED, logging containment NOT_VERIFIED. No external broker/account evidence
was collected; future broker-compliant authentication requirements need independent
verification in a separately authorized phase. All four approval roles PENDING.

Existing tests exercise local guards and disposable observer limits. They do not
validate a nonexistent v2 service, signed HALT, authenticated RPC, durable one-use
store, minimal artifact or complete sink allowlist. No redundant broad suite was
added. The future test matrix labels these gaps D / NOT_EXECUTED; own-process
noninterference and cross-process one-use remain DESIGNED.

## Fresh test commands and evidence

Commands are reproduced in phase-13.5g-runbook.md. Logs are ignored local files
tmp/phase135g/focused.log, unit.log and integration.log. All activity uses synthetic
sessions, loopback peers and test-owned disposable PostgreSQL. Full unit tests
include fake execution/HALT and loopback WebSocket fixtures, never operational
resume/arm/execute or real streaming. Official request-budget tests construct but
do not execute requests; synthetic handoff rejects official-origin clients.

| Fresh run | Passed | Failures | Errors | Skipped | Completion |
|---|---:|---:|---:|---:|---|
| Six-class focused selector | 136 | 0 | 0 | 0 | BUILD SUCCESS, 2026-10-11T12:47:32+05:30 |
| Standalone full Java unit/architecture | 1508 | 0 | 0 | 0 | BUILD SUCCESS, 2026-10-11T12:49:39+05:30 |

Selected integration completion is recorded below after actual results. Repeated
unit phases are not summed as unique coverage. No prior phase totals are reused.
Phase F's historical two full-suite errors remain in its unchanged validation;
the baseline includes the separately documented WebSocket polling correction.
No Phase G Java failure or corrective rerun occurred in the completed runs above.

No unfiltered full -Pintegration verify: no shared production transport, security,
authentication, runtime, persistence, dependency or configuration changed. Relevant
selected disposable integration is rerun. No Python source changes; Python tests,
Ruff and strict mypy not applicable and not run. Inline read-only hash/report audits
are not repository Python implementation changes.

## Preservation audit and auxiliary failures

Initial G1 byte SHA passed:
`56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`.
An initial audit compared frozen source hashes to raw bytes and raised AssertionError
before writing an inventory. Inspection found three CRLF files: candidates.py,
diagnostics.py and opening_features.py. The registered run_phase116.sources uses
UTF-8 read_text and hashes normalized text. All 18 match that convention. Corrected
audit passed; no file or criterion was changed to obtain the match. This failure
is retained here and is not represented as an initial pass.

All 63 protected tracked research files matched normalized HEAD blobs initially;
their exact working-tree byte SHA-256 inventory was saved under ignored
tmp/phase135g/protected.json for final comparison. G1 test_state is
SEALED_NOT_EVALUATED; no July market data opened. Latest Phase 12 gate has all five
members unresolved, acquisition_allowed=false and strategy_evaluation_allowed=false.
No .env/token values read; no trading DB connection or live application started.

## Exact Git inventory

No tracked or staged files modified. Exactly six new/untracked deliverables:

- docs/operations/phase-13.5g-review-plan.md
- docs/architecture/kite-db-free-assurance-contract-v2-proposal.md
- docs/architecture/kite-credential-owner-service-boundary.md
- docs/operations/phase-13.5g-security-decision-matrix.md
- docs/operations/phase-13.5g-runbook.md
- docs/operations/phase-13.5g-validation.md

Tracked diff/stat/name-status are expected empty because new documents are
untracked; final status and explicit untracked inventory must match this list.
Generated target reports and ignored tmp/phase135g logs/inventory are local
synthetic evidence, not deliverables. No production/test source, POM, configuration,
migration, research evidence or historical Phase F document is changed.

## Operational and approval outcome

Old Phase 13.5 contract UNCHANGED; v2 PROPOSED / APPROVAL_PENDING. Global trading DB
nonmutation NOT_PROVEN; own-process noninterference DESIGNED. Real authenticated
provenance NOT_ESTABLISHED; cross-process one-use DESIGNED; independent live HALT
NOT_ESTABLISHED; minimal live packaging NOT_DEPLOYED; logging NOT_VERIFIED.
Operational NO_GO. Security/authentication, runtime/DB, packaging/logging and
user/operator approval fields all PENDING. Real-read authorization under v2 NONE.

Real Kite GETs/other HTTP/WebSockets/order mutations: 0. Real token loads/restoration/
exchange and development DB connections/mutations: 0. Operational HALT resume/arm/
execute/permit: 0. No commit/push. Funds response NOT_COLLECTED; collateral-assisted
MIS NOT_READY. Cash reserve, CNC policy, execution fencing and INR 10,000 full
buffered-notional ceiling unchanged. Phase 12 BLOCK; H1/H2 FROZEN; July TEST SEALED.

Next: independent explicit approval or rejection of the fingerprinted v2 proposal's
narrower assurance by all four roles, then separately authorized implementation and
evidence for legitimate owner authentication, durable claims, independent HALT and
minimal package/sink controls. No automatic authentication, deployment, real read
or trading action follows this review. Stop after final report.

## Final audit

PHASE 13.5G RESULT: DESIGN_REVIEW_COMPLETE. This is design completion with fresh
regression evidence, not approval or implementation of the proposed boundary.

Selected `-Pintegration -Dit.test=KiteEquityReadHarnessIntegrationTest verify`
completed BUILD SUCCESS at 2026-10-11T12:54:37+05:30 (2m45s): full unit phase
1508 passed, 0 failures/errors/skips; selected disposable integration 55 passed,
0 failures/errors/skips. Fresh XML independently corroborates 87 unit suites /
1508 cases and the selected integration class / 55 cases. Unselected old Failsafe
reports excluded. All three Maven commands succeeded on first execution; no
test changes or reruns. JDK dynamic test-agent warnings are retained in logs;
the test harness is not represented as the future agent-free owner deployment.

Project verifier PASS. Final git diff --check PASS; all six untracked documents
also explicitly checked for UTF-8, trailing whitespace and final newline, because
git diff alone omits untracked contents. Tracked diff/stat/name-status and staged
diff empty; exact untracked inventory matches the six paths above. HEAD and
origin/develop remain the full baseline SHA on develop. No generated files enter
the inventory; Maven reports and tmp/phase135g evidence confirmed ignored.

Final G1 raw SHA, all 18 registered text hashes, all 63 initial raw byte hashes
and normalized HEAD comparisons PASS. Plan/v2/owner-boundary fingerprints rechecked
unchanged. Five-member acquisition/evaluation gate remains BLOCK; G1 retains
SEALED_NOT_EVALUATED and no sealed market data was opened. All tracked historical
approval/Phase F evidence, production/test/Python sources, resources, migrations,
funding/risk/CNC/reserve/ceiling and execution safeguards remain unchanged. No
live safety state is inferred from this file-preservation check.

Secret scan PASS: `uv run python scripts/check-secrets.py` exited 0, scanned
1741 text files and reported zero potential secret locations. Final audit prose
contains only test counts, public file digests and status; no credential/account
material. No raw real logs or account responses were collected. All required
checks completed; operational NO_GO and all approval fields remain PENDING.
