# Kite trading reads (Phase 5A)

## Existing boundaries

The Java modular monolith owns broker authentication. `KiteAuthenticationUseCase`
restores an encrypted PostgreSQL token, installs it into the single `KiteSession`,
validates the profile and initializes the instrument registry. REST and authentication
serialize through the session monitor; market data uses the existing immutable
session view. This phase changes neither owner nor lifecycle.

`BrokerAdapter.submit(OrderIntent)` remains an unimplemented execution boundary.
`OrderState` describes internal command processing, not a broker observation.
There were no implemented position/portfolio packages, `Money` or `Quantity` types.
Existing money/price conventions are `BigDecimal`, integral quantities, UTC `Instant`,
and shared UUID `Identifiers.InstrumentId`. Risk rules and order repositories are
unchanged. Reads do not create authoritative ledger entries or drive lifecycle events.

## Added flow

```text
Explicit caller -> broker.application.read provider -> KiteTradingReadAdapter
                -> existing KiteSession + KiteRestTransport (GET only)
                -> KiteTradingReadMapper + one InstrumentRegistry snapshot
                -> immutable broker.domain.read observations
```

Five provider interfaces expose `orders()`, `trades()`, `positions()`, `holdings()`
and `margins()`. One Kite adapter implements these independent interfaces; it does
not implement or call the execution adapter. Models have no Spring, Jackson, HTTP
or broker SDK dependencies. Wire JSON trees, field names and enum mappings remain
inside Kite infrastructure. No new dependency, database table or background worker
is required.

| Provider result | Scope |
| --- | --- |
| `List<BrokerOrder>` | Broker IDs, platform instrument ID, typed side/type/product/status/validity/variety, quantities, prices and timestamps |
| `List<BrokerTrade>` | Broker trade/order IDs, platform instrument ID, side/product, quantity, price and fill time |
| `BrokerPositions` | Separate immutable net/day lists of signed quantities, price/value/P&L and buy/sell totals |
| `List<BrokerHolding>` | Platform instrument ID, ISIN, product, distinct quantity buckets, prices, broker P&L and optional margin-funded holding |
| `BrokerMargins` | Explicit equity/commodity segments, enabled flag, net and typed available/utilised components |

These are selective read projections, not lossless representations of every Kite
field. User identity, free-form status messages, tags, arbitrary metadata and raw
wire status strings do not escape. Broker order/trade IDs are external references;
they never replace internal UUID order IDs. ISIN is descriptive holdings data and
does not create a second instrument identity. Amounts use Kite's account/price units;
there is no currency conversion, balance aggregation or locally calculated P&L.

## Normalization and bounds

The fixed transport origin remains `https://api.kite.trade`. New allowlisted routes:
`/orders`, `/trades`, `/portfolio/positions`, `/portfolio/holdings`, `/user/margins`.
All are GET with existing version/authentication headers. There are no redirects,
retries or arbitrary URLs. The application adapter first requires a profile-validated
session under the existing session monitor. It never accepts an access token as input.

The transport limits compressed and decompressed response bytes to 4 MiB per list/
positions route and 64 KiB for margins. Existing UTF-8, content-encoding and gzip
validation applies. Mapper limits also protect direct use: 8 Mi characters, JSON
depth 16, strings 1,024 characters, property names 128 characters, number text 64
characters, 10,000 rows per array and 500,000 tree nodes. Duplicate JSON keys and
trailing documents are rejected. Unknown additive properties are discarded subject
to structural bounds; unknown margin segments reject the response.

Consumed required fields must have the correct JSON type. Numbers encoded as
strings are rejected except documented broker instrument tokens. Quantities must
be exact signed 64-bit integers; order/fill and holdings buckets are nonnegative,
order/trade totals positive, and position net/overnight quantities may be negative.
Prices cannot be negative; signed balances/P&L remain signed. Decimal values retain
their exact JSON representation with precision <=36, scale between -18 and 18 and
absolute magnitude <10^18. No rounding, default zero or double conversion occurs.
Broker-supplied zero prices/balances remain zero. Multiplier must be a positive integer.

Orders normalize known statuses independently from `OrderState`; COMPLETE becomes
FILLED, OPEN with a partial fill becomes PARTIALLY_FILLED, SL/SL-M become STOP_LIMIT/
STOP_MARKET, CNC/MIS/NRML become DELIVERY/INTRADAY/CARRY_FORWARD. Other documented
statuses/products are typed too, including MTF. A well-formed unknown enum string
becomes UNKNOWN and is discarded at the boundary. Missing, null or non-string enum
fields fail. No unknown value can imply execution permission or a terminal success.

Order counters are individually bounded by the total and filled plus pending
cannot exceed it. COMPLETE requires a full fill; OPEN cannot already be fully
filled. Pending and cancelled quantities can overlap in documented Kite responses
and are not added together. Duplicate natural row keys reject the response: order
ID, order plus trade ID, and instrument plus product for holdings/each position
list separately. Net and day views may contain the same instrument.

Documented optional exchange/parent IDs, exchange timestamps and holding MTF details
use `Optional`; absent data remains absent. Full broker timestamps are interpreted
in Asia/Kolkata and converted to UTC, with strict calendar parsing. Trade
`order_timestamp` can be time-only and is intentionally omitted instead of assigning
an invented date. No fetch timestamp is presented as an exchange timestamp.

Instrument tokens must be positive uint32 values (integer or bounded decimal text).
The token mapping and exchange/symbol mapping must point to the same existing
instrument in a single immutable registry generation. No ID is synthesized and no
reference-data fetch occurs during mapping. Unresolved tokens and tokens whose
exchange/symbol disagrees with the snapshot fail the entire response. Matching
stale reference data cannot be detected; there is no registry age freshness guarantee.

No malformed row is skipped and no partial collection is returned. Empty list
responses and explicitly empty net/day position lists are valid. Margins require
both documented segments with complete values, even for a disabled segment; an
empty object cannot become a zero balance.

## Errors, diagnostics and observability

Existing `BrokerReadException` categories distinguish authentication, configuration,
broker HTTP/API errors, transport failure and invalid response. Failures carry only
safe category/HTTP status and no upstream message, cause, payload or headers.
Existing 401/403 and TokenException handling invalidates the shared session; normal
authentication reconciliation retains ownership of durable credentials.

Every adapter invocation emits `kite.rest.operations` and `kite.rest.duration` using
the established `operation`/`result` labels. The only new operation values are the
five read names; results are success or bounded exception categories. Logs contain
only these safe values. No IDs, financial values, credentials or raw exceptions are
logged. Latency includes session-lock waiting, transport and normalization.

Diagnostics require both explicit flags and `development & !production`. All five
GETs under `/api/development/trading-read` validate the numeric loopback socket peer
and local Host, reject Origin/cross-site browser and forwarded/proxy requests, and
return no-store headers. They expose normalized observations only. No multi-user access
control or proxy deployment is provided; the server must remain loopback bound.
See the [manual runbook](../runbooks/kite-trading-read.md).

Current primary references: [orders/trades](https://kite.trade/docs/connect/v3/orders/),
[portfolio](https://kite.trade/docs/connect/v3/portfolio/),
[account margins](https://kite.trade/docs/connect/v3/user/),
[exceptions](https://kite.trade/docs/connect/v3/exceptions/). Checked 2026-09-20.
