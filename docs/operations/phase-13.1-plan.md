# Phase 13.1 implementation and test plan

Frozen before production edits and new-behavior tests on 2026-10-10.
Clean `develop`; HEAD and `origin/develop` both
`2942195acc4a69606d5904a6111d8075093e0c3b`. Six baseline commands passed.

## Scope and invariants

Extend the existing pure `IntradayFundingEvidence` with an additive detailed
projection and synthetic preflight summary. Preserve `View` and `inspect` API
compatibility. Reuse `CashAccountCapacity`, `IntradayAccountCapacity`, exact
`OrderMarginQuote.Request`, and `ConservativeOrderValuation`. No second broker
client, funding engine, endpoint, Spring wiring, cache or persistence.

Real Kite request budget: **zero**, including profile, margins, calculator,
historical and WebSocket. Public primary documentation research is permitted.
No real/paper orders, operational HALT resume, arm, permits or execution. The
required existing regression suites contain isolated simulated execution and
permit fixtures; those are safety tests, not operational authorization. New
diagnostics have no execution capabilities. Never load `.env` or target the
development database/token store. Keep production resources, migrations,
limits, reserve and INR 10,000 full buffered-notional ceiling unchanged. CNC
funding is unchanged. No executable real SBIN sizing recommendation.

G1 SHA-256 must remain
`56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`;
all 18 frozen source hashes must pass `run_phase116.verify_freeze` without
running research evaluation. All 63 tracked research files are byte-hashed
before edits. H1/H2 FROZEN, SBIN July TEST SEALED; Phase 12 five-member
certification unresolved, acquisition and strategy evaluation BLOCK.

## Inspected paths and gap matrix

Java paths below are relative to `com/kitehybrid/platform`.

| Component / current code | Observed behavior and unknown evidence | Risk / minimal change | Acceptance |
| --- | --- | --- | --- |
| `risk/domain/IntradayFundingEvidence` | Redacted labels, cash-only branch, always NOT_READY; lacks provenance, times, exact-request fingerprint and explicit denials | Add detailed immutable view; no self-certification from numeric collateral terms | Old API unchanged; deterministic redacted output, missing/stale/future/conflicting evidence denies |
| `CashAccountCapacity`, `IntradayAccountCapacity`, `BrokerMargins` | Equity cash lower bound; net only tightens; commodity ignored; utilised collateral deducted; numeric terms cannot prove source | Reuse equations without changing them; distinguish observed collateral from actually available eligible collateral | Equality incl charges/reserve; paisa deficit; net/utilised/commodity cannot manufacture cash; CNC unchanged |
| `OrderMarginQuote`, `KiteOrderMarginAdapter`, `KiteRestTransport` | Narrow NSE BUY MARKET MIS DAY REGULAR; exact local binding; response validates symbol/exchange/equity, total/var/charges; local receipt only; no eligibility terms | Retain calculator and transport; add targeted error/request assertions | Only calculation POST `/margins/orders`; missing/invalid/negative/non-finite/duplicate/trailing/identity errors deny; 401/403/429/5xx/timeout |
| `risk/application/RiskService`, `CurrentAccountExecutionChecks` | Re-read account and exact quote; shared funding/valuation; no atomic broker snapshot | Pure synthetic summary separates gates; no new risk approval or provider | Auth, positions/orders, market, reference, quote, cash, notional, HALT and absent authorization separately visible |
| `FirstLiveCandidatePlanner`, `ExecutionSafetyPolicy`, `LiveTestExecutionChecks`, `OperatorExecutionService` | Full-notional sizing before funding; preflight does not consume; final transport callback refreshes; execute fixture consumes attempt even when denied | Reuse existing rehearsal; extend missing charge/quote/auth final-refresh cases | Cash -.01, increased margin/fees, stale/future quote, changed price/account/session/HALT and competition deny before fake HTTP mutation |
| `OrderReconciliationService`, `KiteTradingReadMapper` | Exact identity checks; COMPLETE normalized FILLED; terminal FILLED/CANCELLED/REJECTED nonblocking | Retain; test summary uses normalized terminal status | Open/pending/unknown block; response-loss reconciliation regression |
| `KiteAuthenticationUseCase.status`, session, REST/WS strict error parsing | Passive status preserves durable tokens; deliberate restore/reset cleanup remains; malformed envelopes not trusted | No auth production edits; run existing unit and disposable restart suites | Missing/expired/rejected auth, restart, network errors and malformed envelopes; preserved encrypted row |
| Diagnostic and architecture boundaries | Default diagnostics disabled, local guards; tradingstatus false | No routes/config edits; strengthen pure diagnostic dependency guard | No gateway/operator/permit/HALT/store/network dependencies; historical gate and Python execution guards unchanged |

Reviewed Phase 10.8A-D funding reports, intraday-first contract, Phase 13.0
plan/validation/runbook and Phase 12.1C gate. Current official broker research
will be recorded with primary URLs and access date in the architecture document.

## Evidence contract and expected failure handling

* `OBSERVED`: normalized account fields. `DERIVED`: unchanged application
  arithmetic. `BROKER_AUTHORITATIVE`: exact calculator margin/charge contract,
  not collateral authority. Synthetic provenance is explicit. `UNKNOWN`: terms
  absent from established provider. Caller metadata cannot establish authority.
