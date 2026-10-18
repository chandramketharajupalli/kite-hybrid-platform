# Exact non-applied patch proposal

PHASE_14_44EA_PARTIAL. Original-source diff is empty: replay.py and original tests remain byte-identical. New declarative contract only; no implemented runner or safe execution claim.

Original source: apps/strategy-engine/src/strategy_engine/phase1444c/replay.py
Original SHA256: 7035454ff0060f3f1c3e2b54d685b51e57f02cd4ad75608c52a567d617178e28
Original test SHA256: ffc3ccf89b865f3d77a9032a5fb058d86e72dd1d5ea98daab4ff62531bcc3e10
Frozen plan SHA256: 6202c4a120288a36f239072013e86245c1777bad73c38c74f535aeb3c469c304
Proposed ADD path: scripts/research/phase1444ea_guest_execution.json
Complete proposed new-file SHA256: e9676d0537d2d5b89102535847a1e17d0badf9a439d838cd05feb397ef6baf61
Patch SHA256: 0435a98894bf77271551e5f0797fa99da322a588bf123a0915757c0bdc31a86f

No imports are added/removed; no runtime API, environment override, monkeypatch or import shadow. Child processes retain original imports and path guards. Contract describes independently isolated guest custody; it cannot create isolation or authorize itself. replay.py remains its current hash because zero source change is proposed. Future supervisor implementation/image hashes are null and require an approved additive amendment.

Exact unified diff below is data only. Hash UTF-8 LF bytes between fences, including final LF. Static review reconstructs bytes in memory; no git apply or target-file creation. Application of this JSON alone cannot make host regression paths valid. Original31 assertions/IDs remain unchanged.

