# Phase 13.4 validation

Date: 2026-10-10. Engineering status: **READ_ONLY_EQUITY_MARGIN_ADAPTER_VALIDATED**.
Real account verification NOT_PERFORMED; real-read proposal UNAPPROVED / DENY.
No engineering test result proves current broker eligibility or authorizes trading.

## Baseline and prerequisites

Before implementation inspection/edits: clean `develop`, HEAD and locally stored
origin/develop both `d61f6b28e84a1edab7b0ff7f8391d36aba16d16b`; `git diff --check`
empty. No fetch/pull/reset/restore/stash/clean/commit/push. Baseline log:

```text
d61f6b2 Document Zerodha MIS collateral evidence gaps
18608d4 Add broker-verified MIS collateral evidence contract
12117e9 Add Kite MIS funding evidence diagnostics
2942195 Harden Kite Connect runtime integration
ac87a96 Document pending corporate-action evidence closure
1624d34 Document corporate-action completeness review
9193e0b Reconcile historical NSE security identities
0e71039 Enforce historical identity certification gate
```

Oracle JDK 21.0.12; wrapper Maven 3.9.11; uv 0.12.10; Docker client/server 29.8.0.
No tool installation or global environment change. Deployment/JVM overrides were
removed only in child test processes as listed in the registered plan. No `.env`
loaded. Python checks used PYTHONDONTWRITEBYTECODE=1 / `-B`.

Plan SHA-256, registered before production edits and unchanged:
`7fcadfab8326f4d24b60c6955372222c675d2c1687ecd96afc34f6f54917ccce`.
First full unit run caught a forbidden broker-infrastructure dependency on risk.
The guard was not weakened: the pure projection moved to the existing
`IntradayFundingEvidence` class, adding one production file to the planned
inventory. No acceptance criterion or funding policy changed.

## Implementation and evidence outcome

The existing transport adds only GET `/user/margins/equity`, with its existing
bounded payload/error/session handling. No order permission or public exposure
changes. New package-local adapter defaults disabled and has no startup wiring.
Strict single-segment normalization reuses `SegmentMargin`; required missing/null
fields fail, signed/unknown values never establish eligibility. Receipt time is
local; broker observation time and snapshot atomicity are unknown.

Private snapshot ownership/session binding denies cross-reader and changed-session
reuse. Its redacted receipt separates synthetic from fixed-origin authenticated
collection. `IntradayFundingEvidence.equityObservation` is a pure, caller-normalized
V1 projection, retaining request/reference fingerprints and freshness/conflicts;
no caller-supplied receipt or schema ID can create PROVEN eligibility.
Single-equity cash-only result is MIS_MARGIN_UNAVAILABLE because complete account
context is absent. Existing full-context cash-only arithmetic remains unchanged.

| Question | Public assessment | Missing independent evidence |
| --- | --- | --- |
| Eligible adjusted collateral | UNKNOWN | Dated, authenticated NSE MIS eligible aggregate, category/haircut contribution and restrictions |
| Actually available collateral | UNKNOWN | Free eligible amount after utilisation, pending commitments and reservations, with authoritative computation |
| Applicable cash-component requirement | CONFLICTING | Exact account/product/category rule, precedence and effective date resolving positive-cash/category wording |
| Qualifying cash-field mapping | CONFLICTING | Broker-confirmed API field/computation, exclusions, utilisation and validity resolving UI naming differences |

All four runtime terms remain UNKNOWN for valid synthetic context; STALE and
CONFLICTING remain context denials. No actual account/request/effective date/expiry
or real evidence fingerprint exists. Collateral-assisted MIS: NOT_READY.
Sources/method/schema assumptions and access dates are in the
[architecture contract](../architecture/kite-read-only-equity-margins.md).
Official GET path confirmed on 2026-10-10; direct single-segment body is a stated
synthetic assumption because the documentation illustrates the combined body.

## Test commands, outcomes and reruns

Commands use the repository wrapper from its root. Detailed local logs are ignored
under `tmp/phase134-*.log`; no raw real account responses or credentials exist.

| Run | Passed | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Registered focused unit selection | 470 | 0 | 0 | 0 | PASS; before architectural relocation |
| First full `mvnw.cmd test` | 1382 | 1 | 0 | 0 | FAIL: KitePhase2SafetyTest detected the new infrastructure-to-risk dependency |
| Full `mvnw.cmd test` after relocation | 1383 | 0 | 0 | 0 | PASS, 1m18s; includes all focused classes on final production layout |
| `mvnw.cmd -Pintegration verify`, unit stage | 1383 | 0 | 0 | 0 | PASS; 82 XML suites |
| `mvnw.cmd -Pintegration verify`, disposable stage | 525 | 0 | 0 | 0 | PASS; 20 XML suites; full build 13m33s, exit 0 |

