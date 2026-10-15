# Phase 14.18 offline portfolio validation

Baseline: 777de800b6ad20acebaa22925f8b34d29020868b, develop = local origin/develop.
All 22 Phase14.17 additions were committed before this phase. No baseline file is
modified; all 1233 tracked hashes are checked after validation gates.
Frozen plan SHA-256: ce9d1d2e45e609f6244295ebb1981e24558a78df457bfd7f9281e0093b1c1f1b.

Final acceptance uses focused-final (49 tests), full-final (547 tests), Ruff over src
and tests, mypy over 65 source/test files, and Ruff on the evidence verifier. Commands,
exit codes, raw stdout/stderr/JUnit hashes and exact final inventory are in the evidence
manifest. The read-only verifier requires all mandatory receipts, zero failures/errors/
skips and matching test totals; this document cannot substitute for those checks.
No Java contracts changed and no Java tests are claimed. No full-suite result from an
earlier phase is presented as a fresh Phase14.18 result.

Earlier attempts are retained externally: 39 then 47 passing focused tests; the first
full run had 545 passing tests. Initial Ruff import/unused-import errors and one mypy
optional-capital test assertion error were corrected only in new source/tests. Final
review added explicit accounting exceptions and rejection of missing completed reports;
the final suite includes both regressions. The earlier full receipt is superseded by
the final run after those changes. No original receipts were overwritten.

All tests use Python -B, PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain with
-p no:cacheprovider and unique external basetemp/JUnit paths. Ruff caching is disabled;
mypy cache and diagnostics are external. Baseline protected caches remain unchanged.

Coverage includes simultaneous scheduling/permutation invariance, duplicate signals,
one symbol across competing strategies, unsupported direction, expiry, sector/correlation
gaps, portfolio/strategy/sector/cash limits, daily-loss HALT, reservations and cancellation,
Decimal precision invariance, target and stop gaps, stop-first collision, EOD closure,
missing delivery, stale qualification, changed risk/data/software/strategy bindings,
invalid/repeated/out-of-order transitions and no broker imports. Crash tests cover
accepted prefixes after proposal, reservation, fill and exit-pending; replay matches
the complete expected journal. Missing/altered/reordered/extra/pending events and a
truncated completed journal reject. A missing completed report is not silently rebuilt.

Synthetic examples contain simultaneous two-symbol trading, missing-EOD delivery and
explicit cancellation. Each bundles its run binding, journal, summary and twelve
deterministic report types: signal decisions, orders, positions, cash, equity, daily/
strategy/symbol realized net P&L, exposure, rejection, HALT and EOD reconciliation.
The missing-EOD case retains its unresolved position and reports HALT/incomplete;
the cancellation case releases its reservation without filling or changing cash.
Normal completion reconciles cash, fees and positions. These are synthetic mechanics,
not genuine trading profits or measured live-market throughput.

The previous 13380-row genuine-data qualification ledger is preserved and rechecked:
all DATA_UNAVAILABLE, null performance, zero genuine qualified pairs. Phase14.18 does
not redefine the 1115-stock/six-strategy/two-window family or invent new market results.
Its readiness report remains BLOCKED for missing genuine history, mapping and approved
portfolio settings. Test-only sector classifications and limits are not real approvals.

Two deterministic evidence replays must match byte-for-byte before certification. The
manifest binds the exact new-file inventory, dependencies, plan and external evidence;
its own canonical fingerprint avoids circular hashing. A separately named external
final-certificate.json records its raw hash, Git blobs and replay results. Its runner
receipt has a different name. Missing external evidence is never silently accepted.
Phase14.17's overwritten original certificate remains unavailable and is explicitly
covered by its existing additive Phase14.17A certificate, not reconstructed here.

Final gates verify G1, 18 frozen source hashes, 63 protected research records, K/L,
historical manifests, all 13 newline reconciliations, LF/Git-byte equivalence, exact
Git inventory and secret/canary scans. No unrelated changes or staged files are allowed.
Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN;
July SEALED; operational NO_GO. Real Kite requests: zero; no credentials, orders,
downloads, infrastructure work, operational HALT changes, staging, commit or push.
