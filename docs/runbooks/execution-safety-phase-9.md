# Phase 9 execution safety runbook

Safe default is `DISABLED + DISARMED`. Empty allowlist, zero quantity cap, zero notional cap, missing authentication, stale or degraded market data, expired risk approval, missing correlation, or unresolved `SUBMITTING` reconciliation state deny execution. Authentication, fresh data, risk approval, or strategy signals never arm execution.

The intended control flow is Signal -> Intent -> VALIDATED -> Risk -> RISK_APPROVED -> hard stop -> explicit bounded arm -> explicit execute -> execution safety policy -> SUBMITTING -> broker. The arm is process memory only and expires; it is not restored after restart. An explicit execute request is the only application path permitted to reach the gateway. Strategy, risk, reconciliation, startup, and authentication restoration have no execution dependency.

The policy writes one durable authorization record for each evaluation. Denials are durable with a bounded reason. An allowed record records only authorization; it is not a submission acknowledgement. The service then performs a PostgreSQL versioned CAS to `SUBMITTING`, and only a successful CAS may call the gateway. A CAS conflict can therefore leave an allowed authorization audit with no HTTP request, which is expected and explainable from the order state.

The reconciliation gate is precise for the current single-account model: a durable order in unresolved `SUBMITTING` state blocks a new execution because account exposure may be uncertain. Other audit rows, completed orders, and ordinary terminal states do not block execution. The gate does not attempt automatic repair.

If emergency stop is active before `SUBMITTING`, execution is denied. If it changes after `SUBMITTING` and before HTTP, the system does not perform an unsafe retry; the attempt remains subject to reconciliation. If the broker has accepted the request, the system does not blindly cancel it. Phase 9 does not test or contact a production order endpoint.
