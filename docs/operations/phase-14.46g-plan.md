# Phase 14.46G frozen recovery plan

Baseline: 7c9e02b75cbcc0ec2b9043b0ae000e6c0d624420; develop equals local origin/develop. Preserve all 1942 tracked files and four excluded untracked files. Explicit owner chat authorizes these eleven preparation additions and restricted custody only; it is not authenticated governance authority.

## Exact inventory

- docs/operations/phase-14.46g-plan.md
- docs/operations/phase-14.46g-schema-diagnosis.md
- docs/operations/phase-14.46g-correction.md
- docs/operations/phase-14.46g-regression-tests.md
- docs/operations/phase-14.46g-dependency-reconciliation.md
- docs/operations/phase-14.46g-validation.md
- research/phase-14.46g/generation-1/schema-recovery.json
- research/phase-14.46g/generation-1/dependency-reconciliation.json
- research/phase-14.46g/generation-1/evidence-manifest.json
- scripts/research/verify_phase1446g.py
- scripts/research/test_phase1446g.py

## Predecessor and diagnosis binding

Phase F remains PARTIAL ? PRESERVED_IN_GIT. F plan SHA-256 c89ad93ce0927b13622b5768753daa50a7e33d2fc0925c62e0172d15058fa0b6; F verifier a94f70655631146aefaa872acef8f255b1e104027c41a7d3c4125672559f31ae; original external failure receipt 2f4814c2a3974d25e26d2cd71b70e9761167ace0c02a1381baff6e37bc28a659. F validation document omits the failure and stays unchanged. F manifest, replays and certificate stay absent.

Inspection shows the F verifier has no schema generator. The original maintenance invocation retained in the conversation defines schema(x), recursively producing prefixItems for every list, including an empty list. Retain this transcript-derived reconstruction explicitly as unsealed source provenance, not invented committed code. Independently reproduce the structural defect using synthetic fixtures and Draft 2020-12 metaschema rejection.

## Correction and validation

Add a strict deterministic snapshot-schema adapter to verify_phase1446g.py: fixed-length arrays; emit prefixItems only for nonempty arrays; empty arrays have minItems=maxItems=0 and items=false. Required object keys and additionalProperties=false; scalar type and const constraints; reject non-JSON input, nonfinite numbers and cycles. Explicit Draft 2020-12 schema identifier. Tuples supported as fixed array snapshots; instances use JSON arrays. Optional object fields only by explicit optional_fields parameter, with constraints preserved when present.

Prepare synthetic original failure reproduction, empty/nonempty exclusions and tuples, nested/optional/required arrays, wrong elements, missing fields, extra keys/items, malformed/wrong-draft schemas, invalid prefixItems, invalid generator inputs, deterministic serialization and corrected metaschema checks. Expected negative rejection is success; unexpected result stops with no retry.

Reconcile committed F evidence:28 artifacts,76 local files hashed,40 missing paths,43 imported BOM POMs,three missing candidate root POM/JARs. Rehash cache without Maven or acquisition. Preserve PARTIAL/MISSING_METADATA/UNVERIFIED_PROVENANCE and null transitive edges. Synthetic schema success is not dependency closure.

Use existing maintenance Python and installed jsonschema in memory only; compile source without pyc; no pytest/Maven/JUnit or blocked46/18/31 tests. Check packet schemas, policy restrictions, source syntax, raw LF/Git bytes, whitespace and heuristic secrets/canaries. Two independent fresh namespace verification invocations; audit guards forbid child processes, network and writes during replay. Complete receipts and identical canonical output fingerprints required. No automatic retry on mandatory failure.

## Custody, sealing and stop conditions

D:/Yogendra/offline-validation-evidence/phase-14.46G/generation-1: explicit conditional creation authorization. Verify owner=current user, ACL only owner/SYSTEM/Administrators, no reparse ancestors, capacity >=100MiB, nonexistence. Exclusive creation; preserve predecessor bytes and ACLs. Save baseline hashes, protected inputs, frozen plan, diagnosis source reconstruction, schema/check receipts, two replay outputs/receipts, final preservation and integrity-only certificate. No dependency binary copies.

Canonical fingerprints: SHA256 of sorted compact ASCII-escaped UTF8 JSON, no NaN. Manifest wraps manifest object with its fingerprint, excludes self-hash; external certificate binds raw manifest and all new artifacts/receipts. Snapshot recovery JSON references replay receipt paths; final manifest binds actual replay fingerprints to avoid circular hashes. Unknown authority fields null.

Stop on Git/evidence/custody drift, unexpected file or validation failure. Do not alter this frozen plan. No existing-file modifications, dependency download, Java/POM changes, credentials, broker calls, endpoint activation, owner enrollment, runtime authorization, staging, commit or push. Preserve13 historical newline reconciliations,1115-symbol universe,13380 research family and73 unavailable receipts.

When schema recovery and evidence succeed but dependencies remain unresolved: PHASE_14_46G_DEPENDENCY_PENDING. Acquisition/implementation/runtime/GateA/B NOT_AUTHORIZED; owner/operator NOT_ESTABLISHED; live orders/protected activation DISABLED; Series v2 PROPOSED_NOT_ADOPTED; operational NO_GO.
