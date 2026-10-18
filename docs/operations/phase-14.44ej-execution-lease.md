# Execution lease and atomic boundary

Phase1444EJExecutionLease.v1 has PENDING,VERIFIED,RESERVED,CONSUMED,EXPIRED,REVOKED,REJECTED,ABORTED states. No real lease is issued. Exact synthetic state transitions live in enforcement.py. Terminal states cannot return to VERIFIED. Reservation inserts permanent decision/nonce tombstones. Consumption requires critical revalidation and never removes tombstones.

Future sequence (not implemented):
1. Verify authentic owner decision and role.
2. Bind baseline/sources/helper/commands/test IDs/resources/cleanup exactly.
3. Verify trusted time, expiry and revocation.
4. Atomically reserve unique decision and nonce.
5. Create an owned Job Object and set/read back mandatory limits.
6. Create pinned root suspended and assign before resume.
7. Verify retained handle/PID/creation, job ownership and helper relationship.
8. Revalidate critical authority under a verified revocation/consumption boundary.
9. Commit durable single-use consumption, then resume once.
10. Retain exit/resource/cleanup receipts.

Crash before reservation: no process exists; a later attempt must reverify the unchanged decision. Crash after reservation or before consumption: fail closed, reconcile/clean suspended owned processes; reservation burned, fresh decision required. Crash after consumption before resume: decision remains spent, never retry resume under a regenerated lease. Crash after resume: owned-job cleanup and retained partial receipts; consumed ID stays spent. Cleanup failure never restores authorization. Unknown commit outcome is treated as spent pending independent recovery. Revocation/expiry between reservation and consumption aborts; after consumption it triggers owned-job containment under separate cleanup authority. No unrelated PID termination.

Exact original eighteen IDs (not executed):

apps/strategy-engine/tests/test_phase1444ef.py::test_live_lifecycle[nonzero-7]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_lifecycle[early-0]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_lifecycle[child-0]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_lifecycle[descendant-0]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_lifecycle[parent-crash-8]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_lifecycle[child-crash-0]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[access-denied]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[image31]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[missing-image]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[wrong-image]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[invalid-handle]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[query-rights]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[assignment-failure]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[membership-failure]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[pid-reuse]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[handle-mismatch]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[creation-mismatch]
apps/strategy-engine/tests/test_phase1444ef.py::test_live_timeout_sentinel_and_exited_query
