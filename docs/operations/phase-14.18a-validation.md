# Phase14.18A correction validation

Baseline: 0a608de56d969ecd6b6d10a7eda799c70e036ae1, develop = local origin/develop.
Frozen plan SHA-256: bed3ef61b3537f550947e219689b71cb14fc19d315d2cf863939f749d912a20c.
Preflight verified the 1233 previous baseline hashes and all 12 committed Phase14.18
artifacts. Current baseline has 1245 files; exactly two tracked edits are authorized:
phase1418/portfolio.py and tests/test_phase1418.py. All other 1243 hashes must stay fixed.
Original bytes for both edited files were archived externally before implementation.

Final mandatory receipts: focused-1 (74 tests: 49 existing portfolio regressions plus
25 canonicalization/compatibility cases), full-1 (572 tests), Ruff src/tests, mypy
src/tests (67 files), report generation and verifier Ruff. Receipt hashes, commands,
raw exits, JUnit totals and stdout/stderr are in the additive manifest. Acceptance
requires zero failures/errors/skips; historical receipts are not fresh correction tests.
The independent evidence verifier recomputes all three policy fingerprints from the
serialized policy, rather than only checking that recorded strings match each other.

Focused coverage includes sector and nested map ordering, Unicode, significant sequence
order, Decimal precision/representation equivalence and exactness, repeated hashing,
changed limits, unsupported/nonfinite values, missing/unknown versions and v1 checkpoint
rejection without writes. Three corrected example tests serialize/deserialize policy
and control, verify every journal fingerprint and replay the complete run. Existing
accounting, paper HALT, EOD, interrupted restart, corrupted journal and risk tests remain.

Python -B and PYTHONDONTWRITEBYTECODE=1 apply throughout. Pytest uses --assert=plain
-p no:cacheprovider with unique external basetemp/JUnit paths; Ruff caching is disabled
and mypy cache is external. No Java changes/tests, broker calls or genuine-data work.
Any failed attempts remain external and are never overwritten by successful reruns.

The original fingerprint discrepancy was reproduced before changes. Corrected payload
SHA-256 is 3dd7a1cf99042ce027011f6a99ca1f298ab62c6254bc85e584171250a925c7d1.
Schema is Phase1418RiskPolicyCanonical.v2. Original evidence is retained, not rewritten
as a successful v2 run. Old checkpoints reject, and unknown fingerprint versions fail
closed. Corrected examples live only under Phase14.18A with separate external stores.

The final manifest records inventory, before/after hashes, software dependencies,
canonical payload, test records and preserved history. Two deterministic verifier
outputs must match. The external detailed certificate and command receipt use distinct
filenames and are checked independently. Final checks include all protected historical
hashes, 13 newline reconciliations, G1/frozen research/K/L/manifests, LF/Git bytes,
whitespace, exact modified/untracked inventory and secret/canary scans.

The existing 13380-row genuine-data ledger remains unavailable with null metrics and
zero genuine qualified pairs. Synthetic limits and results confer no trading approval.
Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN;
July SEALED; operational NO_GO. Real Kite requests zero. No staging, commit or push;
Phase14.19 remains deferred until correction validation and separately authorized commit.
