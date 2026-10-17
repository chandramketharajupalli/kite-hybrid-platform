# Phase 14.25 offline reliability runbook

Use the existing strategy-engine virtual environment, Python -B and
PYTHONDONTWRITEBYTECODE=1. Run tests/test_phase1425.py with pytest --assert=plain
-p no:cacheprovider, an external --basetemp and external JUnit receipts. Run the
full Python tests, Ruff --no-cache and strict mypy with an external cache.
No Java contract changes are authorized or necessary.

Read the sealed evidence manifest and run scripts/research/verify_phase1425.py
with the strategy-engine virtual environment. It verifies protected and new hashes,
receipts, policies and deterministic reports; outputs remain in an external folder.
Do not regenerate or repair historical artifacts. Stop on preservation mismatch.

Keep certificate output and command receipts separate from the replayed manifest.
Two independent output replays must agree. A PARTIAL result retains missing-alert
and complete-authority rollback limitations. No synthetic result advances genuine
qualification or operational authorization. Do not clear operational HALT, acquire
credentials, access brokers, send alerts, stage, commit, push or deploy.
