# Phase 10.8B: authoritative pledged-collateral eligibility review

Review date: 2026-10-05. Baseline: `d5f58816888731e8b4c8a4c3af14ab372540458d`
(`Add MIS intraday margin support`). Initial branch was `develop`, HEAD and
`origin/develop` matched that commit, and the working tree was clean.

## Decision

**LIVE MIS READINESS = NOT_READY. No live order is authorized.**

The bounded real observation explains the large net amount, but does not prove
how much collateral is eligible for this exact account's NSE cash-equity MIS
obligation. The calculation endpoint returned a one-share requirement, not
collateral eligibility or an order acceptance promise. The committed adapter
correctly leaves collateral terms unknown. No production policy was loosened.

| Question | Result |
| --- | --- |
| Authentication and shared initialization readiness | PASS at observation |
| Runtime HALT / emergency stop | HALTED / true throughout |
| Nonzero positions / non-terminal broker orders | None observed |
| Exact SBIN reference | One enabled NSE cash instrument; lot 1, tick 0.05 |
| Pledged collateral present | Yes; eligibility of the aggregate not established |
| Haircut convention | Broker credited collateral is post-haircut; no local haircut invented |
| CASH_COMPONENT_RULE | KNOWN_APPLICABLE: positive cash prerequisite |
| Account-specific cash/equivalent ratio and API balance mapping | UNKNOWN |
| Cash-component satisfaction | NOT_ESTABLISHED |
| AUTHORITATIVE_ELIGIBLE_MIS_FUNDING | UNKNOWN; not inferred from net |
| COLLATERAL_REQUIRED_FOR_CANDIDATE | true under unchanged platform policy, including the minimum probe |
| Cash-only fallback under unchanged policy | Does not pass |
| Fresh price / reviewed buffer and limits / candidate quantity | Not established; no quantity selected |
| MARKET_SESSION | OPERATOR_RECHECK_REQUIRED; not inferred from wall clock |

