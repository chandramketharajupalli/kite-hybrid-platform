# Phase 13.2 validation

**PHASE 13.2 RESULT: MIS_BROKER_EVIDENCE_CONTRACT_VALIDATED**
Engineering validation does not establish broker collateral eligibility.

## Baseline, tools and isolation

Before edits: clean `develop`; HEAD and local `origin/develop` both
`12117e9a61f8f646a704a4ef4bbe856ceb9847b6`, subject
`Add Kite MIS funding evidence diagnostics`. All six baseline commands ran;
status/diff-check were empty. No pull/reset/stash/restore/clean/commit/push.

Tools: Oracle JDK 21.0.12, Maven wrapper 3.9.11, uv 0.12.10, Docker client/server
29.8.0. No installation or global environment change. Maven child processes
removed ambient deployment/JVM-option overrides without printing values.
No dotenv helper, real startup, development database/token inspection or real
broker call. Python verification ran with bytecode generation disabled.

The [plan](phase-13.2-plan.md) was frozen before production edits and new tests.
Required current code and Phase 10.8A-D/13.0/13.1 reports were reviewed. G1
SHA and all 18 frozen sources passed before edits. All 63 tracked research
files were byte-hashed against the initially clean baseline.
Plan SHA-256 at review:
`f9aa84893ba3c0ee1d6a3df02d4c19a7a3acde215aa02a2cc0ab524704c1503c`.

## Evidence and implementation

Public official sources were checked on 2026-10-10; exact titles, URLs,
update-date limitations, segment/product scope and field dictionary appear in
[the contract](../architecture/kite-mis-collateral-contract.md). Positive cash
is a documented general prerequisite. F&O ratio wording, broader approved-list
cash-equivalent wording and additional-security conditions do not establish
the complete rule/qualifying cash field for an unknown current NSE MIS account.
No individual haircut/category or current account capacity was certified.

`KiteMisCollateralEvidence.v1` adds a pure redacted projection to
`IntradayFundingEvidence`. Old APIs and funding equations remain unchanged.
The new projection distinguishes a schema/source identifier from independent
authority: caller-supplied quote fields are OBSERVED, never independently
BROKER_AUTHORITATIVE. Every field's independent authority remains UNKNOWN.
No caller origin, boolean or numeric CollateralTerms can produce PROVEN.

Four explicit question records carry UNKNOWN, STALE or CONFLICTING input-context
status, bounded denial, absent broker effective date and missing-account-
attestation source. Local account/quote/reference times and full request/token-
mapping checksums are separate. Mapping changes remain visible with a stable
platform ID. There are no sensitive balances, raw symbols/tokens/identities or
requests in output, no I/O or public endpoint, and no permit/approval capability.

Eligible adjusted collateral: UNKNOWN. Actually available collateral: UNKNOWN.
Applicable account/request cash-component requirement: UNKNOWN. Exact cash-field
eligibility mapping: UNKNOWN. **Collateral-assisted readiness: NOT_READY.**
Current valid synthetic cash equality still passes only application cash policy.
This work does not select a real quantity or candidate.

The [real-read proposal](phase-13.2-real-read-proposal.md) was created and separate
explicit approval requested under section 11. It proposes at most two GETs and
requires a tested token-preserving harness before use. No real requests were
made; **REAL_EVIDENCE_NOT_COLLECTED**. The
[broker clarification](phase-13.2-broker-clarification.md) is a draft, not sent.
Broker clarification is recommended before any optional account observation.

## Validation commands and actual results

| Run | Actual result |
| --- | --- |
| Focused MIS/contract/calculator/auth/architecture command in plan | 176 passed / 0 failed / 0 errors / 0 skipped |
| Full `mvnw.cmd test` | 1325 passed / 0 failed / 0 errors / 0 skipped across 81 suites; BUILD SUCCESS |
| Full `mvnw.cmd -Pintegration verify` | Repeated 1325 unit tests and 514 disposable integration tests across 19 integration suites; zero failures/errors/skips; BUILD SUCCESS, 15:18 minutes |
| Project verifier | PASS: Maven/JDK requirements, profiles, safety defaults and JSON syntax |
| Secrets scanner | PASS: initial scan 1667 text files; final documentation scan 1666 text files; zero potential secret locations in both |
| Diff check / final freeze | PASS: G1 SHA, all 18 frozen sources, all 63 protected research files unchanged; five-member acquisition BLOCK |
| Python pytest, Ruff, strict mypy | Not run: no Python source/config/dependency changes |

