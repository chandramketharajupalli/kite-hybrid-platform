# Phase 13.16 read-only reproduction

Read [frozen plan](phase-13.16-plan.md), [measurements](phase-13.16-host-measurements.md),
[hypervisor decision](phase-13.16-hypervisor-feasibility.md), [gate matrix](phase-13.16-security-gate-matrix.md)
and [DRAFT packet](phase-13.16-provisioning-authorization-request.md).

Verify the recorded baseline/protected hashes before reproducing; do not repair a
dirty/mismatched tree. No fetch/elevation/install/feature change. Execute only the
plan's selected CIM/PowerShell/WSL/Docker metadata commands. Capture UTC start/end,
units, selected fields and error type/category/code. No host identity, argv, environment,
keys or raw Docker configuration. Querying a command does not authorize enabling it.

Three memory/CPU samples are snapshots, not a stress test. Apply frozen reserves
before viewing the outcome:14GiB available RAM;4cores/8logical and<50% sampled load;
D180GiB at measured disk size, C20GiB. Do not reduce the VM or reserves to manufacture
a positive result. Command failures remain UNKNOWN; continue independent observations
without elevated retries. No workload is created to obtain utilization metrics.

All guest enforcement gates remain NOT_VERIFIED. Retain distinction between Hyper-V
edition support, HypervisorPresent, WSL utility virtualization and a configured isolated
guest. Do not infer firmware-disabled or absent silicon from false exposed flags under
an active hypervisor. Unknown vendor tools outside command discovery remain unknown.

Run repository validation only:

```powershell
uv run --offline python scripts/verify-project.py
uv run --offline python scripts/check-secrets.py
git diff --check
git status --short --untracked-files=all
git diff --name-status
git diff --cached --name-status
```

Check local links, G01-G15 one-to-one coverage, JSON required fields/types, redaction,
new-artifact canaries and exact eight-file inventory. Replay manifest twice using
SHA256 of sorted compact ASCII JSON manifest payload, ensure_ascii=True,
allow_nan=False,UTF-8/no newline, excluding envelope fingerprint. Rehash linked raw
inputs and18 registered universal-newline sources. No Java/Python suite/Ruff/mypy;
no executable changes. Keep failed inspections, not just aggregate shell exit0.

Stop after HOST_FEASIBILITY_INCOMPLETE report and packet. A separately authorized
higher-memory/supported-host feasibility assessment is the next bounded option.
No provisioning, image pull, trading startup, real access, HALT action, stage/commit/push.
