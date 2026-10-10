# Kite Connect runtime hardening

Phase 13.0 is synthetic read-path validation, not live/paper readiness. The
[plan](../operations/phase-13.0-plan.md) pins the scope; the
[validation](../operations/phase-13.0-validation.md) records executed results.
Java retains authentication, account normalization and all execution controls.
Python remains research/signal-only. No client, scheduler, endpoint, migration,
execution permit or strategy routing is introduced here.

## Authentication and storage ownership

`KiteAuthenticationController` owns `/api/broker/kite/auth`: GET `login`,
`callback`, `status`, and POST `reset`. Interactive login stays at Zerodha.
The application never automates password/MFA. The callback requires a matching
browser cookie and opaque state; durable attempts contain digests, expire in
10 minutes and are consumed atomically in PostgreSQL. Restart does not reset
the lifetime or allow replay. Cookie/CSRF and redirect validation are retained.

`PostgresKiteAccessTokenStore` encrypts with AES-256-GCM, random nonce and
store-identity AAD. The external encryption key is not stored in PostgreSQL.
Wrong keys/corrupt rows fail closed with bounded errors. `KiteSession` installs
tokens as UNVERIFIED; profile validation is required before authenticated
market-data/account use. Identity/generation changes fence old sessions and
WebSocket callbacks. Expiration removes in-memory credentials and readiness.

| Operation/failure | In-memory effect | Durable token effect |
| --- | --- | --- |
| Ordinary read: 401/403 | INVALIDATED | Transport has no store reference; passive status does not delete |
| Ordinary read: strict `status=error`, `error_type=TokenException` | INVALIDATED, including the retained 200/400/500 envelope cases | Same |
| 429/5xx without a valid token rejection, transport failure, bad JSON | Failing observation; existing session survives | None |
| Duplicate-key/trailing-document token envelope | Not trusted as auth rejection; read fails normalization | None |
| Historical auth rejection | Failed historical call; shared session/identity preserved | None |
| WebSocket auth handshake/message/close | Reject only the used generation; replacement session survives late failure | Transport has no store reference |
| Status GET after rejection | Reports unauthenticated/initialization unavailable | None |
| Status GET after token expiry | Clears expired in-memory state | None |
| Explicit restore/startup restore of expired/rejected credential | Unauthenticated; fail closed | Existing intentional cleanup remains |
| Transient profile failure during restore | Unauthenticated; initialization unavailable | Keep credential for later explicit retry |
| Explicit callback/reset | Existing validation/persistence/cleanup lifecycle | May save/clear; excluded from a read-only harness |

A read-only harness must not invoke authentication `restore`, `complete`,
`reset`, login-attempt mutation or application startup with real credentials.
Passive status no longer has the surprising durable cleanup side effect.
Preserving a durable row does not make an invalid session usable. The normal
interactive lifecycle retains cleanup on explicit lifecycle operations.

## REST and normalization

`KiteRestTransport` remains the single shared transport. Ordinary allowlisted
GETs are `/user/profile`, `/instruments`, `/orders`, `/trades`,
`/portfolio/positions`, `/portfolio/holdings`, `/user/margins`. `/orders` here
is an explicit account snapshot read, never an account/historical fallback.
Calculation-only POST `/margins/orders` cannot reach the order-mutation helper.
Historical GET is separately bounded to a validated uint32 token, minute route
and at most one day. There is no redirect or automatic REST retry.

Production connect/read timeouts remain 10/30 seconds. Response limits remain
64 KiB profile/margins/calculator, 4 MiB account lists, 32 MiB instruments,
1 MiB historical. Compressed and decompressed bodies are bounded; UTF-8 is
strict. Account, profile, margin and historical parsers reject duplicate keys
and trailing documents. Transport auth-envelope detection now does too and
requires an explicit error status. CSV instrument parsing stays separate.
HTTP status and bounded category survive; raw bodies, causes, credentials,
cookies, state and session IDs do not enter error messages or metric labels.

`KiteTradingReadMapper` keeps net/day positions separate, exact BigDecimal
amounts and explicit missing-field failure. Equity/commodity are normalized
into `BrokerMargins.segments`; neither segment is inferred from an incomplete
object. MIS maps to INTRADAY, CNC to DELIVERY, COMPLETE to FILLED. Capacity
inspection treats FILLED/CANCELLED/REJECTED as terminal; other/unknown statuses
remain blocking. IDs are deduplicated; inconsistent quantities and dates fail.
No-trade empty lists are valid. Observability uses operation/category/counts,
never raw account snapshots. Existing opt-in trading-read routes return detailed
normalized account data and must remain local; they are not public summaries.

