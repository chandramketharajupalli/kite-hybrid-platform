# Phase 14.18B validation

Baseline: bb4622a12bf143cfc9d01c8a3ec8b5d7ef281218 (develop; local origin/develop matches).

Final source validation: 99 focused tests passed and 597 full-suite tests passed. Ruff passed for src, tests and the new verifier using the strategy-engine configuration; mypy passed all 68 source/test files. All tests use Python -B, PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain -p no:cacheprovider, external temporary directories and JUnit. No Java contracts changed.

Failure history is retained: focused-1 had 175 passed and five obsolete-contract failures; amendment 1 updates three regression files. focused-2 passed 180; focused-final passed 184; full-final passed 597. Final review restored an accidentally re-encoded Unicode test literal and added explicit core entry-decision metadata, followed by focused-certified (99) and full-certified (597). mypy-1 found two typed-union inference errors; the correction passes. ruff-final found an unused verifier variable; subsequent lint passes. Interactive formatting diagnostics are summarized separately in external tooling-notes.json. Original receipts and draft examples were not overwritten.

The 09:35 entry now remains quantity 10 at price 102 whether the unfinished candle later reports volume 10,000 or 100. Both corrected runs close at EOD with cash 9998.91520960, fees 1.08479040 and no positions/reservations. Zero-volume EOD retains quantity 10 and cash 8979.56980480, with fees 0.43019520, no reservation, paper HALT and incomplete status. No false closure or realized profit is emitted.

New tests cover previous-volume sizing, fixed all-or-none quantities, availability timestamps, missing/stale volume, future volume/high/low invariance, stop/target collisions and gaps, EOD, failed exits, crash/replay, corruption and incompatible execution policies. Existing portfolio regressions cover reservation release, exposure and daily-loss HALT. Canonical risk policy v2 is unchanged.

All 1,255 baseline hashes were verified before edits. Eight authorized files change (five source, three regression tests); all other 1,247 remain byte-identical. The eight originals are archived externally. Historical reports/manifests, G1, 18 frozen source hashes, 63 research files, K/L governance and 13 newline reconciliations are preserved. All protected hashes are checked after every recorded gate.

The evidence manifest binds source hashes, original bytes, software inventory, execution policy, test commands/results, synthetic scenarios and external records. The read-only verifier performs deterministic manifest replays, artifact/LF/Git-byte/whitespace checks and secret/canary scans. Their final receipts and separate certificate are external, avoiding circular manifest hashing.

Old checkpoints are rejected; no automatic migration. Old synthetic results remain historical under different execution assumptions. The previous-volume proxy does not guarantee real liquidity; genuine performance and readiness remain unavailable. Gate A/B NOT_AUTHORIZED, Stage B ZERO_CALL_ABORT, Phase12 BLOCK, H1/H2 FROZEN, July SEALED, operational NO_GO. Zero real Kite requests; no credentials, orders, downloads, staging, commit or push. Phase14.19 remains deferred until separate commit authorization.
