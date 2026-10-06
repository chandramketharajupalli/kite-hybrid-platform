# Historical corpus and walk-forward research

Phase 11.4 extends research only. Java owns source acquisition/canonical
persistence/export; Python owns independent dataset validation and simulation.
No new migration, Spring bean, execution endpoint, scheduler or trading setting.

## Corpus boundary

The existing HistoricalWindow and HistoricalResearchDataset.v1 remain limited to
31 days. A corpus composes bounded v1 exports rather than weakening those limits.
HistoricalCorpusPlan validates every date against supplied versioned calendar
evidence, at most 366 days/requests, one supported complete session per request.
Unknown dates (including unknown weekends), split sessions, empty plans and budget
overruns fail before fetching. Session counts derive from open/close durations.
The complete chronological plan has a deterministic fingerprint.

HistoricalCorpusAcquisition verifies existing complete chunks and reuses them.
Empty chunks pass through existing ingestion; one session commits at a time after
fetch/validation, without a transaction held across HTTP. Earlier completed chunks
survive later failures. Partial persisted data, conflicting provenance/calendar or
content fail closed; no silent repair, overwrite or recursive retry. DB uniqueness
and the original conflict policy remain authoritative under concurrent workers.
Provider failure stops the current invocation; restart is an explicit operation.

The local Phase114 harness additionally restricts HTTPS host, method, exact
resolved historical path and predeclared query strings. Each query is consumable
once, profile/reference once, 122 historical requests maximum. It uses existing
production adapters and one-start/second pacing, no token writes/exchange. This
tool is outside production startup/test discovery. Historical code cannot import
broker/trading/operator/risk code; the broker-specific harness is separate.

Daily canonical exports and their raw UTF-8 SHA-256 pins form a manifest in the
ignored data directory. Before/after DB/token evidence and acquisition timing are
operational metadata. Core content identity excludes ingestion timing/random IDs;
full artifacts retain their query cutoffs/provenance for replay. No raw responses
or instrument token are persisted in research evidence.

Python Corpus validates every original v1 dataset and bounds the aggregate at
366 days, 366 shards, 12 calendar segments and 150000 bars. Explicit calendar
coverage includes non-trading dates. Instrument/product/lot/interval/adjustment
identity must agree; ordering, gaps, overlapping shards and unknown dates fail.
Every expected session must appear exactly once. No convenient partial subset or
latest-data query exists. Content identity includes calendar/source versions and
canonical bar hashes. Operational cutoffs remain in the full artifact identity.

The offline runner also checks the shared JSON schema, raw artifact pins, the
Java global calendar fingerprint and canonical content independently. It accepts
only bounded date-named local shards; it has no broker/network/database access.

## Simulation and cost contracts

The same engine handles the composed bars in one ledger per partition. It does
not reset capital at shard boundaries. Corpus engine version
`intraday-next-open-v3-session-corpus` supplies only the current confirmed session's
immutable completed prefix, bounding context copies by session length. The legacy
v1/v2 dataset API and golden output remain unchanged. The Phase 11.3 features already
reset per session; regression compares identical fills/equity/metrics between
legacy and corpus contexts. No future bar, same-close fill, overnight position,
margin leverage or real execution path is introduced.

The exact predeclared costs/slippage are part of each spec and result. Historical
dates outside the calibrated tariff window require explicit FIXED_AS_OF. Results
are a dated cost scenario, not reconstructed account contract notes or proof of
realistic liquidity/latency. No parameter search or tariff tuning to P&L.

## Walk-forward boundary

WalkForwardSpec is immutable and content-pinned: expanding TRAIN, adjacent
non-overlapping VALIDATION windows, final chronological TEST, strategies,
implementation hashes, cold-start rule, config, costs, feature/engine/slippage
versions. Maximum 12 folds and 16 unique strategy trials. Current generation is
three folds and four predeclared parameter sets, 24 TRAIN/VALIDATION evaluations.

Each partition starts with identical configured cash, flat position and cold
indicators. TRAIN is a rule-development interval, not ML training. Development
API contains no TEST evaluation branch. TEST data may be integrity-checked but its
strategy metrics/trades are never computed by this API. This is a research contract,
not a security sandbox against arbitrary Python. Existing Phase 11.3 explicit
frozen-selection final TEST interface is not weakened; no corpus final-test
convenience method is added.

Reports retain every fold/trial's metrics, exact result hash and spec-derived fold
identity. They are not sorted by performance and never select a winner. Full
result fingerprints bind trades, costs and equity despite the compact fold report.
Expanding TRAIN windows overlap: their P&Ls must not be pooled as independent
observations. VALIDATION windows do not overlap and each starts with fresh capital.
Repeated development evaluation must reproduce the report. Sealed TEST changes
can change corpus/spec identity but cannot change development fills/metrics.

Generation changes after looking at results must be recorded. More trials increase
overfitting risk; future selection needs a predeclared criterion, all failed trials,
cost/slippage sensitivity and untouched final evaluation. No live strategy is selected.
