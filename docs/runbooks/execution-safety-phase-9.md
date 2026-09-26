# Phase 9 execution safety runbook

Safe default is `DISABLED + DISARMED`. Empty allowlist, zero quantity cap, zero notional cap, missing authentication, stale or degraded market data, expired risk approval, missing correlation, or unresolved `SUBMITTING` reconciliation state deny execution. Authentication, fresh data, risk approval, or strategy signals never arm execution.

The intended control flow is Signal -> Intent -> VALIDATED -> Risk -> RISK_APPROVED -> hard stop -> explicit bounded arm -> explicit execute -> execution safety policy -> SUBMITTING -> broker. The arm is process memory only and expires; it is not restored after restart. An explicit execute request is the only application path permitted to reach the gateway. Strategy, risk, reconciliation, startup, and authentication restoration have no execution dependency.

The final engineering review tightens this flow: authorization/audit -> account-scoped PostgreSQL admission/CAS -> committed SUBMITTING -> safety recheck -> gateway -> session lock -> final safety recheck -> HTTP. See [ADR-017](../adr/ADR-017-execution-admission-and-publication-fencing.md). Arms are bound to a non-secret session identity, expire at the exact deadline, and have a one-hour ceiling. Session replacement requires explicit re-arming.

The policy writes one durable authorization record for each evaluation. Denials are durable with a bounded reason. An allowed record records only authorization; it is not a submission acknowledgement. The service then performs a PostgreSQL versioned CAS to `SUBMITTING`, and only a successful CAS may call the gateway. A CAS conflict can therefore leave an allowed authorization audit with no HTTP request, which is expected and explainable from the order state.

The initial reconciliation gate rejects unresolved `SUBMITTING` state. Account admission is stricter: another SUBMITTING, SUBMITTED, ACKNOWLEDGED, OPEN, PARTIALLY_FILLED, FILLED or CANCEL_PENDING order blocks admission under the current conservative single-account reservation policy. FAILED, REJECTED and CANCELLED orders and historical audit rows do not block it. No automatic repair or reservation release is attempted.

If emergency stop is active before initial authorization, no SUBMITTING transition occurs. A denial after admission but before dispatch records the bounded denial and moves the unchanged submitting record to FAILED/PRE_DISPATCH_DENIED; zero HTTP is sent. Storage failure or a concurrent state change can leave SUBMITTING unresolved, requiring investigation. After the final transport-entry validation, treat the request as potentially in flight. Never retry automatically or blindly cancel after broker acceptance.

Application modify/cancel remain DISABLED. Before enabling them, implement command-specific safety, amended risk terms, immutable command identity, concurrency and reconciliation of request acknowledgement versus terminal broker state.

V10 adds unique non-null broker order identity. If upgrade finds duplicates, investigate ownership offline; do not delete or merge orders automatically. The audit did not migrate the running user database or contact production order endpoints.

## V10 duplicate-history upgrade procedure

1. Keep execution disabled/disarmed and stop application writers. Take a verified PostgreSQL backup before upgrade. Test the upgrade against a restored copy first.
2. In a restricted operator database session, run this read-only preflight (broker IDs are operational account data; do not publish its output):

   ```sql
   SELECT broker_order_id, count(*) AS local_orders
   FROM trading.orders
   WHERE broker_order_id IS NOT NULL
   GROUP BY broker_order_id HAVING count(*) > 1;
   ```

3. If any row exists, stop the upgrade. Preserve every historical order and associated risk/reconciliation/authorization row. Investigate exact broker ownership/correlation from existing evidence; do not infer ownership from symbol, quantity or timestamp proximity. No generic DELETE, merge, nulling or automatic repair is authorized by this runbook.
4. Produce a separately reviewed, backed-up correction plan if authoritative evidence supports one. Otherwise keep the deployment blocked. This release does not mutate the duplicate history to force success.
5. V10 uses a normal transactional CREATE UNIQUE INDEX. On PostgreSQL a duplicate-data failure rolls back the index creation and leaves V9/history/data intact. Check Flyway version and absence of the V10 index; do not blindly run Flyway repair or mark V10 successful. Also distinguish duplicate violations from permissions, lock or disk failures.
6. Only after approved remediation and a clean preflight, rerun normal Flyway migration/validate in a maintenance window. Verify the unique partial index is valid, nullable historical IDs still load, and the application starts disabled/disarmed. The index build blocks concurrent writes and scans existing orders; size the window and free disk accordingly.

Integration coverage uses isolated PostgreSQL databases for V9 -> V10, repeated NULLs, duplicate insertion rejection and failed duplicate-history upgrade with exact row preservation. It never touches the operator database.
