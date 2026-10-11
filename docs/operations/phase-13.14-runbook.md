# Phase 13.14 assessment reproduction

Use the frozen plan and registered raw SHA in the manifest. Verify develop and both
local refs at 19c91847a37bdc85020642e037e349caa7c4dd20 before reproducing from that
baseline; do not repair a mismatch. The completed assessment itself adds eight files.
No fetch, pull, host setup or operational action is implied by this runbook.

1. Rehash the before-work tracked inventory, G1/18/63, K/L and prior manifests using
   their registered conventions. Historical changed test sources bind to retained
   snapshots; never rewrite a predecessor to match current source.
2. Execute only the plan's read-only metadata commands; retain selected non-sensitive
   fields, exit status, timezone time and redacted host identity. A missing Desktop
   executable path is a failed observation, not permission for a broad host search.
3. Compare cached image digest/config/rootfs and official metadata receipts. Record
   OCI_SIGNATURE_NOT_VERIFIED unless a publisher-authorized chain is actually verified.
4. Inspect existing container/volume inventory without starting/removing anything.
   This assessment admits zero new workloads. A future separately authorized fixture
   must enforce max3 before creation and exact-ID/label finally cleanup; any ambiguity
   stops broader tests. Never remove unrelated resources.
5. Review the alternative-host acceptance profile, logging boundaries and matrix.
   Historical owner /proc/denial results do not establish a current process or host.
6. Run offline project/secrets checks and Git audits:

```powershell
uv run --offline python scripts/verify-project.py
uv run --offline python scripts/check-secrets.py
git diff --check
git status --short --untracked-files=all
git diff --name-status
git diff --cached --name-status
```

No Java, integration, Python unit, Ruff or mypy suite is needed for these documentation
and data-only changes. Do not present inherited 97/138/1508 test counts as fresh runs.
Scan eight new artifacts for the existing test canaries without printing values.

Replay the JSON manifest twice from disk: SHA256(json.dumps(manifest, sort_keys=True,
separators=(',', ':'), ensure_ascii=True, allow_nan=False).encode('utf-8')). Exclude
the envelope fingerprint and use no trailing newline in the canonical payload.
Check every baseline/new-file linked raw hash; use universal-newline UTF-8 text only
for the 18 registered frozen sources. The manifest cannot include its own raw hash;
final Git inventory lists its path separately. Evidence consistency is not approval.

No host is provisioned or policy loaded. New host work needs exact authorized topology,
custodians, image trust policy, fault/cleanup budget and acceptance evidence. Retain
all-root rollback, pending governance/V2/domain reviews and NO_GO. Stop after report.
