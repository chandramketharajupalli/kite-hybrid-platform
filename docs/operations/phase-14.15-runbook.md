# Phase 14.15 offline runbook

Use existing Python3.12/uv environment from apps/strategy-engine. No network or credentials.
Focused: uv run --offline pytest tests/test_phase1415.py -q
Full relevant: uv run --offline pytest -q
Lint/type: uv run --offline ruff check src tests; uv run --offline mypy
Java/DB suites are unnecessary: no Java,ingestion,DB or shared schema changes.

Reproduce authoritative unavailable reports into a NEW disposable directory:
uv run --offline python -m strategy_engine.phase1415.reports --universe ../../config/universes/phase14_unique_universe.csv --end 2026-10-12 --output <new-owned-directory>
The date is a planned IST-midnight cutoff,not an acquisition timestamp. The generator refuses
to overwrite outputs. Compare its two file hashes with generation-1/ist-corrected artifacts.
Preliminary generation-1 root pair-results are SUPERSEDED_WINDOW_CONFIGURATION; preserve
for audit only. CSVs happen to match because exact boundaries live in the paired JSON.

Local data workflow: validate preserved FiveMinuteExport/compose contract; attach Dataset
provenance and dataset SHA256; do not set verified flags without attributable local review.
Provide a bounded loader(symbol,months) to jobs.run with canonical rows,registry(),two
ResearchWindows,Settings,dated IntradayCostSchedule and a new owned checkpoint directory.
The loader returns one validated Dataset or None; never downloads data or invents tokens.
Use export(iterator,new directory) for rich CSV/JSONL. Full schema is the Result model;
all fields,folds,trades and fingerprints are retained in JSONL. Audit canonical CSV through
phase14.universe.audit first. Duplicate inputs normalize once,never duplicate evaluation.

Checkpoint recovery: normal close/restart replays exact matching records. A stale lock or
.pending file means STOP and inspect owned files/config/input/outcomes. Do not delete anything
as a default retry. An explicit disposable-job recovery may use a fresh directory with the
same pinned inputs and compare outputs; conflicting/corrupt records cannot be silently trusted.
No security claim against privileged checkpoint rewriting,physical loss or concurrent export.
Export outputs are immutable/create-exclusive; interrupted export uses a new destination.

Synthetic tariff and76-bar fixture in synthetic-benchmark.json are demonstration evidence
only,not broker charges or a genuine NSE session. Microbenchmark allocation/time is not
production capacity. Full real market datasets,calendar/corporate-action evidence and actual
instrument mappings are unavailable. Do not present any synthetic return as historical.

Gate A/B NOT_AUTHORIZED; no API/WebSocket/orders/credentials/token restore/service startup.
No HALT,capital,governance changes. NO_GO,ZERO_CALL_ABORT,Phase12BLOCK,H1/H2FROZEN,JulySEALED.
No commit/push/staging under this phase. Pending reviews remain pending.
