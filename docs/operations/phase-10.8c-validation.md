# Phase 10.8C: pledged composition and cash eligibility

Access/review date: **2026-10-05**. Decision: **NOT_READY**.
This is a read-only review, not permission to trade or to size a live order.

## Result against the seven factual questions

| Question | Established evidence | Remaining limit |
| --- | --- | --- |
| A. What constitutes INR 1,089,220.75181? | Refreshed holdings contain 23 distinct securities with positive `collateral_quantity`, all labelled `pledge`; margins again report that aggregate | No authoritative adjusted per-security contribution or complete reconciliation to that aggregate |
| B. Eligible for NSE SBIN BUY MARKET MIS? | Broker documentation permits collateral for equity intraday in general | This account's approved/additional and cash/non-cash composition and conditions are not certified; UNKNOWN |
| C. Haircut already applied? | Broker documentation describes credited collateral as post-haircut | General convention established; per-security adjusted amounts not exposed by the reviewed holdings contract |
| D. Positive-cash condition? | A positive cash balance is required by pledging terms; negative cash blocks new equity intraday positions | Cash equivalents/category rules also matter; do not import F&O rules blindly |
| E. Which API field proves D? | API definitions distinguish raw cash, opening and current balance | No reviewed authoritative source maps the eligibility condition unambiguously to one API field for this case; UNKNOWN |
| F. Satisfied now? | At the observation, cash/opening were negative and live was positive | NOT_ESTABLISHED; positivity of live alone is not proof |
| G. Cash-only fallback? | Unchanged conservative platform cash policy fails | Authoritative broker cash-only eligibility is not established; no exact future quantity or fresh margin quote here |

`COLLATERAL_ELIGIBILITY=UNKNOWN`.
`CASH_COMPONENT_RULE=KNOWN_APPLICABLE` for the positive-cash prerequisite;
`ACCOUNT_SPECIFIC_CASH_COMPONENT_TERMS=UNKNOWN`.
`READY_FOR_CANDIDATE_SIZING=false`. Unknown facts were not converted to approvals.
The supporting authoritative sources and the distinctions behind these answers
are recorded below.

## Baseline and preserved contract

Before inspection, `git status --short` was empty, branch was `develop`, and
HEAD/local `origin/develop` both were
`6cfeeadbcca2a4006af266006aeb80f4b6e6d27d`.
The requested eight-commit log was read. No Git state was reset or discarded.

Read [Phase 10.8A](phase-10.8a-validation.md) and
[Phase 10.8B](phase-10.8b-validation.md), and directly inspected:
`IntradayAccountCapacity`, `CashAccountCapacity`, `OrderMarginEstimator`,
`OrderMarginQuote`, `KiteOrderMarginAdapter`, `KiteRestTransport`,
`KiteTradingReadAdapter`, `KiteTradingReadMapper`, `BrokerHolding`,
`CurrentAccountExecutionChecks`, `FirstLiveCandidatePlanner`, `RiskService`,
`CashOrderRiskRules`, `RiskLimits`, `OrderRiskInput` and execution/reconciliation
fences. No production implementation was changed.

| Required invariant | Source finding |
| --- | --- |
| A/B. CNC and MIS distinct | Cash-funded DELIVERY preserved; explicit intraday funding gate |
| C. Full notional ceiling | Shared valuation/sizing precedes margin gate; collateral never enlarges Q |
| D. Calculator is read-only | Fixed `/margins/orders`; no fallback to order transport |
| E/F. Final evidence | `validateTransport` refreshes account/quote, then repeats dispatch checks including price/notional |
| G. Initialization | Shared `initializationReady`, authenticated/token/identity checks remain mandatory |
| H. HALT | Independent local atomic latch; no DB/provider dependency |
| I/J. One shot / ambiguity | Operator finally disarms/completes; ambiguous results never trigger retry |
| K. MIS reconciliation | Explicit INTRADAY product mapping and identity matching |

PostgreSQL admission/CAS, reference equality at transport, exact order/session
binding, market freshness, and historical risk records remain unchanged.
The human INR 10,000 full conservative notional ceiling, future <=30-second arm,
one mutation attempt and no automatic retry remain constraints, not enabled
configuration. `ConservativeOrderValuation` and `ConservativeOrderQuantitySizer`
were not modified.

## Authoritative sources and what they establish

All links below were opened on 2026-10-05. No forum/search snippet was used to
establish a safety-critical rule.

