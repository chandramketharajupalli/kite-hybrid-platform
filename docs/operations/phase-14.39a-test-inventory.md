# Phase 14.39A complete collection inventory

Scope: all apps/strategy-engine/tests under the unchanged application pyproject.toml. Exactly 1231 unique test node IDs across 36 modules. No filters, exclusions, marker selection or slow-test deselection. Collection completed in 2.547 seconds, exit 0, with zero collection warnings, errors, skips or deselections. Collection is not test execution; its JUnit contains no executed cases.

Exact node IDs, module paths, per-node markers and fixture names are retained in collection.inventory.json under `C:\Users\CHANDR~1\AppData\Local\Temp\phase1439a-asdu_v_x`. Raw SHA-256: 80f8308035da99ea1500950bf287ccb622217c155459e5221a550c3f2cdaf0cc. Collection stdout, stderr, command, environment controls, JUnit and final collection session receipt are retained separately. Runtime identity and all installed dependency versions/fingerprints are in environment.json. The run uses existing Windows CPython 3.12; no package installation or resolution occurs.

The external observation plugin records results without changing test selection or assertions. Automatic external plugin loading is disabled; default pytest plugins and the explicit observer are recorded. PYTEST_ADDOPTS is removed. Assertion mode is plain; cacheprovider and bytecode writes are disabled. Exact fixture names are recorded as configuration evidence, not fixture values. Inherited Python subprocesses receive the offline guard; isolated -I subprocesses ignore PYTHONPATH and were statically inspected (timezone/package checks), so this is not a claim of OS-level egress containment.

| Module | Collected |
| --- | ---: |
| tests/test_architecture.py | 2 |
| tests/test_backtest.py | 64 |
| tests/test_candidates.py | 17 |
| tests/test_continuity.py | 18 |
| tests/test_contracts.py | 12 |
| tests/test_diagnostics.py | 9 |
| tests/test_multi_instrument.py | 26 |
| tests/test_phase121a.py | 5 |
| tests/test_phase121b.py | 27 |
| tests/test_phase121c.py | 25 |
| tests/test_phase14.py | 32 |
| tests/test_phase1415.py | 48 |
| tests/test_phase1415b.py | 4 |
| tests/test_phase1415d.py | 95 |
| tests/test_phase1416.py | 32 |
| tests/test_phase1417.py | 37 |
| tests/test_phase1418.py | 49 |
| tests/test_phase1418a.py | 25 |
| tests/test_phase1418b.py | 25 |
| tests/test_phase1419.py | 54 |
| tests/test_phase1420.py | 38 |
| tests/test_phase1421.py | 33 |
| tests/test_phase1423.py | 44 |
| tests/test_phase1424.py | 56 |
| tests/test_phase1425.py | 48 |
| tests/test_phase1426.py | 63 |
| tests/test_phase1427.py | 18 |
| tests/test_phase1428.py | 26 |
| tests/test_phase1429.py | 26 |
| tests/test_phase1430.py | 22 |
| tests/test_phase1431.py | 51 |
| tests/test_phase1432.py | 49 |
| tests/test_phase1433.py | 56 |
| tests/test_phase1439.py | 50 |
| tests/test_research.py | 37 |
| tests/test_walkforward.py | 8 |

Marker occurrences: `{"parametrize": 889}`.

Fixture-name inventory: `a, above, accepted, anomaly, b, bad, bar_number, below, c, candidate, case, change, changes, checkpoint, classification, close, confirmation, count, crash, d, definition, entry_minute, expected, fault, field, fills, fixture, gap, hi, host_tz, hours, hypothesis, index, inside, integrated, key, kind, lo, locked, missing, mode, monkeypatch, month, months, mutation, n, name, observed, offsets, op, operation, path, platform, price, prices, raw, reason, reference, request, requests, result, sample, scenario, scope, severity, signed, status, suffix, tamper, tmp_path, tmp_path_factory, updates, value, values, version, volume, which, wins`.

Final certification reconciles this exact ordered node inventory with the full-run inventory, incremental setup/call/teardown events, terminal counts and JUnit test cases. Any missing or unexpected node blocks full-suite certification.
