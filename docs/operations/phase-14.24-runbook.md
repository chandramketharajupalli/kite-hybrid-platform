# Phase 14.24 offline runbook

Use the existing strategy-engine virtual environment, Python -B and
PYTHONDONTWRITEBYTECODE=1. Supply only sealed synthetic Phase 14.20 session outcomes and
explicit Phase1424Observation.v1 evidence to phase1424.analytics.exposure. Supply separately
sealed Phase1424IndependentTruth.v1 and an expected alert-ledger SHA-256 to monitoring.
Neither entry point acquires data or executes orders. Call publication only with an external
directory and complete input bindings. Do not publish over existing incompatible evidence.

Before running, verify Git, raw baseline hashes, historical preservation and the excluded
Phase 14.22 plan. Run pytest --assert=plain -p no:cacheprovider with external basetemp and
JUnit paths. Run Ruff --no-cache and mypy --cache-dir outside the repository. Recheck raw
protected hashes after each gate. Preserve failures under unique external receipt names.

Review observed and full exposure separately. A null adjusted metric is not zero performance.
Review late, duplicate and missed alerts separately, and distinguish acknowledgment from
resolution. Review engineering, genuine strategy and operational readiness independently.
The synthetic examples cannot authorize genuine instruments, data, risk limits or trading.

Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN;
July SEALED; operational NO_GO. No broker requests, credentials, network, downloads,
notifications, staging, commit, push, infrastructure or operational HALT changes.
