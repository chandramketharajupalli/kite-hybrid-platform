# Phase 13.18 reproduction and stopping runbook

Documentation/public research only. Read the [frozen plan](phase-13.18-plan.md) and
[validation](phase-13.18-validation.md). No fetch, provisioning, image download,
account login, contact, test container or runtime execution is authorized here.

1. Verify develop/a55967391158a8d3e0a7d374e045d2819ff0d27b HEAD and local origin,
   initial clean inventory, registered G1/18/63/KL/prior fingerprints. Stop on mismatch.
2. Verify plan raw SHA6709b437be50550ca0668d37eed18263f41a52e3d5a5628fb90557408a71ee7c.
   Read source register URLs and retrieval interval; future price rechecking is a
   new observation, never a rewrite of this historical manifest. Do not replay CIM
   or public acquisitions automatically after their one-run budget is exhausted.
3. Recompute manifest fingerprint over its manifest object using Python json.dumps
   with sort_keys=True,separators=(',',':'),ensure_ascii=True,allow_nan=False,
   UTF-8 bytes/no newline and SHA256. Exclude envelope fingerprint. Repeat from disk.
4. Verify raw linked docs/baseline inventories and source-normalized18 entries under
   their registered universal-newline convention. Do not normalize raw-byte parents.
5. Validate exact15 labels,75 cells, permitted enums, source IDs/time metadata,
   arithmetic and five null totals; read local links and scan all nine new files.
6. Run uv run --offline python scripts/verify-project.py and
   uv run --offline python scripts/check-secrets.py; git diff --check;
   git status --short; git diff --name-status; git diff --cached --name-status.
   Separately inspect untracked whitespace and content; Git diff omits it.

No Java/Python test-suite/Ruff/mypy execution is warranted without executable edits.
Project and secret scripts are validation commands, not unit-suite counts. No live
cleanup is needed because this phase creates zero resources; existing containers
were not inventoried or touched. Never claim global zero containers or telemetry.

Stop with CONCRETE_HOST_SELECTION_INCOMPLETE while quotes, exact compatibility,
image signature, host enforcement or custody remain missing. Next evidence is
India landed/configured quotes, exact cloud quota/image/billing and named independent
root/sink authorities; future contact/provisioning needs separate explicit scope.
Keep NO_GO, NOT_READY and all governance/trading restrictions unchanged.
