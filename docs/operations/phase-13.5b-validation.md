# Phase 13.5B validation evidence

Date: 2026-10-10. Engineering result: **SYNTHETIC_COMPOSITION_VALIDATED**.
Operational decision: **NO_GO**, independently of engineering test results.
Previous Phase 13.5 Stage B remains **ZERO_CALL_ABORT**, actual real requests 0.

## Baseline and plan

Initial working tree clean, branch develop. HEAD and origin/develop both
676087d9676073fd3de2f43e16321637958fce19. All requested baseline commands ran;
git diff --check was empty. No reset/stash/restore/clean/pull, commit or push.
JDK 21.0.12, Maven wrapper 3.9.11, uv 0.12.10, Docker client/server 29.8.0.

Plan frozen before production edits and new tests. Exact byte SHA-256:
12FD1B1C8BC36E146275782AC33D9422C07AC2553FF3EAC7D4D2AF872FA1E289.
G1 SHA verified, existing verifier passed all 18 source hashes, 63 protected
research-file SHA-256 values captured before edits. Five-member acquisition and
strategy evaluation remain disallowed with five unresolved corporate-action reasons.
July TEST not opened; no strategy evaluation or historical corpus access.

## Changes and evidence

Two narrow diagnostic corrections:

1. knownHaltedAt adds positive process-local startup/epoch evidence without altering
   existing execution HALT behavior. Unknown startup evidence now denies handoff
   issuance and harness admission, or discards an in-flight result. A supplier
   which changes the epoch while being observed also denies.
2. Controlled wire rechecks Apache wire/header DEBUG at request creation and
   execute. Late detected logging enablement closes the factory and spends the
   reserved attempt without dispatch or recovery. Live sink safety is not proven.

No auth lifecycle, persistence SQL, normal startup, order adapter, funding approval,
public endpoint, configuration or risk change. Existing synthetic handoff still
rejects official clients. No live launcher or real-session composition added.

New tests cover unknown HALT at issue/admission/response; concurrent owner, session
and epoch invalidation; historical receipt with unusable capability after logout;
late logging changes; 301/302/307/308 without follow-up; direct PostgreSQL ACL denial
of INSERT/UPDATE/DELETE/TRUNCATE/DDL/sequence/escalation/write-function operations;
privilege drift denial and committed write-then-restoration between snapshots.
Existing suites cover replay, expiry, clock rollback, same-owner concurrency,
serialization/private-field JSON, logging markers, timeout/response loss, malformed
and bounded JSON, statistics visibility, schema/content changes and auth restart.

The restoration counterexample passes equality despite two committed writes. This
is evidence that hashes cannot certify all-writer exclusion. The existing child
process lock proves only cooperative exclusion; remote/non-cooperating/new clients
remain outside that proof. See the [operational matrix](phase-13.5b-operational-isolation.md).
Post-read capture remains before revocation checks and is asserted after auth/
isolation loss. Raw rows and private fingerprints are never emitted in reports.

## Newly executed results

| Run | Passed | Failures | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Focused handoff/isolation/harness/wire/architecture/HALT | 132 | 0 | 0 | 0 | PASS, 27.053s |
| Focused disposable build: full unit stage | 1504 | 0 | 0 | 0 | PASS |
| Focused disposable harness/observer | 49 | 0 | 0 | 0 | PASS, build 1m35s |
| Standalone full Java unit/architecture | 1504 | 0 | 0 | 0 | PASS, 1m11s |
| Full -Pintegration verify: unit/architecture | 1504 | 0 | 0 | 0 | PASS |
| Full -Pintegration verify: disposable integration | 574 | 0 | 0 | 0 | PASS, build 13m54s, exit 0 |

Commands: focused selector and full commands in
[operational isolation](phase-13.5b-operational-isolation.md). Build output remains
ignored under tmp/phase135b-*.log. No failed runs so far; no prior phase counts
are reused. Full integration was required for the additive shared HALT and controlled
wire changes. Existing operator tests use fake dispatch/permit/HALT fixtures only.

