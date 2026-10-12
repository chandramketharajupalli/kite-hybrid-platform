# Phase 14.0 architecture and existing implementation audit

Java continues to own all ingestion/control-plane capabilities; Python owns offline
research and has no broker or execution dependencies. All Phase 14 output is
BACKTEST_ONLY. No signal from this module reaches an order coordinator.

Existing components reused (paths relative to the repository):

| Capability | Existing implementation | Phase 14 change / coverage |
|---|---|---|
| Broker-neutral candle port | `apps/trading-core/src/main/java/com/kitehybrid/platform/historical/application/HistoricalMarketDataProvider.java` | Reused without another broker port |
| Instrument registry | `instrument/application/InstrumentRegistry.java`, `instrument/domain/InstrumentSnapshot.java` under the same Java package | Existing snapshot rejects duplicate symbol/token keys; new `HistoricalUniverseResolver` reviews a candidate master before registry publication, retaining every unresolved result |
| Authenticated reads | `broker/infrastructure/kite/KiteSession.java`, `KiteRestTransport.java` | Existing session-only transport, body limits, fixed routes, no retries or token clearing on historical failure; allowlisted `5minute` route added |
| Historical adapter | `broker/infrastructure/kite/KiteHistoricalAdapter.java` | One-day maximum retained, IST offset validation, five-minute alignment; production factory denies five-minute mode, package-private fixture seam enables it |
| Ingestion | `historical/application/HistoricalDataIngestionService.java` | Five-minute duplicate/out-of-order/missing/out-of-session data rejected before append; malformed data never becomes an accepted series |
| Persistence | `historical/infrastructure/PostgresHistoricalBarRepository.java`, `db/historical/V11__historical_market_data.sql` | Additive V12 permits FIVE_MINUTE, captures source token/snapshot time, fixes interval-aware closed-bar cutoff; immutable value conflict checks retained |
| Restart | `historical/application/HistoricalCorpusAcquisition.java` | Existing complete-session evidence acts as checkpoint; restart replays hashes, no refetch for complete sessions; partial evidence fails closed |
| Calendar / planning | `historical/domain/TradingCalendar.java`, `historical/application/HistoricalCorpusPlan.java` | Explicit days including holidays; five-minute interval added with old constructor and minute fingerprints unchanged; new dry-run planner deduplicates instruments, up to 366 days |
| Export | `historical/infrastructure/HistoricalResearchExporter.java` | Existing minute schema unchanged; distinct five-minute schema, token and snapshot provenance; shared Java/Python fixture |
| Research | `apps/strategy-engine/src/strategy_engine/backtest/`, `research/` | Existing minute engine, indicators, costs and H1/H2 research are frozen; no edits. New `phase14/` imports their immutable value and cost contracts, adds interval-specific evaluation |
| Execution safety | `strategy/application/StrategyOrderCoordinator.java`, risk/operator/shared controls | No edits, no calls from new research. HALT and buffered INR10,000 ceiling unchanged |

Data flow: supplied CSV -> deterministic audit/canonical copy -> offline dated master
resolution -> dry-run request plan -> independently authorized future acquisition
using existing Java port -> append-only historical chunks and conflict-safe bars ->
pinned five-minute JSON -> Python fingerprint/calendar validation -> independent
symbol/strategy/window evaluation -> JSON/CSV research reports. No automatic promotion.

Tests: `Phase14HistoricalTest`, `KiteHistoricalAdapterTest`,
`HistoricalResearchExporterTest`, `PostgresHistoricalBarRepositoryTest`,
`KiteHistoricalPipelineTest` and Python `test_phase14.py` cover the additions;
existing architecture and full suites remain required.

No dated 1,115-symbol instrument master was supplied. Reports therefore contain
1,115 UNRESOLVED rows, no invented tokens, and no deletion of members. Resolver
supports RESOLVED/UNRESOLVED/AMBIGUOUS/INACTIVE/NON_EQUITY with dated snapshot/hash
and reasons. INACTIVE requires supplied evidence, not absence inference. Previous
token mapping changes/reuse are ambiguous. A current CASH mapping alone cannot
prove historical listing continuity or distinguish every fund/ETF from a share;
instrument classification and corporate-action evidence still require review.

Scope limitations: this is an offline research capability, not a new operational
acquisition authorization service. The historical default migration location stays
isolated. Zero-retry failure requires a separately reviewed restart; Retry-After
cannot be violated by an automatic retry because none exists. No cross-process
quota coordinator is implemented. Raw accepted observations are append-only;
malformed batches are rejected, not stored as corrected market data. Missing bars
are enumerated by quality checks; no interpolation or corporate-action adjustment
is performed. Current membership has survivorship bias; hashes prove consistency,
not authenticity or historical membership. Existing Phase12 continuity gates stay
unchanged and are not bypassed by the new dry-run planner.