The focused selector is recorded verbatim in the frozen plan. The new unit class
has 51 cases for exact GET/auth, disabled-before-HTTP, strict malformed/oversized
shapes, signed decimals, segment confusion, synthetic provenance, redaction,
HTTP/transport failure taxonomy, session changes and stale/request/reference
conflicts. Architecture checks preserve the original broker/risk separation and
exclude write/startup/execution capabilities. No architecture exception was added.

Existing full suites cover cash equality/one-paisa deficit, charges/margin increases,
full-notional ceiling, CNC, COMPLETE-to-FILLED terminal normalization, pending and
unknown orders, preflight/final-refresh deterioration, response loss, competing
instances, HALT/permit fencing and reconciliation. Those execution exercises are
isolated fake fixtures, not operational live/paper dispatch.

New disposable integration passed all 11 response scenarios: success, 401/403/429/500/503,
malformed, oversized, timeout, connectivity and TokenException. Fixture migration
and synthetic token seed precede observation. The observer uses SELECT-only role
and default read-only transactions; compares every trading table's count/content
and encrypted token bytes without printing them, and checks unchanged HALT epoch
plus zero order/authorization rows. No Spring startup or development store.

Python source unchanged: pytest/Ruff/strict mypy not run, as conditional in scope.
Project verifier PASS (exit 0); secrets PASS (1682 files, zero candidate locations,
exit 0) after deliverable preparation; diff check PASS. Final checks recorded below.

## Exact Git inventory

Modified tracked files:

```text
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteRestTransport.java
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteTradingReadMapper.java
apps/trading-core/src/main/java/com/kitehybrid/platform/risk/domain/IntradayFundingEvidence.java
apps/trading-core/src/test/java/com/kitehybrid/platform/TradingReadArchitectureTest.java
```

New/untracked deliverables:

```text
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityMarginReadAdapter.java
apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityMarginReadAdapterTest.java
apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityMarginReadIntegrationTest.java
docs/operations/phase-13.4-plan.md
docs/operations/phase-13.4-real-read-proposal.md
docs/operations/phase-13.4-runbook.md
docs/operations/phase-13.4-validation.md
docs/architecture/kite-read-only-equity-margins.md
```

No README/index edit needed. Generated Maven targets and local test/hash logs are
ignored, not deliverables. No bytecode, raw broker responses or generated artifacts
in tracked/untracked deliverable inventory. Resource/configuration/migration diffs
are empty. All production changes were inspected, including untracked adapter.

## Freeze and safety audit

G1 manifest SHA-256 matches
`56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`.
Existing `run_phase116.verify_freeze` verifies all 18 source fingerprints without
strategy evaluation. Baseline SHA-256 inventory of all 63 protected tracked
research files compares unchanged. Five Phase 12 CORPORATE_ACTION_UNRESOLVED
reasons remain; acquisition/evaluation allowed both false. July TEST unopened.

No `.env`, application defaults, migrations, risk limits, cash capacity/CNC,
preflight/final-dispatch, token lifecycle or frozen research modifications.
INR 10,000 full buffered-notional ceiling and cash-only reserve unchanged.
Operational real account GETs, calculator requests, historical GETs, WebSockets,
order mutations, development DB/token writes, HALT resume/arm/execute: all **0**.
No real-read approval received or request attempted. No commit/push.

Next bounded action: review the unsent Phase 13.3 broker questions for exact
dated NSE MIS eligible/free collateral and cash-field semantics. The Phase 13.4
one-GET proposal remains unapproved and may establish observations only; its
real no-write credential-loading harness is not activated. No automatic live promotion.

## Final audit record

Executed `git status --short`, `git diff --check`, `git diff --stat`,
`git diff --name-status`, `git ls-files --others --exclude-standard`, and full
production Java/resource diffs. Inventory: four modified tracked files, eight
new/untracked files, exactly as listed above. Tracked diff: 63 insertions,
4 deletions; Git diff-stat excludes the separately inventoried new files.
No staging, commit or push. HEAD and origin/develop remain the exact baseline.
Final repeated project/secrets/diff commands all exited 0; the final secret scan
examined 1681 text files and found zero candidate locations. The earlier 1682-file
scan also passed; scan totals include local transient text files.

Surefire XML: 82 suites, 1383 tests, zero failures/errors/skips. Failsafe XML:
20 suites, 525 tests, zero failures/errors/skips. No skipped integration classes.
The initial unit architecture failure is recorded above; there was no integration
failure or integration rerun. FinalReadinessEvidence 41, IntradayMarginRehearsal
42, KiteAuthenticationRestart 8 and new KiteEquityMarginRead 11 all passed.
The focused selector also ran as part of both successful full unit stages.

Final freeze verification repeats G1 SHA, all 18 source checks, all 63 baseline
protected research hashes, unchanged frozen-plan SHA and five-member BLOCK.
The approval question was presented for the concrete one-GET proposal; no approval
was received. The real-request boundary was not crossed. All operational zero
counts and restrictions above remain in effect.
