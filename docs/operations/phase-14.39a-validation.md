# Phase 14.39A validation

Independent decision: PHASE_14_39A_PARTIAL. The complete application suite passes; the required full-application Ruff gate fails and actual pytest-worker CPU/peak-RAM measurements are unavailable. No existing source, tests, configuration or historical evidence was modified.

Baseline: 2327f019792f42f4ace67f33a6bf4377f069d85b; develop and local origin/develop agree. Frozen plan SHA-256: ea9bb85a6346a190532a9bcfa8f2c13b079bb6d5c91cd0c074dcc3d6ec0a17e2. External custody: `C:\Users\CHANDR~1\AppData\Local\Temp\phase1439a-asdu_v_x`.

| Gate | Result |
| --- | --- |
| Full collection | 1,231 unique nodes, all 36 test modules; no exclusions, warnings, skips or deselections |
| Full execution | 1,231 passed; zero failures, errors, skips or deselections; exit 0 |
| Runtime | pytest summary 458.61 seconds; JUnit suite time 458.494 seconds; process wall time 459.531 seconds; budget 1,800 seconds |
| Focused regression | 75 passed: 50 Phase 14.39 pipeline and 25 Phase 14.18B causal tests |
| Full-application Ruff | FAIL: one I001 at tests/test_phase1439.py:3; original file preserved |
| Strict application mypy | PASS: 117 source files under unchanged configuration |
| Added replay verifier | Ruff and strict mypy PASS; an initial line-length finding was corrected only in this new unsealed script, with failed receipt retained |
| Historical timeout | FULL_SUITE_TIMEOUT, original 300-second result preserved |
| Protected baseline | 1,548 raw tracked hashes match; Phase 14.22 exception unchanged/untracked |

The full inventory equals the collection inventory in exact order, markers and fixture configuration. Incremental events account for every setup/call/teardown phase; all 1,231 call node IDs are unique and equal the collected node set. JUnit contains all 1,231 cases. stdout/stderr were streamed directly to custody, with complete exit/timestamp/environment receipts. There was one full-suite execution and no retry or budget extension. The initial sequential runner stopped at Ruff; a separate recorded continuation ran the still-unexecuted mypy/full gates without changing their commands or budgets.

Slowest measured tests (seconds):

| Node ID | Setup | Call | Teardown | Total |
| --- | ---: | ---: | ---: | ---: |
| tests/test_phase1417.py::test_qualification_full_matrix_replay | 0.003 | 87.900 | 0.001 | 87.903 |
| tests/test_phase1416.py::test_full_unavailable_family_and_replay | 0.003 | 56.162 | 0.001 | 56.166 |
| tests/test_phase1425.py::test_report_replays | 0.004 | 46.289 | 0.000 | 46.293 |
| tests/test_phase1427.py::test_process_guard | 0.003 | 6.815 | 0.000 | 6.818 |
| tests/test_phase1426.py::test_deterministic_reports | 0.003 | 6.705 | 0.000 | 6.708 |
| tests/test_phase1427.py::test_integrated_scenarios[A] | 6.301 | 0.001 | 0.000 | 6.303 |
| tests/test_phase1427.py::test_deterministic_scenario_replay | 0.003 | 6.282 | 0.000 | 6.286 |
| tests/test_phase1415.py::test_full_universe_rows_and_deterministic_exports | 0.002 | 5.559 | 0.000 | 5.562 |
| tests/test_phase1427.py::test_installed_dependency_integrity | 0.000 | 4.041 | 0.000 | 4.041 |
| tests/test_phase1421.py::test_restart_safe_publication | 0.004 | 3.070 | 0.000 | 3.075 |

Observed bottlenecks are complete family generation/replay, qualification-matrix replay and deterministic report replay. Existing tests exercise 13,380-pair research outputs and filesystem report verification. These durations locate slow test workloads; they are not a profiler attribution of individual internal functions or CPU versus I/O cost. No expensive tests were excluded, mocked or rewritten.

Resource caveat: 93 process snapshots were taken without API errors, but a follow-up process-identity diagnostic confirmed the sampled Popen PID was the 45,568-byte virtual-environment launcher; the interpreter runs as a different PID. Its sampled CPU total 0 seconds and peak working set 5,009,408 bytes must not be described as pytest-worker usage. Actual worker CPU and peak RAM remain null. Original process receipts are unchanged; the unsealed assessment correction and diagnostic are retained separately. No full-suite rerun was made to fill this gap.

External custody footprint increased from 12306582 to 399074651 file bytes during the full run; free disk decreased from 52931891200 to 52503887872 bytes. These are observations of custody files and filesystem capacity, not exclusive process I/O accounting. Synthetic test outputs remain outside the repository. Large test fixtures and caches are retained externally; logs, JUnit, process/configuration receipts, environment inventory and observer scripts are individually hash-sealed.

Offline boundary: static import audit found no application broker/HTTP transport dependencies. Python audit guards deny network connect/DNS/send operations and credential-file reads. One open was denied before access, consistent with the existing negative .env guard fixture; no socket network events succeeded. The log intentionally omits private path values. Isolated -I child tests bypass inherited PYTHONPATH and were separately inspected as offline timezone checks. This does not establish OS-level production egress containment.

Preservation covers frozen G1, eighteen source hashes, sixty-three research files, K/L governance, thirteen newline reconciliations, canonical 1,115-symbol universe and 13,380 combinations. Phase 14.38's two sealed EOF warnings remain unchanged. Phase 14.39 retains its original PARTIAL manifest and incomplete historical timeout receipt. New artifacts use LF, single terminal newline and raw/Git-byte equivalence; scans and deterministic replays are sealed in the final manifest and external certificate.

The replay reproduces the exact Phase 14.39 synthetic pipeline report, strategy registry, calendar, universe and causal policy. Scope remains REPRESENTATIVE_ONE_SYMBOL_INTEGRATION, not FULL_UNIVERSE_END_TO_END_VALIDATION. Synthetic S01-S06 results do not qualify genuine strategies.

Required next decision: authorize a minimal correction to the committed test import order and subsequent verification if desired. No such correction is applied here. Resource sampling should target the actual interpreter process in a future separately decided diagnostic run; current full-suite results are not retroactively relabeled.

Genuine qualified pairs 0; real Kite requests 0; Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase 12 BLOCK; H1/H2 FROZEN; July SEALED; operational NO_GO. No staging, commit, push, credential access, data acquisition, orders, external notifications or deployment.
