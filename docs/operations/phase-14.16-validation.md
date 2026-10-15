# Phase 14.16 fresh offline validation

Git baseline: `91760b073ee39ce62eb42ef041e8b75d9650e44f`, develop and origin/develop.
All 1,189 baseline tracked files remain unchanged. The index remains untouched.
Preflight verified all committed Phase14.15 artifacts, original archived files,
1,150 original baseline hashes, 13 newline reconciliations, G1, 18 frozen sources,
63 protected research files, K/L, historical manifests and 1,115-symbol universe.
Phase14.15D's 147/429 receipts were checked as historical evidence only.

Frozen plan SHA-256:
`673415cbc31efe651f4d0be4f7c9f6377bafa2d7bff7f6ee46e57fb5b08ac310`.
Current software inventory fingerprint:
`33fc2643665e8dc2caca027407827373d9eb67ce2ce0493985b8085173ad9dbb`.
Full-family qualification policy fingerprint:
`aaf9b9eeebf23816728174b99a8dec2584205a9d12b9d7bf131c645df9ffedde`.
Unavailable-data job ID:
`27983605697a73774a1e6782d562942247de431c085ea820d26ddb69efb7f136`.

## Fresh checks

- Focused: 32 passed, zero failures/errors/skips, focused-certified receipt.
- Full Python suite: 461 passed, zero failures/errors/skips, full-certified receipt.
- Ruff: src/tests and the new evidence verifier passed with caching disabled.
- Mypy: 58 source files passed, external cache.
- Synthetic integration: accepted six-/twelve-month imports, all six strategies,
  12 SYNTHETIC rows and 13,368 UNAVAILABLE rows, zero qualified; deterministic replay.
  S06 entered at 102 at 04:05 UTC and exited EOD at 102 at 04:15 UTC; net after
  hypothetical costs was -0.10847904. Altering only the last candle preserved the
  earlier signal, entry time and entry price. This is test performance only.
- Independent unavailable-data run: 13,380 rows, 6,690 per window, 1,115 symbols,
  zero qualified and all performance metrics null. Replay matched. Fixed evaluation
  end is 2026-10-01 IST midnight; observation time is 2026-10-15 UTC.
- Python -B, PYTHONDONTWRITEBYTECODE=1, plain pytest assertions, no cache provider,
  external temporary/JUnit/diagnostic locations throughout. No Java changes or Java tests.
- Baseline raw hashes checked after every major gate. No protected bytes changed.

Raw receipts, failed attempts, temporary data and checkpoints remain under
`C:/Users/CHANDR~1/AppData/Local/Temp/phase1416-c1j54ocj`.
The evidence manifest pins commands, exits, JUnit, stdout/stderr and scripts.
The read-only verifier checks report completeness, exact new-file inventory,
LF/Git-byte equivalence, historical preservation and secret/canary scans.
Two post-seal deterministic replay results and their raw stdout hashes are recorded
in that external directory's `certification.json`, outside the self-hashed manifest.

## Preserved failures and corrections

The first diagnostic run passed 23 tests before coverage expansion; it is not the
certification result. Initial Ruff diagnostics found formatting/import-order issues.
The expanded run had 27 passes and two fixture failures: its August dataset cutoff
could not cover an October window. The two corrected fixture cases passed.
The next run had 31 passes and one fixture failure: generated calendar times included
seconds rather than the Java contract's HH:mm representation. Its corrective case
passed. The first full run passed 461, but mypy rejected a test's indirect import;
the test now uses the direct evaluator import. Final focused/full/tool reruns passed.
No frozen implementation or historical evidence was changed to fix these failures.

## Exact additive inventory

All entries are new, unstaged files; raw hashes and sizes are in the manifest.

```text
apps/strategy-engine/src/strategy_engine/phase1416/__init__.py
apps/strategy-engine/src/strategy_engine/phase1416/imports.py
apps/strategy-engine/src/strategy_engine/phase1416/jobs.py
apps/strategy-engine/tests/test_phase1416.py
docs/architecture/phase-14.16-offline-data-orchestration.md
docs/operations/phase-14.16-data-quality-policy.md
docs/operations/phase-14.16-plan.md
docs/operations/phase-14.16-runbook.md
docs/operations/phase-14.16-validation.md
research/phase-14.16/generation-1/checkpoint-ledger.json
research/phase-14.16/generation-1/coverage-report.json
research/phase-14.16/generation-1/dataset-quality-report.json
research/phase-14.16/generation-1/evidence-manifest.json
research/phase-14.16/generation-1/import-ledger.json
research/phase-14.16/generation-1/job-plan.json
research/phase-14.16/generation-1/qualification-report.json
research/phase-14.16/generation-1/rejection-report.jsonl
research/phase-14.16/generation-1/run-summary.json
research/phase-14.16/generation-1/stock-strategy-results.csv
research/phase-14.16/generation-1/stock-strategy-results.json
research/phase-14.16/generation-1/synthetic-example.json
scripts/research/verify_phase1416.py
```

No genuine authorized local data, dated genuine master, authoritative exchange calendar
or corporate-action package was supplied. Those are prerequisites for genuine research,
not evidence supplied by a synthetic test. Permission records require substantive
operator review; hashes provide consistency, not authenticity. Crash locks and partial
publication remain fail-closed for operator inspection. No synthetic throughput claim
is made for genuine 1,115-stock histories. Paper/live execution is not enabled.

Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase 12 BLOCK; H1/H2 FROZEN;
July SEALED; operational NO_GO. Real Kite requests: zero. No staging, commit or push.
