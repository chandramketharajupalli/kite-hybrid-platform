# Phase 10.6: Conservative pre-trade notional enforcement

This implementation does not authorize live trading or configure first-live
parameters. **The proposed 10000 ceiling bounds conservative pre-trade valuation,
not the realized fill value of a MARKET order.**

## Baseline and safety scope

Starting branch: `develop`. HEAD and local `origin/develop` both matched
`2e5107b20a8a459bb48bd1d8dfdf87d1db175912`; working tree was clean. No fetch,
reset, stash, discard, commit or push was performed.

Real Kite mutations and real development database test mutations: **zero**.
No real resume/arm/execute, SBIN order, risk approval, token operation or live
quantity selection occurred. `.env`, application YAML, allowlists, configured
limits, universe, migrations, RuntimeTradingHalt and reconciliation implementation
are unchanged. Execution tests use Testcontainers PostgreSQL and synthetic
sessions/instruments with fake or loopback brokers; no real deployment is started.

## Implementation and exact contract

See [ADR-019](../adr/ADR-019-conservative-pre-trade-valuation.md).
`ConservativeOrderValuation` is a pure immutable domain result created from:
order type, positive long quantity, current positive market price, optional limit
price and the validated existing risk buffer. MARKET forbids a limit price;
LIMIT takes the higher of market and limit. Its exact result is
`reference × buffer × quantity`, with no floating-point conversion or rounding.
Positive ceilings use inclusive BigDecimal comparison. Invalid/missing evidence
fails closed. No production SBIN or 10000 special case exists.

`RiskLimits` validates the existing buffer through the shared contract. Execution
holds that same immutable bean and derives approval-policy identity from it;
there is no independently configurable buffer/version pair. Normal execution,
risk order-value and independent first-live caps all apply to the same current
conservative observation. First-live checks receive the immutable valuation,
not a raw price to multiply independently.

Each preflight and execution authorization revalues. Account-admission callbacks
also revalue before transaction work, after the account-lock wait and before
commit. Post-admission and session-locked final dispatch checks revalue again.
There is no cached READY-price authorization. Other risk rules, expiry boundaries,
generation fencing and session/order/version binding remain in force.

An observational preflight denial leaves UNUSED intact. Execute denial before
claim consumes an existing UNUSED permit; after claim, finally consumes the
owning permit and disarms. Admission denial rolls back SUBMITTING; a later
non-halt dispatch denial retains existing FAILED handling. HALT after committed
admission retains SUBMITTING. Success, rejection, malformed response, timeout,
response loss and persistence failure never restore UNUSED or trigger a retry.
Broker-ID-first, exact-correlation fallback and explicit reconciliation are unchanged.

The quantity allocator is intentionally deferred. This phase validates supplied
integral quantities; it never chooses a live quantity or creates an order from a budget.

## Reproduction before production edits

Synthetic `9500 × 1.10 × 1 = 10450` was rejected by actual cash risk against
10000 while both original execution checks accepted raw 9500. Seven baseline
characterization cases passed, including fractional prices, equality, below-cap,
one extra unit and buffer 1.0/>1.0. Changing the assertions to the required common
decision produced **three failures, zero errors**, before editing production code.

Local ignored logs: `tmp/phase106-baseline-gap.log` and `tmp/phase106-red.log`.
After implementation, the initial focused risk/execution run passed all 19 cases
(`tmp/phase106-green.log`). The retained regression now requires conservative
agreement rather than preserving the old unsafe expectation.

## Verification matrix

| Area | Evidence |
|---|---|
| Exact money | Synthetic 9999.99 / 10000 / 10000.01; scale-equivalent ceilings; fractional prices and buffers; Long.MAX_VALUE; bounded precision/scale |
| LIMIT | 100/105 uses 105; 110/105 uses 110, then applies 1.10 |
| Invalid inputs | Null/zero/negative/extreme prices, invalid quantities/buffers, malformed Spring buffer configuration |
| Shared policy | Risk and execution invoke one evaluator; live checks use its result; same RiskLimits instance; changed buffer invalidates old approval fingerprint |
| Independent caps | Normal 8000 beats first-live 10000; first-live 10000 beats normal 12000; risk 9000 independently rejects 9500 |
| Real operator wiring | Disposable persisted candidate, actual RiskService, operator preflight/arm/execute, fake broker; no synthetic production rows |
| TOCTOU | READY at 9900, then 10001: denied with zero POSTs |
| Admission | Price rise at each of three fences: rollback, zero POSTs, bounded denial audit |
| Final dispatch | Barrier after SUBMITTING; price rises; both normal-only and first-live-only cap breaches block HTTP |
| Market evidence | Missing/zero/stale/future/unhealthy ticks; stale/future exchange timestamps; absent exchange timestamp remains supported |
| Phase 10.5 | Halt, arm revocation, restart, final-dispatch halt, halted reconciliation and SUBMITTING recovery regressions |
| One shot | Success, rejection, malformed acknowledgement, reset, response loss, timeout, persistence failure and actual child-JVM crash checkpoints |

## Executed results — 2026-09-30 IST

| Verification | Result |
|---|---|
| Final full Java unit/architecture suite | 1,059 passed; zero failures/errors/skips |
| Final affected integration suites | 156 passed; zero failures/errors/skips; Maven BUILD SUCCESS |
| Latest integration results across all 14 suites | 393 passed; zero failures/errors/skips, combining the broad run and corrective rerun described below |
| Project/default verification | PASS |
| Secret scan | 630 text files scanned, zero findings |
| Production package | Normal TradingCoreApplication entrypoint; shared valuation present; integration/crash helpers absent |
| Diff and protected source/configuration checks | PASS; no configuration, migration, halt/arming or reconciliation implementation changes |

The broad `./mvnw.cmd -Pintegration verify` run passed 1,059 unit tests and ran
387 integration cases. Six existing changed-evidence tests exposed loss of
specific audit reasons when checks moved earlier into admission. Execution was
denied with zero broker requests; audit mapping was corrected to retain known
enum reasons and map unrecognized/NONE failures to EVIDENCE_UNAVAILABLE.

After that correction and six additional integration cases, the final command was:

```powershell
.\mvnw.cmd -Pintegration '-Dit.test=LocalKiteOrderExecutionIntegrationTest,OneOrderOperatorIntegrationTest,RuntimeTradingHaltIntegrationTest' verify
```

It reran all 1,059 unit tests plus 51 local execution, 79 one-order and 26 runtime
halt cases successfully. The remaining 237 integration cases had passed in the
broad run. Thus 393 is the combined latest-report total, **not a claim that one
final unrestricted integration command ran all 393**. No failed case is left
unverified after correction.

The initial focused integration run also exposed two fixture issues: selecting
an arbitrary audit row among equal timestamps, and constructing the child crash
probe with buffer `1` rather than the default `1.0`, which changes the existing
risk-policy fingerprint. Those fixture issues were corrected; crash checkpoints
and admission audit tests passed on rerun. No production fingerprint normalization
or weaker matching was introduced.

Ignored local evidence:

- `tmp/phase106-full-verify.log`: broad-run results and the six corrected audit failures.
- `tmp/phase106-final-safety-verify.log`: final successful unit and affected integration run.
- `apps/trading-core/target/phase106/evidence.json`: test totals, source/report/log and artifact hashes.

The valuation-consistency blocker is closed for local/disposable validation.
Live authorization, real configuration, current account/reference/session evidence
and quantity selection remain outside this phase. Changes remain uncommitted on
`develop`; HEAD and local origin/develop remain at the required starting baseline.
