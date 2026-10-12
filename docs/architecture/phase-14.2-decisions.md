# Phase 14.2 architecture and decisions

The existing platform already imports strict offline master bytes, resolves exact
symbols, validates five-minute bars and exports pinned research datasets. Preserve
those components and add only reporting around them; no new transport or registry.

| Audited component | Reuse / remaining limit |
|---|---|
| KiteOfflineInstrumentMaster, KiteInstrumentCsvMapper | Hash/size/strict UTF-8/schema/classification checks; no downloading. Malformed snapshots reject whole import. |
| DatedInstrumentMaster | Observation time separate from optional publisher date; hashes do not attest source truth. |
| OfflineUniverseResolution, HistoricalUniverseResolver | Exact source spelling/order, stale/duplicate/token ambiguity; ZERODHA-to-KITE comparison projection preserves original mapper values. |
| OfflineHistoricalPilot | Existing planner caps 2-5 instruments/5 dates/25 calls; Phase14.2 draft independently narrows to <=2 instruments, one session, <=2 calls. No grant or dispatcher added. |
| HistoricalBar, HistoricalQuality, TradingCalendar | Existing aligned positive OHLC, nonnegative volume, explicit calendar and gap checks; stricter than nonnegative prices by rejecting zero prices. |
| HistoricalDataIngestionService, PostgresHistoricalBarRepository | Existing complete-window validation, transaction rollback, immutable-value conflict detection, idempotency and checkpoint replay. No new DB mutations or migrations. |
| HistoricalResearchExporter and Python phase14 | Pinned schema/content fingerprints; RSI14/EMA21 same-bar crossings, next-bar entry, conservative stops/targets, EOD, costs and pair-specific OOS checks remain unchanged. |
| KiteSession, KiteRestTransport | Authentication and no-redirect/timeouts exist, but broad transport also exposes order methods and is NOT a reviewed isolated pilot process. Do not instantiate/start it for real access. |
| KiteHistoricalAdapter | Production five-minute flag remains false; synthetic seams are not operational authorization. |

New OfflineDataVerification reuses OfflineUniverseResolution for the requested
ledger vocabulary. RESOLVED/AMBIGUOUS remain unchanged; absent exact symbols become
MISSING; other NOT_FOUND and INVALID_SOURCE become INELIGIBLE; STALE_MASTER becomes
MASTER_UNAVAILABLE with its original stale reason. All candidates/provenance and
source ordinals survive. No mapping is guessed and no source row is dropped.

Candle review takes a typed window/bars/calendar plus declared source/master/action
hashes and cutoff/as-of. Existing quality checks determine gaps and invalid windows.
The report accepts zero bars for a quarantined session, emits deterministic flags
and content hash, and never fills prices. No candles gives NOT_EVALUATED. A synthetic
pass is labelled synthetic; OFFLINE_CHECKS_PASSED describes structural consistency,
not authentic source, corporate-action truth, trading eligibility or permission.
Raw parsing rejects malformed OHLCV before a typed input exists; callers must retain
raw rejection metadata separately. This class has no I/O/storage/credential capability.

The master route proposal narrows the prior draft to documented /instruments/NSE.
The existing broad adapter uses /instruments and is not silently changed or used.
Any future isolated reader requires separate code/security review, strict egress,
one-use approval enforcement and proof that no order-capable API is reachable.

No genuine master or candles were supplied. Hashes/publisher dates in synthetic
tests are fixtures only. Legal/entitlement, custody, calendar, corporate-action,
master lineage and signed approvals remain explicit real-access stop gates.