The [official exceptions/rate-limit page](https://kite.trade/docs/connect/v3/exceptions/#api-rate-limit)
was checked on 2026-10-10: historical 3 requests/second, quote 1/second, other
endpoints 10/second. The existing historical limiter deliberately starts at
most one request/second per process. Ordinary account reads are on demand with
no polling/retry loop; cross-process coordination remains an operator obligation
before any separately authorized broker use. No new claim of a distributed
rate limiter is made. A 429 stops the operation instead of provoking retries.

## Funding evidence, never authorization

The [Phase 10.8 contract](intraday-first-funding-contract.md) is unchanged:
full buffered notional is independent of required margin; first-live ceiling
stays INR 10,000; reserve is cash-only; charges remain separate. Aggregate net
only tightens funding. Available collateral is not cash, and utilised liquid/
stock collateral never becomes available collateral. Commodity adds no NSE
capacity. Existing conservative cash exclusions and CNC behavior remain intact.

`IntradayFundingEvidence.inspect` is a pure Java diagnostic projection of the
existing cash-only funding equations. It has no Spring wiring, broker/store
access, cache, order identity creation, execution capability or HTTP endpoint.
Caller-supplied observations must come from a single current synchronous read
attempt and bind to the exact quote request. Local receipt time is checked for
future/stale values; it does not establish an atomic broker snapshot. The
caller must pass current authentication, never a remembered success flag.

The view contains classifications and a cash-only policy result, no balances,
symbols, account IDs, quantity, tokens or raw broker fields:

| Fields | Classification |
| --- | --- |
| Equity cash fields, net, available/utilised collateral | OBSERVED for usable current normalized inputs |
| Application cash lower bound | DERIVED from unchanged conservative policy |
| Exact-request required margin and charges | BROKER_AUTHORITATIVE under the existing current calculator-provider contract |
| Eligible adjusted collateral, applicable cash component, cash-field eligibility | UNKNOWN; numeric domain terms cannot prove provenance |

`cashOnlyFunding=APPROVED` means only that existing cash-only arithmetic passes.
`readiness` remains `NOT_READY`, including that case, because the view cannot
certify broker eligibility or trading gates. Stale/auth-lost/request-mismatched
inputs have UNKNOWN fields. No prior snapshot can populate them. Tests include
equality, one-paisa shortfall, increased margin/charges and unverified terms.

The [official calculator](https://kite.trade/docs/connect/v3/margins/) supplies
margin and charges, but not the complete authoritative account collateral/cash
terms required here. The production adapter continues to leave terms absent.
No collateral-dependent readiness is established by this phase.

## WebSocket, historical and runtime boundaries

`JdkKiteWebSocketTransport` keeps the fixed production WSS origin and a
package-private loopback-only test seam. Handshake 401/403 and recognized auth
messages/close codes invalidate only the used generation. Auth messages must
now be single strict JSON documents; ambiguous text cannot revoke credentials.
Unrecognized text is ignored by the gateway, does not create ticks and cannot
make stale data fresh. Binary decoding, bounded fragmented assembly, single
worker queue, drop-newest overflow and degraded-quality latch are retained.

Gateway reconnection is bounded with capped jitter/backoff; subscriptions/modes
are restored before CONNECTED. Deduplication, registry/session/epoch fencing,
idle watchdog, max subscriptions and out-of-order store rejection are unchanged.
Heartbeat proves socket activity only. Freshness requires every desired tick.
Exhaustion, stale data and overflow cannot permit preflight or dispatch.

Historical bars retain certified interval-start semantics, UTC canonical time,
explicit IST conversion, ZERODHA reference namespace and half-open windows.
Immutable PostgreSQL rows/provenance, one-day chunks, bounded request windows,
content hashes, replay conflicts and dual dataset/decision cutoffs are retained.
`HistoricalContinuityGate` remains mandatory at the multi-instrument acquisition
composition before any delegate/provider/repository access. The lower-level
single-instrument adapter is not a certificate authority and must not be used
to bypass that composition. Synthetic valid certificates exercise the path;
the real Phase 12 artifacts remain BLOCK. No sealed July corpus is opened.

Routes verified from source:

| Surface | Meaning and default exposure |
| --- | --- |
| `/actuator/health/liveness` | Process liveness, not broker/trading readiness |
| `/actuator/health/readiness` | Readiness state and DB in normal configuration; test profile excludes infrastructure |
| `/actuator/tradingstatus` | Not exposed by default; hard `ready=false`, effective stop and runtime HALT; legacy reason string retained |
| `/actuator/kitestatus` | Not exposed by default; local state/registry counts; `tradingReady=false` |
| `/actuator/marketdatastatus` | Not exposed by default; gateway quality/freshness |
| `/api/broker/kite/auth/status` | Passive sanitized status; no broker call or durable-token write |
| `/api/development/trading-read/*` | Explicit read flags, development and not production, loopback checks |
| `/api/development/market-data/*` | Explicit stream/diagnostic flags, development and not production, local guards |

Default actuator exposure stays health/info/prometheus; env/configprops values
remain hidden. Loopback binding is unchanged. No broad exposure was enabled.
Runtime HALT starts HALTED; liveness cannot resume it. Execution remains separately
gated by risk, infrastructure, fresh account/market evidence, arming, permits
and final revalidation. Reconciliation may consume read observations but cannot
dispatch or retry an ambiguous order. Existing architecture tests enforce those
directions and keep research away from broker execution.
