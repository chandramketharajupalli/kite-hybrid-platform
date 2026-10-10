# Phase 13.1 validation

**PHASE 13.1 RESULT: MIS_EVIDENCE_DIAGNOSTICS_VALIDATED.**
The planned synthetic/integration scope passed. No live/paper order authorization
and no real evidence collected. Collateral-assisted readiness remains NOT_READY.

## Baseline and isolation

The six required baseline commands ran before changes. Clean `develop`, HEAD
and local `origin/develop` both resolved to
`2942195acc4a69606d5904a6111d8075093e0c3b`, subject
`Harden Kite Connect runtime integration`. `git diff --check` was empty.
No pull, reset, stash, restore, clean, commit or push was performed.

Installed tools: Oracle JDK 21.0.12, repository Maven wrapper 3.9.11,
uv 0.12.10, Docker client/server 29.8.0. No installations or global environment
changes. Child Maven processes removed ambient deployment/JVM-option overrides
without printing their values. No dotenv helper, regular application startup
or development DB/token inspection was used. Python used no-bytecode mode.

The [plan](phase-13.1-plan.md) was written and frozen before production edits
and before tests of new behavior. It records the inspected paths, gap matrix,
permitted files, synthetic matrix, acceptance, zero-call budget and stop rules.
Phase 10.8A-D, Phase 13.0 and Phase 12.1C contracts were reviewed.
Unmodified plan SHA-256 at final review:
`a9dff0f0d9b5feb52b0156b5cc4600659382da9dac21db5fb49ecb4098cdb36a`.

## Implemented behavior

Only production file `risk/domain/IntradayFundingEvidence.java` changed.
Original `View`/`inspect` API remains intact. Additive detail provides provenance,
equity segment, local account/quote timestamps, freshness, complete request
binding/checksum and deterministic bounded denial reasons. It exposes no raw
balances, broker/account/session identity or credentials. Numeric collateral
terms cannot self-certify eligibility.

The synthetic summary separates authentication, account observation freshness,
orders, positions/cleanliness, market health/freshness, instrument identity,
quote freshness, cash sufficiency, collateral, full notional, HALT and absent
authorization. It reuses existing funding/capacity/valuation logic and always
returns NOT_READY. It has no I/O, execution, permit or risk-decision capability.
No endpoint, configuration, migration, production arithmetic or CNC change.

