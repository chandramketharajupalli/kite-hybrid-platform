# Phase 14.16 frozen implementation plan

Baseline: `91760b073ee39ce62eb42ef041e8b75d9650e44f`, develop and local origin/develop.
Preflight: clean index/worktree; 39 Phase 14.15 artifacts committed and hash matched;
1,150 original baseline hashes, 27 archived originals, 13 newline reconciliations,
G1, 18 frozen sources, 63 research files, K/L and historical manifests verified.
Universe: 1,140 source rows minus 25 duplicates = 1,115 enabled NSE symbols.
Prior D receipts: 147 focused / 429 full, independently checked receipts, not new tests.
The external pre-edit inventory covers all 1,189 tracked files; SHA-256:
`c8cfa49bfd83efadc0337f495e8835a665bdf6ba3987600bdf0789e062d24364`.

## Scope and contracts

Add a phase1416 Python package, focused tests, a read-only evidence verifier and
the requested architecture, quality, runbook and validation documents. No existing
tracked file may change. Reuse HistoricalResearchDataset.five-minute.v1 JSON,
FiveMinuteExport, Calendar, compose, Series quality and Phase1415 evaluation,
family binding, execution, costs and qualification. Java V12 historical tables
remain untouched. An isolated local file store avoids a new database dependency.

Inputs are explicit bounded UTF-8 JSON export chunks and a typed request containing
the full calendar, identity and source evidence. No discovery of arbitrary files,
broker access, network access or credentials. A hash-verified permission statement
must bind source, identity, permitted hashes, retention and research use. Local
presence alone confers no trust. Synthetic data always remains synthetic;
missing authorization is UNVERIFIED_LOCAL_DATA and quarantined. Missing calendar,
corporate-action or mapping evidence also prevents accepted performance.

Use existing UTC timestamps and Asia/Kolkata calendars, without weekday inference,
gap filling or adjustment fabrication. Preserve raw source hashes and timezone
semantics. Store only accepted, versioned content with atomic publication and
idempotent replay. Expose explicit expiry checks; deletion requires a separate
operator action and is never performed automatically.

## Execution and budgets

Single worker, one symbol/window at a time; maximum 32 input chunks, 4 MiB per
chunk, 32 MiB total input, 30,000 candles, 400 calendar days and 256 MiB store per
operation. Batches are export chunks, never the whole universe. Bound job duration
to 3,600 seconds checked between pairs; each pair is bounded by the frozen engine.
Use complete 1,115 x six x two family identity, 70% target and frozen policy.
Bind all inputs, permission/retention, calendars, corporate actions, costs, windows,
software, registry and execution settings. Atomic checkpoints reject stale bindings.
Do not present partitions or synthetic outcomes as genuine qualified opportunities.
All 13,380 missing-data rows retain null metrics. Separate synthetic example runs.

## Validation and preservation

Use synthetic fixtures only. Test valid import, invalid identity/provenance/interval,
OHLCV/order/duplicates/gaps, IST/holidays, corporate actions, limits, interruption,
replay, binding changes, null reports and no-lookahead integration. Run focused then
full Python suite, Ruff no cache, mypy external cache; Java unchanged, no Java run.
Python -B and PYTHONDONTWRITEBYTECODE=1; pytest --assert=plain -p no:cacheprovider;
all temporary/JUnit/log/cache output in a unique external directory. Preserve failed
attempts. Verify all 1,189 baseline hashes after major gates. Max 20 minutes per gate.
Stop immediately on protected changes, unexpected baseline changes, safety failures
or frozen methodology defects. Correct additive implementation defects with receipts.

## Deliverables and certification

Requested five documents and generation-1 evidence manifest, additive source/tests,
import/quality/coverage/job/checkpoint/qualification/rejection/run reports and exact
13,380 CSV/JSON rows. Record exact inventory, hashes, commands, exit codes, test totals,
secret/canary checks and two deterministic verifier replays. Keep raw receipts and
private inputs outside Git. Record this plan's raw hash before implementation in
external evidence and final manifest; amendments must be additive.

Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase 12 BLOCK; H1/H2 FROZEN;
July SEALED; operational NO_GO. No staging, commit, push or infrastructure work.
