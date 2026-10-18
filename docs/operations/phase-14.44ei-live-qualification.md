# Future live qualification proposal

Exactly18 original IDs are frozen below, reconciled by AST against the original F source and the historical first-failure inventory. Original assertions and source bytes are unchanged; the failed normal case is not silently counted as remaining. The H packet's18IDs match exactly.

Proposed bounds: maximum4owned processes/3children, wall5seconds per workload, CPU10seconds, working-set256MiB, output8MiB, file writes32MiB, retries0. Driver300seconds, cleanup10seconds. Separately owned sentinel1process/15seconds/64MiB. No limit is owner-approved or independently enforced. The source/resource packet cannot execute itself.

No guest or live custody is created. Future destination, Job Object identity, owner, authentication, trusted timestamp, expiry, revocation, durable replay, cleanup and rollback authority remain null. Exact executable pins and command arguments are inherited as proposed references in live-approval.json, not permission to execute. Existing F allowlist and historical custody must not be changed.

Rollback must stop only verified owned jobs and remove only separately approved new implementation/custody. Historical evidence and unrelated processes are excluded. Stop on the first unexpected identity, resource, timeout or cleanup failure; preserve partial receipts.

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
