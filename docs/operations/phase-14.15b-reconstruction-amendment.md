# Phase 14.15B additive reconstruction amendment

Baseline: 1cad5f0f4d587837678e41cc3c64780a065a4edd, develop, matching local origin/develop.
All 1150 tracked raw hashes pass. Original frozen plan and IST amendment recovered only
after matching their recorded hashes. Diagnostic inventory is immutable; original transcript,
IDE storage and temporary CSV copies are untouched. External diagnostic snapshots are local
private recovery aids, not repository artifacts or a claim of independent immutable custody.

Reconstructed Python is NEW IMPLEMENTATION, not asserted byte-identical recovery. Use recorded
creation literals, reviewed sequential final edits and cached earlier revisions for comparison.
Retain reconstructed preliminary UTC-window reports as SUPERSEDED; corrected IST reports are
authoritative. Compare both JSON hashes with recorded history. Benchmark is a new measurement.

Concrete correction beyond recorded edits: ResearchWindow's direct constructor must reject
non-IST-midnight boundaries, not only its standard_window factory. Add fresh tests for this
and persisted software/strategy version mismatch denial. No existing tracked source changes.

Tests use PYTHONDONTWRITEBYTECODE=1, Python -B, external PYTHONPYCACHEPREFIX,
pytest --assert=plain -p no:cacheprovider and fresh external basetemp/JUnit output paths.
Ruff uses --no-cache; mypy cache is external. Stop on any baseline byte change. Preserve fresh
failures and historical fixture failures separately. No Java changes or new network dependency.

Deliver original reconstructed package/tests/docs/reports plus this amendment, fresh tests,
immutable diagnostic inventory, reconstruction validation, test results, exact inventory,
and additive fingerprinted evidence manifest. Manifest hashes all artifacts except itself;
its payload fingerprint and two read-only replay results establish deterministic integrity.
No credentials, Kite calls, staging, commit or push. Gate A/B NOT_AUTHORIZED; NO_GO.
