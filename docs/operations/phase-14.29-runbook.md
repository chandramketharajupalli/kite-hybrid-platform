# Phase 14.29 offline reassessment runbook

Verify develop, actual HEAD/local origin/develop, clean tracked/index state, sealed
Phase 14.28 bytes and the separately untracked Phase 14.22 plan before work. Compare
universe bytes using binary reads from working tree, git show blob, resource manifest,
both wheels and canonical source. Never use text pipelines for CRLF-sensitive resources.

Use PYTHONDONTWRITEBYTECODE=1 and Python -B. Invoke build_phase1429.py with a new empty
external evidence directory. It uses the cached declared backend, selects frozen source
and resources, builds twice, compares exact archives and probes the new wheel in the
already-provisioned Phase 14.28 isolated interpreter. No dependency install, upgrade,
network resolution, cache cleaning or source-checkout fallback is permitted.

Run focused tests test_phase1429.py plus test_phase1428.py; run full Python tests,
Ruff --no-cache and strict mypy with external cache. pytest uses --assert=plain
-p no:cacheprovider, external basetemp and JUnit receipts. Check all baseline hashes after
every gate. Retain failed receipts and corrective reruns. Do not read .env or credentials.
No Java source/contracts change; Java tests are not a claim of this phase.

Seal source/report/receipt hashes and exact new inventory, then execute
scripts/research/verify_phase1429.py twice in independent processes. It re-audits installed
files and cache metadata without modifying them, verifies retained builds and compares
canonical reports. Preserve identical replay receipts, external certificate and separate
certificate-check receipt. Missing mandatory evidence must fail closed.

Acceptance remains conditional while required original archives or registry/lockfile
trust are unresolved. Do not download evidence, infer owner acceptance, start production,
clear HALT or change capital limits. Genuine qualification is BLOCKED; operational NO_GO.
No staging, commit, push, fetch, reset, clean, restore or Git configuration changes.
