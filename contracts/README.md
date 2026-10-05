# Cross-language contracts

Authoritative language-independent JSON Schema Draft 2020-12 definitions live
in schemas/v1. Envelope and SignalEvent.v1 describe the signal boundary;
HistoricalResearchDataset.v1 separately describes offline research input.
Both languages validate the same fixtures. Java must validate schema before
binding its wire DTO; Python uses frozen Pydantic models in addition to schema tests.

Envelope: schema_version, event_id, event_type, event_timestamp, optional
correlation_id, payload. Omit correlation_id if absent; explicit null is invalid.
UUID strings are lowercase canonical form. UTC timestamps end in Z and use at
most six fractional digits. Event identity and signal identity are distinct.

Quantity is a JSON integer value, positive whole units, bounded by Java int.
Producers must emit an integer token, never a string, boolean or floating token.
JSON Schema's mathematical integer definition alone cannot enforce lexical
integer tokens (e.g. 10.0); consumers must additionally reject numeric coercion.
Price is a nonnegative plain decimal string: up to 12 integral and 8 fractional
digits, no exponent or leading plus. Zero is schema-valid but rejected by the
initial Java price risk rule. Currency, lot size and valuation are future
instrument/risk concerns.

v1 schemas reject unknown properties. Do not silently widen v1; negotiate a new
version for payload extensions or changed meaning. Schema filenames/IDs and
event_type values are stable. Consumers reject unsupported versions. Validate
both schemas and fixtures when evolving a version. Schema IDs are identifiers,
not runtime network endpoints; test resolvers load local files only.

Transport and serialization are separate. No queue, network channel or delivery
guarantee exists in Phase 1. JSON is not a commitment for high-volume raw ticks;
future binary encoding may preserve the same domain meaning.

## Roadmap (no speculative payload schemas)

| Event | Define when |
| --- | --- |
| TickEvent, CandleEvent | market-data normalization/candle semantics are implemented |
| OrderIntent | approved intent boundary and required order fields are settled |
| OrderSubmitted, OrderAccepted, OrderRejected, OrderCancelled | OMS and broker acknowledgement semantics exist |
| PartialFill, Fill | fill identity, quantity/price/fees and deduplication are defined |
| PositionChanged | authoritative fill projection exists |
| RiskRejected | risk decision publication requirements exist |
| TradingHalted | durable runtime halt controls and actor attribution exist |

Java SignalContractTest and Python tests/test_contracts.py share fixtures.
Neither fixture compatibility nor schema validation proves runtime delivery,
throughput, risk completeness or exactly-once processing.

## Offline historical research dataset v1

`HistoricalResearchDataset.v1.schema.json` is a standalone artifact, not a live
event/envelope. Java HistoricalResearchExporter emits it from a pinned historical
repository replay. Python `strategy_engine.backtest.dataset.Dataset.from_json`
consumes it without network or database access. Both test the same
`historical-research.synthetic.json`: a synthetic five-minute session, **not real
SBIN market data or authoritative exchange calendar evidence**.

One NSE cash instrument, MINUTE bars, interval-start UTC timestamps and explicit
Asia/Kolkata session dates. Prices/OI are exact decimal strings (18 integral,
10 fractional digits); volume is a nonnegative Java long. Null OI means absent.
Bars and calendar dates are strictly increasing; no duplicate keys, dates or
bars. Provenance is deduplicated and sorted by source, version, reference hash,
calendar hash. Decimal scale is not content identity. Java HistoricalFingerprint
and Python bar_fingerprint use the same versioned canonical encoding.

Schema validation alone does not prove OHLC consistency, hash agreement or
session completeness. Python additionally checks all of these, requires explicit
known session evidence and rejects incomplete/partial/split sessions in v1.
JSON duplicate keys, trailing documents, non-UTC timestamps and numeric price
tokens are rejected. Future changes to these semantics require a new contract
version. No broker tokens, account IDs, credentials or random chunk IDs cross
this boundary. Dataset cutoff and original decision cutoff remain explicit.

The exporter uses the existing repository's immutable first-observation
provenance. Ingestion details remain in research PostgreSQL; the artifact keeps
the semantic source/reference/calendar versions needed to identify simulation
input without making random database IDs or export wall-clock time part of
result identity. Retain the complete artifact with the experiment. A hash proves
content integrity against a trusted pin, not authenticity or corporate-action
certification. No provider is contacted to verify an artifact during backtesting.
