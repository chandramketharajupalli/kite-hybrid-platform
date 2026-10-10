# Phase 13.2 frozen plan

Frozen before production edits/new-behavior tests, 2026-10-10. Baseline clean
`develop`; HEAD and `origin/develop` both
`12117e9a61f8f646a704a4ef4bbe856ceb9847b6`.

## Allowed scope and thresholds

Add a versioned, redacted collateral contract inside existing
`IntradayFundingEvidence`. Preserve `inspect`, `detail`, `View`, `Summary` and
all funding arithmetic. No new client, provider, risk engine, endpoint, bean,
configuration, database schema or execution capability. A caller-provided
quote, origin flag or numeric CollateralTerms cannot establish independent
broker authority. The new contract must not repeat the legacy calculator
BROKER_AUTHORITATIVE label as independently verified evidence.

PROVEN requires independently established broker authority, current effective/
observation times, exact account/product/request scope and complete eligible
amount/cash-field terms. No such provider exists. This phase must therefore
keep the four answers UNKNOWN (or STALE/CONFLICTING for unusable observation
context), with no invented amounts/effective dates and always NOT_READY.
General positive-cash broker policy is distinct from account-specific compliance.
No public documentation or caller metadata is an account attestation.

Real requests default to zero. No real/paper orders, real WS/candles, login/token
exchange, operational HALT resume/arm/permits/execute, development DB/token
writes, strategies, commit or push. Existing required regression suites include
isolated simulated execution fixtures; those are tests, not operational use.
Keep reserve cash-only, full buffered notional <= INR 10,000, CNC, limits,
defaults, config, `.env`, migrations, preflight/final refresh and reconciliation
unchanged. No executable candidate/quantity selection.

G1 hash `56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`
and all 18 sources passed. Baseline byte hashes of all 63 tracked research files
are recorded in ignored tmp. No protected Phase 11/12 evidence edits or sealed
July access. Five-member Phase 12 acquisition stays BLOCK; no research evaluation.

## Inspection and gaps

| Existing component | Observation / risk | Minimal action and acceptance |
| --- | --- | --- |
| IntradayFundingEvidence | Legacy exact-quote labels describe a provider contract but accept caller-supplied inputs; no versioned independent-authority boundary | Add contract with explicit source IDs, authority-not-established, question status, validity and redacted scope; old APIs unchanged |
| BrokerMargins, CashAccountCapacity, IntradayAccountCapacity, RiskLimits | Strict segment shape; conservative cash floor/exclusions; net only tightens; utilised is not available; numeric terms cannot prove origin | Reuse original inspect; no equations/limits/reserve/CNC changes; equality/paisa/utilisation/commodity regressions |
| OrderMarginQuote, KiteOrderMarginAdapter, KiteRestTransport | Exact local request; calculator sends symbol/exchange/MIS/BUY/MARKET/quantity; DAY enforced locally; no response token/full echo/broker timestamp or collateral eligibility | Bind new diagnostic reference to request plus ZERODHA broker token mapping checksum; do not claim broker attestation. Wrong mapping/missing/stale/future evidence denies |
| CurrentAccountExecutionChecks, first-live planner, ExecutionSafetyPolicy, operator preflight | Current four-account-read + exact quote, shared full valuation and final callback checks; no atomic snapshot | No edits; reuse full synthetic final-refresh, contention and response-loss tests |
| Authentication and reconciliation | Passive status preserves durable rows; restore/reset deliberate; exact identity and COMPLETE->FILLED retained | No edits; rerun fake auth and disposable restart/reconciliation |
| Architecture/continuity | Pure domain has no I/O/permit capability; mandatory Phase 12 gate | Existing guard covers nested additions; add explicit contract-boundary assertion if useful; zero provider/repository on denied certification |

Reviewed actual types/paths plus Phase 10.8A-D, 13.0/13.1 validation and existing
MIS architecture. Primary URLs will be catalogued with scope and access date;
missing publication/update dates will be marked not stated, not inferred.

## Permitted files

Production only `apps/trading-core/src/main/java/com/kitehybrid/platform/risk/domain/IntradayFundingEvidence.java`.
Tests: new `risk/domain/IntradayCollateralContractTest.java`, existing
`ConservativeValuationArchitectureTest.java`, and diagnostic-only case in
`IntradayMarginRehearsalIntegrationTest.java` if needed. No other production
edit without a documented narrow defect/addendum before editing.
Docs: phase-13.2 plan, validation, runbook, broker-clarification, real-read-proposal
and `docs/architecture/kite-mis-collateral-contract.md`.

## Frozen test matrix and success criteria

* Contract version/stable serialization/immutable metadata/redaction. Caller
  origin or numeric terms never produces PROVEN or independently authoritative
  fields. Missing authority means UNKNOWN, not a guessed zero. No fake effective
  broker timestamp or account identity in output.
* Current vs stale/future account/quote/reference; missing quote; exact symbol,
  identity, broker namespace/token mapping mismatch. Changed mapping must change
  scoped checksum even when stable platform ID remains the same.
* Existing cash-only equality incl margin/charges/reserve, paisa drop, increased
  fees, net insufficiency, utilised collateral, unknown haircut/category,
  conflicting segments/commodity, negative/malformed/non-finite values, wrong
  product/quantity, stale market/auth loss/orders/positions, FILLED terminal,
  competition, response loss, full buffered-notional and CNC cases must pass.
* Existing calculator HTTP tests: only POST /margins/orders; 401/403/429/5xx,
  malformed/missing charges/timeout fail closed, no mutation fallback/retry.
* New contract in halted disposable fixture cannot change operator/permit/order
  state. Read-only and frozen-strategy architecture guards remain passing.
* Full unit/architecture and full integration (conservative scope choice) pass;
  report actual counts/errors/skips and reruns. No relaxed acceptance.

Use JDK 21/repository wrapper, isolated child environment per Phase 13.1 runbook,
fake services/loopback and Testcontainers PostgreSQL. No development startup or
dotenv helper. Python no-bytecode mode for verification; no Python edits planned.

```powershell
.\mvnw.cmd '-Dtest=IntradayCollateralContractTest,IntradayFundingDiagnosticsTest,IntradayFundingEvidenceTest,IntradayAccountCapacityTest,KiteOrderMarginAdapterTest,KiteAuthenticationUseCaseTest,ConservativeValuationArchitectureTest,TradingReadArchitectureTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

## Real evidence, recovery and stop rules

Public docs cannot certify current account terms. Draft the bounded read-only
proposal and ask separate approval under specification section 11. While it is
unapproved, continue synthetic engineering and record REAL_EVIDENCE_NOT_COLLECTED.
No request is made on silence. Broker clarification is a draft only; never send.
Even approved aggregate reads might not resolve any of the four terms.

Stop real-read work on absent approval, missing/expired token, unknown HALT or
write isolation, unexpected endpoints or errors. Stop all unsafe work on baseline/
freeze mismatch, development target, credential disclosure or unplanned safety
change. Do not reset/discard anything. Tests own disposable shutdown. Final
inventory distinguishes tracked/untracked files and audits all production diffs.