Project verifier PASS. Initial secret scan: 1706 text files, zero potential secret
locations. Interim diff --check PASS. Python source unchanged; pytest/Ruff/strict
mypy not applicable and not run. Final XML totals independently summed: 87 Surefire
suites (1504 passed), 21 Failsafe suites (574 passed), no failures/errors/skips.
No tool installations or global Java/environment changes.

## Exact deliverable inventory

Modified tracked files:

```text
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadHandoff.java
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadHarness.java
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadRequestFactory.java
apps/trading-core/src/main/java/com/kitehybrid/platform/shared/application/RuntimeTradingHalt.java
apps/trading-core/src/test/java/com/kitehybrid/platform/RuntimeTradingHaltTest.java
apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadHandoffTest.java
apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadRequestFactoryTest.java
apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadHarnessIntegrationTest.java
```

New/untracked files:

```text
docs/operations/phase-13.5b-plan.md
docs/operations/phase-13.5b-validation.md
docs/operations/phase-13.5b-operational-isolation.md
docs/architecture/kite-real-session-composition-review.md
```

## Operational outcome

REAL_AUTH_SESSION_NOT_ESTABLISHED; LIVE_HALT_NOT_ESTABLISHED;
LIVE_WRITER_EXCLUSION_NOT_ESTABLISHED; LIVE_OBSERVER_NOT_ESTABLISHED;
LIVE_BASELINES_NOT_ESTABLISHED; LIVE_LOGGING_NOT_ESTABLISHED.
No live facts were measured. Exact route/budget and redaction are source/synthetic
controls, not independently instantiated live guarantees. Operational NO_GO.

Eligible adjusted collateral and free collateral UNKNOWN. Exact NSE MIS cash rule
and qualifying-field public-source conflicts unresolved; current account terms
UNKNOWN. Collateral-assisted MIS NOT_READY. No real account response collected,
no executable candidate/quantity selected, no support draft sent.

Real Kite GETs/other requests/WebSockets/order mutations: 0.
Real token/development DB mutations: 0. Operational HALT resume/arm/execute: 0.
INR 10,000 full buffered-notional ceiling, cash reserve, CNC and final dispatch
unchanged. No .env, application safety defaults, migrations or protected research
changes intended or authorized. H1/H2 FROZEN; SBIN July TEST SEALED.
No commit/push. No automatic request after this phase.

Next bounded action: separate design/operational review of legitimate existing
authenticated-session composition and enforceable all-writer exclusion, followed
by fresh independent live precondition review and separately scoped authorization.

## Final audit

The full build completed successfully at 2026-10-10 20:59:43 +05:30. All new code
was frozen before this run; only validation documentation was completed afterward.
No failing test/build run occurred in this phase. Focused counts are overlapping
subsets, not added to the full totals. Old Phase 13.5/13.5A totals remain historical.

Final protection verifier passed the expected G1 SHA, all 18 source hashes, all 63
protected file hashes and the unchanged plan hash. Acquisition/evaluation remains
BLOCK. Production diffs were reviewed: only the four files in the inventory above;
resource diffs empty. Git status, diff --stat/name-status/check and untracked
inventory match eight modified tracked files plus four new documents. No staged
files, .env/config/migration/POM/Python/risk/reserve/CNC/protected research changes.
Generated target output and temporary fixture logs/hash inventory remain ignored.
No raw account evidence or real token material was collected or committed.
Final project verification and diff --check passed. Final secret scan covered
1707 text files with zero potential secret locations. HEAD and origin/develop
remain 676087d9676073fd3de2f43e16321637958fce19 on develop; no staged changes.

Engineering validation does not close any live prerequisite or authorize a request.
The official-origin rejection remains intact. Operational NO_GO is the final
certification result; no real follow-up is performed automatically.
