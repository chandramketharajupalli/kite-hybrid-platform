# Phase 14.44E-N frozen preparation plan

Baseline 9bd13a1c36e9696c22c86bbb2a28a0bddf9748ef; develop equals local origin/develop.
1818 tracked hashes captured; predecessor E-M manifest cf9c2198bde0fab6dc2ef93f36f4f77cf19f99c97b210242796769b4c7b61f12, certificate 3004ac8cf3263c2329f3bd317e47793bcd957b7f62c94e217310e2de13bf89f1, replay d2468df28a705a75cdc4bcae81cc2247b58c1074515602a5e167766a1623ba9e verified. Partial history remains partial, including helper mojibake U+00BB, WinError5/31, conhost rejection and unverified containment.

Read-only local discovery: Get-ItemProperty HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion (EditionID, ProductName, DisplayVersion, CurrentBuild, UBR); Get-CimInstance Win32_OperatingSystem (Caption, Version, BuildNumber, OSArchitecture); Win32_Processor (Architecture, VirtualizationFirmwareEnabled, VMMonitorModeExtensions, SecondLevelAddressTranslationExtensions); Win32_ComputerSystem (HypervisorPresent); Win32_OptionalFeature filtered Hyper-V, Containers, Sandbox/DisposableClientVM, VirtualMachinePlatform and WSL; Get-Service vmms,vmcompute,com.docker.service; Get-Command docker.exe,VBoxManage.exe,vmrun.exe; metadata only for standard installation paths; WindowsPrincipal IsInRole Administrator; Get-Volume D. No Docker daemon/context connection, no private settings or VM inventory credentials, no remote CIM, elevation or feature enablement. Unsafe/unavailable queries become UNVERIFIED; no retry or privilege escalation. Presence is not approval.

Assess A current workstation, B dedicated host, C remote host, D Windows containers, E deferral. Select only evidence-backed concrete candidate; otherwise DEFER_PENDING_APPROVED_INFRASTRUCTURE. No unsupported compatibility/licensing claims. Exact guest OS/build/source/hash/signing/patch/licensing/provider/architecture/offline/revocation fields remain null when unknown. CPython3.12 guest executable/DLL/stdlib/packages/archive closure independently required; host interpreter alone insufficient.

Controls: guest-owned disposable filesystem, read-only byte-identical source, no host sealed custody/repository writes/credential files/production database, reparse protection and independent write inventory. Network disabled at independently enforced boundary, no DNS/NAT/bridge/proxy/reenablement. No Kite/DB/profile credentials, .env or credential-store inheritance. Exact executable/PID+creation/owned-job child identity, suspended assignment before resume where applicable, owned-only timeout cleanup, no unrelated termination. CPU/memory/process/output/storage enforcement must be distinguished from measurements.

Proposed future resource ceilings inherited from M: 4 processes,3 children,300s wall,120s CPU,512MiB memory,8MiB output,64MiB writes,0 retries. Not approved or enforced. No infrastructure qualification command can be finalized without a selected provider; command fields remain null and packet ineligible for execution approval.

Freeze original46 IDs/source hashes below; pytest --import-mode=importlib and both original modules required. Exact proposed pytest/Ruff/strict-mypy argument arrays and source scopes inherited unchanged from M approval, retained externally as proposed-validation-scope.json (NOT_AUTHORIZED). Fresh external mypy cache, no NUL cache. Schema scope: owner authority,trusted time,replay authority,lease,resource,cleanup,pending decision. No original46/18/31 tests, pytest/Ruff/mypy subprocesses or native supervisor.

Evidence transfer design: guest creates immutable manifest/JUnit/raw stdout/stderr/process/resource/failure receipts; host pulls approved files into fresh quarantine through separately authorized controlled one-way export; hash/manifest/size/type reconciliation; no guest writable sealed host share; host alone seals imported evidence. Preserve crashed guest evidence under separate authority, never overwrite; revoke lease; approved shutdown/destruction and verification of no residual resources, no automatic retry.

Preparation: compile only new verifier in memory, static API audit, JSON schema and positive/negative in-memory policy checks. Two independent verifier calls in fresh namespaces in existing maintenance interpreter, no child process; each has completion receipt and identical canonical output. No OS containment claim. Discovery command budget60s per query, preparations60s per invocation, no timeout termination of unowned processes. Mandatory failure stops, preserve partial receipts, no automatic retries. Capability query restrictions are recorded rather than escalated.

Custody D:/Yogendra/offline-validation-evidence/phase-14.44E-N/generation-1 fresh,reparse-free,outside repo/predecessors,existing restricted operator/SYSTEM/Administrators ACL inherited unchanged,at least1GiB free. User authorizes this preparation custody. Exclusive new writes only. Freeze this plan before other artifacts; any correction requires additive amendment and scope approval if beyond12paths.

Final checks: exact12 new files plus unchanged untracked Phase14.22, all1818baseline and predecessor hashes,13newline reconciliations,1115symbols/13380family frozen,73 HISTORICAL_RECEIPTS_UNAVAILABLE, LF/Git bytes/whitespace/secrets. No credentials,network requests,provisioning,remote connections,dependencies,owner enrollment,real authority,Seriesv2,staging/commit/push. GateA/B NOT_AUTHORIZED, StageB ZERO_CALL_ABORT, Phase12 BLOCK,H1/H2 FROZEN,July SEALED,NO_GO.

Canonical JSON sorted compact ensure_ascii=True allow_nan=False UTF8. Manifest fingerprints body; self raw hash in separate certificate. Replay inputs exclude later receipts/manifest/certificate to avoid cycles.

## Exact permitted additions

docs/operations/phase-14.44en-plan.md
docs/operations/phase-14.44en-host-capabilities.md
docs/operations/phase-14.44en-infrastructure-options.md
docs/operations/phase-14.44en-isolation-selection.md
docs/operations/phase-14.44en-guest-image.md
docs/operations/phase-14.44en-runtime-closure.md
docs/operations/phase-14.44en-containment-requirements.md
docs/operations/phase-14.44en-validation.md
research/phase-14.44en/generation-1/infrastructure-assessment.json
research/phase-14.44en/generation-1/infrastructure-approval.json
research/phase-14.44en/generation-1/evidence-manifest.json
scripts/research/verify_phase1444en.py

## Exact original 46 node IDs

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

## Original test source hashes

{
  "apps/strategy-engine/tests/test_phase1444ej.py": "5239472a4ba542fcc6d64e35593a2e88e9e1632579bb47d6f53740f25ba466a2",
  "scripts/research/test_phase1444ej.py": "c4bcc883ecda5f86601983d0ed95e38a2aac46d1b1ba9e97f7566fcefa81cf4d"
}
