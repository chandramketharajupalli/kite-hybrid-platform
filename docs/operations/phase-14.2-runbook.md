# Phase 14.2 offline runbook

Never load .env, restore a session, run broker diagnostics, start the trading app,
or send API traffic. This phase has no authorized real request. Start with develop,
matching local refs, clean baseline and committed Phase14.1 inventory. Verify the
original manifests and exact Phase14.0A newline rules without rewriting evidence.

1. Reconcile original/duplicate/canonical universe CSVs; preserve 1,115 rows/order.
2. If an externally supplied dated master is available in a later authorized
   review, hash immutable bytes before KiteOfflineInstrumentMaster.importBytes.
   Pass required source/observation/publisher metadata; never infer dates or tokens.
3. Use OfflineDataVerification.resolve, which delegates existing resolution.
   With no actual master, produce MASTER_UNAVAILABLE for all symbols; keep reasons.
4. Review candle input only through existing raw mapper/domain validators and
   OfflineDataVerification.review. Empty actual input is NOT_EVALUATED, not a pass.
   Quarantined windows accept zero bars; keep original observations and flags.
5. Present decision A and B separately. Missing binding fields, permissions,
   source evidence or containment means stop before any network dispatch.

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
.\mvnw.cmd -o -pl apps/trading-core '-Dtest=Phase142ReadinessTest,Phase141OfflineTest,KiteHistoricalAdapterTest' test
uv run --offline --project apps/strategy-engine pytest apps/strategy-engine/tests/test_phase14.py -q -p no:cacheprovider
.\mvnw.cmd -o -pl apps/trading-core test
uv run --offline --project apps/strategy-engine pytest apps/strategy-engine/tests -q -p no:cacheprovider
uv run --offline --project apps/strategy-engine ruff check apps/strategy-engine/src apps/strategy-engine/tests scripts/research/verify_phase142.py
uv run --offline --project apps/strategy-engine mypy --strict apps/strategy-engine/src apps/strategy-engine/tests scripts/research/verify_phase142.py
.\mvnw.cmd -o -Pintegration '-DskipUnitTests=true' '-Dit.test=PostgresHistoricalBarRepositoryTest,KiteHistoricalPipelineTest' verify
uv run --offline python scripts/research/verify_phase142.py
uv run --offline python scripts/verify-project.py
uv run --offline python scripts/check-secrets.py
```

Testcontainers owns only disposable PostgreSQL; no development/production database.
Check its cleanup from test logs; never remove unknown containers or directories.
Ignored target outputs are local evidence inputs, not deliverables. Preserve failed
attempts and fresh suite totals; do not overwrite old manifests or use old results
as new passes. New text must be LF and identical under Git clean filtering.

Finish with exact inventory, parent/source hashes, unavailable actual-data reports,
synthetic quality examples, sealed manifest and two read-only replays. Do not stage,
commit/push, provision infrastructure, activate acquisition or change HALT/orders.
