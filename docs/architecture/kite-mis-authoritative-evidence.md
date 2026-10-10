# NSE MIS authoritative evidence review, Phase 13.3

Review date: **2026-10-10**. Outcome: **BROKER_TERMS_UNRESOLVED** for the four
current account/request questions. Public-source research improved the evidence
map but did not establish current eligibility. No account was accessed, no
instrument/quantity selected, and no support reply received. Collateral-assisted
MIS remains **NOT_READY**. REAL_ACCOUNT_VERIFICATION_NOT_PERFORMED.

## Source ledger

All sources were accessed on the review date. Unless a date is explicitly given
below, publication, last update and effective date were not stated in retrieved
article content. Copyright/crawl dates are not effective dates. These are public
policies/schema descriptions, not authenticated current-account attestations.
Only article bodies, not user comments, support this review.

| ID | Official title/source | Scope and supported conclusion |
| --- | --- | --- |
| S1 | [User: Funds and margins](https://kite.trade/docs/connect/v3/user/#funds-and-margins) | Equity/commodity API schema, including segment GET. `available.collateral` describes pledged-stock margin; `utilised.stock_collateral` and `utilised.liquid_collateral` describe usage. No documented field here certifies the exact free eligible NSE MIS portion or qualifying cash computation. |
| S2 | [Margin calculation](https://kite.trade/docs/connect/v3/margins/) | `/margins/orders` calculates request margin/charges considering positions and open orders. It does not certify the four terms, reserve capacity or establish an account-specific eligibility lifetime. |
| S3 | [Terms of Service for pledging](https://support.zerodha.com/category/console/portfolio/pledging/articles/pledging-terms-of-services) | General positive-cash prerequisite; its ratio paragraph specifically addresses F&O. Previous closing values and haircuts contribute to collateral. An explicit 2026-04-01 date applies to an F&O fee change, not to an NSE MIS field mapping or the entire article. |
| S4 | [Approved list of securities](https://zerodha.com/approved-securities) | Broad cash/noncash category conditions, cash-equivalent coverage without separate cash, noncash 50% wording, minimum haircut conditions and member limits. Table says daily updates; retrieved dynamic rows were unavailable. No individual security/category/haircut certified. |
| S5 | [Which stocks and mutual funds can be pledged with Zerodha for collateral margins?](https://support.zerodha.com/category/console/portfolio/pledging/articles/list-of-stocks-pledge-zerodha) | Repeats broad cash-equivalent/full-margin versus noncash/50% category wording. It does not identify the qualifying Kite JSON field or current account composition. |
| S6 | [What does the Collateral (liquid funds) under Funds mean?](https://support.zerodha.com/category/trading-and-markets/general-kite/funds/articles/what-does-liquidbees-collateral-margin-in-the-funds-mean) | Haircut-adjusted liquid-fund/ETF pledge margin is cash-equivalent; its ratio discussion explicitly concerns F&O. UI category wording is not proof that a utilised API field is free cash. |
| S7 | [How are the values on the Kite dashboard and funds page calculated?](https://support.zerodha.com/category/trading-and-markets/general-kite/funds/articles/kite-dashboard-and-fund-values-calculation) | UI available cash is described as current cash; used margin includes open-order blocks; collateral totals combine categories. No exact UI-to-API mapping is supplied. |
| S8 | [What is available cash and opening balance on Kite?](https://support.zerodha.com/category/trading-and-markets/trading-faqs/general/articles/what-is-available-cash-and-opening-balance-on-kite) | UI available cash can include unsettled funds and pay-ins; opening is day-start cash after reversing prior blocks. These descriptions do not establish eligible cash after every pending commitment. |
| S9 | [What does Available margin, Used margin and Available cash mean?](https://support.zerodha.com/category/trading-and-markets/general-kite/funds/articles/what-does-margin-available-and-margin-used-mean) | Describes UI available cash as carried-forward prior-day closing balance. This differs from S7/S8's current/mixed description; no article effective date resolves the naming conflict. |
| S10 | [What does "free cash" mean?](https://support.zerodha.com/category/trading-and-markets/margins/margin-leverage-and-product-and-order-types/articles/free-cash-meaning) | Its broad trading-capacity calculation includes collateral, pay-in and usage; therefore the UI phrase cannot be used as evidence of the application's cash-only reserve. |
| S11 | [Clarification on additional brokerage for F&O trades](https://zerodha.com/z-connect/general/clatification-on-additional-brokerage-fno-trades) | Published 2026-03-27; described fee change effective 2026-04-01. F&O ratio/shortfall discussion refers to UI Available Cash. It does not supply an NSE MIS API-field mapping. |
| S12 | [Regulatory changes from 2nd May: Segregation of client collateral](https://zerodha.com/z-connect/general/regulatory-changes-from-2nd-may-segregation-of-client-collateral) | Published 2022-05-04, describes implementation from 2022-05-02. Historical segment/client allocation context; its ratio example is F&O. Not current broker-account permission. |
| S13 | [SEBI: Segregation and Monitoring of Collateral at Client Level](https://www.sebi.gov.in/sebi_data/attachdocs/jul-2021/1626778249298.pdf) | Circular SEBI/HO/MRD2_DCAP/CIR/2021/0598, dated 2021-07-20, linked by S12. Paragraphs 4-7 concern segmented client reporting/allocation; paragraph 18 distinguishes clearing-member cash-equivalent requirements from individual allocation. Historical primary context, not a verified current NSE MIS exemption or Kite field definition. Current amendments/applicability require broker confirmation. |
| S14 | [Introducing instant pledging](https://zerodha.com/z-connect/kite/introducing-instant-pledging) | Published 2026-01-05. Distinguishes pending/successful pledges and credit processing. Useful lifecycle context, not proof of current unencumbered capacity. No pledge action performed. |
| S15 | [How many securities are available for pledging?](https://support.zerodha.com/category/console/portfolio/pledging/articles/securities-available-for-pledging) | Approved versus additional securities have different usage priority and cash/liquid conditions. Account composition is unknown. |
| S16 | [Why is the error "Your account has a negative cash balance" displayed when I place an order?](https://support.zerodha.com/category/trading-and-markets/alerts-and-nudges/kite-error-messages/articles/error-message-negative-cash) | Explicitly blocks new equity intraday and F&O positions when cash is negative. This supports a general denial condition, but neither identifies its Kite JSON cash field nor resolves cash-equivalent category precedence. |

The regulatory source was read as background for the linked broker explanation;
no complete current regulatory-consolidation or exemption determination is
claimed. Public searches of official Zerodha support, Z-Connect and bulletins
did not establish the missing exact NSE MIS cash-field mapping. This is a
bounded investigation, not proof that no authoritative source could exist.
Developer-forum search results containing other users' account examples were
not adopted as current broker policy, copied into fixtures or retained here.

## Four independently unresolved questions

Common target scope: the existing supported NSE cash-equity BUY / MARKET / MIS /
DAY / REGULAR request shape. No actual account, symbol/token, quantity or request
is bound. EffectiveAt and expiresAt are **absent**, not the review date. No
current account observation time/fingerprint exists. Public review fingerprints
below identify this assessment only. Real evidence collected: **NO** for every row.

| ID / question | Status | Source support and missing evidence | Freshness / validity |
| --- | --- | --- | --- |
| Q1 Eligible adjusted collateral | UNKNOWN | S1/S3-S6/S15: category/haircut principles only. Missing authenticated account/product eligible aggregate, actual pledge state, restrictions and adjusted contribution. Holdings value/list membership cannot fill this gap. | No account observation or valid-through assertion; cannot be used |
| Q2 Actually available collateral | UNKNOWN | S1/S7/S12/S13: usage/allocation concepts. Missing free eligible amount after pending orders, positions, broker reservations and encumbrances, with authoritative double-counting semantics. Neither total collateral nor utilised fields supplies it. | No consistent current snapshot, sequence or reservation lifetime |
| Q3 Applicable cash-component rule | CONFLICTING | S3 positive-cash prerequisite versus S4/S5 broad cash-equivalent coverage without separate cash; F&O-specific S6/S11 and historical S13 do not resolve the exact NSE MIS category/account rule. Missing precedence, scope and effective-date clarification. | Undated policy conflict; no inferred ratio or exemption is valid |
| Q4 Qualifying cash-field mapping | CONFLICTING | S7/S8 current/mixed UI cash versus S9 prior-closing description; S1 distinguishes raw cash/opening/live fields but does not bind one to the prerequisite. Exact API computation remains UNKNOWN. Missing exclusions, pending commitments and broker-confirmed mapping. | No scoped mapping or expiry; naming similarity is not proof |

CONFLICTING records an unresolved source/applicability conflict; it is not an
assertion that the customer's balances contradict each other. No balances were
read. No question is PROVEN. General policy facts are supported separately and
cannot convert this outcome to BROKER_TERMS_RESOLVED or trading readiness.

## Reproducible public-review fingerprints

Hash the UTF-8 bytes of each descriptor below, without a trailing newline, using
SHA-256. These are assessment identifiers, **not hashes of fetched page bytes,
account evidence, credentials or token rows**. Source IDs refer to the ledger
above. Versioned Git review preserves the human source/claim interpretation.

```text
phase13.3|Q1|UNKNOWN|S1,S3,S4,S5,S6,S15|2026-10-10|NSE-MIS|ACCOUNT-NONE|REQUEST-NONE|EFFECTIVE-UNKNOWN|EXPIRY-UNKNOWN
phase13.3|Q2|UNKNOWN|S1,S7,S12,S13|2026-10-10|NSE-MIS|ACCOUNT-NONE|REQUEST-NONE|EFFECTIVE-UNKNOWN|EXPIRY-UNKNOWN
phase13.3|Q3|CONFLICTING|S3,S4,S5,S6,S11,S13|2026-10-10|NSE-MIS|ACCOUNT-NONE|REQUEST-NONE|EFFECTIVE-UNKNOWN|EXPIRY-UNKNOWN
phase13.3|Q4|CONFLICTING|S1,S7,S8,S9|2026-10-10|NSE-MIS|ACCOUNT-NONE|REQUEST-NONE|EFFECTIVE-UNKNOWN|EXPIRY-UNKNOWN
```

Fingerprint values are recorded in the validation report with the calculation
command. No invented account/request hash substitutes for absent evidence.

## Existing contract and safe use

Reuse `KiteMisCollateralEvidence.v1` unchanged. It projects normalized input
fields into OBSERVED/DERIVED/UNKNOWN with independent authority UNKNOWN; its
SYNTHETIC and CALLER_SUPPLIED_NORMALIZED origins are both **UNVERIFIED** provenance.
An official schema ID names a schema, not an authenticated collection event.
Authenticated account responses, if later authorized, would still be observations
whose free/eligible meaning needs independent scope. A support statement would
need authenticated provenance, explicit account/product/date scope, exact
request applicability and validity before any future trusted provider review.

The public review's source conflicts are not injected into runtime records:
V1 has no public-policy ingestion or account attestation provider. Its STALE and
CONFLICTING values describe supplied observation context. Valid synthetic inputs
still produce four UNKNOWN terms. This separate document reports policy/source
conflicts without creating a second risk engine or changing production behavior.

The new six-case regression varies candidate adjusted amounts and minimum cash
claims, including zero and changed values. All remain unverified; a one-paisa
cash shortfall cannot become approval. There is no authenticated category or
haircut field to test as accepted: absence is the fail-closed boundary, not a
simulated broker certification. Existing suites cover account cash conflicts,
utilisation, exact quote binding, freshness, auth, final refresh and CNC behavior.

Do not derive available cash from net, subtract utilised collateral from a total
as a broker-certified formula, add commodity credits, or infer a category from
holdings labels. S1's several balances remain observations. The application's
cash floor and exclusions are conservative policy, not broker RMS reconstruction.
Cash-only reserve, separate margin/charges, INR 10,000 full buffered-notional
ceiling, HALT and permits remain unchanged. No public endpoint or harness added.

Next: review the [unsent questions](../operations/phase-13.3-broker-clarification.md).
The [one-GET proposal](../operations/phase-13.3-real-read-proposal.md) remains DENY
without explicit approval and all no-write harness checks. Even success would
not close the four terms automatically. See [validation](../operations/phase-13.3-validation.md).
