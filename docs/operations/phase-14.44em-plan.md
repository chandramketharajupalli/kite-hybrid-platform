# Phase 14.44E-M frozen preparation plan

Baseline: 265fe3aa48aa1d9c8f4bbe1a1579a0d0aebbf9c5, develop equals local origin/develop.
All 1806 tracked raw hashes captured. E-L manifest d9f818aa4bc733e97563ddcc2fab127e3b611d511244f1e3952ec19902f71762 and external certificate ad2a49171a61cd924285603bb7b5024a1b7bed79a5873b502aef721d8182f33a verified. Replacement helper b0a1b6ee6200609b14256afbca2a99d24d89a42f3e26d31fdd715180eaf2c20a remains passive.

Historical external K capture.py begins c3 af c2 bb c2 bf (valid UTF-8 literal mojibake, not BOM); U+00BB SyntaxError exit 1. Original repository collector unchanged. Original J module-name collision remains historical. The exact 46 IDs below are source-reconciled, never runtime-collected or executed.

Architecture: assess E-E/F native jobs, separate job runner, isolated guest and deferral. Select DEFER_PENDING_APPROVED_ISOLATION_HOST: existing jobs do not establish filesystem/network/credential restrictions. No qualified guest. New runner_policy.py is a pure model, not a runner. No process/native/network/filesystem/credential APIs; real authorization always False.

Process requirements: executable path and SHA pin; suspended creation; job assignment before resume; retained handle, PID plus creation time, parent identity and owned job membership; no PID-only termination; owned-job-only cleanup; no silent retries; partial failure receipts; access-denied versus exited state distinguished. These remain design requirements, not live qualification.

Containment: independently enforced repository read-only, sealed custody exclusion, no production databases/credential files, disposable writes with inventory/reparse rejection; no network/DNS/NAT/proxy or reactivation; no inherited Kite/database/profile secrets or host credential store. Sanitized environment alone is insufficient. No security-policy changes. Trust, trusted time and replay authority remain unavailable.

Future command proposals: pinned existing interpreter, -B -m pytest --import-mode=importlib -p no:cacheprovider --collect-only -q followed by both original modules; execution with --junitxml in separately authorized fresh output custody. Ruff check --no-cache --config apps/strategy-engine/pyproject.toml; mypy --strict --namespace-packages --explicit-package-bases --config-file apps/strategy-engine/pyproject.toml --cache-dir fresh external directory. Exact argument arrays and source scope retained in pending packet; no PYTHONPATH override or configuration edits. No tool execution through a runner in this phase.

Proposed future maxima: processes 4, children 3, wall 300 seconds, CPU 120 seconds, working set 512 MiB, output 8 MiB, writes 64 MiB, retries 0. Not approved or OS-enforced. Job process/CPU/memory mechanisms require independent qualification; working-set observations are not memory enforcement. External independently enforced storage/output/network controls required.

Preparation budget: 31 negative policy cases plus one positive synthetic binding, in memory only; 60 seconds aggregate, no child creation. Execute direct function calls in maintenance interpreter, no pytest. Compile source in memory, no pyc. JSON contract schema checked in process. Two independent verify() invocations with separate completion receipts in the same maintenance interpreter, no subprocesses; explicitly not process-supervised replays. Ruff/mypy NOT_EXECUTED; containment gap requires PARTIAL. No original46/18/31 tests.

Custody: D:/Yogendra/offline-validation-evidence/phase-14.44E-M/generation-1; fresh, outside repo and sealed predecessors, restricted inherited operator/SYSTEM/Administrators ACL, no changes to existing ACLs, capacity above 1 GiB. Retain baseline, frozen plan, static/schema/policy receipts, verification inputs, two replay outputs/receipts, preservation and certificate. New files exclusive-create; no overwrite of predecessor evidence.

Stop on changed baseline, hashes, custody or unexpected native/network/credential behavior; no retries after mandatory failure. Additive amendment required for plan changes. Rollback only future separately authorized disposable assets; no historical edits. No staging/commit/push, guest, owner enrollment, amendment or Series v2. Preserve 73 HISTORICAL_RECEIPTS_UNAVAILABLE, 13 newline reconciliations, 1115-symbol/13380-combination freeze, Phase14.22 unchanged/untracked, Gate A/B NOT_AUTHORIZED and NO_GO.

Canonical JSON: sorted keys, compact separators, ensure_ascii True, allow_nan False, UTF-8. Manifest hashes body only, self raw hash in external certificate. Replay output excludes its own receipt and certificate to prevent hash cycles.

## Exact additive inventory

apps/strategy-engine/src/strategy_engine/phase1444em/__init__.py
apps/strategy-engine/src/strategy_engine/phase1444em/runner_policy.py
apps/strategy-engine/tests/test_phase1444em.py
docs/architecture/phase-14.44em-runner-boundary.md
docs/operations/phase-14.44em-plan.md
docs/operations/phase-14.44em-containment-assessment.md
docs/operations/phase-14.44em-pytest-authorization.md
docs/operations/phase-14.44em-validation.md
research/phase-14.44em/generation-1/runner-assessment.json
research/phase-14.44em/generation-1/pytest-approval.json
research/phase-14.44em/generation-1/evidence-manifest.json
scripts/research/verify_phase1444em.py

## Exact source-reconciled 46-test inventory

apps/strategy-engine/tests/test_phase1444ej.py::test_security[owner]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[role]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[root]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[signature]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[payload]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[baseline]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[integration]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[policy]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[supervisor]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[helper]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[command]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[missing_test]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[duplicate_test]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[changed_tests]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[limits]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[process_limit]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[cpu_limit]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[memory_limit]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[output_limit]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[cleanup]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[trusted_time]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[expired]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[future]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[clock_rollback]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[revoked]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[duplicate_decision]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[duplicate_nonce]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[concurrent]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[crash_before_reserve]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[crash_after_reserve]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[crash_before_consume]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[crash_after_consume]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[ledger_rollback]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[checkpoint]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[invalid_transition]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[promotion]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[native]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[live]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[database]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[series]
apps/strategy-engine/tests/test_phase1444ej.py::test_security[kite]
apps/strategy-engine/tests/test_phase1444ej.py::test_happy_fixture_and_consumption_barrier
scripts/research/test_phase1444ej.py::test_no_authority_from_any_fixture_state
scripts/research/test_phase1444ej.py::test_fixture_immutable
scripts/research/test_phase1444ej.py::test_revocation_before_consumption
scripts/research/test_phase1444ej.py::test_clock_forward_jump
