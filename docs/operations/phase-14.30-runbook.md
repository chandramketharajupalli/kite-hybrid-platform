# Phase 14.30 offline evidence runbook

Use the exact full baseline recorded in the manifest. Verify develop, local origin/develop,
clean index, expected three tracked modifications, exact additive inventory and the
separate unchanged untracked Phase 14.22 plan. Do not stage, reset, restore or clean.

Read-only replay from the repository root requires the existing strategy-engine virtual
environment, PYTHONDONTWRITEBYTECODE=1 and PYTHONPATH pointing to apps/strategy-engine/src:

    apps/strategy-engine/.venv/Scripts/python.exe -B scripts/research/verify_phase1430.py

The replay verifies original protected raw hashes except the three explicitly recorded
changes, all artifact/receipt hashes, retained failures, corrected JUnit totals, provenance
recalculation, pinned resources, wheel RECORD and metadata, archive contents, genuine
qualification family, LF/Git equivalence and secret/canary scans. It does not install
packages or regenerate historical evidence. Missing evidence fails closed.

Two recorded independent replay receipts must have identical stdout bytes. The external
integrity certificate binds these receipts and the final raw/canonical manifest hashes.
A separate command receipt verifies that certificate; it is not an independent signer
or a production attestation. External temporary evidence must remain available.

Build and assessment scripts are evidence generators, not read-only replay commands.
They require a new unique external directory and the explicit frozen plan scope. Never
rerun them over sealed evidence. The current clean-installation result is BLOCKED; do
not copy cached installed packages, recreate missing originals or use network resolution.

Tests use -B, --assert=plain, -p no:cacheprovider and external temporary directories.
Run Ruff from apps/strategy-engine with its pyproject.toml configuration to retain the
existing import-root interpretation. Use an external strict-mypy cache. Java source
and contracts are unchanged. Preserve all initial failures and corrective receipts.

No real Kite access, credential reads, orders, subscriptions, notifications, operational
HALT changes or release authorization follows from any offline result. Operational NO_GO.
