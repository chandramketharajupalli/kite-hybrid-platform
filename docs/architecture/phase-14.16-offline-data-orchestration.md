# Offline historical data orchestration

Phase1416 is a Python research adapter over the frozen Java five-minute export
contract and Phase1415D evaluator. It has no database, broker, credential or network
adapter. Java owns authoritative instrument resolution and operational trading.
Historical V12 tables and Java exporters are reused through their existing export
boundary; neither their schemas nor their implementations change.

`imports.Request` supplies explicit JSON chunk paths and raw SHA-256 values,
instrument identity, source timestamp, provenance classification, complete calendar
chunks and retention scope. `FiveMinuteExport` verifies the Java content fingerprint,
OHLCV and provenance fields. `compose` verifies ordered, nonoverlapping exports,
complete date classifications and normalized UTC Series identity. `Series.quality`
checks every required five-minute candle against explicit Asia/Kolkata sessions.
The importer preserves raw hashes and normalizes through those existing contracts.
Unknown dates, missing candles and corporate-action uncertainty never become prices.

Accepted data is an immutable content-addressed Imported JSON record outside Git.
Quarantine records contain reasons and provenance hashes but no candle dataset.
Exclusive pending-file publication prevents incomplete imports from appearing ready.
Repeated byte-identical imports are idempotent; conflicts fail. The operator owns
local store custody: hashes detect changes, not malicious replacement of an entire
trusted evidence chain. Permission and authoritative-review statements require an
attributable reviewer and exact scope/hash matches; this is not signature verification
or automatic discovery of legal authorization.

The job binds the full frozen family, settings, tariff, software inventory, explicit
dataset-record hashes, observation time and execution budget. Dataset records bind
the raw input, calendar, retention and import request. One worker processes canonical
symbols in order, six strategies for each six-/twelve-month window. Phase1415D retains
completed-candle decisions, next-bar entry, costs, conservative exits and chronological
training/validation/holdout boundaries. Strategy definitions are never optimized.

Each symbol is an atomic 12-result partition. Checkpoints include ordered identities,
result checksum and run identity. Replay verifies the software, full-family policy,
dataset records and every partition before returning a completion summary. Partial
partitions cannot become complete. States are PLANNED, DATA_UNAVAILABLE or
DATA_QUARANTINED or READY, RUNNING for accepted data, CHECKPOINTED, then COMPLETED
only after full report publication. Exceptions produce additive FAILED or
REPLAY_REJECTED diagnostics; preflight rejection raises before creating a run.
Crash locks and pending report directories require operator inspection; the workflow
never automatically deletes a lock or discards partial evidence.

No local data was supplied for this phase's real-universe evidence run. The public
results therefore express data availability, not measured market performance.