1. [Kite holdings](https://kite.trade/docs/connect/v3/portfolio/#holdings):
   `collateral_quantity` is quantity used as collateral; `collateral_type` is a
   nullable string. Quantity buckets and previous close are exposed. There is
   no documented per-row haircut, adjusted collateral amount, approved category
   or cash-equivalent eligibility flag. A pledge label does not encode those
   missing facts.
2. [Kite margin fields](https://kite.trade/docs/connect/v3/user/#funds-and-margins):
   distinguishes available collateral from utilised stock/liquid collateral.
   Raw cash, opening balance, live balance and net are different observations.
   The definitions do not specify which is the pledged-use positive-cash gate.
3. [Credited equity collateral](https://support.zerodha.com/category/trading-and-markets/general-kite/funds/articles/what-does-collateral-margin-in-the-funds-mean):
   collateral from pledged holdings is haircut-adjusted and may support equity
   intraday. This establishes general product applicability, not this account's
   adjusted eligible composition.
4. [Pledging terms](https://support.zerodha.com/category/console/portfolio/pledging/articles/pledging-terms-of-services):
   positive cash is a prerequisite; collateral is based on previous close after
   haircut. The terms' 50% cash/equivalent discussion explicitly concerns F&O.
   This review does not assume that paragraph alone defines SBIN cash MIS.
5. [Official approved list](https://zerodha.com/approved-securities):
   authoritative location for security categories/haircuts, updated daily.
   Its general notes distinguish cash-equivalent versus non-cash collateral.
   The document reader exposed the notes and a dynamic "Fetching data" state,
   not populated security rows or a retrievable CSV link. This is an access
   limitation, not evidence that the list is empty or any holding is ineligible.
   No current list snapshot/version was obtained, no symbol match was claimed,
   and no scraping, private endpoint discovery or browser automation was added.
6. [Cash versus non-cash pledge categories](https://support.zerodha.com/category/console/portfolio/pledging/articles/list-of-stocks-pledge-zerodha):
   describes full use of cash-component collateral and a 50% non-cash component
   with cash/equivalents for the remainder, including charges for excess use.
   This broader category guidance matters independently of the F&O paragraph;
   it does not map this account's collateral or cash API fields. It cannot be
   used to assume either no requirement or that exactly half of this candidate's
   margin must be raw cash. Its cash-equivalent treatment must also be reconciled
   with the general positive-cash prerequisite before claiming satisfaction.
7. [Approved versus additional securities](https://support.zerodha.com/category/console/portfolio/pledging/articles/securities-available-for-pledging):
   distinguishes approved and additional Group A securities, priority of use,
   and cash/liquid requirements for the latter. Pledge acceptance alone does
   not establish these categories.
8. [Negative-cash rejection](https://support.zerodha.com/category/trading-and-markets/alerts-and-nudges/kite-error-messages/articles/error-message-negative-cash):
   relates debit balance to funds-statement obligations and blocks new equity
   intraday/F&O positions. It does not name the decisive API JSON field.
9. [Available cash and opening balance](https://support.zerodha.com/category/trading-and-markets/trading-faqs/general/articles/what-is-available-cash-and-opening-balance-on-kite):
   UI available cash can include unsettled funds and intraday deposits; opening
   is the start-of-day cash baseline. This does not prove that UI available cash
   equals API `available.cash` in the observed negative-opening/positive-live case.
10. [Funds-page calculations](https://support.zerodha.com/category/trading-and-markets/general-kite/funds/articles/kite-dashboard-and-fund-values-calculation):
    UI total collateral combines equity and liquid collateral; available margin
    includes credits. UI available categories cannot be equated to API utilised
    fields merely because values match.
11. [Free cash](https://support.zerodha.com/category/trading-and-markets/margins/margin-leverage-and-product-and-order-types/articles/free-cash-meaning):
    the described buying-power expression includes direct collateral and
    conditional credits. Therefore its name does not make it an authoritative
    cash-only funding measure or resolve the positive-cash gate.
12. [Order-margin calculation](https://kite.trade/docs/connect/v3/margins/):
    a calculation for an exact proposal, not placement/reservation. The response
    does not supply adjusted eligible composition or account cash-component terms.

The public sources establish several rules, but leave an account-specific
mapping gap. A broker clarification of that gap is required; no rule was chosen
because it happens to make the account pass.

## Real field classification and composition

The reviewed normalized holdings model retains instrument identity, ISIN,
quantity, used/unsettled/realised/authorised/opening quantities,
`collateralQuantity`, price fields, discrepancy and margin-funded metadata.
It discards the wire `collateral_type`. That string is described only as type
of collateral; preserving it in production would not supply missing haircut,
approved-list category or cash eligibility. No production extension is warranted
solely to turn the observed `pledge` label into a funding approval.

For this investigation, the isolated harness obtained one bounded holdings body,
validated it through the committed `KiteTradingReadMapper`, and inspected only
pledge metadata in that same in-memory response. No second holdings request or
raw payload persistence. Only aggregate counts and a derived gross mark left
that boundary; no individual symbols, ISINs, broker tokens or account IDs.

| Field / derived observation | Classification | Permitted interpretation |
| --- | --- | --- |
| `collateral_quantity` / `collateral_type` | UNKNOWN as a monetary capacity | Pledged units/opaque type, not a funding amount |
| sum(collateral quantity x close price) | RAW_PLEDGED_VALUE, derived gross mark only | Not a broker adjusted allocation or guaranteed complete collateral book |
| `available.collateral` | AVAILABLE_COLLATERAL | Reported aggregate; documented credited-collateral convention is post-haircut, but unrestricted eligibility unknown |
| Per-security adjusted contribution | UNKNOWN | No authoritative amount in reviewed holdings contract |
| `utilised.stock_collateral` | UTILISED_COLLATERAL | Never add to available collateral |
| `utilised.liquid_collateral` | UTILISED_COLLATERAL | Never relabel as free cash equivalents |
| `net` | UNKNOWN as standalone eligible collateral | Mixed aggregate, not pure cash or an independent collateral source |

No observed per-security field can be classified as an authoritative
HAIRCUT_ADJUSTED_VALUE. The overall post-haircut convention does not fill that
component-level absence.

Fresh observation window: **2026-10-05 13:10:37-13:10:42 UTC**
(18:40:37-18:40:42 IST). Observations are not atomic across broker calls.

| Pledge evidence | Result |
| --- | ---: |
| Positive collateral-quantity rows / distinct securities | 23 / 23 |
| `collateral_type=pledge` rows | 23 |
| Blank/missing / other type rows | 0 / 0 |
| Discrepancy-flagged pledged rows | 0 |
| Derived previous-close gross mark (INR) | 1279365.65 |
| Authoritative adjusted component total | UNKNOWN |
| Verified eligible / verified ineligible / unknown securities | 0 / 0 / 23 |

Zero verified eligible/ineligible means **unclassified**, not zero actual
eligible/ineligible securities. The 23 identified pledge rows are not proof
they are the complete set of contributors to the broker aggregate. No global
or per-security haircut was inferred from the difference between gross mark and
reported collateral.

Reconciliation semantics: all arithmetic uses BigDecimal, no floating point.
A component reconciliation would require authoritative adjusted amounts at
compatible valuation times. Exact `compareTo == 0` would be required; no
rounding tolerance is justified by the reviewed sources, so none is allowed.
Those components are unavailable: reconciliation is **NOT_ESTABLISHED**, not
PASS and not a claimed numerical discrepancy between comparable measures.

## Cash and margin evidence

The Phase 10.8B observation was the starting evidence, not assumed current. The
new bounded read returned the same values:

| Equity field | INR value / state | Meaning/use in this review |
| --- | ---: | --- |
| enabled | true | Equity segment available |
| net | 1089539.25181 | Mixed aggregate, never independent buying power |
| available.collateral | 1089220.75181 | Reported collateral aggregate |
| available.cash | -14681.5 | Documented raw cash; not selected as decisive gate without mapping |
| openingBalance | -14681.5 | Start-of-day baseline |
| liveBalance | 318.5 | Current available balance; positivity alone insufficient |
| intradayPayin | 15000 | Deposit during day, not added a second time |
| adhocMargin | 0 | No extra broker credit observed |
| utilised.debits / payout / holdingSales | 0 / 0 / 0 | No such usage in this observation |
| utilised.stockCollateral / liquidCollateral | 1089220.75181 / 0 | Utilised fields, not independent available components |
| realisedM2M / unrealisedM2M | 100 / 0 | PnL observations, not new trusted funding |
| optionPremium / span / exposure / delivery / turnover | All 0 | No funding additions |

Observed identities, with exact decimal semantics:

```text
-14681.5 + 15000 = 318.5
1089220.75181 + 318.5 = 1089539.25181
```

These explain this snapshot's arithmetic, not the historical transactions that
caused the opening deficit or a universal RMS formula. They do not authorize
`net + collateral` or `available collateral + utilised stock collateral`.
The equality of available and utilised collateral remains semantically
unresolved for this account; no amount is counted twice.

The API's wording that raw cash includes intraday pay-in does not explain why
this observation's raw cash remains equal to opening while live reflects pay-in.
The support UI descriptions do not provide the missing explicit mapping.
`liveBalance > 0` and `net - collateral > 0` are therefore observations, not
authoritative positive-cash eligibility proof. Conversely, negative API raw
cash alone is not reported as proof the broker would reject the order.

The unchanged CNC floor remains -14681.5; MIS policy additionally excludes the
15000 pay-in and positive 100 realised PnL, producing policy cash -29781.5 in
the synthetic account-shaped regression. That is a deliberately conservative
local result, not broker-reported debt. No cash-floor, credit, reserve or
collateral-deduction rule was relaxed.

Cash-only fallback therefore does not pass **platform policy**. Authoritative
broker cash-only capacity remains UNKNOWN. The Phase 10.8B one-share quote
(191.60000000000002 margin plus 0.374966948 charges) is historical, not current;
it was not extrapolated or compared to live balance to assert readiness.
`COLLATERAL_REQUIRED=true` under the unchanged policy for a positive candidate;
whether the broker independently requires it cannot be established here.
No quantity, fresh price, reserve or new margin estimate was selected.

## Cleanliness, reference and runtime safety

Authenticated, token available, shared initialization ready and execution
identity available were all true at observation. Exact NSE SBIN resolved once,
was enabled in the single universe row, CASH segment, lot 1, tick 0.05.
Platform ID remained `050f94dd-e639-364f-97a6-595594de6543`; no broker token value
was emitted. This is reference eligibility, not account-specific trade approval.

Nonzero net/day positions: 0/0. Non-terminal orders: 0. SBIN holding/position:
none observed. SBIN orders/trades today: 0/0. Committed terminal semantics
FILLED (including normalized COMPLETE), CANCELLED and REJECTED were retained.

Before launching, the read budget was declared: **one each** of profile,
instruments, holdings, margins, positions, orders and trades; **zero** calculator
requests, order mutations and market-data connections. Actual usage matched:

```text
GET /user/profile          1
GET /instruments           1
GET /portfolio/holdings     1
GET /user/margins           1
GET /portfolio/positions    1
GET /orders                1
GET /trades                1
POST /margins/orders        0
POST/PUT/DELETE /orders...  0
```

The opt-in local Java harness reused production transport/read adapters and the
holdings mapper. Its interceptor allowed only those seven HTTPS GET paths on
`api.kite.trade`, no query/alternate destination, no redirect, at most once per
path. All POST/PUT/DELETE were denied, including calculation POST in this phase.
No retries or polling. HALT was verified before DB/broker stages, per broker
request, after account reads and at completion.

Normal Spring startup, Flyway, execution/arming services and WebSocket startup
were not invoked. Token-store save/clear and authentication exchange were
disabled by local wrappers; existing valid-token restoration reused the shared
profile/reference initialization flow. Invalid/expired authentication would
stop without token removal. A guard rehearsal without required runtime settings
exited with zero broker requests before DB work.

Child-process settings: SERVER_ADDRESS=127.0.0.1, execution/operator/live-test
flags false, ENABLE_LIVE_TRADING=false, EMERGENCY_STOP=true, market data false,
JVM `-Duser.timezone=UTC`. No listener. The reviewed environment helper ran only
inside that child process; PowerShell execution-policy bypass was process-only.
`.env` hash was unchanged. No User/Machine environment, Windows timezone, Java
global configuration or database timezone change.

Every DB connection used `default_transaction_read_only=on`, a 5-second statement
timeout, JDBC read-only mode and explicit rollback. Read-only state was checked.
Before/after evidence transactions additionally used REPEATABLE READ. Token
loading was SELECT-only; no application migrations or writes occurred.

| Table | Before | After |
| --- | ---: | ---: |
| kite_access_tokens | 1 | 1 |
| kite_login_attempts | 0 | 0 |
| orders | 0 | 0 |
| order_idempotency | 0 | 0 |
| risk_decisions | 0 | 0 |
| reconciliation_decisions | 0 | 0 |
| reconciliation_trades | 0 | 0 |
| strategy_evaluations | 0 | 0 |
| execution_authorizations | 0 | 0 |

Selected token row count 1; issued `2026-10-05T10:08:26.720571Z`, expiry
`2026-10-06T00:30:00Z`, validity true before/after. An in-memory equality
fingerprint of the full stored token row also matched, so unchanged count alone
was not used to claim token preservation. Neither fingerprint nor ciphertext,
nonce, token, encryption key, API secret or account ID was emitted/persisted.
Trading-table counts are supplementary evidence; no-write connections/code
provide the write prohibition, not a claim counts can detect arbitrary updates.

Final HALT: HALTED; emergency stop: true. Zero orders, execution, arms, resumes,
permits, token writes, DB writes or market-data subscriptions. The process
exited; no recurring diagnostic remains running.

Local ignored artifacts: `tmp/Phase108cReadOnlyReview.java`,
`tmp/prepare-phase108c.py`, `tmp/phase108c-run.ps1`, compiled classes and
`tmp/phase108c-real-review.log`. The log is bounded evidence, not raw responses.
The harness is local infrastructure, not a new production API.

## Synthetic regressions

The received regression list ends at item 16 (authentication loss). All supplied
items are covered below; no claim is made about an unseen continuation.

| Required case | Deterministic evidence |
| --- | --- |
| 1. Large net, negative cash, positive live | Existing negative-shape and Phase 10.8B observed-shape tests |
| 2/3. Net includes collateral; reject double count | New `netAlreadyIncludingCollateralCannotFundASecondCopy`: 100 cash + 2000 collateral gives 2100 capacity, denies a 3000 requirement |
| 4. Utilised is not available | New fully-utilised test leaves zero eligible collateral and denies; existing deduction test retained |
| 5/6. Commodity / disabled equity | Existing huge-commodity and disabled-equity tests |
| 7/8/9. Unknown eligibility / haircut / cash component | Parameterized missing-fact test keeps complete terms absent and denies collateral-dependent funding |
| 10. Cash-only MIS | Existing sufficient-cash/no-collateral approval and equality boundary |
| 11. CNC | Existing same-account DELIVERY denial; cash policy unchanged |
| 12. INR 10000 size | Existing planner compares 20000 versus 1000000 funding with identical quantity/full-notional ceiling |
| 13. No order mutations from calculator | Exact calculation path assertion; zero POST/PUT/DELETE order routes |
| 14/15. Timeout / malformed calculation | Existing bounded failure parameter cases, single calculation request, no retry |
| 16. Authentication loss | Existing 401 case plus new pre-calculation invalidation test asserting zero HTTP requests |

The missing-fact test documents the existing all-or-absent `CollateralTerms`
contract. It does not pretend the numeric domain value can authenticate a
provider's provenance. The production Kite adapter supplies no terms, so unknown
real haircut/category/cash facts cannot become a production approval through it.
No invented broker terms or haircut percentages were added.

Final focused command:

```powershell
.\mvnw.cmd '-Dtest=KiteOrderMarginAdapterTest,ConservativeValuationArchitectureTest,IntradayAccountCapacityTest,IntradayCandidatePlannerTest,ConservativeOrderQuantitySizerTest,OrderReconciliationServiceTest' test
```

**77 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**, recorded in
`tmp/phase108c-tests-final.log` after all test edits.
Tests use synthetic data and mock HTTP; they do not load real tokens or mutate
the development DB. No mutation-capable integration suite was run in this
review-only phase. Historical integration results were not represented as rerun.
`git diff --check` passed. Changes are limited to two test files, this report and
the operations index; no production code, migration, environment, universe,
configuration or live limits changed. No commit or push was performed.

## Required resolution before sizing

The next factual evidence must be an authoritative current collateral statement
or list snapshot matched privately to pledged securities, with approved/additional
and cash-equivalent categories plus adjusted contributions, and explicit broker
clarification of the positive-cash gate's API field/ledger mapping for NSE equity
MIS. The matching available/utilised collateral values also need explanation.
An official list match alone would not resolve debit/cash eligibility or supply
an atomic adjusted-account snapshot.

No support message was sent, no ledger export taken, no scraping performed and
no arbitrary tolerance/haircut chosen. The report identifies concrete missing
facts rather than declaring the account accepted or rejected by broker RMS.

Only after those questions are resolved, or authoritative cash-only evidence
makes collateral unnecessary while platform policy also passes, may a separate
review reach READY_FOR_CANDIDATE_SIZING. INR 10000 remains full buffered market
notional; required margin is only an additional gate. Future price, margin,
collateral, portfolio, open orders, authentication and market state must still
be revalidated at the existing execution boundaries. This review reserves none
of them and grants no live authorization.
