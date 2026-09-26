# ADR-016: Phase 9 execution safety boundary

Status: accepted.

The platform starts with execution disabled and disarmed. A risk approval is a durable prerequisite, but it is not execution authorization. Immediately before the order CAS to `SUBMITTING`, a broker-independent `ExecutionSafetyPolicy` evaluates independent gates: enabled configuration, volatile runtime arming, emergency stop, current order state, matching unexpired risk approval and policy version, authenticated Kite session, stable instrument allowlist, hard quantity and BigDecimal notional caps, fresh market data, unresolved reconciliation state, and persisted broker correlation.

The policy has no gateway dependency and cannot execute. Its decision uses bounded reasons and is audit-ready. The authorization audit is written as part of policy evaluation before the order CAS. An allowed audit means only that the gates passed at that instant; it does not claim that `SUBMITTING` was persisted or that a broker accepted the request. The order CAS/state and broker result remain separate execution facts. If the CAS fails, the allowed authorization remains a durable explanation of the attempted authorization and no HTTP request is made.

The runtime arm is an in-memory latch with a bounded expiry; restart always returns to disarmed. Arming never scans or submits orders. The repository compare-and-set on the exact order version remains the authoritative concurrency boundary, so a changed order cannot pass the transition twice. `SUBMITTING` remains ambiguous until reconciliation; no automatic retry or panic cancellation is performed if the kill switch changes after that point. Under the single-account safety policy, any durable unresolved `SUBMITTING` order blocks a new execution until reconciliation resolves it; this gate does not repair or cancel the existing order.

Phase 9 uses only loopback fake-broker verification. No real Kite order endpoint is contacted.
