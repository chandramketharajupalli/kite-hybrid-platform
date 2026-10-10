# Phase 13.5D frozen deployment-isolation protocol

Registered 2026-10-10 before implementation and new tests. Clean develop, HEAD and
origin/develop both db3e5d697b80b95a806010258426b14d590ba831. JDK 21.0.12,
Maven wrapper 3.9.11, uv 0.12.10, Docker client/server 29.8.0. G1 manifest,
18 sources and 63 protected research hashes verified before work. Stage B remains
ZERO_CALL_ABORT; this phase has zero real broker requests and real token loads.

## Acceptance and scope

Success means a reviewed deployment design, fresh passing unit/architecture and
disposable PostgreSQL tests, and honest operational NO_GO. It never means a real
GET. Existing production controls are sufficient for synthetic composition; do not
add a launcher, loader, Spring wiring, public endpoint or production configuration.
Keep official-origin rejection, request fencing, funding policy and HALT unchanged.

Allowed edits are this plan; phase-13.5d-validation.md, certification-matrix.md and
runbook.md under docs/operations; docs/architecture/kite-read-only-deployment-isolation.md;
and narrowly justified tests in KiteEquityReadHarnessIntegrationTest.java and
ControlledEquityReadArchitectureTest.java. No production edits are planned.
All other paths require a documented demonstrated gap before expanding scope.
No research, migrations, configuration, secrets, risk, CNC or reserve changes.

## Threat model and evidence boundaries

Consider replay, wrong owner/recipient, stale session, clock rollback, concurrent
consume, crash, privileged same-JVM reflection, memory dumps, stolen process
credentials, logger changes, retries and redirects. Private references constrain
trusted code; UUIDs and fingerprints do not authenticate a broker session.

Consider Windows services/tasks/IDE launches, Java and non-Java clients, Docker/WSL,
remote hosts, alternate credentials, existing sessions, new sessions, privileged
roles, inherited ACLs, triggers and functions. Cooperative locks, NOLOGIN alone
and equal snapshots do not prove global exclusion. Retain their counterexamples.
The database observer and writer-control authority must be independent. Observer
ACLs do not fence other clients. Multi-statement snapshots are not atomic and
cannot detect every transient write or privilege change restored between captures.

Compare dedicated minimal JVM, read-only disposable sidecar and offline synthetic
mode. No live topology is deployed. Current full application classpath is not a
minimal isolated distribution. Cross-process private-reference handoff is not
available without a separately reviewed trust design; no portable token artifact.

## Tests registered before results

1. Re-run handoff, isolation, harness, request factory, HALT and architecture tests:
   identity/epoch/replay/expiry/revocation, unknown HALT, loss during response,
   strict route/one attempt/one execute/no retry/redirect, bounded parsing and
   logging/serialization containment. No official-origin dispatch.
2. Disposable extension: deny new connections for an enumerated fixture writer,
   terminate only its owned container backend, prove old and new writer denial;
   then demonstrate that a privileged separate connection still defeats a claim
   of global exclusion. Preserve existing NOLOGIN-only counterexample.
3. Test-only canonical permission fingerprint: role flags/memberships, database,
   schema, table, sequence and application-function privileges. Repeated capture
   matches; TRIGGER grant differs and aborts before HTTP; revoke restores hash,
   explicitly demonstrating the snapshot blind spot. Never print hash contents.
4. Re-run disposable token/content/count/schema, inherited rights, visibility,
   external writer, empty-table schema, rollback and write/restore tests. Post-read
   integrity remains attempted after auth/HALT/isolation loss.

Use only Testcontainers PostgreSQL and loopback fixtures. Fixture administrator
authority is bounded to its disposable container; no development DB connection.
Do not terminate any unrelated host process or real database session.

## Commands and decision rules

Use repository mvnw.cmd with JDK 21. Focused test selector:
KiteEquityReadHandoffTest,KiteEquityReadIsolationTest,KiteEquityReadHarnessTest,
KiteEquityReadRequestFactoryTest,ControlledEquityReadArchitectureTest,RuntimeTradingHaltTest.
Run full `./mvnw.cmd test` and `./mvnw.cmd -Pintegration
-Dit.test=KiteEquityReadHarnessIntegrationTest verify`. Full unfiltered integration
is additionally required if shared production auth/transport/HALT/DB/config changes.
Run `uv run python scripts/verify-project.py`, `uv run python scripts/check-secrets.py`
and `git diff --check`. Python tests/Ruff/mypy apply only if Python source changes.
Count fresh reports, excluding stale unselected integration reports. Record failures
and reruns honestly. Recheck G1/18/63 and protected boundaries after testing.

Stop for baseline mismatch, secrets, real-system dependencies or unsafe isolation.
Incomplete required tests mean PARTIAL; unsafe baseline/design means BLOCKED.
Live provenance, HALT, writer exclusion, observer baselines and sink safety remain
NOT_ESTABLISHED/NOT_VERIFIED, so operational NO_GO regardless of synthetic success.

## Artifacts and fingerprint

SHA-256 of the exact bytes of this plan is frozen after creation and recorded in
validation. Do not edit acceptance criteria after results. Reports contain only
sanitized decisions/counts and source paths; no real balances, session identifiers,
token hashes or credentials. Ignored tmp logs contain synthetic test output only.
No commit/push and no automatic follow-up GET.