* Track equity segment, local observation and quote receipt, CURRENT/STALE/
  FUTURE/UNKNOWN freshness, and exact full-request SHA-256 binding. The hash
  contains no account/session/credential material; raw symbol/quantity/balances
  are omitted from diagnostic output. It is an identity checksum, not a secret
  anonymizer or broker attestation. No broker timestamp is invented.
* Eligible adjusted collateral, actually available collateral, applicable
  cash-component rule and cash-field eligibility remain UNKNOWN. Numeric
  `CollateralTerms` alone cannot promote any of these. Always NOT_READY.
* Missing/invalid inputs, wrong request, unsupported/disabled equity, stale or
  future evidence and authentication loss produce explicit bounded denials.
  No saved snapshot, previous report or commodity funds can repair them.
* Preflight summary is synthetic-only and stateless. Authorization is always
  absent; no PASS field grants permission. Account cleanliness is a first-review
  diagnostic, not a new generic production risk rule. Recompute every time.
* 401/403 invalidate in-memory ordinary read authentication; passive status
  never edits tokens. Transient failures do not revoke tokens absent a valid
  explicit TokenException. No automatic retry or `/orders` fallback.

## Synthetic test matrix and exact success criteria

Use fixed clocks, BigDecimal, existing fake services, literal loopback peers
and disposable Testcontainers PostgreSQL. No Redis authority is needed.

1. Cash equality including reserve/charges passes only cash-policy sufficiency;
   -.01 cash, +.01 margin or charges deny. Positive net, large/fully utilised
   collateral, unknown terms, conflicting/disabled equity and commodity funds
   cannot promote eligibility. Negative/invalid/non-finite input fails closed.
2. Missing quote; wrong symbol/instrument/quantity/product; stale/future account,
   quote and market times; exact request and redaction tests. Invalid request
   shapes rejected by existing constructors/adapter; no relaxed acceptance.
3. Preflight separately reports auth, account cleanliness, open/pending/unknown
   orders, nonzero net/day positions, market health/freshness, instrument binding,
   quote freshness, cash, collateral, full notional, HALT and authorization.
   Terminal FILLED is nonblocking. Missing evidence never becomes PASS.
4. Final-refresh rehearsal extends the existing fixture; all changed-evidence
   cases assert zero fake order POSTs and immutable historical risk decisions.
   Existing competition, response-loss, auth/restart and reconciliation suites
   remain mandatory. Diagnostic invocation consumes no permit.
5. Architecture rules prove the diagnostic has no application/infrastructure,
   network, persistence, gateway, operator, risk-decision or permit capability.
6. Full Java unit/architecture and full disposable integration pass with actual
   counts. Project/secrets/diff and final freeze/protected/config audits pass.
   Any required incomplete validation is PARTIAL; safety/baseline blocker BLOCKED.
   No acceptance criterion is changed after observing results.

## Permitted files and validation commands

Production: only `risk/domain/IntradayFundingEvidence.java`. Tests:
`IntradayFundingEvidenceTest`, a focused detailed-diagnostic test in the same
package if needed, `ConservativeValuationArchitectureTest`,
`KiteOrderMarginAdapterTest`, and `IntradayMarginRehearsalIntegrationTest`.
Documentation: this plan, `phase-13.1-validation.md`, `phase-13.1-runbook.md`,
`docs/architecture/kite-mis-funding-evidence.md`; operations index if needed.
Any newly demonstrated safety defect outside this list needs a documented
narrow plan addendum before its production edit, without relaxing acceptance.

Use repository wrapper/JDK 21 in child PowerShell with deployment/JVM-option
environment overrides removed as in the Phase 13.0 runbook. Do not print values.
Set `PYTHONDONTWRITEBYTECODE=1` and use `python -B` for freeze checks.

```powershell
.\mvnw.cmd '-Dtest=IntradayFundingEvidenceTest,IntradayFundingDiagnosticsTest,IntradayAccountCapacityTest,KiteOrderMarginAdapterTest,ConservativeValuationArchitectureTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

Full integration is planned even though no shared execution/auth/transport
behavior changes. Count XML results, distinguish failures/errors/skips and
record reruns. Python source changes are not planned; if changed, run full
pytest, Ruff and strict mypy. Inspect all production diffs and separately
inventory untracked files. No commit/push.

## Rollout, recovery and stop conditions

Local uncommitted review only. No normal development application startup.
Tests own disposable contexts/sockets/containers and shutdown. Stop on a real
request, development DB target, credential disclosure, freeze mismatch,
unexpected user edits, or changed safety defaults. Diagnose failures without
loosening policy. Do not delete unrelated resources or discard user work.

Real evidence is not required to prove conservative UNKNOWN behavior. Report
REAL_EVIDENCE_NOT_COLLECTED. If a future essential real read is proposed, first
write `phase-13.1-real-read-proposal.md` with endpoint, exact budget/pacing,
redaction, HALT/no-write guards and token/DB comparisons, then obtain separate
explicit authorization. This phase provides none.
