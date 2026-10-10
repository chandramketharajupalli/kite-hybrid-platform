# Kite MIS funding evidence and diagnostic contract

Phase 13.1 extends `IntradayFundingEvidence` without changing risk, funding,
planner, preflight, execution or authentication behavior. It is a Java-only,
stateless diagnostic. No Spring bean, HTTP endpoint, account fetch, cache,
database access or execution capability is added. Real evidence was not
collected: **REAL_EVIDENCE_NOT_COLLECTED**. Collateral-assisted readiness remains
**NOT_READY**.

## Official evidence review

Primary sources accessed **2026-10-10**. Public documentation requests only;
no authenticated Kite request, security-master download or corpus acquisition.
These facts describe broker interfaces, not this account's current eligibility.

| Official source | Meaning and limit relevant to this phase |
| --- | --- |
| [Kite v3 funds/margins](https://kite.trade/docs/connect/v3/user/#funds-and-margins) | `/user/margins` separates equity and commodity. `available.cash` includes intraday pay-in; `opening_balance` is the day-start balance; `live_balance` is current balance. `intraday_payin` is today's deposit and `adhoc_margin` is broker credit. `available.collateral` is pledged-stock margin. `net` includes mixed credits/collateral; it is not an independent free-cash pool. `utilised.stock_collateral` and `utilised.liquid_collateral` report used margin, not available funds. `debits` aggregates usage; `payout` is withdrawals; `holding_sales` is sold holdings' value. SPAN/exposure are derivative blocks and delivery is a sale-related block. These definitions do not establish a current eligible-free-collateral amount or an exact NSE MIS cash-field eligibility mapping. |
| [Kite v3 calculation](https://kite.trade/docs/connect/v3/margins/) | JSON `POST /margins/orders` calculates per-order margin considering positions/open orders. Inputs include exchange, symbol, side, variety, product, type, quantity, price and trigger price. Response `total` is margin block; `charges.total` is charges; `cash` denotes cash credit. The documented response does not echo every request field or provide a broker timestamp, cash-component rule, account eligibility attestation or reservation. The adapter binds its returned quote to the submitted request locally. DAY validity is enforced locally; the documented calculator request has no validity field. |
| [Collateral in Funds](https://support.zerodha.com/category/trading-and-markets/general-kite/funds/articles/what-does-collateral-margin-in-the-funds-mean) | Collateral equity is pledged-security margin after haircuts; equity intraday is a supported use. This establishes general product support, not current spendability of an account API aggregate. |
| [Pledging guide](https://support.zerodha.com/category/console/portfolio/pledging/articles/what-is-pledging) | Collateral valuation uses previous closing prices after haircut. The cash/equivalent 50% discussion is explicitly F&O; it must not become a universal cash-equity MIS rule. The guide also describes a prolonged debit restriction. It supplies no current account/category allocation. |
| [Pledge categories](https://support.zerodha.com/category/console/portfolio/pledging/articles/securities-available-for-pledging) | Approved collateral is used before additional-security collateral. Additional-security usage has interest and a 50% cash/liquid-collateral requirement. Category-specific guidance cannot be applied to an unknown aggregate composition. |
| [Pledging terms](https://support.zerodha.com/category/console/portfolio/pledging/articles/pledging-terms-of-services) | Positive cash is a prerequisite for use of pledged margin; the 50% paragraph concerns F&O. This is a known general constraint, but does not identify which API balance proves positive cash for the current NSE MIS request. |
| [Negative-cash rejection guidance](https://support.zerodha.com/category/trading-and-markets/alerts-and-nudges/kite-error-messages/articles/error-message-negative-cash) | Relevant broker error guidance for investigating cash eligibility; do not use a previous rejection or success as a substitute for current authoritative terms. |
| [Approved securities](https://zerodha.com/approved-securities) | Public eligibility/haircut reference, not proof of a particular account's pledged quantities, adjustments, availability or debit restrictions. No holdings were matched and no account was accessed. |

The inferred application conclusion from these sources is narrow: the established
Kite adapter cannot populate complete collateral terms. Unknowns must remain
unknown. Historical manual pledged-margin use and earlier phase snapshots do
not close any current evidence gap. No universal MIS cash ratio, account haircut
or cash-field interpretation was invented.

## Unchanged application policy

The authoritative application equations remain in
[intraday-first funding contract](intraday-first-funding-contract.md),
`CashAccountCapacity` and `IntradayAccountCapacity`. `detail` calls the original
`inspect`, which deliberately strips optional numeric collateral terms before
evaluating the existing cash-only branch. No duplicate funding formula exists.

Cash must cover the full required margin, estimated charges and positive
cash-only reserve in that branch. Equality passes; one paisa less fails. Net can
only tighten capacity. Commodity balances, utilised collateral, pay-in/credit
assumptions and stale amounts cannot add accepted cash. Full buffered notional,
position and exposure caps remain independent of required margin. CNC is intact.

The summary takes a reviewed full-notional ceiling and refuses values above
INR 10,000. This is only a diagnostic guard on the supplied review budget, not
a production configuration change. The shared valuation checks the complete
quantity at buffered market price, risk max-order value and account exposure.
This phase calculates no real executable SBIN quantity.

## Additive API and evidence taxonomy

The original `View(Readiness, RiskReason, Map)` and `inspect(...)` signatures
and results are preserved. New APIs are `detail(...)` and
`summarizeSynthetic(...)`; nested immutable records contain only bounded enums,
times, identity checksum and redacted metadata. No raw balance, account ID,
broker order ID, symbol, quantity, credential, header, cookie or session ID is
returned or logged.

| Label | Provenance | Interpretation |
| --- | --- | --- |
| OBSERVED | ACCOUNT_FIELDS | Current normalized equity cash/net/available/utilised fields, without amounts |
| DERIVED | APPLICATION_POLICY | Existing conservative cash lower bound |
| BROKER_AUTHORITATIVE | EXACT_REQUEST_CALCULATION | Margin/charges under the established calculator contract, not eligibility or authority to execute |
| UNKNOWN | UNESTABLISHED | No established eligible adjusted amount, actually available collateral, cash component or cash-field mapping |

`Origin.SYNTHETIC` explicitly identifies fixtures. `CALLER_SUPPLIED_NORMALIZED`
means the caller supplied normalized observations; it is not a cryptographic
attestation or evidence that real reads occurred. A caller cannot certify
collateral by setting origin or supplying `CollateralTerms`. Synthetic
BROKER_AUTHORITATIVE labels test the provider contract only.

`accountSegment=EQUITY` identifies the required segment. Account and quote times
are local observation/receipt times, never broker timestamps or atomic-snapshot
claims. Freshness is CURRENT only for nonfuture observations strictly younger
than configured max age; equality with the maximum is STALE. Missing clock/time
or nonpositive duration is UNKNOWN. Future times are explicit FUTURE.

Request binding compares the complete existing `Request`, including platform
instrument identity, exchange, symbol, side, type, product, validity, variety and
quantity. Mismatch is CONFLICTING. A versioned length-prefixed encoding of the
expected request yields its SHA-256 checksum. The checksum supports exact
identity comparisons, contains no account/session data and is not an anonymizer
for low-entropy order intent. Keep this diagnostic private. No request or quote
is retained between calls.

## Deterministic synthetic preflight summary

Each check is PASS, BLOCKED or UNKNOWN, with stable enum ordering:

* Authentication and account observation freshness are separate. Current means
  timestamp/auth checks passed, not that every account field is valid.
* Orders are clear only for normalized FILLED, CANCELLED or REJECTED. OPEN,
  partial, pending and UNKNOWN block. COMPLETE must first normalize to FILLED.
* Positions in either net/day array must be zero for first-review cleanliness.
  Holdings still enter the shared exposure calculation; this diagnostic does
  not impose a new generic execution policy on holdings or positions.
* Market health and tick freshness, exact registry identity/lot and quote
  binding/freshness have distinct checks. Market prices use existing
  `CashOrderRiskRules.priceReason`; its boundary semantics are unchanged.
* Cash-only policy sufficiency and collateral eligibility are separate.
  Uncomputable funding remains UNKNOWN. Collateral eligibility stays UNKNOWN.
* Full notional uses `ConservativeOrderValuation` and shared exposure checks.
  Uncomputed checks remain UNKNOWN; invalid review ceilings are BLOCKED.
* HALT observation is separate; authorization is always BLOCKED. Overall
  readiness is always NOT_READY, including when all arithmetic checks pass.

The summary consumes no permit, creates no risk decision and cannot dispatch.
It is not wired into production execution or exposed over HTTP. A previous
summary is never reusable as a final execution check.

Existing production final refresh stays in `CurrentAccountExecutionChecks`
and `ExecutionSafetyPolicy` through the transport callback. The extended
disposable rehearsal verifies increased fees, stale/future quotes and auth loss
alongside cash drop, margin rise, changed price, orders/positions, session,
HALT and competing instances. Denials precede fake order HTTP mutation.
Existing response-loss reconciliation retains exact identity checks.
Sequential account reads still cannot reserve funds or remove the race after
the final observation. Diagnostic purity does not claim to solve that race.

## Boundaries

Architecture guards prohibit diagnostic dependencies on application services,
infrastructure, network, SQL/I/O, execution gateway, operator, HALT controller,
order/risk stores, risk decisions and permits. Calculator tests assert only
`POST /margins/orders`, with no order mutation routes and no retries.
Authentication/REST/WebSocket behavior from Phase 13.0 is unchanged. Passive
`/api/broker/kite/auth/status` preserves durable tokens; deliberate lifecycle
cleanup is not invoked by diagnostics. Actuator exposure and localhost/default
guards are unchanged. Historical continuity remains mandatory and Phase 12
acquisition remains BLOCK. No Python broker execution path was added.
