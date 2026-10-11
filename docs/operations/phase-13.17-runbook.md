# Phase 13.17 assessment reproduction

Read the [frozen plan](phase-13.17-plan.md), [comparison](phase-13.17-option-comparison.md),
[gates](phase-13.17-security-gate-matrix.md), [cost model](phase-13.17-cost-and-risk-model.md)
and [unsent draft](phase-13.17-provisioning-authorization-draft.md).

Verify the declared clean baseline, local tracking ref and registered evidence before
writing. No fetch, normalization repair, resource login or host measurement is needed.
Preserve raw hashes and the18 source-defined universal-newline hashes distinctly.
Review P-source guidance only within the plan's public documentation allowance;
quote/availability/support not established stays TBD/UNKNOWN. Do not carry historical
measurements forward as current enforcement or turn option feasibility into approval.

Check exact G01-G15 labels against Phase13.15 and Phase13.16;45 option cells must have
allowed status plus source, test, authority and failure consequence. G01 repository
pass does not certify a host. Verify cost equations and unknown propagation; never
fill missing prices with zero. No new executable code or test suite is required.

```powershell
uv run --offline python scripts/verify-project.py
uv run --offline python scripts/check-secrets.py
git diff --check
git status --short --untracked-files=all
git diff --name-status
git diff --cached --name-status
```

Replay twice from disk: SHA256(json.dumps(manifest,sort_keys=True,
separators=(',',':'),ensure_ascii=True,allow_nan=False).encode('utf-8')). No trailing
newline in canonical payload; exclude envelope fingerprint. Check linked document,
baseline, parent raw/canonical and protected hashes both times. Check local Markdown
links, source IDs,15 names/45 cells, schema fields, redaction, whitespace and known
synthetic-canary representations without printing their values. Preserve failed checks.

No containers/VMs/cloud resources were created: cleanup NOT_APPLICABLE to phase
actions, not a fresh global inventory. Future max3 admission/owned-ID cleanup remains
mandatory and untested here. No full Java/Python/Ruff/mypy runs absent executable
changes. Stop after ALTERNATIVE_HOST_SELECTION_INCOMPLETE and draft. Next scope is
evidence collection for a concrete priced host and independent custodians, not
provisioning. No stage/commit/push or automatic authorization.
