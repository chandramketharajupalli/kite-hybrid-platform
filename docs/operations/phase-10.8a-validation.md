# Phase 10.8A — MIS funding and pledged collateral

Engineering and synthetic validation only. No real order is authorized. The
implemented scope is NSE cash-equity BUY / MARKET / INTRADAY (MIS) / DAY / REGULAR.
DELIVERY still maps to CNC and keeps its original funding rule. No real SBIN
quantity has been selected. INR 10,000 is the conservative full market-notional
ceiling in the rehearsal, never a margin budget or a guarantee of MARKET fill value.

## Baseline and safety

The initial working tree was clean, on `develop`, with HEAD and local
`origin/develop` both `8bb22cca943ee76e63018b4a7ca6daaec934f485`.
The requested status, branch, revisions and eight-commit log were inspected before
editing. No reset, restore, stash, checkout, clean, commit or push was performed.
No migration, `.env`, application configuration, live default, allowlist, risk
limit, reserve, arm duration, token, universe or real database was changed.

All execution validation uses the existing disposable PostgreSQL fixture and
literal loopback fake broker. The fixture rejects non-loopback broker URLs and
uses Testcontainers JDBC coordinates. No real broker diagnostic, margin
calculation or order request was made. Public official documentation was read.
The received request ends at `39. FIRST-L`; the missing continuation was requested.
This report covers the supplied requirements and preserves existing candidate,
permit, admission and reconciliation boundaries.

## Supplied prior observation, not current evidence

The user supplied this approximate Phase 10.8 read-only observation:

| Equity field | Prior value |
|---|---:|
| enabled | true |
| net | 1089539.25181 |
| available.cash | -14681.5 |
| available.openingBalance | -14681.5 |
| available.liveBalance | 318.5 |
| utilised.debits / payout / holdingSales | 0 / 0 / 0 |
| Existing conservative usableCash | -14681.5 |

Non-zero net positions, non-zero day positions and non-terminal broker orders
were all zero. No SBIN holding or position was observed; SBIN orders and trades
that day were zero. This phase did not recheck these facts. No collateral amount
or collateral eligibility breakdown was supplied; synthetic tests use invented
collateral, explicitly not a reconstruction of the account. Neither aggregate
net nor a negative cash field proves whether Kite would accept an MIS order.

## Official evidence and normalization

