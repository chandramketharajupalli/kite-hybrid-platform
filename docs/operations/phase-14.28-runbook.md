# Phase 14.28 offline package runbook

Never start the production application. Keep Gate A/B NOT_AUTHORIZED, Stage B
ZERO_CALL_ABORT, Phase 12 BLOCK, H1/H2 FROZEN, July SEALED and operational NO_GO.
No credentials, broker/network calls, historical downloads, notifications or orders.

Use the existing Python environment with PYTHONDONTWRITEBYTECODE=1 and Python -B.
Build using scripts/research/build_phase1428.py with a NEW empty external directory.
The tool selects explicitly hashed source/resources, cached Hatchling 1.27.0, and fixed
SOURCE_DATE_EPOCH. It never invokes a resolver or pip bootstrap. It builds two wheels
and source archives, checks wheel RECORD, creates a fresh venv without pip, installs
verified wheel members and copies verified installed runtime dependencies. The one
unused dotenv console launcher is deliberately omitted. Original dependency archive
provenance remains a separate question. Preserve failures; use a new output directory
for corrective builds rather than overwriting failed evidence.

The isolated child uses -I -B, an unrelated CWD, empty PYTHONPATH, read-only package
resources and an audit hook denying checkout reads, .env reads, sockets and subprocesses.
Call strategy_engine.phase1428.runtime.assess() for the supported synthetic assessment.
No monkeypatch redirects historical research APIs. The returned signals are synthetic
outputs, not genuine recommendations, fills, performance or qualification evidence.

Run focused/full pytest with --assert=plain -p no:cacheprovider, external --basetemp and
JUnit output, Ruff --no-cache and strict mypy with external cache. Verify all baseline
hashes and the separate Phase 14.22 plan after each gate. Never run secret scans over .env.
After sealing, run scripts/research/verify_phase1428.py twice; compare canonical output
bytes. The verifier does not rebuild or replace historical evidence. Retain external
certificate and separate verification command receipt. Missing evidence fails closed.

Technical recommendation remains OFFLINE_RC_CONDITIONAL. Obtain original archive bytes
matching trusted lock digests offline before claiming provenance closure. Broader
standalone historical API support requires a separately reviewed scope. Physical symlink
escape validation needs suitable Windows permissions; OS egress, independent custody,
power-loss durability and multi-host guarantees remain unestablished. No owner approval
or live-trading authority follows from these checks.