```diff
--- /dev/null
+++ b/scripts/research/phase1444ea_guest_execution.json
@@ -0,0 +1,88 @@
+{
+  "baseline": "137b948141a475f53b9cd4737c33dcf67c8fc422",
+  "budgets_seconds": {
+    "child": 20,
+    "collection": 120,
+    "tests": 300
+  },
+  "cleanup": "SEPARATE_EXACT_PATH_VOLUME_AUTHORIZATION_AFTER_RECEIPT_RETENTION",
+  "design": "ISOLATED_BYTE_IDENTICAL_SOURCE_COPY",
+  "executable": false,
+  "guest_fixture_root": "D:\\Yogendra\\offline-validation-evidence\\phase-14.44C\\generation-1",
+  "guest_image_sha256": null,
+  "guest_label": "TEST_ONLY_NAMESPACE_NOT_HISTORICAL_EVIDENCE",
+  "host_backing_root": "D:\\Yogendra\\offline-validation-evidence\\phase-14.44E-A\\disposable-generation-1",
+  "invariants": [
+    "ORIGINAL_SOURCE_AND_ASSERTIONS_UNCHANGED",
+    "NO_PRODUCTION_PATH_OVERRIDE",
+    "NO_ENVIRONMENT_ONLY_AUTHORIZATION",
+    "NO_FALLBACK_TO_HOST_SEALED_CUSTODY",
+    "NO_CREDENTIALS",
+    "NO_NETWORK_OR_BROKER",
+    "NO_PRODUCTION_DATABASE",
+    "NO_GUARD_WEAKENING",
+    "NO_REPARSE_ESCAPE",
+    "NO_REPLAY_SEMANTIC_CHANGE",
+    "NO_HISTORICAL_MUTATION",
+    "NO_SOURCE_IMPORT_SHADOWING"
+  ],
+  "isolation_attestation": null,
+  "mapping_status": "PROPOSED_NOT_VERIFIED",
+  "on_missing_binding": "DENY_BEFORE_LAUNCH_NO_FALLBACK",
+  "owner_approval": null,
+  "process_binding": "OWNED_HANDLES_PLUS_PID_CREATION_AND_PARENT_LIFETIME",
+  "required_result": {
+    "collected": 31,
+    "deselected": 0,
+    "errors": 0,
+    "executed": 31,
+    "failed": 0,
+    "passed": 31,
+    "skipped": 0
+  },
+  "runtime_closure_fingerprint": null,
+  "source_hashes": {
+    "apps/strategy-engine/src/strategy_engine/phase1444b/trust.py": "fa2e8e00b9702e947de35a486800f95b439cbfd99fa75817ff918b69bc46a3a0",
+    "apps/strategy-engine/src/strategy_engine/phase1444c/crypto.py": "1c443e753fbe4ba8e0ef34ff47fb6b8108db0f967e04b02a118db83ff2dae695",
+    "apps/strategy-engine/src/strategy_engine/phase1444c/replay.py": "7035454ff0060f3f1c3e2b54d685b51e57f02cd4ad75608c52a567d617178e28",
+    "apps/strategy-engine/tests/test_phase1444c.py": "ffc3ccf89b865f3d77a9032a5fb058d86e72dd1d5ea98daab4ff62531bcc3e10"
+  },
+  "source_transfer": "BYTE_IDENTICAL_READ_ONLY_NO_SECRETS",
+  "status": "NOT_AUTHORIZED",
+  "supervisor_identity": null,
+  "test_ids": [
+    "tests/test_phase1444c.py::test_decision_binding[changes0-OWNER_NOT_ENROLLED]",
+    "tests/test_phase1444c.py::test_decision_binding[changes1-OWNER_NOT_ENROLLED]",
+    "tests/test_phase1444c.py::test_decision_binding[changes2-TRUSTED_TIME_UNVERIFIED]",
+    "tests/test_phase1444c.py::test_decision_binding[changes3-WRONG_BASELINE]",
+    "tests/test_phase1444c.py::test_decision_binding[changes4-WRONG_AMENDMENT_RAW]",
+    "tests/test_phase1444c.py::test_decision_binding[changes5-WRONG_AMENDMENT_CANONICAL]",
+    "tests/test_phase1444c.py::test_decision_binding[changes6-WRONG_CERTIFICATE]",
+    "tests/test_phase1444c.py::test_fixture_cannot_supply_real_authority[changes0]",
+    "tests/test_phase1444c.py::test_fixture_cannot_supply_real_authority[changes1]",
+    "tests/test_phase1444c.py::test_fixture_cannot_supply_real_authority[changes2]",
+    "tests/test_phase1444c.py::test_inspection_does_not_consume_and_reopen_survives",
+    "tests/test_phase1444c.py::test_unique_id_and_nonce[change0]",
+    "tests/test_phase1444c.py::test_unique_id_and_nonce[change1]",
+    "tests/test_phase1444c.py::test_future_and_expired[when0]",
+    "tests/test_phase1444c.py::test_future_and_expired[when1]",
+    "tests/test_phase1444c.py::test_clock_rollback_survives_reopen",
+    "tests/test_phase1444c.py::test_revocation_survives_restart[owner]",
+    "tests/test_phase1444c.py::test_revocation_survives_restart[key]",
+    "tests/test_phase1444c.py::test_revocation_survives_restart[root]",
+    "tests/test_phase1444c.py::test_revocation_survives_restart[decision]",
+    "tests/test_phase1444c.py::test_process_restart",
+    "tests/test_phase1444c.py::test_cross_process_concurrent_consumers",
+    "tests/test_phase1444c.py::test_crash_before_commit_rolls_back",
+    "tests/test_phase1444c.py::test_crash_after_commit_cannot_reconsume",
+    "tests/test_phase1444c.py::test_corrupt_storage_denies",
+    "tests/test_phase1444c.py::test_tampered_consumption_denies",
+    "tests/test_phase1444c.py::test_missing_database_never_resets",
+    "tests/test_phase1444c.py::test_no_overwrite_or_wrong_identity",
+    "tests/test_phase1444c.py::test_expiry_race_before_commit_denies",
+    "tests/test_phase1444c.py::test_revocation_serializes_before_consumer",
+    "tests/test_phase1444c.py::test_revocation_race_consumer_commits_first"
+  ],
+  "unsafe_pid_tree_termination": false,
+  "version": "Phase1444EAGuestExecutionContract.v1"
+}
```

Rollback removes only a separately approved new contract/disposable resource after receipt retention; no historical restore/delete or consumed-approval reset. A reviewed supervisor and independent namespace attestation remain required. No runtime compatibility success is claimed.