Reviewed on 2026-10-05. `BrokerMargins` and `KiteTradingReadMapper` already retain
every margin component below as bounded BigDecimal. No available liquid/stock
split was discarded: those fields belong to **utilised**, not **available**.
The documented field meanings are summarized here. [Kite funds and margins](https://kite.trade/docs/connect/v3/user/#funds-and-margins)

| Field | Documented meaning |
|---|---|
| enabled | Segment availability |
| net | Aggregate availability, including additional credits/collateral |
| available.cash | Cash, including intraday pay-in |
| openingBalance | Start-of-day balance |
| liveBalance | Current balance |
| collateral | Pledged-stock margin |
| intradayPayin | Intraday deposits |
| adhocMargin | Broker-assigned additional margin |
| utilised.debits | Utilised margin |
| exposure / span | Exposure / SPAN blocks |
| realisedMarkToMarket / unrealisedMarkToMarket | Realised / unrealised P&L |
| optionPremium | Option premium |
| payout | Withdrawals |
| holdingSales | Holding-sale credit |
| turnover | Turnover utilisation |
| liquidCollateral / stockCollateral | Utilised collateral components |
| delivery | Delivery-sale block |

Zerodha describes collateral credited after haircut and usable for equity
intraday. This does not establish the account's current eligible composition.
No local haircut percentage is applied. [Collateral funds](https://support.zerodha.com/category/trading-and-markets/general-kite/funds/articles/what-does-collateral-margin-in-the-funds-mean)

The published 50% cash discussion for F&O must not be applied automatically to
cash-equity MIS. Pledging documentation also describes debit-duration restrictions
and additional pledge categories. [Pledging](https://support.zerodha.com/category/console/portfolio/pledging/articles/what-is-pledging)
The page about eligible securities specifies cash/liquid requirements for an
additional securities category. Aggregate collateral does not identify that
category. [Eligible securities](https://support.zerodha.com/category/console/portfolio/pledging/articles/securities-available-for-pledging)

Consequently this implementation does **not** infer a universal MIS cash ratio,
liquid-collateral eligibility, debit-age eligibility or account entitlement.
These are unresolved production inputs. An explicit `CollateralTerms` observation
can carry eligible adjusted collateral and minimum cash in the broker-independent
model; only synthetic providers supply those terms in this phase. There is no
configuration switch that marks unknown collateral eligible.

The official order-margin endpoint is a calculation-only POST to
`/margins/orders`. It considers existing positions and open orders, returns a
required total and estimated charges, and does not place/reserve an order. Its
response identifies exchange/symbol/type but does not echo every input or supply
the collateral eligibility/cash-minimum terms above. [Kite margin calculation](https://kite.trade/docs/connect/v3/margins/)

`KiteOrderMarginAdapter` sends exactly one immutable request, with no order ID,
tag or correlation. The locally retained request binds the response to that
operation; this is not a broker-signed quantity echo. It accepts one equity row
matching exchange/symbol, positive `total == var`, nonnegative estimated charges,
and zero derivative/offset components and P&L. Other shapes fail closed. This is
an intentionally restricted response subset, not a claim all valid broker
responses have that shape. Duplicate keys, missing fields, numeric strings,
overflow, malformed responses and mismatches are denied without response logs.
The transport has a fixed calculation route, body/response bounds, existing
timeouts/authentication handling, and no retry. Order-mutation transport methods
are not called by this adapter.

## Product-specific funding contract

`ConservativeOrderQuantitySizer` is unchanged and has no account fetching.
Full buffered price determines quantity, with floor rounding and lot adjustment;
human, normal, first-live and risk ceilings and quantity/exposure headroom all
remain upper bounds. MIS funding cannot increase quantity. After sizing, an
exact-quantity margin quote is a separate gate. Missing quote returns a bounded
quantity with NOT_READY for observational estimation; it creates no order.

`CashAccountCapacity` preserves DELIVERY:

```
CNC cash = min(cash, openingBalance, liveBalance, equity.net)
           - positive(debits) - positive(payout) - positive(holdingSales)
CNC cash >= conservativeNotional + cashReserve
```

Shared position and full buffered exposure arithmetic is extracted once. MIS
permits supported positive, unit-multiplier intraday net positions in addition
to DELIVERY positions; day rows are checked but not added again to net. Short,
derivative, unknown, margin-funded or discrepant state remains unsupported.
Holdings quantity + unsettled quantity + pledged quantity remains the existing
conservative upper bound; overlapping buckets may over-count. Holdings do not
become intraday positions. Every non-terminal broker order remains blocking;
FILLED, CANCELLED and REJECTED are the only terminal exceptions.

`IntradayAccountCapacity` applies these additional conservative policy choices:

| Component | MIS treatment and rationale |
|---|---|
| Cash/opening/live | Preserve CNC floor; never substitute aggregate net for cash |
| Net | Additional upper bound only, never independently spendable cash |
| Collateral | Not cash; zero eligibility without explicit current terms; cap by both broker available collateral and eligible adjusted amount |
| Utilised liquid/stock collateral | Deduct from eligible collateral; do not reinterpret as free liquid/stock balances |
| Pay-in and adhoc | Excluded credits; never expand capacity |
| Positive realised/unrealised P&L | Excluded; no hypothetical/unsettled profit funding |
| Option premium | Exclude absolute value; no reliance on ambiguous sign/credit conventions |
| Debits/payout/holding sales | Preserve deductions from cash and aggregate net bound |
| SPAN/exposure/delivery/turnover | Never additions; negative values rejected; non-cash positions and open orders remain blocked by account checks |
| Commodity segment | Never used for NSE funding, even if very large |

All components are synchronous observations, potentially changing intraday.
Opening balance is a start-of-day field, not a current liquidity guarantee.
These exclusions are local conservative policy, not assertions that the broker
disallows the excluded funds. Deductions may overlap broker net/usage accounting;
over-counting deductions reduces capacity and does not manufacture buying power.

```
excluded = payin + adhoc + positive(realisedPnL) + positive(unrealisedPnL)
           + abs(optionPremium)
cash = CNC cash - excluded
eligibleCollateral = max(0, min(availableCollateral, explicitEligibleAdjusted)
                           - utilisedLiquidCollateral - utilisedStockCollateral)
netBound = equity.net - excluded - positive(debits) - payout - holdingSales
effectiveCapacity = min(cash + eligibleCollateral, netBound)

cash >= explicitMinimumCash + estimatedCharges + cashReserve
effectiveCapacity >= requiredMISMargin + estimatedCharges + cashReserve
```

Without collateral terms, eligible collateral is zero and minimum cash is the
**entire** required margin. Thus cash-funded MIS can pass without collateral
knowledge; an order relying on collateral returns COLLATERAL_UNSUPPORTED.
The reserve remains cash-only. Negative cash cannot be repaired by collateral
under this platform policy. The supplied account shape therefore remains denied,
even under synthetic eligible terms if it cannot meet the cash reserve.

Disabled/missing equity, missing/null/overflowing fields and prohibited negative
components fail closed. Domain values, sums, valuation and sizing use BigDecimal
and integral quantities only. No cash/credit/haircut guessing or alternate
floating-point formula is introduced. Specific reasons distinguish
MIS_MARGIN_UNAVAILABLE, MIS_MARGIN_INSUFFICIENT, COLLATERAL_UNSUPPORTED and
MARGIN_ESTIMATE_UNAVAILABLE.

## Timing, binding and costs

Risk approval, operator preflight and final account revalidation share the same
capacity contract. The new read port is independent of Kite DTOs and diagnostic
controllers. Risk reads an exact quote after positions/holdings/margins/orders;
preflight and final transport do likewise. Quotes have a local receipt timestamp,
not an invented broker timestamp, and exact immutable intent plus age checks.
No quote or account observation is cached across attempts.

The components are not an atomic broker snapshot. A broker-side change after its
read can still race transport; this phase cannot reserve cash, collateral or price.
After synchronous reads the existing final local checks run again for fresh
price/health, full notional, initialization, session, HALT, permit, risk policy,
DB state and reference generation. The adapter's last reference equality check
is retained. PostgreSQL account admission, unresolved-order/reconciliation
blockers, CAS and SUBMITTING remain the durable cross-instance authority.

| Operation, otherwise eligible | CNC reads | MIS reads |
|---|---:|---:|
| Risk evaluation | 4 GET | 4 GET + 1 calculation POST |
| Explicit operator preflight | 4 GET | 4 GET + 1 calculation POST |
| Operator execute, including internal preflight and final transport | 8 GET | 8 GET + 2 calculation POST |
| Final transport portion of execute | 4 GET | 4 GET + 1 calculation POST |
| Pure candidate planner | 0 | 0 |

Arm/preflight calls made separately add their own observations. Early denial can
short-circuit reads. The count is fixed per observation, with no per-instrument
holdings/positions requests. The extra calculation adds one round trip to each
observation and consumes broker rate allowance. Slow reads can expire the arm or
market evidence and deny execution. No retry or new timeout/config was added.
HALT performs no broker/database work and remains immediate during a blocked
margin calculation. Account-sensitive capacity/quote `toString` output is redacted.

The risk-policy digest now includes an `intraday-margin-v1` semantic salt, keeping
the existing `cash-v1:<sha256>` storage format. Historical approvals are not
rewritten; old policy evidence is rejected and needs a separate re-risked order.
No migration is required. Authenticated, token available, initialization ready
and identical execution identity remain mandatory. The 30-second proposed arm
and one invocation/no retry contract are unchanged.

## Failure and recovery

| Observation | Before admission | After admission / final check | Order POST possible? | Permit / recovery |
|---|---|---|---|---|
| Margin unavailable, collateral unknown, capacity insufficient | Deny; RISK_APPROVED retained | Deny; FAILED | No | Execute consumes; inspect evidence and create/re-risk a new order where required |
| Price over full-notional cap, stale market, changed reference, init/session unavailable | Deny | Deny; FAILED unless HALT wins | No | Consumed; no resize/reapproval/retry |
| HALT | Deny | SUBMITTING retained if already admitted | No if caught before HTTP | Consumed; explicit resolution |
| DB admission/CAS failure | No dispatch | Local authoritative denial | No | Consumed; inspect durable state |
| Broker rejection/auth rejection | Not applicable | Existing rejection handling | One | Consumed; no automatic retry |
| Timeout/reset/malformed acknowledgement/response loss | Not applicable | SUBMITTING conservatively retained | One may have escaped | Consumed; explicit reconciliation only |
| Post-response persistence failure | Not applicable | SUBMITTING conservatively retained | One may have escaped | Consumed; explicit reconciliation only |

Preflight alone does not consume a permit. An execute invocation consumes it even
when denied before transport under the existing operator semantics. More funds
after denial do not restore the permit. Read-only reconciliation works while
HALTED and never resubmits. A new MIS response-loss test exposed the old
DELIVERY-only reconciliation product mapper. It now maps INTRADAY explicitly;
order/trade product mismatches still produce CONFLICT. Existing risk audit
remains historical.

## Validation

Latest suite results: **1,143 unit tests and 490 disposable integration tests**,
zero failures, errors or skips. These are the latest results per suite, not a
claim that the first full integration command passed.

| Run | Result |
|---|---|
| Final full unit run, `tmp/phase108a-unit-final.log` | BUILD SUCCESS; 71 suites / 1,143 tests |
| Full integration run, `tmp/phase108a-integration.log` | 486 tests; two new MIS failures and one existing CNC ambiguous-response error |
| Final focused integration run, `tmp/phase108a-final-targeted.log` | BUILD SUCCESS; 100 tests across FinalReadinessEvidence, IntradayMarginRehearsal, SbinCandidateRehearsal and PostgresReconciliationStore |
| Latest aggregate, `tmp/phase108a-validation-summary.json` | 71 unit suites / 1,143 tests; 17 integration suites / 490 tests; all passing |
| Final diff/configuration review | `git diff --check` clean; baseline revisions unchanged; no configuration or migration edits |

The MIS failures established the missing reconciliation product mapping and a
second-instance test fixture that had inherited lower default limits. Both were
corrected. The CNC success case encountered AMBIGUOUS during the run overlapping
the unit suite; it passed in the complete sequential 41-case evidence rerun.
The existing fake transport has a 250ms read timeout. This is consistent with a
transient timing failure, not proof of its cause; no timeout or production fence
was relaxed. All 34 final MIS rehearsal cases passed.

Commands used (PowerShell requires quoted dotted Maven properties):

```powershell
.\mvnw.cmd test
.\mvnw.cmd -Pintegration '-DskipUnitTests=true' '-Dspring-boot.repackage.skip=true' verify
.\mvnw.cmd -Pintegration '-DskipUnitTests=true' '-Dspring-boot.repackage.skip=true' '-Dit.test=FinalReadinessEvidenceIntegrationTest,IntradayMarginRehearsalIntegrationTest,PostgresReconciliationStoreTest,SbinCandidateRehearsalIntegrationTest' verify
```

Integration commands use `-Dspring-boot.repackage.skip=true`: an initial test
launch stopped at a Windows executable-jar rename failure before tests ran.
This bypasses packaging only; production sources and integration tests compile
and execute normally. No deployable executable-jar claim is made.
New tests cover full-notional dominance, equal funding boundaries, explicit and
unknown collateral terms, negative cash, cash-only MIS, disabled equity, huge
commodity balances, credits/usage deductions, malformed quotes/components,
shared holdings/positions/exposure, unchanged CNC, and equal candidate quantities
at INR 20,000 versus INR 1,000,000 eligible funding.

The synthetic MIS rehearsal changes collateral, cash, required margin, quote
availability/binding, price, freshness, initialization, session, reference and
HALT immediately before transport. It checks zero fake order POSTs, unchanged
risk records, consumed permits, one winner under competing instances, immediate
HALT during a blocked calculation and explicit halted reconciliation after
response loss. Architecture checks retain pure sizing/planning and verify that
account/margin calculation calls read ports and cannot call order mutations.

## Remaining real readiness decision

**REAL_COLLATERAL_ASSISTED_MIS = NOT_READY.** Authoritative collateral eligibility,
cash-minimum/debit restrictions and acceptable response shape require further
bounded read-only evidence. The Kite adapter deliberately supplies unknown terms.
No real account state, fresh SBIN price/reference, margin estimate, market session
or V10 readiness was rechecked here. MARKET_SESSION remains
OPERATOR_RECHECK_REQUIRED. This work supports review of the model; it does not
authorize a real candidate, arm or execution.