Ignored logs are `tmp/phase132-focused.log`, `phase132-unit.log`,
`phase132-integration.log`, `phase132-project.log`, `phase132-secrets.log`.
Counts were cross-checked with Surefire/Failsafe XML. Repeated unit tests are
not added together as unique tests. No failure-driven reruns were needed.
No test acceptance was relaxed.

New 22-case contract suite tests both caller origins, numeric-term nonpromotion,
cash equality/deficits/increased costs, missing data/auth/clock, stale/future
account/quote/reference, symbol/namespace/quantity conflicts, changed broker
mapping, immutable deterministic redaction and legacy API compatibility.
Existing suites cover fully utilised collateral, unknown haircut/category,
cash/segment conflicts, commodity exclusion, negative/non-finite/malformed
responses, 401/403/429/5xx/timeout, missing charges, stale market data, order
statuses and COMPLETE->FILLED, positions, competition, response loss, full
notional and unchanged CNC. Calculator routes remain calculation-only.

The existing halted disposable diagnostic case now invokes V1 and verifies
unknown authority, unchanged operator/permit status, no order/authorization
rows, zero gateway calls and zero fake order mutations. Existing final-refresh
denial cases and simulated execution regressions remain distinct from
operational actions. No production execution/auth/transport behavior changed.

## Final audit and inventory

Final audit passed: branch and both Git refs still resolve to the full baseline
SHA above. Required status, diff-check, stat, name-status, untracked inventory
and production/resource diffs were inspected. Tracked diff: two files, 105
insertions, no deletions; seven new files listed separately below. All changes
remain unstaged; no commit/push. The plan hash remains unchanged.

No `.env`, application resources/configuration, migrations, safety defaults,
risk limits, cash reserve, CNC funding, INR 10,000 full buffered-notional ceiling,
Python bytecode or generated build artifacts changed in the Git inventory.
H1/H2 G1 remains FROZEN, SBIN July TEST remains SEALED, and all five Phase 12
members retain CORPORATE_ACTION_UNRESOLVED with acquisition/evaluation denied.
Operational HALT was not resumed; arming, execution and authorization remain
disabled. No diagnostics endpoint or Actuator exposure was added.

Modified tracked paths:

```text
apps/trading-core/src/main/java/com/kitehybrid/platform/risk/domain/IntradayFundingEvidence.java
apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/IntradayMarginRehearsalIntegrationTest.java
```

New untracked paths:

```text
apps/trading-core/src/test/java/com/kitehybrid/platform/risk/domain/IntradayCollateralContractTest.java
docs/architecture/kite-mis-collateral-contract.md
docs/operations/phase-13.2-plan.md
docs/operations/phase-13.2-validation.md
docs/operations/phase-13.2-runbook.md
docs/operations/phase-13.2-broker-clarification.md
docs/operations/phase-13.2-real-read-proposal.md
```

Real Kite reads/calculator calls, historical GETs, real WebSockets and order
mutations: 0. Development DB/token mutations: 0. Operational HALT resume,
arm/execute, permits and live/paper dispatch: 0. No before/after development
token fingerprint is claimed because that store was not accessed.

Remaining evidence: independently authenticated current account/product/request
binding; eligible adjusted and actually free collateral; complete applicable
cash/category/debit rule; exact qualifying cash field/exclusions; effective
time/validity and consistency during changing utilisation. The six draft broker
questions identify these precisely. No amount, policy exemption, account fact
or effective date has been guessed. Next bounded work is broker clarification
and source review; any optional real observation needs separate authorization
and all harness checks. No automatic live/paper or acquisition promotion.
