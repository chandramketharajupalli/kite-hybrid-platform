# Read-only equity-margin observations — Phase 13.4

Review/access date: 2026-10-10. This is an engineering observation contract, not
broker funding approval. No account evidence was collected. The existing
`KiteMisCollateralEvidence.v1` contract, risk engine and funding policy remain unchanged.

## Official sources and limits

| Source | Verified support and limitation |
| --- | --- |
| [Kite User: Funds and margins](https://kite.trade/docs/connect/v3/user/#funds-and-margins) | GET `/user/margins/:segment`, with `equity` or `commodity`; version 3 and token authorization headers. The illustrated success/data body contains both segments. The single-segment direct object used here is an explicit adapter assumption, not an observed account response. |
| [Kite Margin calculation](https://kite.trade/docs/connect/v3/margins/) | Calculation-only POST `/margins/orders` is separate. Its estimate does not attest to free eligible collateral, the qualifying cash field, or reserve capacity. This phase performs no real calculation. |
| [Kite Exceptions and errors](https://kite.trade/docs/connect/v3/exceptions/#api-rate-limit) | HTTP/authentication errors and 429 rate limiting; the documented other-endpoints limit is 10 requests/second. Our proposed one-call total budget is stricter and has no retry. |
| [Zerodha Terms of Service for pledging](https://support.zerodha.com/category/console/portfolio/pledging/articles/pledging-terms-of-services) | Positive-cash prerequisite; ratio paragraph is scoped to F&O. The explicit 2026-04-01 date concerns an F&O fee change, not NSE MIS cash-field mapping. |
| [Approved securities](https://zerodha.com/approved-securities) | Category/haircut conditions and broad cash-equivalent versus noncash wording. Neither the list nor its category rules proves an account's currently free capacity. |

Publication/update dates for the API pages and overall policy articles were not
stated in retrieved content. Access date is not an effective date. Phase 13.3's
[source review](kite-mis-authoritative-evidence.md) retains the unresolved cash
rule and UI-field conflicts. No F&O ratio or category exemption is imported into
NSE cash MIS. No approved-security rows or account values are certified here.

## Field dictionary and normalization

The existing `BrokerMargins.SegmentMargin` is reused. `enabled` is boolean;
all numeric fields are exact `BigDecimal`. The API describes `net` as aggregate
margin, `available.cash` as raw cash including pay-ins, `opening_balance` as
day-start balance, `live_balance` as current cash, and `collateral` as pledged
margin. `utilised.stock_collateral` and `liquid_collateral` represent usage.
These schema descriptions do not establish an NSE MIS qualifying cash computation.

Adapter field inventory (all required by this strict application contract):

| Location | Fields retained internally |
| --- | --- |
| Root segment | `enabled`, `net` |
| `available` | `adhoc_margin`, `cash`, `opening_balance`, `live_balance`, `collateral`, `intraday_payin` |
| `utilised` | `debits`, `exposure`, `m2m_realised`, `m2m_unrealised`, `option_premium`, `payout`, `span`, `holding_sales`, `turnover`, `liquid_collateral`, `stock_collateral`, `delivery` |

Missing/null values fail rather than become zero. Wrong types, nonfinite/oversized
numbers, duplicate keys, trailing documents and invalid envelopes fail. Existing
numeric limits remain: precision at most 36, scale -18 through 18, absolute value
below 10^18. Signed numbers remain observations; they do not approve capacity.
Unknown fields are bounded and ignored, including caller claims of eligibility.
The single-segment entry point rejects equity/commodity/segments wrappers and
contradictory explicit segment labels. It never aggregates commodity credit or
changes the existing two-segment `/user/margins` mapper.

Neither holdings value nor total-minus-utilised arithmetic proves free collateral.
The application does not manufacture an absent eligibility/category/haircut field.

## Collection and evidence binding

`KiteEquityMarginReadAdapter` is package-local, unwired and disabled by default.
Explicit construction enables one GET per `read()` invocation; there is no
automatic polling, retry, startup hook, controller, configuration property or
Actuator endpoint. It reuses `KiteRestTransport`, adding only the equity GET enum.
Production transport retains fixed HTTPS origin, no redirects, 10s connect/30s
read bounds and 64 KiB wire/decoded limits. No order permission changes.

An internal snapshot contains normalized equity, local receipt time, private
session identity and reader ownership. It has no public serializer accessors
and a constant redacted `toString`. The funds schema provides no broker timestamp,
account identifier, atomicity or reservation lifetime; those are not invented.
Receipt time is not a guarantee that balances, positions and orders are atomic.
Session/reader binding prevents replay across readers or replaced sessions; it
does not independently establish the customer's broker account identity.

Injected transport is `SYNTHETIC_TRANSPORT`. Only the fixed official transport
factory can mark a successfully received snapshot `BROKER_AUTHENTICATED_OBSERVATION`.
That is collection provenance, never independent funding authority. In this
phase only synthetic snapshots exist. Caller-normalized V1 inputs stay
`CALLER_SUPPLIED_NORMALIZED`/unverified; no source-catalog ID can manufacture a
collection event. The adapter receipt distinguishes authenticated collection;
the V1 normalized projection remains unverified for backward compatibility.
There is no runtime wiring joining these objects to funding approval.

`IntradayFundingEvidence.equityObservation` performs no HTTP, persistence or cash
arithmetic. The adapter only exposes a redacted receipt through `observation`
after validating snapshot/session/reader ownership. The pure projection accepts
caller-normalized receipt context, never an authority claim, and reuses V1
request/reference fingerprints, quote and receipt freshness, and conflict checks;
only equity field classifications become OBSERVED with independent authority
UNKNOWN. The snapshot cannot be externally constructed. Raw balances, session
IDs, credentials and symbol/token values are excluded from the diagnostic.
There is no independent attestation provider or PROVEN path.

| Question | Public evidence assessment | Runtime consequence |
| --- | --- | --- |
| Eligible adjusted collateral | UNKNOWN: no authenticated eligible aggregate/category/haircut scope | UNKNOWN; no capacity credit |
| Actually available collateral | UNKNOWN: no free-after-commitments field or broker-confirmed computation | UNKNOWN; no subtraction inference |
| Applicable cash-component rule | CONFLICTING public scope/category wording; exact account/request rule unknown | UNKNOWN unless supplied context is stale/conflicting; never PROVEN |
| Qualifying cash field | CONFLICTING public UI descriptions; exact API mapping unknown | UNKNOWN; no largest-positive-field selection |

All rows lack account attestation, effective date, expiry and real-evidence
fingerprint. Existing V1 fingerprints identify synthetic request/reference
context only, not broker truth. Collateral-assisted readiness stays NOT_READY.
A single equity read lacks positions/orders and full account capacity, so this
projection reports `MIS_MARGIN_UNAVAILABLE` for cash-only funding. Existing
complete cash-only arithmetic (margin + charges + cash reserve) remains unchanged.
INR 10,000 full buffered notional, CNC, final refresh, permits and HALT are untouched.

## Failures and no-write boundary

Disabled or mismatched transport/session construction: CONFIGURATION. Missing,
expired or unvalidated session and changed snapshot binding: AUTHENTICATION,
before HTTP/reuse. HTTP 401/403 or a strict valid TokenException clears only
in-memory authentication through existing transport behavior. 429/5xx:
BROKER_API. Timeout/connectivity: TRANSPORT. Invalid/oversized content:
INVALID_RESPONSE. No retry or `/orders` fallback; upstream text/causes are discarded.

The adapter has no token-store, SQL, operator or execution dependency. Disposable
integration loads a seeded synthetic token using SELECT-only credentials with
read-only transactions, then verifies all trading-table content/counts and token
bytes unchanged. Seed/migration writes precede observation and affect only the
disposable container. No normal application startup is used by this new test.

See the [runbook](../operations/phase-13.4-runbook.md),
[unapproved proposal](../operations/phase-13.4-real-read-proposal.md),
and [validation](../operations/phase-13.4-validation.md).

## Registered-plan implementation correction

The first full unit run found that the planned in-adapter reconciliation violated
an existing architecture rule prohibiting Kite infrastructure dependencies on
risk. The guard was preserved. Reconciliation moved to the existing pure
`IntradayFundingEvidence` class as additive `equityObservation`; the broker
adapter returns only redacted collection metadata. This adds that existing
production file to the registered inventory without changing acceptance criteria,
funding approval or any existing method. The frozen plan itself is unchanged.
