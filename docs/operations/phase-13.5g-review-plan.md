# Phase 13.5G review plan

Version: ReviewPlan.13.5G.v1, frozen 2026-10-11 before evaluation or tests.
Baseline: clean develop; HEAD and origin/develop both
`a191b9c455c5bc916519846e431ba8341bc799dc`. Required preflight passed.
Fingerprint: SHA-256 of this file's exact bytes, recorded in validation before
evaluation; no self-referential digest. Criteria changes require a new version,
reason, old/new fingerprints and reassessment. No criteria change is planned.

## Claims and decision criteria

Compare A (trading JVM/local handoff with DB observer), B (isolated JVM receiving
a credential), and C (isolated credential owner executing a narrow authorized read).
Select on credential exposure, enforceability, attack surface, restart behavior,
proof strength, feasibility and operational burden. No option is operationally
ready. Produce DiagnosticAssuranceContract.v2 as a proposal only. Preserve the
old contract, approval and Phase F evidence unchanged.

Own-process noninterference requires independently verified absence of direct and
indirect trading-state write authority through package, identity, filesystem and
network controls. It does not prove global trading DB invariance or absence of
other writers. Removing global assurance is a material change requiring separate
explicit approval; the old one-GET approval cannot transfer to this proposal.

Design completeness requires: eight-claim comparison, trust/ownership boundaries,
legitimate authentication origin, durable one-use protocol, independent HALT,
minimal packaging, sink allowlist, funding boundary, negative-case evidence map,
approval roles, exact inventory and reproducible validation. A described control
is DESIGNED until its actual boundary is tested; old local tests cannot certify
cross-process behavior. Missing mandatory validation yields PARTIAL; unsafe
baseline or frozen evidence mismatch yields BLOCKED. Operational status NO_GO.

## Threat model and source hierarchy

Consider malicious/spoofed requester; wrong owner/account; replay, expiry,
revocation, clock skew; concurrent replicas, crash and ambiguous dispatch;
forged/stale/conflicting HALT; dependency/startup authority; credential export;
logs, dumps, proxies, agents and telemetry; external DB writers and write/restore;
untrusted response and unwarranted funding promotion. Compromised host, broker,
authorizer or attestor invalidates the corresponding trust assumption; signatures
alone do not prove a truthful assertion. No global-writer-exclusion assumption.

Source hierarchy: current explicit scope and frozen evidence; actual source and
method signatures at baseline; reproducible synthetic/disposable tests; historical
reports labeled by phase; official public broker documentation for protocol
constraints only. Caller flags, UUIDs, origin labels and synthetic authentication
are not independent live evidence. No account endpoint is consulted for research.

## Allowed validation and exclusions

Documentation-only implementation is preferred: reuse existing negative tests,
with precise untested design gaps instead of redundant suites or toy production
controls. Read source, tests, POM and historical reports. Run the six-class focused
selector from Phase F, full Java unit/architecture suite, and selected disposable
KiteEquityReadHarnessIntegrationTest. Local synthetic WebSocket fixtures in the
required full suite are allowed; real Kite WebSockets are forbidden. Full
unfiltered integration is required only if shared production transport, security,
authentication, runtime or persistence changes; none is intended. Python checks
pytest/Ruff/strict mypy apply only to Python changes, not read-only inline audits.
Run project/secrets/diff checks and audit untracked/generated files.

G1 raw SHA must equal
`56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`.
Verify all 18 source hashes using the registered UTF-8 read_text convention;
initial naive raw-byte comparison failed for three CRLF files, then matched the
actual convention in run_phase116.sources (no content changed). Verify 63 protected
research files against normalized HEAD blobs and retain initial raw byte hashes
for final equality. Never open July market data or run research evaluations.
Preserve Phase 12 five-member BLOCK, July seal, H1/H2, HALT, cash reserve, CNC,
execution fencing, INR 10,000 full buffered notional, config, migrations and .env.

ZERO real broker requests, tokens/load/restore, login, trading DB access/mutations,
live application/service startup, HALT resume/arm/execute, commit or push. No
production loader, deployment, approval parser or revised-contract activation.
Stop if any required work needs a secret, real endpoint, existing DB, guard bypass
or protected-file mutation. Never repair a baseline with Git destructive commands.

## Approval separation and deliverables

Six new files only: this plan; architecture/kite-db-free-assurance-contract-v2-proposal.md;
architecture/kite-credential-owner-service-boundary.md; operations/phase-13.5g-security-decision-matrix.md;
operations/phase-13.5g-runbook.md; operations/phase-13.5g-validation.md (all under docs).
Record proposal byte fingerprint separately in validation after finalizing design.

Security/authentication, runtime/DB, packaging/logging and user/operator approvals
are all PENDING. Separate design completeness, synthetic validation, human contract
approval, live certification, real-read authorization and trading authorization.
No phase promotes another. Authentication provenance and live HALT remain
NOT_ESTABLISHED; real packaging NOT_DEPLOYED; logging NOT_VERIFIED; collateral MIS
NOT_READY. Finish with the requested report and stop.
