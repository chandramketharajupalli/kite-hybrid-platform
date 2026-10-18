# Original31 and synthetic safety specifications

Exactly31 IDs reconciled with C48 original collection and D31deselections; D17selected disjoint. Every row remains NOT_RUN_SEALED_CUSTODY_CONFLICT. No tests executed in E-A. Source/assertion bytes unchanged.

Module: apps/strategy-engine/tests/test_phase1444c.py. SHA256 ffc3ccf89b865f3d77a9032a5fb058d86e72dd1d5ea98daab4ff62531bcc3e10. Common dependencies: ledger fixture -> tmp_path/fixture.sqlite -> Ledger.create -> frozen guest CUSTODY; identity TEST_ONLY:ledger1; injected2026-01-02UTC clock. Original assertions remain authoritative; summaries do not replace them.

| Exact original node ID | Purpose / expected assertion | Database operation | Child/file dependency |
| --- | --- | --- | --- |
| `tests/test_phase1444c.py::test_decision_binding[changes0-OWNER_NOT_ENROLLED]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_decision_binding[changes1-OWNER_NOT_ENROLLED]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_decision_binding[changes2-TRUSTED_TIME_UNVERIFIED]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_decision_binding[changes3-WRONG_BASELINE]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_decision_binding[changes4-WRONG_AMENDMENT_RAW]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_decision_binding[changes5-WRONG_AMENDMENT_CANONICAL]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_decision_binding[changes6-WRONG_CERTIFICATE]` | Reject invalid enrollment/time/baseline/amendment/certificate binding | Create database; attempt consume; rejection | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_fixture_cannot_supply_real_authority[changes0]` | Reject missing root or promotion to real source/action | Create database; validate rejected synthetic decision | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_fixture_cannot_supply_real_authority[changes1]` | Reject missing root or promotion to real source/action | Create database; validate rejected synthetic decision | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_fixture_cannot_supply_real_authority[changes2]` | Reject missing root or promotion to real source/action | Create database; validate rejected synthetic decision | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_inspection_does_not_consume_and_reopen_survives` | Inspect without consumption; persist consumption across reopen | Create, inspect, consume, close/reopen | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_unique_id_and_nonce[change0]` | Reject duplicate decision or nonce | Create, consume, duplicate insert attempt | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_unique_id_and_nonce[change1]` | Reject duplicate decision or nonce | Create, consume, duplicate insert attempt | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_future_and_expired[when0]` | Reject outside approval validity | Create; attempt consume with invalid clock | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_future_and_expired[when1]` | Reject outside approval validity | Create; attempt consume with invalid clock | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_clock_rollback_survives_reopen` | Reject earlier time after reopen | Create, consume, reopen, attempt earlier consume | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_revocation_survives_restart[owner]` | Persist specified authority/key/root/decision revocation | Create, revoke, reopen, reject consume | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_revocation_survives_restart[key]` | Persist specified authority/key/root/decision revocation | Create, revoke, reopen, reject consume | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_revocation_survives_restart[root]` | Persist specified authority/key/root/decision revocation | Create, revoke, reopen, reject consume | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_revocation_survives_restart[decision]` | Persist specified authority/key/root/decision revocation | Create, revoke, reopen, reject consume | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_process_restart` | Consume once across separate interpreters | Create, subprocess consume/replay; child receipt writes | Original Python-B child imports, stdin path, child-mode.json beside database |
| `tests/test_phase1444c.py::test_cross_process_concurrent_consumers` | Exactly one consumer succeeds | Create; two subprocess transactions; child receipt writes | Original Python-B child imports, stdin path, child-mode.json beside database |
| `tests/test_phase1444c.py::test_crash_before_commit_rolls_back` | Precommit crash leaves decision unconsumed | Create; child exit17 before commit; reopen/retry | Original Python-B child imports, stdin path, child-mode.json beside database |
| `tests/test_phase1444c.py::test_crash_after_commit_cannot_reconsume` | Postcommit crash preserves consumption | Create; child exit17 after commit; reopen/reject replay | Original Python-B child imports, stdin path, child-mode.json beside database |
| `tests/test_phase1444c.py::test_corrupt_storage_denies` | Reject corrupt SQLite bytes | Create; overwrite database; reopen | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_tampered_consumption_denies` | Reject consumption/audit inconsistency | Create/consume; DELETE consumed; reopen | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_missing_database_never_resets` | Missing database must not silently regenerate | Create; unlink database; reject reopen | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_no_overwrite_or_wrong_identity` | Reject overwrite and wrong ledger identity | Create; exclusive-create attempt; wrong-identity reopen | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_expiry_race_before_commit_denies` | Expiry at precommit recheck aborts transaction | Create; consume with changing clock; inspect rollback | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_revocation_serializes_before_consumer` | Earlier revocation blocks consumption | Create/reopen; revoke; reject consume | Same fixture.sqlite; no child interpreter required |
| `tests/test_phase1444c.py::test_revocation_race_consumer_commits_first` | Concurrent revocation orders after committed consume | Create; consume transaction; concurrent revoke; later rejection | Same fixture.sqlite; no child interpreter required |

## NOT_EXECUTED specifications

All29 scenarios are requirements only. Real OS confinement, authorization authenticity and trusted time remain unresolved; synthetic flags cannot certify them.

1. **Default custody behavior:** Unchanged host module accepts only original guarded root. No sealed writes to prove this.
2. **Authorized disposable custody:** Require independent guest mapping, approved launch capability and exact image/source; not a caller flag.
3. **Production override rejection:** Host/env/imported contract cannot redirect custody; deny before launch.
4. **Missing authorization:** Deny before directory/process creation.
5. **Wrong authorization fingerprint:** Changed patch/source/path/scope rejects.
6. **Expired authorization:** Trusted interval outside validity denies; no local-clock fallback.
7. **Arbitrary path rejection:** Unlisted host/guest root denies.
8. **Repository path rejection:** Writable host repo/source mapping rejects.
9. **Sealed-custody write rejection:** Any host predecessor mount/write rejects; verify unchanged hashes.
10. **Junction escape:** Reparse ancestor/swapped parent denies without target write.
11. **Symlink escape:** Link at file/ancestor denies.
12. **Unsafe parent:** ACL/owner/volume mismatch rejects before provisioning.
13. **Child without custody binding:** Missing supervisor session capability denies child start/acceptance.
14. **Child wrong custody binding:** Wrong VM/storage/nonce rejects, no fallback.
15. **Concurrent replay:** Original case: exactly one consume; verified children.
16. **Restart recovery:** Original case: persisted consume/replay denial.
17. **Crash before commit:** Original exit17 before commit permits integrity-checked retry.
18. **Crash after commit:** Original exit17 after commit preserves consumption/replay denial.
19. **Persistent revocation:** Original owner/key/root/decision cases persist across reopen.
20. **Ledger corruption:** Original corrupt bytes reject, no reset.
21. **Audit-chain tampering:** Original consumption-table tamper rejects.
22. **Credential access rejection:** OS boundary denies host/guest credentials; no real secret fixture.
23. **Network access rejection:** Approved OS boundary denies egress; synthetic probes only.
24. **Unrelated PID protection:** Earlier parent creation/reused PID excluded; terminate only verified owned handles.
25. **Original31 node preservation:** Collection exactly packet IDs, no duplicates/replacements.
26. **No hidden deselections:** 31executed/passed; zero failures/errors/skips/deselections, report deviations.
27. **Historical preservation:** Host baseline/custody hashes unchanged; no regenerated receipts.
28. **No Seriesv2 implementation:** Exact file/scope checks, no activation.
29. **No real Kite requests:** No broker/credential/network capability, zero real calls.
