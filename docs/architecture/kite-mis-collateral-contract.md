# Versioned Kite MIS collateral evidence contract

`KiteMisCollateralEvidence.v1` is a redacted diagnostic, not a broker-verified
funding approval. The established provider does not supply the four required
account-specific terms. All remain UNKNOWN on valid current inputs, with
`collateralAssistedReadiness=NOT_READY`. No real account evidence was collected.

## Sources and scope

All sources below were accessed **2026-10-10** through public documentation.
No page displayed a reliable publication/update date in the retrieved content;
update date is **not stated**, not the crawl/access date. The approved list says
its table updates daily; no dated security rows were retrieved by this review.

| ID / title and URL | Scope / fact / limitation |
| --- | --- |
| KITE_V3_FUNDS_SCHEMA — [User: Funds and margins](https://kite.trade/docs/connect/v3/user/#funds-and-margins) | API field semantics for equity and commodity; dictionary below. This is not an exact-account eligibility assertion. |
| KITE_V3_ORDER_MARGIN_SCHEMA — [Margin calculation](https://kite.trade/docs/connect/v3/margins/) | JSON calculation-only POST `/margins/orders`; margin and charges consider existing positions/open orders. No final cash/collateral eligibility attestation, reservation, broker timestamp, instrument-token echo or complete request echo. DAY is enforced by local Request; the documented calculator input does not include validity. |
| ZERODHA_PLEDGING_TERMS — [Terms of Service for pledging](https://support.zerodha.com/category/console/portfolio/pledging/articles/pledging-terms-of-services) | Pledged use has a positive-cash prerequisite. Its 50% cash/equivalent paragraph explicitly concerns F&O. It does not identify an API field proving positive cash for NSE MIS. |
| ZERODHA_COLLATERAL_USE — [Does Zerodha provide margin on holdings, and what can I use the collateral margin for?](https://support.zerodha.com/category/console/portfolio/pledging/articles/will-zerodha-give-me-margin-on-the-shares-i-hold-and-what-can-i-use-my-collateral-margin-for) | Equity intraday is supported. The page also describes intraday realised obligations and member pledge limits. General support does not establish this account's remaining eligible capacity. Commodity/single-ledger discussion does not expand application NSE funding policy. |
| ZERODHA_APPROVED_LIST — [Approved list of securities](https://zerodha.com/approved-securities) | Daily category/haircut reference. Notes distinguish cash-component collateral from noncash collateral and describe 50% usage/cash-equivalent conditions. The wording is broader than the F&O terms paragraph; exact application to this NSE MIS case needs clarification. Dynamic security rows were not available in retrieved text; no haircut/category was certified. List membership does not prove free account capacity. |
| ZERODHA_PLEDGE_CATEGORIES — [How many securities are available for pledging?](https://support.zerodha.com/category/console/portfolio/pledging/articles/securities-available-for-pledging) | Approved collateral has priority over additional securities. Additional-security usage has interest and a cash/liquid-component condition. Unknown composition cannot establish which rule applies. |

The apparent difference between positive cash, cash-equivalent coverage and
category-specific ratio language is an **unresolved applicability question**,
not permission to choose the most generous interpretation. No universal NSE MIS
ratio or qualifying cash field is inferred. Positive cash is a documented
general prerequisite; complete account/request compliance is still UNKNOWN.

## Field dictionary: API semantics vs application policy

Actual Kite success shape is `data.equity` and `data.commodity`, each with
`enabled`, `net`, `available` and `utilised`. The Java `BrokerMargins.segments`
map is the normalized form, not a claimed upstream `segments` JSON wrapper.
Both normalized segments must be present; only enabled equity funds are used
for NSE. Values are exact bounded BigDecimal, not binary-float arithmetic.

The broker meanings in this table derive from KITE_V3_FUNDS_SCHEMA:

| API path under segment | Broker field meaning | Unchanged conservative application treatment |
| --- | --- | --- |
| `enabled` | Segment enabled | Disabled/missing equity denies |
| `net` | Mixed net trading balance including credits/collateral | Tightening bound only; never independent cash/collateral |
| `available.cash` | Raw cash including intraday pay-in | One member of conservative cash floor |
| `available.opening_balance` | Day-start balance | Floor, not current eligibility proof |
| `available.live_balance` | Current balance | Cannot override a lower floor |
| `available.intraday_payin` | Today's deposits | Excluded from MIS accepted lower bound |
| `available.adhoc_margin` | Broker extra margin | Excluded credit |
| `available.collateral` | Pledged-stock margin | Observed aggregate; no eligibility inference |
| `utilised.stock_collateral` | Used pledged stock/ETF margin | Never free capacity |
| `utilised.liquid_collateral` | Used pledged liquid ETF/fund margin | Never free cash equivalents |
| `utilised.debits` | Aggregate margin usage | Positive usage deducted conservatively |
| `utilised.payout` | Withdrawals | Deducted |
| `utilised.holding_sales` | Sold holdings value | Deducted, not settled cash proof |
| `utilised.m2m_realised`, `m2m_unrealised` | Booked/unbooked intraday P&L | Positive credits excluded |
| `utilised.option_premium` | Premium received from shorts | Absolute component excluded |
| `utilised.span`, `exposure` | Derivative margin blocks | Not NSE funding additions |
| `utilised.delivery` | Sale-related delivery block | Not additional funding |
| `utilised.turnover` | Used turnover allowance | Not additional funding |

Application treatment is deliberately conservative and may contain overlapping
deductions; it is not a reconstruction of broker RMS. Source definitions alone
do not specify available eligible post-encumbrance amounts. Holdings pledge
labels/quantities, price multiplication and list matches cannot supply them.
Unknown is not a numerical zero. No amount is put in this diagnostic contract.

The existing `CashAccountCapacity` and `IntradayAccountCapacity` retain the
cash floor, excluded credits, net bound and optional-terms rules documented in
[intraday-first funding contract](intraday-first-funding-contract.md). Reserve
remains cash-only, charges remain separate, and full buffered-notional sizing
remains capped independently. CNC/risk configuration is unchanged.

## Authority and versioning

`IntradayFundingEvidence.collateralContract(...)` is additive. `inspect`,
`detail`, `View`, `Summary` and synthetic preflight remain compatible. The old
BROKER_AUTHORITATIVE quote label means the calculator's expected contract; it
does not authenticate caller-supplied values. V1 explicitly projects that label
to OBSERVED with `independentAuthority=UNKNOWN`. Derived application cash remains
DERIVED. Missing fields remain UNKNOWN. Source identifiers name schemas/policy,
not a network request or collection event. Origin is SYNTHETIC or
CALLER_SUPPLIED_NORMALIZED, never a trust switch.

No factory path produces PROVEN or an independently broker-authoritative field.
The enum reserves PROVEN for a separately reviewed future evidence provider.
Numeric `CollateralTerms`, authentication booleans, approved-list references,
successful estimates and metadata supplied by callers cannot create that proof.
Public output-record construction is not authentication; no risk or execution
consumer uses these records. No provider/authority toggle is added.

Four questions are always explicitly represented:

| Question | Required broker authority still missing |
| --- | --- |
| ELIGIBLE_ADJUSTED_COLLATERAL | Current eligible haircut-adjusted amount for account/product, with category/restriction applicability |
| ACTUALLY_AVAILABLE_COLLATERAL | Current unencumbered amount and utilisation semantics without double counting |
| CASH_COMPONENT_REQUIREMENT | Complete positive-cash/ratio/category/debit rule for this exact NSE MIS request |
| CASH_FIELD_ELIGIBILITY | Exact API/ledger field and exclusions that establish compliance |

Each term contains status, bounded denial, `NO_ACCOUNT_ATTESTATION` source and
null effectiveAt. No broker effective date is fabricated. Scope records local
account/quote/reference times separately. STALE and CONFLICTING describe
unusable **input context**, not a claim that the missing authority was once
proven. Current inputs still yield UNKNOWN. Future timestamps are CONFLICTING;
missing/auth-unavailable input is UNKNOWN with a denial. Mismatch/future has
priority over auth loss, then stale, unavailable, and missing authority. All
underlying freshness/binding states remain visible for multi-fault diagnosis.

## Exact scope and limits

Scope contains EQUITY, INTRADAY, the full expected-request checksum and a
separate checksum including the ZERODHA instrument-token mapping. The existing
request binds platform identity, NSE symbol, side, quantity, type, validity and
variety. Reference identity/exchange/symbol/type/namespace must match, and its
local timestamp must be current. A changed broker token changes the mapping
checksum even when the platform identity is stable. Raw symbol, instrument
token and quantity are not returned. Checksums are not secret anonymization.

The calculator does not accept/echo an instrument token. This local scope
checksum does not claim that the broker authenticated that mapping. Reference
origin is caller supplied and cannot substitute for future independently
verified instrument/account provenance. Missing/stale/conflicting reference
also makes the new contract's cash diagnostic unavailable; original cash policy
and original APIs are unchanged. Freshness boundaries reuse existing durations.

No account identifier is accepted or emitted by V1. Consequently V1 cannot
prove account-specific binding at all. A future trusted provider must establish
that privately, along with effective time, validity window, source integrity
and the four complete terms; it cannot be implemented by promoting a caller
flag. All maps are immutable and serialization has deterministic ordering.

## Safety and reproducible validation

No fetch, cache, repository, HTTP endpoint, Spring bean, logger or permit is
introduced. Existing architecture guards cover all nested diagnostic types.
Final dispatch still uses the established current-account and safety callbacks,
not this contract. Existing synthetic tests cover final deterioration,
contention, response loss, terminal normalization and token-preserving status.
The halted disposable diagnostic test also invokes V1 and checks unchanged
operator/permit state, zero gateway calls and zero order/authorization rows.

See [plan](../operations/phase-13.2-plan.md),
[validation](../operations/phase-13.2-validation.md),
[runbook](../operations/phase-13.2-runbook.md) and the unsent
[broker questions](../operations/phase-13.2-broker-clarification.md).
The [real-read proposal](../operations/phase-13.2-real-read-proposal.md) is
separate and unapproved by default. Even successful aggregate reads cannot
replace missing broker policy/field authority. Phase 12 remains BLOCK; frozen
strategies and sealed July evidence are not evaluated. No live promotion.
