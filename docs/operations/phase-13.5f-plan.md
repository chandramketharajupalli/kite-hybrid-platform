# Phase 13.5F frozen predecision plan

2026-10-10. Clean develop; HEAD/origin/develop both
e7868c479779636e6dc9e749a2e9440a2369e492. Required Git preflight passed before
edits. Initial G1 SHA, existing 18-source verifier and all 63 protected byte hashes
passed; normalized Git blobs also match HEAD. Phase 12 acquisition/evaluation BLOCK.
July TEST unopened. Protected inventory retained locally in ignored
tmp/phase135f-protected.json, never edited as research evidence.

## Decision and allowed scope

Compare A existing JVM/observer, B separate DB-free JVM and C isolated custody/read
service with external attestation. Select a future architecture only; no real
ownership, approval or deployment claim. Define documentation-only
DiagnosticAssuranceContract.v1 with explicit old/new guarantee comparison.
All removed snapshot/global-invariance requirements are CONTRACT_CHANGE_PENDING_APPROVAL.
Preserve the old harness, integrity guard and official-origin handoff rejection.

Allowed changes: six documents named in the user deliverables: this plan,
phase-13.5f-validation.md, phase-13.5f-decision-matrix.md, phase-13.5f-runbook.md
under docs/operations; kite-db-free-assurance-contract.md and
kite-authenticated-session-ownership.md under docs/architecture. No code or schema
runtime activation, module, dependency, launcher, credential bridge or config edit.
Existing synthetic controls suffice for their existing claims; do not duplicate
tests or claim they validate new external attestation or minimal packaging.

## Threats and evidence hierarchy

Threats: owner/recipient spoofing, replay, stale session/epoch, races, bearer export,
malicious process/agent, Windows/WSL/Docker/remote writers, privileged DB roles,
triggers/functions, shared storage, transient write/restore, arbitrary logging
sinks, telemetry/dumps and response loss. Distinguish own no-write, own no-access
and global no-writer claims. Only the first two are DB-free design objectives.

Authority hierarchy: scoped independent authenticated operational evidence;
reviewed artifact/deployment controls; source/test evidence; caller labels.
Synthetic authentication, UUIDs, origin flags and snapshots do not establish live
broker identity or global exclusion. Separate design recommendation, synthetic
validation, verified live prerequisites and explicit real-read authorization.

Future ownership design must identify legitimate interactive-auth provenance,
account/session/owner binding, authenticated recipient/channel, expiry/revocation,
atomic consume, crash/restart denial and no portable bearer artifact. HALT requires
independent issuer, instance scope, monotonic epoch, freshness, challenge/replay
protection and compromise handling. No real login, token load or attestor here.

## Registered validation

Fresh focused tests: KiteEquityReadHandoffTest,KiteEquityReadIsolationTest,
KiteEquityReadHarnessTest,KiteEquityReadRequestFactoryTest,
ControlledEquityReadArchitectureTest,RuntimeTradingHaltTest.
Fresh full `./mvnw.cmd test`. Relevant disposable proof will also be rerun using
`./mvnw.cmd -Pintegration -Dit.test=KiteEquityReadHarnessIntegrationTest verify`
to preserve writer/privilege/snapshot counterexamples used in the contract comparison.
No unfiltered integration needed absent shared production changes. No Python
changes: pytest/Ruff/mypy not applicable. Use JDK 21 and repository wrapper.

Map existing negative tests to owner/recipient, expiry/replay/revocation, unknown
HALT/epoch, route/method/budget/second execute, response loss, serialization,
call-graph separation and disposable observer limits. Do not represent these as
tests of unimplemented external signatures, sink allowlists, custody or packaging.
Run project/secrets/diff checks and final G1/18/63/gate audit. Count actual fresh
reports; preserve any failure and correction; never count stale integration reports.

## Acceptance, artifacts and stop

Design validation requires a versioned normative proposal, guarantee table,
ownership/attestation protocol and threat decisions, packaging allow/deny lists,
logging event/sink policy, decision matrix, synthetic-only runbook and passing
required regressions. No real implementation is required by this documentation
scope. Missing required checks => PARTIAL; baseline/safety failure => BLOCKED.
Global DB nonmutation NOT_PROVEN, real ownership/HALT NOT_ESTABLISHED, live
packaging/egress/logging NOT_VERIFIED, revised contract APPROVAL_PENDING and real
read NOT_AUTHORIZED. Operational NO_GO regardless of synthetic success.

Zero real HTTP/WS/token/development-DB activity and operational HALT/arm/permit/order
actions. No .env, migrations, defaults, risk/CNC/reserve/notional, research or July
access. No commit/push or destructive Git. Stop on any need to bypass a guard.
No raw balances, token/session identifiers or private hashes in reports. Frozen
plan SHA-256 recorded in validation before tests; criteria remain unchanged.
