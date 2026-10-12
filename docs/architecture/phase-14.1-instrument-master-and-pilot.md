# Phase 14.1 instrument master and pilot architecture

All new code is offline and additive. No Spring wiring, broker client, credential
provider, registry publication, execution handoff or database migration is added.

| Existing capability | Reuse and audit conclusion |
|---|---|
| broker/infrastructure/kite/KiteInstrumentCsvMapper | Strict required columns, numeric tokens, exchange/segment/type/expiry semantics. Reused for every imported row. Retains duplicate rows for ambiguity detection. |
| instrument/domain/Instrument, InstrumentIdentity | Canonical classification and stable UUID independent of broker token. Reused unchanged. |
| instrument/application/HistoricalUniverseResolver | Exact symbol matching, ambiguity, changed/reused token guard. Wrapped without changing its frozen source. |
| historical/application/FiveMinuteAcquisitionPlan, HistoricalCorpusPlan | Explicit calendar/session splitting and request counts, used by new pilot proposal. |
| historical/domain/HistoricalBar, HistoricalQuality, TradingCalendar | OHLCV invariants, missing/unexpected bars, explicit holidays/early-close evidence. Wrapped for strict completed-window/hash/order checks. |
| historical/application/HistoricalDataIngestionService | Rejects duplicate/unordered five-minute rows, excludes incomplete bars, requires complete calendar coverage before append. Unchanged. |
| historical/infrastructure/PostgresHistoricalBarRepository | Existing READ COMMITTED transaction, idempotent bar keys and conflicting-value rollback, chunk provenance/checkpoints; no new storage path. |
| historical/infrastructure/HistoricalResearchExporter and contracts/schemas/v1 | Existing read-only export and five-minute schema/fingerprint remain unchanged. |
| strategy_engine/phase14 | Existing Python indicators, next-bar fills, costs, OOS pair qualification and contracts revalidated through tests; no alternative engine. |

New data flow: caller-supplied immutable bytes + provenance ->
KiteOfflineInstrumentMaster -> DatedInstrumentMaster -> OfflineUniverseResolution
-> per-source ledger -> selected resolved rows -> OfflineHistoricalPilot proposal.
There is no arrow from the proposal to network dispatch. requireRealDispatch()
always throws and accepts no credential or I/O callback. Existing production
five-minute adapter remains disabled. Other historical legacy entry points are
not granted permission by this new offline API.

Compatibility correction discovered by focused tests: existing CSV mapping uses
ZERODHA while the frozen Phase14 research resolver checks KITE. The new wrapper
requires ZERODHA inputs and creates an in-memory KITE comparison view only for
the legacy resolver. Returned candidates/instruments remain original ZERODHA
objects; stable IDs, exact token values and source evidence are unchanged. No
guessed identity or duplicate resolution algorithm was introduced.

Snapshot hashes cover exact supplied bytes. Source and evidence references are
restricted identifiers with no userinfo/query fields; exceptions never embed CSV
contents. UTF-8 is strict; BOM, UTF-16, malformed/mixed byte sequences and NUL are
rejected. Header/schema and classification validation reuse existing broker code.
Optional listing_status is a documented local CSV extension: empty, UNSPECIFIED,
ACTIVE or INACTIVE. Native snapshots usually do not attest listing status.

Storage limitations retained: historical bars are keyed by platform instrument ID,
interval and timestamp, with token/reference provenance on chunks. This is not a
new token-keyed raw-observation store. Existing continuity/mapping checks remain
necessary before any real ingestion; no production pipeline is wired here.
Corporate-action consistency and real calendar completeness require independent
external evidence. Hashes establish consistency, not authenticity or entitlement.
