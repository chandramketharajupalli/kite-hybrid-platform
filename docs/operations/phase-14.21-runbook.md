# Offline analytics runbook

Use Python -B with PYTHONDONTWRITEBYTECODE=1 and the repository's existing strategy-engine environment. Import strategy_engine.phase1421.analytics.run. Supply the explicit local Phase 14.20 synthetic package path, its separately verified raw SHA-256 and a new external output directory. The committed reference package is research/phase-14.20/generation-1/synthetic-multi-sessions.json. Never replace its evidence or the sealed original reports.

run(path, expected_raw_hash, external_directory) publishes analytics.json and completion.json. The analytics file contains named scenarios, each with portfolio/daily/equity metrics, six-strategy and 1,115-symbol attribution, cost analysis, risk-adjusted values or null reasons, monitoring, recovery, readiness and gaps. The completion receipt binds input bytes, output bytes and software. Compare the receipt and report bytes on replay. A missing completion receipt does not establish success; an altered source or existing output conflict is rejected.

Only SYNTHETIC_FIXTURE is accepted for performance calculations in this phase. VERIFIED_AUTHORIZED_LOCAL_DATA is a future trust class requiring its own permission/calendar/corporate-action evidence. UNVERIFIED_LOCAL_DATA and DATA_UNAVAILABLE cannot be promoted by using local filenames; unsupported provenance is rejected. Genuine readiness and qualification remain blocked.

For validation use pytest --assert=plain -p no:cacheprovider with external basetemp/JUnit, Ruff --no-cache, and mypy with an external cache. Preserve all failed receipts. Check all baseline raw hashes after each gate. Never stage, commit, push, access credentials, call Kite, send notifications or change operational HALT from this workflow.
