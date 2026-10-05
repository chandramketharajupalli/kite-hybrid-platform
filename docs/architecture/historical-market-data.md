# Historical market-data foundation

## Boundary review before implementation

Baseline: `3bb4203`, develop, clean; HEAD equals origin/develop. Java owns
acquisition and durable canonical data; Python remains a research consumer.
Reuse `InstrumentId`, an immutable registry snapshot, `KiteSession`, existing
HTTP timeout/body-limit conventions and JdbcTemplate/Flyway/Testcontainers.
Do not reuse latest-tick storage as history or connect ingestion to the strategy
coordinator (which can create orders). No Spring auto-start, controller, scheduler,
token restore/login, execution service or live-data connection is needed.

Direction: historical source -> ingestion -> canonical bars -> PostgreSQL ->
research consumers. Future signals still require intent, risk, authorization and
execution safety. Historical code neither reads nor alters trading HALT.

## Provider evidence (accessed 2026-10-05)

[Kite historical API](https://kite.trade/docs/connect/v3/historical/) documents
GET `/instruments/historical/{token}/{interval}`, date-time request parameters,
offset timestamps and OHLCV arrays. It supports minute, 3/5/10/15/30/60minute and
day; Phase 11.0 implements only minute. Continuous futures and OI acquisition are
outside this NSE cash scope. The example includes its `to` candle: the adapter
requests through the boundary and filters to the internal half-open window.

The page does not explicitly define timestamp start/end convention or current
per-interval maximum request ranges. Neither is guessed. A one-day application
chunk is conservative, not a claim about the broker's maximum. Real normalization
is blocked with TIMESTAMP_SEMANTICS_UNVERIFIED until authoritative confirmation;
the package-private synthetic adapter seam tests an explicit start convention.
There is no public override or environment toggle for that gate.

[Kite rate limits](https://kite.trade/docs/connect/v3/exceptions/#api-rate-limit)
state three historical requests/second. A shared process-local limiter permits
at most one start per second, without automatic retry. Multiple processes sharing
credentials must be coordinated externally before real ingestion. HTTP 429 and
transient errors stop the run. No retry policy is shared with order transport.

[NSE timings](https://www.nseindia.com/resources/exchange-communication-holidays)
describe regular cash trading and date-specific exceptions. No permanent holiday
list or weekday inference is installed. An explicit versioned calendar supplies
confirmed session windows, non-trading dates, and UNKNOWN for absent dates.

## Initial contract

One-minute bars cover `[startTime,startTime+60s)`. UTC Instant is authoritative;
Asia/Kolkata is explicit for request conversion and calendar dates. Prices use
canonical exact decimals (NUMERIC(28,10) bounds), volume long, optional OI exact
nonnegative decimal. No local price/corporate-action adjustment. Provider
adjustment status remains UNSPECIFIED; data must not be called point-in-time
corporate-action-safe. Symbol changes require explicit identity mapping later.

Requests are half-open, minute-aligned, at most 31 days. Chunking uses at most
one day per call. Each chunk is fetched/validated before a short atomic DB
transaction. Earlier successful chunks survive later failure; restart rechecks
identical bars, rejects any changed values, and never overwrites history.

Confirmed-session gaps are structured missing expected minute starts, limited to
the requested window and closed-bar cutoff. Unknown dates remain separate from
gaps; non-trading dates have no expected bars. Bars outside confirmed sessions
are reported as unexpected rather than silently repairing them. No filling.

Research queries require a dataset ingestion cutoff AND a decision-time cutoff.
Only bars first persisted by the dataset cutoff, with bar end <= decision time,
are returned, sorted ascending. This prevents future candle leakage, not proof
that historical vendor corrections were available at the original trading time.
Pinned content hashes and immutable first-observation provenance support replay;
there is no revision API. New ingestion of changed history fails with CONFLICT.

Implementation and validation details are recorded in the Phase 11.0 report.

## Persistence and reproducibility details

The next available migration is V11. It lives in `db/historical`, deliberately
outside the trading application's default `db/migration` location. An isolated
research datasource explicitly migrates both locations. No production bean or
startup path selects it. Never point that configuration at the development
trading database: existing exact-V10 execution readiness intentionally rejects
V11. The migration is forward-only and has no changes to trading/auth tables.

`historical_bars` has primary key `(instrument_id,bar_interval,start_time)`; that
index supports the ordered range query without another redundant index.
NUMERIC(28,10) values are validated before JDBC, so PostgreSQL cannot silently
round a supported canonical value. SQL repeats OHLC/quantity/key constraints.
`historical_ingestion_chunks` records run/chunk IDs, full request and chunk bounds,
source/version, receipt/ingestion/cutoff times, reference/calendar fingerprints,
content hash and accepted/inserted counts. No raw response or credential is stored.

The first observation owns each bar's provenance and ingestion time forever.
Replays create new chunk evidence but do not rewrite existing bars. Concurrent
workers rely on PostgreSQL uniqueness; conflict comparison follows the blocked
insert in READ COMMITTED, and any mismatch rolls back all inserts and evidence
for that chunk. Chronological insertion order avoids opposite-order lock cycles.
Provider I/O happens before `append`; ingestion does not start a surrounding DB
transaction. There is no automatic retry, including on DB errors.

A successful result reports calls/received/accepted/in-response duplicates,
incomplete exclusions, per-chunk inserted/existing counts, first/last starts,
content hash and bounded quality evidence. A failed run throws a bounded reason;
previous committed chunks remain durable and identical reruns complete safely.
No failed chunk is marked successful; no partial overall success is returned.

`query` returns bars and first-provenance chunk IDs; `evidence` reads that durable
metadata. Save the query's two cutoffs, returned hash, calendar dataset and
calendar fingerprint with a research experiment. `replay` rejects a changed
manifest (including a late commit/backfill with the same observation timestamp).
A timestamp cutoff alone is not a transaction snapshot or immutable dataset ID.
An empty dataset has a deterministic hash but is not proof of completeness.

Calendar input is immutable caller-supplied versioned evidence, not an installed
authoritative calendar. Retain its exact map/source/version externally with the
experiment; only its fingerprint is persisted per chunk. Multiple windows per
date support split/special sessions. Dates not supplied are UNKNOWN, even on
weekends; no assumption about holiday schedules or emergency closures.

The current registry establishes present identity/mapping only. It cannot prove
that a historical token was never reused, a symbol never changed, or a stock was
listed throughout the requested history. Such historical identity and corporate
action validation remains a dataset certification requirement. No automatic
remapping, adjustment, split/bonus/dividend repair or forward-fill is performed.

## Using the Java boundary

Construct `HistoricalDataIngestionService` explicitly with an initialized
registry, read-only provider, isolated `PostgresHistoricalBarRepository`, and
Clock. Supply a bounded `HistoricalWindow`, cutoff no later than the clock,
and explicit `TradingCalendar`. No API server, strategy or execution service is
required. Fetch budgets are at most 31 calls per instrument/request and 3000
provider rows per call (at most 1440 unique minute bars after range validation).
Persistence/query size is bounded by those windows; no universe traversal.

Use `HistoricalBarRepository.Query(window, decisionCutoff, datasetCutoff)` for
research. At 09:17, the bars starting 09:15 and 09:16 are eligible; 09:17 is not.
Cutoff completeness does not promise the vendor published its final candle at
that exact instant. Unknown/failed quality evidence must be resolved before a
dataset is certified for backtesting. A caller must not equate retrieval success
with a complete, corporate-action-safe, point-in-time dataset.

Phase 11.1 may consume pinned canonical datasets for features/backtests. No
strategy, optimization, paper trading or execution wiring is introduced here.
