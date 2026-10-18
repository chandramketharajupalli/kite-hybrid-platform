# Phase 14.44E exact regression inventory

REGRESSION_INVENTORY_VERIFIED; EXECUTION_NOT_PERFORMED. All IDs below are read verbatim from sealed receipts, not recreated from parameter guesses.

Original module: apps/strategy-engine/tests/test_phase1444c.py. Common fixture assumptions: TEST_ONLY decision, ledger identity TEST_ONLY:ledger1, injected UTC2026-01-02 clock, pytest tmp_path/fixture.sqlite. All31 require ledger; safe_path accepts only the frozen C root. Historical reason for every row: NOT_RUN_SEALED_CUSTODY_CONFLICT. Database writes and child receipts must never target host historical C custody.

Frozen E plan SHA256: d26c3b1c05c2ab2267656fa5cc957c19cc51f1c86d80c265da9df6b8e3ef820d.

Source receipts:

D:\Yogendra\offline-validation-evidence\phase-14.44D\generation-1\c-safe.events.jsonl raw SHA256 8685915e378cd28f97f1f0e5daf9b0eca6a0f621cef9c9116b1de1c5bdd5f5ef

D:\Yogendra\offline-validation-evidence\phase-14.44C\generation-1\security.inventory.json raw SHA256 27eac048ea9f4593cbebbcf48e4bb27483e70b606bd6b7a68af9d3325829b71c

Reconciliation: C48 original = D17 selected +31 unique deselected, with no intersection or omitted ID. Canonical ordered-ID-list SHA256 ffdd2941fe109228efe488f2461dbf3c7ce7731102a9443e1eea74bf92a829cf.

| Exact original node ID | Purpose | Required database operations |
| --- | --- | --- |
| `tests/test_phase1444c.py::test_decision_binding[changes0-OWNER_NOT_ENROLLED]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection |
| `tests/test_phase1444c.py::test_decision_binding[changes1-OWNER_NOT_ENROLLED]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection |
| `tests/test_phase1444c.py::test_decision_binding[changes2-TRUSTED_TIME_UNVERIFIED]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection |
| `tests/test_phase1444c.py::test_decision_binding[changes3-WRONG_BASELINE]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection |
| `tests/test_phase1444c.py::test_decision_binding[changes4-WRONG_AMENDMENT_RAW]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection |
| `tests/test_phase1444c.py::test_decision_binding[changes5-WRONG_AMENDMENT_CANONICAL]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection |
| `tests/test_phase1444c.py::test_decision_binding[changes6-WRONG_CERTIFICATE]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection |
| `tests/test_phase1444c.py::test_fixture_cannot_supply_real_authority[changes0]` | Reject missing root or promotion to real source/action | Create database; validate rejected synthetic decision |
| `tests/test_phase1444c.py::test_fixture_cannot_supply_real_authority[changes1]` | Reject missing root or promotion to real source/action | Create database; validate rejected synthetic decision |
| `tests/test_phase1444c.py::test_fixture_cannot_supply_real_authority[changes2]` | Reject missing root or promotion to real source/action | Create database; validate rejected synthetic decision |
| `tests/test_phase1444c.py::test_inspection_does_not_consume_and_reopen_survives` | Inspect without consumption; persist consumption across reopen | Create, inspect, consume, close/reopen |
| `tests/test_phase1444c.py::test_unique_id_and_nonce[change0]` | Reject duplicate decision or nonce | Create, consume, duplicate insert attempt |
| `tests/test_phase1444c.py::test_unique_id_and_nonce[change1]` | Reject duplicate decision or nonce | Create, consume, duplicate insert attempt |
| `tests/test_phase1444c.py::test_future_and_expired[when0]` | Reject outside approval validity | Create; attempt consume with invalid clock |
| `tests/test_phase1444c.py::test_future_and_expired[when1]` | Reject outside approval validity | Create; attempt consume with invalid clock |
| `tests/test_phase1444c.py::test_clock_rollback_survives_reopen` | Reject earlier time after reopen | Create, consume, reopen, attempt earlier consume |
| `tests/test_phase1444c.py::test_revocation_survives_restart[owner]` | Persist specified authority/key/root/decision revocation | Create, revoke, reopen, reject consume |
| `tests/test_phase1444c.py::test_revocation_survives_restart[key]` | Persist specified authority/key/root/decision revocation | Create, revoke, reopen, reject consume |
| `tests/test_phase1444c.py::test_revocation_survives_restart[root]` | Persist specified authority/key/root/decision revocation | Create, revoke, reopen, reject consume |
| `tests/test_phase1444c.py::test_revocation_survives_restart[decision]` | Persist specified authority/key/root/decision revocation | Create, revoke, reopen, reject consume |
| `tests/test_phase1444c.py::test_process_restart` | Consume once across separate interpreters | Create, subprocess consume/replay; child receipt writes |
| `tests/test_phase1444c.py::test_cross_process_concurrent_consumers` | Exactly one consumer succeeds | Create; two subprocess transactions; child receipt writes |
| `tests/test_phase1444c.py::test_crash_before_commit_rolls_back` | Precommit crash leaves decision unconsumed | Create; child exit17 before commit; reopen/retry |
| `tests/test_phase1444c.py::test_crash_after_commit_cannot_reconsume` | Postcommit crash preserves consumption | Create; child exit17 after commit; reopen/reject replay |
| `tests/test_phase1444c.py::test_corrupt_storage_denies` | Reject corrupt SQLite bytes | Create; overwrite database; reopen |
| `tests/test_phase1444c.py::test_tampered_consumption_denies` | Reject consumption/audit inconsistency | Create/consume; DELETE consumed; reopen |
| `tests/test_phase1444c.py::test_missing_database_never_resets` | Missing database must not silently regenerate | Create; unlink database; reject reopen |
| `tests/test_phase1444c.py::test_no_overwrite_or_wrong_identity` | Reject overwrite and wrong ledger identity | Create; exclusive-create attempt; wrong-identity reopen |
| `tests/test_phase1444c.py::test_expiry_race_before_commit_denies` | Expiry at precommit recheck aborts transaction | Create; consume with changing clock; inspect rollback |
| `tests/test_phase1444c.py::test_revocation_serializes_before_consumer` | Earlier revocation blocks consumption | Create/reopen; revoke; reject consume |
| `tests/test_phase1444c.py::test_revocation_race_consumer_commits_first` | Concurrent revocation orders after committed consume | Create; consume transaction; concurrent revoke; later rejection |