The positive cash prerequisite follows the official [pledging terms](https://support.zerodha.com/category/console/portfolio/pledging/articles/pledging-terms-of-services).
Their 50% cash/equivalent rule is expressly for F&O; this review does not import
it into all cash-equity MIS orders. Separately, the [pledge-category guidance](https://support.zerodha.com/category/console/portfolio/pledging/articles/securities-available-for-pledging)
distinguishes approved and additional securities and specifies a cash/liquid
component for additional securities. Available aggregate collateral does not
identify this account's eligible category mix.

## Real observation: bounded and non-authorizing

Observation window: **2026-10-05 12:47:08-12:47:13 UTC**
(18:17:08-18:17:13 IST). These are receipt/operation observations, not a
transactional broker snapshot or a continuing freshness guarantee.

Authentication reported `authenticated=true`, `tokenAvailable=true`,
`initializationReady=true`, and execution identity available. No identity value
or credentials were emitted. Initialization used the existing profile and
instrument initialization semantics.

Reference evidence:

- Platform InstrumentId: `050f94dd-e639-364f-97a6-595594de6543`.
- NSE / SBIN / CASH; exactly one match and one enabled universe row.
- Lot size 1; tick size 0.05; broker token present internally, never recorded.
- Registry/universe eligibility passed. This does not establish account-specific
  broker tradability entitlement or guarantee order acceptance.

Account evidence:

| Bounded observation | Value |
| --- | ---: |
| Nonzero net positions | 0 |
| Nonzero day positions | 0 |
| Non-terminal broker orders | 0 |
| SBIN position / holding | Neither observed |
| SBIN orders today / trades today | 0 / 0 |
| Pledged holding rows | 23 |

The committed mapper normalizes raw COMPLETE to FILLED. FILLED, CANCELLED and
REJECTED are terminal; other statuses remain blocking. Holdings are not equated
to intraday positions. No individual holding rows, account identifier or raw
account response was included in evidence.

| Equity margin field | Observed INR value |
| --- | ---: |
| enabled | true |
| net | 1089539.25181 |
| available.cash | -14681.5 |
| available.openingBalance | -14681.5 |
| available.liveBalance | 318.5 |
| available.collateral | 1089220.75181 |
| available.intradayPayin | 15000 |
| available.adhocMargin | 0 |
| utilised.debits / payout / holdingSales | 0 / 0 / 0 |
| utilised.liquidCollateral | 0 |
| utilised.stockCollateral | 1089220.75181 |
| utilised.realisedM2M / unrealisedM2M | 100 / 0 |
| utilised.optionPremium / span / exposure / delivery / turnover | All 0 |

Exact decimal identities in this sample:

```text
-14681.5 + 15000 = 318.5
1089220.75181 + 318.5 = 1089539.25181
```

Thus the large observed net is accounted for by collateral plus live balance;
the positive live balance is accounted for by opening balance plus pay-in.
These are observed identities, not a universal broker accounting formula.
The historical transaction(s) causing the opening deficit cannot be determined
from this snapshot. The [negative withdrawal-balance guidance](https://support.zerodha.com/category/console/ledger/articles/withdrawal-balance-is-negative)
describes possible ledger causes, but does not identify this account's cause.
No ledger investigation was performed.

Notably, `utilised.stockCollateral` equals `available.collateral` despite no
observed nonzero positions, open orders or debits. It is not additional buying
power. Nor is subtracting those two fields an authoritative way to establish
available collateral. The precise interpretation for this account remains a
broker clarification item.

## Authoritative semantics and limits of the evidence

Sources were opened and reviewed on 2026-10-05. API field definitions below are
from [Kite funds and margins](https://kite.trade/docs/connect/v3/user/#funds-and-margins).
Every listed API field is retained by the committed normalized model. The model
has no separate **available** liquid/stock collateral split.

| API field | Documented meaning / kind | Phase 10.8A use and conservative limitation |
| --- | --- | --- |
| enabled | Segment permission | Equity must be enabled; commodity never funds NSE |
| net | Aggregate including credits/collateral | Bound, never independently trusted buying power |
| cash | Raw cash, documented as including pay-in | Cash floor; observed cash/live discrepancy needs clarification |
| opening_balance | Start-of-day balance | Cash floor; not current unrestricted funding alone |
| live_balance | Current balance | Cash floor; does not override other evidence |
| collateral | Pledged-stock margin | Explicit eligible terms required |
| intraday_payin | Deposits during day | Excluded by MIS policy |
| adhoc_margin | Broker extra margin | Excluded, not trusted cash |
| debits | Aggregate utilisation | Positive amount deducted |
| payout | Withdrawals | Deducted |
| holding_sales | Sale value | Deducted; not trusted settled cash |
| liquid_collateral / stock_collateral | Utilised collateral | Never counted as available funds |
| span / exposure | Derivatives blocks | Not cash or NSE collateral eligibility |
| realised / unrealised M2M | PnL | Positive credits excluded |
| option_premium | Premium received | Absolute amount excluded |
| delivery / turnover | Delivery block / turnover usage | Retained, not funding additions |

This table describes local conservative treatment, not an authoritative
reconstruction of broker RMS. Account balances, credits, usage and collateral
must be treated as mutable; opening balance is a daily baseline, not a freshness
certificate. Cash-like aggregates do not prove settlement or absence of
conditional credits. Haircut is inapplicable to cash/usage fields; it applies to
credited pledged collateral. Missing eligible composition cannot be repaired
by renaming utilised fields as available.

The [collateral-margin explanation](https://support.zerodha.com/category/trading-and-markets/general-kite/funds/articles/what-does-collateral-margin-in-the-funds-mean)
supports equity intraday use and identifies credited collateral as adjusted for
haircut. The [pledging overview](https://support.zerodha.com/category/console/portfolio/pledging/articles/what-is-pledging)
also describes eligibility and debit restrictions. No raw pledge valuation or
locally invented haircut was used. Post-haircut value alone does not prove the
account meets cash-component, category or debit conditions.

The official [negative-cash rejection guidance](https://support.zerodha.com/category/trading-and-markets/alerts-and-nudges/kite-error-messages/articles/error-message-negative-cash)
explicitly includes new equity intraday positions. However, it does not map the
relevant cash balance to this snapshot's negative API cash/opening fields versus
positive live balance. Therefore neither broker rejection nor broker acceptance
is inferred solely from these fields. The [Kite funds-display explanation](https://support.zerodha.com/category/trading-and-markets/general-kite/funds/articles/kite-dashboard-and-fund-values-calculation)
also does not justify equating UI available collateral categories with API
utilised collateral.

## Calculation-only one-share probe

The reviewed application path was:

```text
OrderMarginEstimator -> KiteOrderMarginAdapter -> KiteRestTransport
  -> POST https://api.kite.trade/margins/orders
```

The official [margin calculation API](https://kite.trade/docs/connect/v3/margins/)
documents this as a calculation that considers positions/open orders. Its MARKET
example uses zero price and trigger price. The request used NSE, SBIN, BUY,
MARKET, MIS, regular, quantity **1** (one valid lot), price 0, trigger price 0.
DAY remains the local intent: this calculation schema has no validity field.
The eventual order encoding separately supports DAY.

| Result | Exact decimal value |
| --- | ---: |
| Required margin | 191.60000000000002 |
| Estimated charges | 0.374966948 |
| Collateral terms supplied | None |

The existing adapter bound the single equity result to the local request and
validated its supported result shape. The response does not establish an
eligible cash/collateral split or minimum cash component. No price was inferred
from margin or leverage. No second/larger probe was performed.

The calculator does not place, modify, cancel, reserve, risk-approve or authorize
an order. The separate [order API](https://kite.trade/docs/connect/v3/orders/)
uses POST `/orders/:variety`, PUT/DELETE `/orders/:variety/:order_id` for mutations.
Those methods/routes were inaccessible to the review harness. A successful
calculation is not an RMS acceptance guarantee.

## Unchanged funding policy and cash-only fallback

DELIVERY retains the conservative cash floor:

```text
min(cash, openingBalance, liveBalance, equity.net)
  - positive(debits) - positive(payout) - positive(holdingSales)
```

For the observed fields it is **-14681.5**. MIS additionally excludes pay-in,
adhoc margin, positive realised/unrealised PnL and absolute option premium.
The synthetic regression of these fields therefore produces policy cash
**-29781.5**. This is a conservative policy result, NOT broker-reported cash
debt or an assertion that broker cash fell by that amount. Subtracting pay-in
from an already conservative opening floor can over-deduct; this phase did not
relax that reviewed policy to manufacture a pass.

Without explicit `CollateralTerms`, eligible collateral under the policy is
zero and full estimated margin plus charges and cash reserve must be cash
funded. Here that fallback fails even before any positive reserve. The specific
funding denial is `COLLATERAL_UNSUPPORTED`. DELIVERY also remains denied.
Unknown authoritative eligibility is not a claim the collateral has zero
broker value.

With explicit terms, existing code caps collateral by available collateral and
the supplied eligible adjusted amount, deducts utilised liquid/stock collateral,
and separately checks minimum cash, charges, reserve and aggregate capacity.
These are conservative local fences, not a documented formula for deriving
broker availability. The real adapter supplies no such terms, so this review
does not activate that hypothetical path or certify its account applicability.

No full real portfolio risk decision was run. The account-shaped regression
uses a synthetic portfolio and synthetic price solely to exercise policy; it
does not substitute a made-up tick for real candidate sizing.

## Quantity and future risk configuration

No market-data stream was started: collateral semantics failed an earlier gate.
No old tick was reused, no current SBIN price was claimed, and no maximum
candidate quantity or notional was selected. The one-share calculator request
is not a sized candidate, OrderRecord, OrderId or risk approval.

The unchanged sizing contract, once all prerequisites exist, is:

```text
unit = freshPrice * validatedBuffer
budgetUnits = floor(10000 / unit)
lotQuantity = lotSize * floor(budgetUnits / lotSize)
Q = lot-floor(min(lotQuantity, all stricter quantity/headroom caps))
notional = unit * Q
```

Recompute exact notional and require it at or below the human, normal execution,
first-live and risk caps. INR 9999.99 and 10000.00 pass the human boundary;
10000.01 fails. Margin is an additional gate and never a divisor for allocating
the INR 10000 exposure budget. A zero-lot result stays ineligible.

The nine risk-value keys below were absent in the process and `.env` presence
check. No values were changed. Committed defaults are fail-closed:

| Contract | Default | Future requirement, not configured here |
| --- | --- | --- |
| risk enabled | false | Separate reviewed decision |
| maxOrderQuantity | 0 | Explicit positive cap |
| maxOrderValue | 0 | Positive full-notional cap; stricter limit wins |
| maxPositionQuantity | 0 | Explicit positive aggregate position cap |
| maxExposure | 0 | Explicit positive exposure cap |
| marketDataMaxAge | 0s | Explicit positive freshness limit |
| registryMaxAge | 0s | Explicit positive reference freshness limit |
| priceBuffer | 1.0 | Validated >=1; default is not a human-reviewed first-live choice |
| cashReserve | 0 | `RiskLimits.configured()` requires >0; remains cash-only |

There is no new MIS reserve or live switch. Explicit collateral terms are
evidence, not a configuration shortcut. All existing holdings/position exposure,
open-order and final revalidation rules remain applicable. A future arm remains
bounded by 30 seconds and any stricter configured maximum; no arm occurred.

## Runtime isolation and exact real request count

A temporary local Java review harness reused committed adapters; the normal
Spring application was not started. Normal startup can run Flyway and token
restoration paths that clear expired credentials, so it was unsuitable for this
strict no-write review without additional isolation.

The harness loaded the existing encrypted token through the existing store in a
PostgreSQL read-only transaction (`default_transaction_read_only=on`, verified
by `SHOW transaction_read_only`, with statement timeout). It rolled back and
closed the connection before broker calls. An in-memory token-store wrapper
rejected save/clear; login/exchange were unavailable. No auth refresh/reset,
Flyway, durable risk/order write or execution service was invoked.

Process-local settings enforced loopback address, execution/operator/live-test
and live trading false, emergency stop true, market data disabled and
`-Duser.timezone=UTC`. No HTTP listener was opened. No Windows/global Java/DB
timezone or persistent environment was changed. A child PowerShell used
process-only execution-policy bypass for the reviewed launcher after the
initial script-policy failure; it did not change persistent policy. `.env`
SHA-256 was equal before/after the real run.

The transport interceptor allowed only fixed HTTPS `api.kite.trade` paths,
rejected queries/alternate destinations, checked HALT per request and allowed
each route once. Existing no-redirect transport behavior was retained. An
invalid-runtime guard rehearsal exited before requests. The successful run:

| Route | Count |
| --- | ---: |
| GET /user/profile | 1 |
| GET /instruments | 1 |
| GET /portfolio/positions | 1 |
| GET /orders | 1 |
| GET /trades | 1 |
| GET /portfolio/holdings | 1 |
| GET /user/margins | 1 |
| POST /margins/orders | 1 |
| Order placement / modification / cancellation | 0 |

HALT was checked before requests, after account reads and at completion. Final
state remained HALTED with emergency stop true. Execution invocations, token
writes, database writes, market-data subscriptions, permits, arms and resumes:
**zero**. The harness exited; no review runtime remains running.

Ignored local artifacts: `tmp/Phase108bReadOnlyReview.java`,
`tmp/phase108b-run.ps1`, `tmp/phase108b-real-review.log`, compiled classes and
classpath. They contain no persisted credentials/raw account payloads. The
tracked durable evidence is this bounded report and synthetic/mocked tests;
the temporary harness is not a new production diagnostic or execution endpoint.

## Committed architecture review and regressions

Direct review covered `OrderMarginEstimator`, `OrderMarginQuote`,
`KiteOrderMarginAdapter`, `KiteRestTransport`, `KiteTradingReadConfiguration`,
`IntradayAccountCapacity`, `CashAccountCapacity`, `CashOrderRiskRules`,
`OrderRiskInput`, `RiskLimits`, `RiskService`, `FirstLiveCandidatePlanner`,
`CurrentAccountExecutionChecks`, `OrderConfiguration`,
`OrderReconciliationService`, associated fences and Phase 10.8A validation.

| Invariant | Reviewed result |
| --- | --- |
| Separate DELIVERY and MIS funding | Preserved; CNC -> CNC, INTRADAY -> MIS |
| Full conservative notional and quantity | Shared valuation/sizer; margin cannot enlarge Q |
| Calculation-only transport | Fixed margin route; no mutation method dependency |
| Current account / estimate | Refreshed in execution and final dispatch checks |
| Price and market health | Final conservative valuation/freshness fences remain |
| Authentication | Shared initialization readiness plus same execution identity |
| HALT | Independent local atomic state; fail-closed default |
| PostgreSQL admission and CAS | Remain authoritative; broker reads do not replace them |
| One-shot / ambiguity | Permit consumed through existing finally semantics; no automatic retry |
| MIS reconciliation | Strict normalized product identity; explicit read-only reconciliation remains available while halted |
| Candidate plan / sizer | Observational/pure; no order creation, authorization or broker mutation |

Only tests/documentation changed. Adapter tests now record method/path and
explicitly assert exactly one POST `/margins/orders` and zero POST/PUT/DELETE
`/orders...`, including malformed/error/timeout cases. The new synthetic
funding regression preserves the observed decimal relationships, rejects
unknown collateral eligibility and retains CNC denial.

Executed after final test edits:

```powershell
.\mvnw.cmd '-Dtest=KiteOrderMarginAdapterTest,ConservativeValuationArchitectureTest,IntradayAccountCapacityTest,IntradayCandidatePlannerTest,ConservativeOrderQuantitySizerTest,OrderReconciliationServiceTest' test
```

**71 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.** These are pure or
mocked tests, separate from explicit real diagnostics. No mutation-capable DB
integration test was run this phase; prior Phase 10.8A integration results are
historical and are not claimed as rerun. No migration, production source,
configuration, universe or token changes. No commit or push.

## Evidence still required and TOCTOU limits

Before a collateral-assisted candidate can be declared review-ready, obtain
authoritative clarification/evidence for:

1. Which API cash balance establishes the positive-cash condition when cash and
   opening are negative but live balance is positive after pay-in.
2. Current eligible approved/additional collateral composition, available cash
   equivalents, applicable cash component and debit restrictions for this exact
   cash-equity MIS case.
3. Meaning of the matching available collateral and utilised stock collateral
   values; no arbitrary subtraction can replace this clarification.
4. A trustworthy way to supply explicit collateral terms to the application.
   The calculation API alone does not provide them.
5. Explicit human-reviewed risk parameters, then fresh SBIN market evidence and
   exact notional sizing, with authoritative market-session/operator recheck.

No support message was sent and no holdings/ledger export was taken. The
underlying opening deficit would require a separately bounded ledger review if
its historical cause is needed.

This observation reserves no price, cash, collateral or margin. Price, reference
data, margins, required margin, holdings, positions, orders, authentication and
market health can change. Any separately authorized future execution must still
perform the existing admission and final transport revalidation, exact-order
and session binding, HALT and one-shot checks. An ambiguous escaped request
requires explicit reconciliation, never a retry based on remaining margin.
