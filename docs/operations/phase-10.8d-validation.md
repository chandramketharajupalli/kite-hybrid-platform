# Phase 10.8D: intraday-first funding contract and readiness

## Result

**PHASE 10.8D RESULT: NOT_READY. This phase does not authorize a live order.**

NSE cash-equity INTRADAY/MIS is the primary current product. The engineering
contract is now explicitly documented in
[intraday-first funding and strategy boundary](../architecture/intraday-first-funding-contract.md).
The existing implementation already provides a conservative, machine-enforceable
funding gate; no demonstrated production defect justifies loosening it.

The minimum accepted funding evidence is an exact current margin/charge quote,
current normalized equity/account evidence, an explicit application cash lower
bound, positive cash reserve, and authoritative collateral terms only when the
exact candidate needs collateral. Aggregate collateral plus a successful margin
calculation does not supply those missing terms. Cash-only MIS can pass without
collateral terms when the unchanged cash policy covers margin, charges and
reserve. The Phase 10.8C account shape does not pass that policy.

## Baseline and source review

The supplied `<PHASE_10_8C_COMMIT>` placeholder resolved unambiguously by its exact
subject to **78f4c2088e8d824abc5f0ba1686dfcc0946e9fc8**, `Document pledged collateral
composition review`. Initial branch was `develop`; HEAD and local
`origin/develop` both matched; `git status --short` was empty. The requested
ten-commit log was inspected before source work. No reset, stash, restore,
checkout, clean, commit or push.

Reviewed ADR-019 and Phase 10.6, 10.7, 10.7A, 10.8A, 10.8B and 10.8C reports,
then checked source for valuation/sizing, planner, risk/capacity, current-account
checks, execution/admission/transport, one-shot/HALT and reconciliation. The
architecture document maps the actual call graph and its final transport callback.
Earlier report limitations are historical where later phases remediated them.

