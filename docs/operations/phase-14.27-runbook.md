# Phase 14.27 offline assessment runbook

Use the recorded checkout and existing Python environment. Set PYTHONDONTWRITEBYTECODE=1 and run Python -B. Use unique external output directories, pytest --assert=plain -p no:cacheprovider with external basetemp/JUnit, Ruff --no-cache and strict mypy with an external cache. Do not invoke production launchers, read .env, resolve network dependencies or touch broker credentials.

1. Verify develop, full HEAD/origin, committed Phase 14.26 inventory, all protected hashes and the separate Phase 14.22 plan. Stop before edits on a mismatch.
2. Verify the frozen Phase 14.27 plan and declared source/import closure. Compare installed RECORD hashes and versions with uv.lock. Review un-hashed entries and unverified original archive provenance separately.
3. Run focused RC tests and the full Python suite. The eight scenarios reuse unchanged prior implementations. Integration input bindings reference canonical serialized arguments or immutable fixture-constructor hashes plus the preserved external input/journal records. Scenario H stores its full synthetic input, reference subset and observation subset.
4. Run the recorded external cached-backend build script. It copies only selected source into external staging directories and produces no order-capable executable. Verify two wheel byte hashes, wheel RECORD contents and two source archives. The isolated package resource probe must retain its unresolved result until separately corrected.
5. Inspect static security and guarded-process receipts. Python audit guards deny network/subprocess/credential probes but do not demonstrate OS containment. Keep PRODUCTION_EGRESS_NOT_VERIFIED.
6. Run scripts/research/verify_phase1427.py twice with Python -B. It verifies sealed inventory, recorded receipts, dependencies and evidence, then generates fresh synthetic scenarios and source archives only in external temporary directories for comparison. It never repairs or recreates historical evidence. Compare both returned JSON outputs byte-for-byte.
7. Verify the external certificate and separate command receipt. Retain initial lint/type failures and successful corrective reruns. Confirm no tracked/index changes and only declared new files plus the preserved Phase 14.22 exception.

The external evidence directory is recorded in the manifest. Losing required receipts or build artifacts invalidates verification; do not silently reconstruct them. The RC identity binds the evidence payload before the identity is inserted into the enclosing manifest, avoiding self-reference.

The current technical recommendation is OFFLINE_RC_CONDITIONAL with engineering PARTIAL. It is not owner acceptance, a deployable standalone package or trading permission. Genuine strategy stays BLOCKED and operations NO_GO. Do not stage, commit, push, provision infrastructure, change HALT/capital or contact Zerodha.
