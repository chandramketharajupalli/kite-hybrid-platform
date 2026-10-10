# Phase 13.5B frozen certification plan

Registered 2026-10-10 before production edits or new tests. Clean develop;
HEAD and origin/develop: 676087d9676073fd3de2f43e16321637958fce19.
JDK 21.0.12; Maven wrapper 3.9.11; uv 0.12.10; Docker 29.8.0.
G1 manifest and 18 source hashes verified; 63 protected research hashes captured.
Five-member acquisition/evaluation remains BLOCK. July TEST is not opened.

## Scope and evidence requirements

Preserve Phase 13.5 Stage B ZERO_CALL_ABORT, zero requests. Committed handoff
rejects official-origin clients and is a trusted same-process synthetic object
capability, not authenticated IPC. Keep this rejection. No token loader, real
launcher, Spring startup, broker client duplication or funding engine is allowed.
Real broker request budget is ZERO, including previously conditionally approved GET.

Code/tests can establish synthetic behavior only. Operational GO requires independent
current evidence for authenticated session provenance, known live HALT/epoch,
all-writer exclusion, observer privileges/visibility, private live integrity
baselines, and actual logging/sink safety. Missing any term means NO_GO. A caller
boolean, UUID, official schema identifier or successful test cannot supply proof.

## Threat model and narrow corrections

Cover replay/theft, wrong owner/recipient, expiry/clock rollback, session change,
concurrent consume/revocation, epoch replacement, lease loss and process takeover.
Existing owner references are meaningful only inside trusted code; hostile JVM
agents/reflection/OS administrators are outside that boundary. No portable secrets.

RuntimeTradingHalt currently converts unavailable startup evidence to true for
execution safety. Add an independent known-halted observation for diagnostics;
retain existing execution/status/resume semantics. Harness and handoff must reject
unknown startup evidence before HTTP and discard results on later loss. Recheck
verbose wire/header logging at the wire boundary as well as harness admission.

Test cooperating exclusion and explicitly demonstrate non-cooperating limitations:
new/remote writers, services/tasks/IDE/containers/WSL, and committed writes restored
between snapshots. Read-only roles and before/after equality do not prove exclusion.
Returned redacted receipts are historical observations, never continuing authority.

## Allowed exact file scope

Under apps/trading-core/src/main/java/com/kitehybrid/platform/:
- shared/application/RuntimeTradingHalt.java (additive diagnostic observation only)
- broker/infrastructure/kite/KiteEquityReadHarness.java
- broker/infrastructure/kite/KiteEquityReadHandoff.java
- broker/infrastructure/kite/KiteEquityReadRequestFactory.java

Matching tests RuntimeTradingHaltTest, KiteEquityReadHandoffTest,
KiteEquityReadRequestFactoryTest, KiteEquityReadHarnessTest,
KiteEquityReadIsolationTest, ControlledEquityReadArchitectureTest and
integrationTest/.../kite/KiteEquityReadHarnessIntegrationTest may change.
Only the four requested Phase 13.5B documentation files are new deliverables.
No auth lifecycle, integrity SQL, config, migration, risk, reserve, CNC, research,
sealed data, .env or broker-order adapter changes. Preserve guarded sequence query.

## Acceptance matrix

- Existing atomic consume/replay/owner/session/expiry/revocation/serialization tests
  pass; unknown HALT at issue/admission and during response denies without retry.
- Before-dispatch invalidation yields zero peer requests. During-response loss
  attempts post-read integrity, discards evidence and cannot dispatch again.
- Exact loopback GET; 301/302/307/308, 401/403/429/5xx, timeout/response loss,
  malformed/duplicate/trailing/oversized bodies; bounded parsing and one execute.
  Official process-wide budget tested without executing a real request object.
- Verbose logging enabled after admission still prevents dispatch. Synthetic marker
  logs and private-field/native serialization do not expose capability secrets.
- Disposable observer: actual denied INSERT/UPDATE/DELETE/TRUNCATE/DDL/sequence/
  role escalation/write-function attempts; absent statistics and privilege drift
  deny before HTTP. Existing content/count/empty-table-schema checks pass.
- A committed write followed by restoration can compare equal: explicitly record
  this counterexample as a limitation, not an isolation PASS. No live exclusivity.
- Architecture excludes auth restoration, execution/permit/operator/startup/order
  dependencies. Synthetic receipts never promote collateral evidence to PROVEN.

## Validation and stop conditions

Run focused Maven tests, full ./mvnw.cmd test, and full ./mvnw.cmd -Pintegration
verify because shared HALT/wire code changes. Disposable PostgreSQL only; existing
fake operator regression suites are not operational authorizations. Run project
verifier, secret scan, diff --check, G1/18/63 hashes and final Git inventory.
Python unchanged: pytest/Ruff/mypy not applicable unless source changes.
Report failures and reruns, never reuse previous counts. Baseline/safety failure
blocks; incomplete validation is PARTIAL. Do not relax acceptance criteria.

Plan fingerprint is SHA-256 of this file's exact bytes, recorded externally in
validation after creation and checked again at completion (avoids self-reference).
Temporary hashes/logs stay ignored; no token fingerprints, raw balances or account
payloads in artifacts. Deliver validation, operational-isolation matrix and
real-session-composition review. No commit/push or automatic real follow-up.
