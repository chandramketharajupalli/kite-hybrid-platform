# Bounded acquisition decision

Phase1446HAcquisitionDecision.v1 remains NOT_AUTHORIZED and DEFER_PENDING_SOURCE_VERIFICATION. It binds the actual baseline, frozen plan, partial allowlist path and canonical fingerprint. The packet cannot authorize itself.

Approved repository/endpoint, maximum requests, maximum bytes, timeout, retry limit, destination, operator, approval evidence, trusted timestamp, expiry, revocation, durable replay and cleanup authority are explicitly null. These are unresolved approval requirements, not unlimited defaults. Unknown limits must deny execution.

Future independently approved procedure, not executed:
1. Verify owner/operator authority, exact decision payload, trusted validity, revocation and durable replay protection.
2. Require a complete exact artifact allowlist and independently approved repository identity, endpoint and release paths.
3. Establish deny-by-default network boundaries, explicit DNS/TLS/redirect policy, finite request/byte/time/retry budgets and isolated destination.
4. Acquire only approved POM/JAR/metadata objects; prohibit arbitrary Maven expansion and credentials unless separately approved.
5. Verify expected hashes, publisher provenance/signatures where required and licenses before releasing quarantined artifacts.
6. Quarantine unexpected/failed objects; retain failure evidence. Cleanup needs explicit authority and bounded target paths.
7. Produce durable immutable acquisition receipts and reconcile the exact manifest.
8. Populate an isolated offline repository, demonstrate complete dependency/plugin closure and seek separate Java runtime authorization.

No workload-family, infrastructure, Java, broker or live-execution approval is implied. Current requests/downloads=0; dependency acquisition/implementation/runtime/GateA/B NOT_AUTHORIZED; operational NO_GO.