Public primary documentation was checked on 2026-10-10. The
[architecture report](../architecture/kite-mis-funding-evidence.md#official-evidence-review)
records exact URLs, field meanings and limitations. General pledged-margin
support and positive-cash prerequisites are documented; account-specific
eligible adjusted collateral, actually available collateral, applicable cash
component and cash-field mapping remain UNKNOWN. **REAL_EVIDENCE_NOT_COLLECTED**;
**COLLATERAL_ASSISTED_READINESS = NOT_READY**. No real executable SBIN quantity
was calculated or recommended.

## Test evidence

| Invocation | Actual result |
| --- | --- |
| Focused unit/architecture, first run | 121 run: 120 passed, 0 assertion failures, 1 error, 0 skipped. A new PARTIALLY_FILLED fixture incorrectly had zero fills; the normalized constructor correctly rejected it. Fixed fixture to a genuine partial fill. No production acceptance change. |
| Focused rerun | 121 passed / 0 failed / 0 errors / 0 skipped |
| Full `mvnw.cmd test` | 1303 passed / 0 failed / 0 errors / 0 skipped, 80 suites; BUILD SUCCESS |
| Full `mvnw.cmd -Pintegration verify` | Repeated 1303 unit tests; 514 disposable integration tests across 19 suites, 0 failures / 0 errors / 0 skipped; BUILD SUCCESS, 14:22 elapsed |
| `uv run python scripts/verify-project.py` | PASS: Maven/JDK requirements, profiles, safety defaults and JSON syntax |
| `uv run python scripts/check-secrets.py` | PASS: 1659 text files scanned, zero potential secret locations |
| Python pytest / Ruff / strict mypy | Not run: no Python source/config/dependency changes |
| `git diff --check` | PASS, no output |

Ignored logs: `tmp/phase131-focused-unit.log`,
`tmp/phase131-focused-unit-rerun.log`, `tmp/phase131-unit.log`,
`tmp/phase131-integration.log`, `tmp/phase131-project.log`, `tmp/phase131-secrets.log`.
Counts derive from Maven summaries and Surefire/Failsafe XML; repeated suites
are not added together as unique tests. No initial error is represented as PASS.

Focused coverage includes cash equality with fees/reserve, paisa/fee/margin
changes, positive net with insufficient cash, large/fully utilised collateral,
disabled/conflicting equity and commodity values, negative components,
stale/future account/quote/market times, missing quote/auth, request mismatch,
unsupported shapes, all normalized order statuses, both position arrays,
oversized full notional, fixed-clock deterministic serialization, redaction
and immutability. Existing CNC funding tests are included. Calculator additions
cover 401/403/429/500/503, duplicate/trailing JSON, NaN/Infinity, wrong segment,
negative charges and exact calculation-only route counts without retries.

The extended disposable MIS rehearsal adds fee growth, stale/future quote and
auth-loss final-refresh denial, alongside existing cash, margin, account,
market, session, HALT and competing-instance cases. A new diagnostic-only
runtime case stays halted and checks unchanged operator status, zero order/
authorization rows and zero gateway/HTTP mutation calls. Existing full suites
cover token-preserving passive status, restart, malformed REST/WS envelopes,
historical certification and response-loss reconciliation. Simulated execution
fixtures in regression suites are distinct from operational actions.

Selected integration counts: final-readiness 41, MIS rehearsal 42 (five added
cases), authentication restart 8, local fake-order transport 51, one-order
operator 79, preflight dry-run 81, operator rehearsal 83, runtime HALT 26,
synthetic candidate 20. These all passed, without skips. Remaining suites
exercise disposable migrations, historical immutability/replay and durable
token/login/order/risk/reconciliation/strategy stores. No integration rerun was
needed. Redis was not used: these paths use PostgreSQL and injected services;
no Redis-dependent production path changed.

## Safety and final inventory

Final audit PASS. The G1 SHA and all 18 source hashes passed before edits
and at final review. All 63 tracked research byte hashes match the clean baseline.
The Phase 12.1C artifact retains five CORPORATE_ACTION_UNRESOLVED reasons and
both acquisition/evaluation false. No sealed July bars were read.

Real Kite account/profile/margin/calculator requests: 0. Real historical GETs:
0. Real WebSockets: 0. Real order mutations: 0. Development DB/token mutations:
0. Operational real/paper dispatch, HALT resume, arm, permits and execute: 0.
No before/after development token fingerprint is claimed because that store
was never accessed. Only disposable test databases were written by fixtures.

Final inventory, all unstaged: four modified tracked files and five
new untracked files. Tracked diff is 233 insertions / 4 deletions; new files
are separately inventoried because `git diff --stat` omits them.

Modified:

```text
apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/IntradayMarginRehearsalIntegrationTest.java
apps/trading-core/src/main/java/com/kitehybrid/platform/risk/domain/IntradayFundingEvidence.java
apps/trading-core/src/test/java/com/kitehybrid/platform/ConservativeValuationArchitectureTest.java
apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteOrderMarginAdapterTest.java
```

New:

```text
apps/trading-core/src/test/java/com/kitehybrid/platform/risk/domain/IntradayFundingDiagnosticsTest.java
docs/architecture/kite-mis-funding-evidence.md
docs/operations/phase-13.1-plan.md
docs/operations/phase-13.1-runbook.md
docs/operations/phase-13.1-validation.md
```

No operations index change was needed; the runbook, plan, architecture and
validation cross-link directly. Maven outputs/logs remain ignored, not delivery
files. No tracked bytecode/build artifact, raw broker response, `.env`, config,
trading migration or protected research file changed.
All production diffs were inspected; only the additive pure diagnostic changed.
HEAD/origin remain the exact baseline on develop. Shared funding equations,
cash reserve, INR 10,000 first-live full-notional ceiling, CNC policy, HALT,
default execution/diagnostic flags and Actuator exposure are unchanged.
H1/H2 G1 remain FROZEN; SBIN July TEST remains SEALED. No commit or push.

Remaining authoritative gaps are the four eligibility terms above plus their
current account/product/request binding. Known general broker rules cannot
replace them. Next bounded work is broker clarification of that evidence
contract; any account read needs its own separately authorized proposal.
No automatic live/paper, strategy or historical-acquisition promotion.
