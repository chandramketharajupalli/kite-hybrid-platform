# Phase 13.19 offline verification runbook

Read the [frozen plan](phase-13.19-plan.md), [validation](phase-13.19-validation.md)
and [authorization packet](phase-13.19-authorization-draft.md). No operational step.

1. Check develop/9738d72bf9f8d3897a3f0cf4be1387c68d1c42c7 localrefs and protected
   source registrations. Do not fetch or repair. This historical packet is additive;
   future runs must inventory their actual tree without overwriting this generation.
2. Verify frozen plan SHA a392503568eb49cb9abd5dc7a736357820d51aef255e7c1b8c81fedf55a5fcda.
   Evidence freshness and completeness are separate from byte integrity.
3. Read manifest object twice from disk, sorted compact ASCII JSON
   sort_keys=True,separators=(',',':'),ensure_ascii=True,allow_nan=False,
   UTF8/no newline; SHA256 must equal envelope fingerprint. Exclude self digest.
4. Verify raw linked docs/919 baseline/63 research/KL/parents/receipts, registered
   universal-newline18 sources and canonical parents. Never substitute normalization.
5. Verify exact5 IDs,68 procurement fields,75 gate records and original15 names AND
   criteria, source/time/reference integrity, seven custody roles, null full costs,
   Decimal components and denied status promotions. Check local links/all new files.
6. Run uv run --offline python scripts/verify-project.py and
   uv run --offline python scripts/check-secrets.py. Run git diff --check,
   git status --short, git diff --name-status and git diff --cached --name-status;
   inspect untracked files and whitespace separately. Do not stage/commit/push.

No unit/integration/Ruff/mypy suites: no executable change. No Docker operations,
cleanup or global resource inventory. Research URLs may change; a future authorized
read is new evidence, never a silent rewrite of this generation. Public price
components are not exact offers; user assertion is not independent custody.
Stop after report; obtain separately authorized evidence/assignments before any
contact or acquisition. V2/second review/four reviews PENDING; operational NO_GO.
