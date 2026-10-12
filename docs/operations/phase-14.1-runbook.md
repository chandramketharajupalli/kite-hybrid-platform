# Phase 14.1 offline runbook

Use existing JDK21 and offline Maven/uv dependencies. Never start the trading app,
load .env, restore credentials, run broker diagnostics or connect a live transport.

1. Verify develop, expected HEAD/local origin, clean baseline and original Phase14
   manifest plus Phase14.0A path-scoped reconciliation. The Phase14.1 verifier also
   validates its sealed manifest once present. Do not regenerate older evidence.
2. Supply immutable bytes to KiteOfflineInstrumentMaster.importBytes with exact
   byte hash/size and DatedInstrumentMaster.Provenance. Source/evidence references
   must be safe identifiers, no credential-bearing URL. Observation and optional
   publisher date are separate. No file or network loading occurs in the importer.
3. Resolve the entire source list via OfflineUniverseResolution.resolve. Supply
   previous dated master when available. Do not replace a missing snapshot with
   synthetic mappings. Retain every status/candidate/reason/provenance field.
4. A selected resolved subset may be passed to OfflineHistoricalPilot.plan. The
   result is UNAPPROVED_OFFLINE_ONLY. requireRealDispatch always rejects.
5. Validate supplied candles through existing adapter/domain types and the new
   strict quality wrapper; use explicit calendar and content fingerprint. Never
   fill missing prices. Real corporate actions/calendar still require external review.

```powershell
$env:PYTHONDONTWRITEBYTECODE='1'
.\mvnw.cmd -o -pl apps/trading-core '-Dtest=Phase141OfflineTest,Phase14HistoricalTest,KiteHistoricalAdapterTest' test
uv run --offline --project apps/strategy-engine pytest apps/strategy-engine/tests/test_phase14.py -q -p no:cacheprovider
.\mvnw.cmd -o -pl apps/trading-core test
uv run --offline --project apps/strategy-engine pytest apps/strategy-engine/tests -q -p no:cacheprovider
uv run --offline --project apps/strategy-engine ruff check apps/strategy-engine/src apps/strategy-engine/tests
uv run --offline --project apps/strategy-engine mypy apps/strategy-engine/src apps/strategy-engine/tests
.\mvnw.cmd -o -Pintegration '-DskipUnitTests=true' '-Dit.test=PostgresHistoricalBarRepositoryTest,KiteHistoricalPipelineTest' verify
uv run --offline python scripts/research/verify_phase141.py
uv run --offline python scripts/verify-project.py
uv run --offline python scripts/check-secrets.py
```

Integration uses Testcontainers disposable PostgreSQL and mock HTTP only. No
shared DB/Compose state. The unit test emits a full MASTER_UNAVAILABLE ledger in
target for independent comparison with additive evidence; no real master is present.
New tests do not execute external HTTP and architecture assertions forbid network,
credential, risk/order and live-transport dependencies from offline additions.

Keep ignored target logs locally and preserve failed-attempt totals/reasons in
additive evidence. Do not stage caches/build output or modify historical manifests.
Stop after offline validation and present the separate owner authorization draft.
