# Phase 14.30 frozen plan

Baseline: develop, HEAD and local origin/develop
91db90a3e8329c2273a3c414d32a1b01dc04e362. All 13 Phase 14.29 additions are
committed; 1,397 tracked files are clean. The separate Phase 14.22 plan retains
e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31.

Preflight verified the Phase 14.29 manifest, artifact and external receipt hashes,
54 focused/1,003 full passing receipts, retained failures, installed provenance,
resource bytes, both archives and historical preservation. The historical verifier
initially rejected its obsolete pre-commit baseline; its Git lifecycle assertions
were adapted in memory for the current committed baseline, then passed. No evidence
was rewritten. Thirteen newline reconciliations, 18 source hashes, 63 research files,
K/L governance, 1,115 symbols and 13,380 combinations passed. Capture all current raw
tracked hashes externally before implementation.

## Dependency requirement and permitted correction

Windows sys.platform is win32; the timezone search path is empty. With tzdata imports
explicitly denied and TZPATH empty, ZoneInfo("Asia/Kolkata") raises
ZoneInfoNotFoundError. Restoring the installed tzdata 2026.4 succeeds. The existing
lockfile already contains tzdata 2026.4, currently a development transitive dependency.
This is a runtime requirement for the selected scheduler/strategy import closure.

Add exactly tzdata==2026.4; sys_platform == 'win32' to project dependencies. Add its
Windows-marked edge and exact constraint to the strategy-engine lock entry manually
and offline. Preserve every other dependency version, artifact hash and lock entry.
No resolver, network, package update or installed-environment modification is allowed.
Non-Windows runtime continues to require an available system timezone database; this
phase certifies no new non-Windows support claim.

Permitted tracked edits: apps/strategy-engine/pyproject.toml and
apps/strategy-engine/uv.lock only. Add narrowly scoped dependency-assessment code,
tests, build/evidence scripts and Phase 14.30 documentation/reports. No existing
trading, calendar, resource, test or historical evidence edits.

## Installation, builds and budgets

Create a fresh external Windows venv without pip or system-site-packages. Use only
retained, applicable archives verified against existing locked digests for runtime
installation. Do not reconstruct wheels or copy installed packages. If any required
input is unavailable, classify clean installation BLOCKED, retain its empty inventory
and missing-input list, and do not claim fresh standalone execution success.

Build two selected offline wheels using the cached declared Hatchling 1.27.0 backend,
and two deterministic source ZIP archives with the existing explicit resource/source
allowlist. Preserve resource bytes and original build hashes. A guarded probe may
reconfirm behavior in the existing isolated environment but must be labeled separately
from the blocked fresh-installation gate. No source checkout or credential reads.

Use one test worker, bounded synthetic fixtures, maximum 4,096 cache candidates and
128 selected build inputs. Budget 15 minutes for the full Python suite, 5 minutes per
other gate, with caches, bytecode prevention and temporary outputs outside checkout.
Use Python -B, PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain -p no:cacheprovider,
Ruff --no-cache and external mypy cache. No Java changes or Java validation claims.

## Acceptance, evidence and stop conditions

Require metadata/marker/lock tests, timezone absence and conversion tests, resource
regressions, dependency inventory checks, full Python tests, Ruff, strict mypy,
two byte-identical builds of each format, guarded runtime evidence, secret/canary
checks and two deterministic manifest replays. Preserve every failed validation
attempt and corrective rerun. Verify protected raw hashes after every major gate;
exclude only the two explicitly authorized metadata edits from preservation equality.

Keep original archive provenance distinct from installed RECORD integrity. Preserve
the historical 39 verified distributions, 38 missing third-party originals, zero
applicable archive matches and absent independent registry attestation. Missing
archives block clean installation; they cannot be treated as a passing negative test.

Deliver the plan, architecture boundary, installation report, RC decision, runbook,
validation document, deterministic assessment and sealed manifest with exact inventory,
original/new hashes, test receipts, plan fingerprint, protected hashes and limitations.
Record an external integrity certificate and separate command receipt.

Stop on unexpected repository changes, protected-byte mismatch, resource mutation,
network requirement or unsafe dependency substitution. Missing installation inputs
produce a PARTIAL phase and CONDITIONAL RC technical recommendation, not owner approval.
No genuine qualified pairs; Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase 12
BLOCK; H1/H2 FROZEN; July SEALED; operational NO_GO. No real Kite requests, credentials,
operational HALT changes, staging, commit or push.
