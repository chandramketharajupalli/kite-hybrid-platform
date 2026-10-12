# Offline replay

Do not start the application or load credentials. Verify develop, baseline refs and
exact inventory. Set PYTHONDONTWRITEBYTECODE=1. Import sealed
scripts/research/verify_phase142.py with importlib, bind BASELINE in memory to
7c1a6ade34b2d1070cea748d506109e196ad8236 and call verify(). No on-disk edits.
This reuses the exact 13-path reconciliation against committed Git blob bytes.

Run Maven offline with -pl apps/trading-core and
-Dtest=Phase141OfflineTest,Phase142ReadinessTest,KiteHistoricalAdapterTest,HistoricalArchitectureTest test.
Run uv run --offline --project apps/strategy-engine pytest
apps/strategy-engine/tests/test_phase14.py -q -p no:cacheprovider.
Use the same uv project for ruff check --no-cache and mypy over src and tests;
mypy cache belongs under apps/trading-core/target/phase143-mypy.

Manifest: SHA256 canonical JSON of envelope.manifest, sort_keys=True,
separators=(',', ':'), ensure_ascii=True, allow_nan=False, must equal fingerprint.
Check every baseline_raw_sha256/artifacts_raw_sha256 using raw bytes. Check pinned
ledgers, all 1115 ordinals and universe. Replay twice without regenerating evidence.
Manifest excludes its own bytes; final report gives an external raw hash.

Run git diff --check, repository scripts/check-secrets.py, canary scan and exact
porcelain inventory. New LF bytes must equal Git clean-filter bytes (hash-object).
No staging. Any failure stops without repairing prior evidence. Future real requests
require separate A/B decisions and security review; this packet cannot dispatch.