The official [margin calculation API](https://kite.trade/docs/connect/v3/margins/)
and [margin field contract](https://kite.trade/docs/connect/v3/user/#funds-and-margins)
were rechecked on 2026-10-05 for the narrow evidence question. No repeated
investigation into whether collateral can generally support intraday trading.
The calculator remains calculation-only POST `/margins/orders`, no order route
fallback, no retry, and no inferred eligible-collateral/cash terms.

## Accepted funding policy

The complete equations and trusted/unknown input table are in the architecture
document. The implementation has these decisive properties:

- CNC keeps its conservative minimum cash/opening/live/net floor and deductions.
- MIS uses that cash floor plus its existing exclusions; this is **application
  acceptance policy**, not a replica of Zerodha RMS or a claim that the broker
  rejects every excluded funding source.
- Without authoritative terms, eligible collateral is zero and the full exact
  required margin, charges and reserve must be cash funded.
- With complete current terms, eligible collateral is capped by reported
  available collateral and the supplied eligible adjusted aggregate; utilised
  collateral is deducted, never added. Net only tightens the total bound.
- `availableCollateral=X` and `utilisedStockCollateral=X` cannot become `2X`.
  `cash=100, collateral=2000, net=2100` cannot become 4100.
- The production calculator adapter supplies absent terms. Domain terms do not
  establish their own provenance. No user toggle, pledge label, historical
  report or arbitrary haircut can populate them.

A future authoritative aggregate eligible amount plus applicable cash terms
could satisfy the narrow contract without per-security RMS reconstruction.
This is a smaller evidence route than a general collateral engine. The currently
available aggregate and calculator response do not prove that contract, so
collateral-dependent readiness remains blocked. Per-security analysis is not
mandated merely for its own sake.

## INR 10,000 and exact intent

Full buffered market notional remains independent of margin. Planning sizes
from fresh price x shared validated buffer, floors by lot and hard caps, then
recomputes exact BigDecimal notional. Risk max-order-value, normal execution and
first-live caps must be reviewed at INR 10,000 or any stricter value for the future
controlled process; no real limit was configured here. Equality passes; one
paisa over fails. Generic production code does not hard-code the human amount.

| Boundary | Existing enforcement |
| --- | --- |
| Planning | Human, risk, normal, first-live and account exposure caps; exact recomputation |
| Risk approval | Current shared conservative valuation vs reviewed risk max-order-value; position/exposure/funding gates |
| Preflight | Current normal/risk/first-live valuation plus fresh account/quote |
| Admission | Revaluation before/after lock wait and before CAS transaction commit |
| Final dispatch | Current quote/account refresh, then current local valuation and all fences again |

Margin never divides the INR 10,000 budget or increases Q. Price changes can
invalidate a previously affordable exact order even when margin remains ample.
Changed quantities need new review/risk identity. MARKET execution can fill above
the observed pre-trade notional; the ceiling is not a fill-value guarantee.

## Real evidence and read budget

**Phase 10.8D real broker read budget: 0. Actual: 0.** No real review harness,
Spring runtime, broker calculator, DB connection or token read was needed.
Funding evidence remains unresolved before the permitted price-collection gate,
so no SBIN or full-universe WebSocket subscription was started.

Phase 10.8C evidence is accepted as historical, observed on
2026-10-05 13:10:37-13:10:42 UTC, not refreshed or presented as current:

| Historical observation | Value |
| --- | --- |
| available.collateral | INR 1089220.75181 |
| liveBalance / equity.net | INR 318.50 / 1089539.25181 |
| openingBalance / cash | INR -14681.50 / -14681.50 |
| intradayPayin | INR 15000 |
| Pledged securities | 23 |
| Derived previous-close gross mark | INR 1279365.65; not an adjusted amount or local haircut |
| Net/day nonzero positions; non-terminal orders | 0 / 0; 0 |
| SBIN holding/position/orders/trades | None / none / 0 / 0 |
| SBIN reference | One NSE CASH identity, enabled universe row, lot 1, tick 0.05 |
| Authentication | Authenticated/token/initialization/identity available at that observation |
| HALT | HALTED, emergency stop true at that observation |
| Token and DB preservation in 10.8C | Token fingerprint and nine table counts unchanged |

The observed arithmetic `collateral + live = net` and `opening + pay-in = live`
is not a reusable RMS formula. No net/collateral/pay-in double counting or
haircut inference. Under unchanged local policy that historical shape produces
cash floor -14681.5 and MIS policy cash -29781.5 after exclusions, not a broker
debt assertion. It therefore cannot justify a current cash-only candidate.

Real readiness fields in this phase:

| Field | Result |
| --- | --- |
| SBIN reference | RECHECK_REQUIRED; prior identity not asserted current |
| SBIN market data | NOT_STARTED |
| Candidate quantity / conservative notional | NONE / NOT_CALCULATED |
| Required MIS margin | NOT_OBSERVED; old one-share estimate not reused |
| Eligible funding / funding headroom | UNKNOWN / NOT_ESTABLISHED |
| Authentication / account cleanliness | RECHECK_REQUIRED |
| Real HALT | Real runtime not started/accessed; prior HALTED evidence historical |
| Real broker order mutations / DB mutations / token mutations | 0 / 0 / 0 |

No before/after real token/DB measurement was repeated because the real DB and
token store were never accessed in this phase. The 10.8C preservation evidence
is not misrepresented as a new check. Any future real harness still requires
read-only transactions, no Flyway/auth exchange/token writes, an independent
HALT guard, bounded reads and new before/after preservation checks.

## Synthetic regression work

Added focused unit evidence for:

- cash-only MIS with charges and positive cash reserve: equality passes, one
  paisa below denies; unknown collateral can coexist when it is not needed;
- sufficient aggregate net/collateral plus a successful quote still denies when
  required terms are absent;
- net below cash-plus-eligible-collateral only tightens capacity, never adds to it.

Added disposable integration cases that risk-approve cash-only MIS at exactly
2000 margin + 1.25 charges + 1 reserve = 2002.25 cash. Unchanged evidence sends
one loopback order request; a one-paisa cash drop or required-margin increase
at the final callback denies with `MIS_MARGIN_INSUFFICIENT`, zero order POSTs,
FAILED durable state and a consumed permit. Historical risk rows stay unchanged.

All required final-boundary changes are covered by the retained MIS rehearsal:

| Change before final transport | Expected result |
| --- | --- |
| Price 800 -> 900, Q=11, buffer=1.10 | Notional 9680 -> 10890, zero order POST despite sufficient funding |
| Required margin rises | Insufficient funding; zero order POST |
| Collateral falls | Insufficient funding; zero order POST |
| Cash deteriorates | Cash/reserve fails; zero order POST |
| Position appears beyond reviewed capacity | Position/exposure gate denies; zero order POST |
| Pending broker order appears | Blocking order gate denies; zero order POST |
| Authentication initialization disappears / session replaced | Deny; zero order POST |
| HALT activates, including during blocked calculator | Deny; local HALT remains immediate |
| Reference changes / price becomes stale | Deny; zero order POST |

The generic risk model can allow positions within limits; first-live cleanliness
is an additional review prerequisite, not a new blanket generic position policy.
Existing cases also retain response-loss reconciliation while halted, rejection,
timeout/reset/malformed acknowledgement, post-response persistence failure,
one-shot consumption and cross-instance admission with at most one winner.

Added an architecture check that strategy application/domain code cannot depend
on execution gateway, arming, HALT control, operator execution or safety policy;
calls to `OrderApplicationService` are limited to order proposal (`place`).
Existing architecture checks preserve read-only account ports, pure sizing,
shared valuation and the separation from broker infrastructure.

## Test isolation and validation

New execution tests reuse the existing Testcontainers PostgreSQL fixture, unique
disposable databases, synthetic sessions/account state, injected clocks and
guarded literal `127.0.0.1` broker transport with redirects rejected. They do not
load `.env` or real tokens. No development database coordinates are used for tests.

Tests run in a child PowerShell that clears ambient deployment/JVM-option
variables for that process only; no diagnostics environment helper is sourced.
The initial check found zero such ambient variables, and test fixtures also
remove system-environment/property sources before applying synthetic overrides.
Default-off tests remain unchanged. No persistent environment settings changed.

Executed commands (the local launcher clears ambient deployment variables in a
child process only):

```powershell
.\mvnw.cmd '-Dtest=ConservativeOrderValuationTest,ConservativeOrderQuantitySizerTest,IntradayAccountCapacityTest,IntradayCandidatePlannerTest,KiteOrderMarginAdapterTest,ConservativeValuationArchitectureTest,StrategyArchitectureTest' test
.\mvnw.cmd -Pintegration '-DskipUnitTests=true' '-Dit.test=IntradayMarginRehearsalIntegrationTest,FinalReadinessEvidenceIntegrationTest' verify
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

| Verification | Result |
| --- | --- |
| Focused unit run | 100 passed, zero failures/errors/skips; before the additional strategy architecture check |
| Focused disposable integration run | 78 passed, zero failures/errors/skips, including all 37 MIS and 41 final-readiness cases |
| Full unit/architecture run | 1155 passed, zero failures/errors/skips, including the new architecture check |
| Full unrestricted integration verify | BUILD SUCCESS; 1155 unit/architecture and 493 integration tests, zero failures/errors/skips; 14m34s, completed 2026-10-05 19:53:09 +05:30 |
| Project/default verifier | PASS |
| Secret scan | PASS, zero findings |
| Diff whitespace check | PASS |

Two initial local launcher attempts stopped before usable test results: Windows
PowerShell treated Mockito stderr as a terminating error; another launch split
the single `test` argument into characters. The ignored launcher now captures
native exit status without stopping on stderr warnings and uses a typed argument
array. No assertion or production logic was weakened. The successful focused
and full-unit results above are from corrected launches. No orphan test JVM or
Testcontainers container remained from the first aborted run. No JAR lock or
packaging bypass has been needed.

Local ignored logs: `tmp/phase108d-focused-unit.log`,
`tmp/phase108d-focused-integration.log`, `tmp/phase108d-unit.log`,
`tmp/phase108d-integration.log`; the two aborted launcher logs are retained
separately. Final source changes are tests and documentation only.

## Strategy and product handoff

The architecture document prepares historical data -> normalized bars/ticks ->
features -> signals -> backtest -> costs/slippage -> walk-forward/out-of-sample
validation -> paper -> separately authorized risk-approved live MIS execution.
It records data provenance, time integrity, leakage controls and execution-model
requirements without implementing a new strategy platform.

Python remains research/signal-only; Java owns the trading control plane.
Execution must remain strategy-independent. Current `StrategyOrderCoordinator`
still proposes DELIVERY and stops at optional risk evaluation; MIS intent routing
is an explicit future handoff, not silently advertised as implemented. No
scheduler, public execution endpoint or automation authorization was added.
F&O, DELIVERY expansion, Forex and Commodity remain out of scope and need their
own reviewed contracts.

## Remaining blockers and next boundary

1. No production source of complete authoritative collateral terms for this
   account and exact MIS request. Aggregate reported collateral and required
   margin alone are insufficient. An authoritative aggregate attestation with
   cash terms could suffice; a general per-security collateral engine is not
   required by this design.
2. The broker positive-cash API mapping remains unresolved. Application policy
   is explicit and conservative, and rejects the historical account shape; it
   must not be loosened just to make the account pass.
3. Fresh reference, account cleanliness, authentication/HALT, market-session,
   applicable risk parameters and price/exposure evidence are required for a
   later candidate review. Existing holdings require fresh exposure pricing;
   an SBIN-only stream does not itself certify the whole portfolio.

No live quantity is selected while these gates remain unresolved. A future
review result would still be observational, not permission to resume, arm or
execute. Every future attempt retains final revalidation and explicit
reconciliation; no retry follows an ambiguous outcome.

## Final Git inventory

Branch remains `develop`; HEAD and `origin/develop` remain
`78f4c2088e8d824abc5f0ba1686dfcc0946e9fc8`. No commit or push.
Production changes: **none**. Configuration, migrations and environment files
are unchanged. The ignored local test launcher/logs are not source changes.

`git status --short`:

```text
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/IntradayMarginRehearsalIntegrationTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/StrategyArchitectureTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/risk/domain/IntradayAccountCapacityTest.java
 M docs/architecture/system-overview.md
 M docs/operations/README.md
?? docs/architecture/intraday-first-funding-contract.md
?? docs/operations/phase-10.8d-validation.md
```

`git diff --check`: clean, no output.

`git diff --stat` (tracked files only; the two new documents above are untracked):

```text
 .../IntradayMarginRehearsalIntegrationTest.java    | 22 ++++++++++++++++
 .../platform/StrategyArchitectureTest.java         | 16 ++++++++++++
 .../risk/domain/IntradayAccountCapacityTest.java   | 29 ++++++++++++++++++++++
 docs/architecture/system-overview.md               |  6 +++++
 docs/operations/README.md                          |  2 ++
 5 files changed, 75 insertions(+)
```

`git diff --name-status`:

```text
M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/IntradayMarginRehearsalIntegrationTest.java
M apps/trading-core/src/test/java/com/kitehybrid/platform/StrategyArchitectureTest.java
M apps/trading-core/src/test/java/com/kitehybrid/platform/risk/domain/IntradayAccountCapacityTest.java
M docs/architecture/system-overview.md
M docs/operations/README.md
```
