# Phase 13.15 design review runbook

Controlling [frozen plan](phase-13.15-plan.md); candidate
[architecture](../architecture/kite-controlled-linux-vm-testbed.md),
[comparison](phase-13.15-platform-comparison.md), [acceptance matrix](phase-13.15-security-acceptance-matrix.md)
and [DRAFT proposal](phase-13.15-provisioning-proposal.md). This is not a VM launcher.

1. Verify branch/refs/clean baseline and registered historical fingerprints. Do not
   fetch or repair. Preserve raw bytes and registered source newline conventions.
2. Review each gate's proposed authority/evidence and unresolved host/image/root/sink
   fields. No missing field can be inferred from a successful design check.
3. Check local Markdown links, exact new inventory, prohibited credentials/canaries
   and before/after tracked hashes. No public link crawling beyond frozen research.
4. Run read-only repository checks:

```powershell
uv run --offline python scripts/verify-project.py
uv run --offline python scripts/check-secrets.py
git diff --check
git status --short --untracked-files=all
git diff --name-status
git diff --cached --name-status
```

5. Replay manifest twice: SHA256 over json.dumps(manifest, sort_keys=True,
   separators=(',', ':'), ensure_ascii=True, allow_nan=False).encode('utf-8'), no
   trailing newline, excluding envelope fingerprint. Verify linked document/baseline
   raw hashes, parent canonical fingerprints and 18 normalized source hashes.
6. Report DESIGN_READY only for document/evidence-plan completeness. Keep provisioning
   pending and actual security UNVERIFIED. Java/Python suites NOT_RUN without code.

Future tests require new explicit scope: host feasibility, approved acquisition,
effective guest controls, focused owner/diagnostic/resource gate, witness/rollback,
then full standalone/root regressions only after preceding gates pass. Preserve
failures; do not weaken LSM/userns/signature/sink assertions. Max3 workloads checked
before creation, exact-ID ownership and unconditional cleanup; zero-owned final
inventory. These steps are proposed, not executed or authorized here.

Keep V2/governance second review/four domains PENDING; NO_GO/MIS NOT_READY and all
trading/research safeguards. Stop after the packet. No provisioning, operational
startup, token access, broker request, commit or push follows automatically.
