# Phase 14.31 Gate 31-A runbook

The repository baseline and all 1,412 tracked raw hashes must match the evidence manifest.
Only the exact additive Phase 14.31 inventory and unchanged Phase 14.22 plan may be
untracked. No tracked source, dependencies, lockfile or historical evidence may change.

Read-only replay from repository root, using the existing strategy-engine interpreter
and PYTHONPATH pointing to apps/strategy-engine/src:

    PYTHONDONTWRITEBYTECODE=1
    apps/strategy-engine/.venv/Scripts/python.exe -B scripts/research/verify_phase1431.py

This verifies artifact/packet fingerprints, recomputes the closure and compatible wheel
selection from local evidence, checks authorization remains PENDING, validates all sealed
receipts and protected hashes, verifies LF/Git-byte equivalence and scans for secrets and
canaries. It neither acquires nor installs artifacts. Missing evidence fails closed.

The preparation generator writes only new packet files to a unique external directory.
It must not overwrite sealed outputs. It does not create the proposed quarantine, contact
registries, install packages or modify caches. Do not run hypothetical Gate 31-B or 31-C
steps until separate owner decisions and the required trust evidence have been recorded.

Tests use Python -B, PYTHONDONTWRITEBYTECODE=1, --assert=plain, -p no:cacheprovider and
external temporary paths. Run Ruff --no-cache from apps/strategy-engine using its
pyproject configuration; strict mypy uses an external cache. No Java changes or claims.
Preserve all failed attempts. Two independent manifest replays must return identical
stdout, bound by an external integrity certificate and separate command receipt.

The certificate is not an owner signature, trusted registry attestation or authorization.
No successful synthetic test changes PENDING, installation NOT_AUTHORIZED or operational
NO_GO. Stop at Gate 31-A. Do not stage, commit, push or access credentials or Kite.
