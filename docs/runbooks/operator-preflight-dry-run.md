# Operator preflight dry run (Phase 10.1)

**THIS HARNESS DOES NOT AUTHORIZE LIVE TRADING.**

`OperatorPreflightDryRunTest` is integration-test infrastructure only. It boots
`TradingCoreApplication` with `WebApplicationType.NONE` and the development
profile against a new database in a Testcontainers PostgreSQL 17.6 container for
each case. It does not load `.env`, use the development database, restore a real
token, or start Kite market streaming. Testcontainers removes its containers.
No production source, configuration, migration, or operator transport is added.

Run from the repository root with an existing JDK 21 and a running Docker engine:

```powershell
# Set JAVA_HOME for this process to your existing JDK 21 if needed.
.\mvnw.cmd -pl apps/trading-core -Pintegration -DskipUnitTests=true '-Dit.test=OperatorPreflightDryRunTest' verify
```

For the structural checks and full regression suite:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -Pintegration -DskipUnitTests=true verify
```

The full integration profile also runs the existing **loopback fake-broker**
execution tests. Those tests are separate from this harness. The dry-run harness
never invokes `execute`, `executeRiskApproved`, or any gateway mutation method.
No test requires production Kite mutation.

Current Phase 10.5 fixtures start HALTED and explicitly release the runtime latch
in synthetic setup before constructing otherwise-valid risk evidence. This
test-only setup never resumes a real environment. See the
[runtime halt runbook](runtime-emergency-stop.md).

## Construction and invocation

The real Spring configuration supplies `OperatorExecutionService`,
`ExecutionSafetyPolicy`, `LiveTestExecutionChecks`, `PostgresOperationalReadiness`,
`OrderApplicationService`, `PostgresOrderRepository`, `RiskService`, and
`PostgresRiskDecisionStore`. Application/repository spies delegate real behavior;
they only detect forbidden calls. The actual application context and Flyway
datasource wiring are exercised, including destroy/recreate against the same
disposable database.

Test-only replacements provide a deterministic clock, an in-memory instrument
registry, the production `InMemoryLatestMarketDataStore`, a controlled health
source, synthetic broker read snapshots, and an authenticated synthetic
`KiteSession`. Authentication and WebSocket transports are fakes. REST transport
is loopback-only and records HTTP methods; the execution gateway fails the test
on invocation. No real token, account, customer, instrument token, or broker order
ID is needed. Command identity is synthetic; application-generated order UUID
and correlation are validated and persisted through normal boundaries.

Each fixture places one primary order through `OrderApplicationService.place`,
asserts `VALIDATED`, evaluates it through `RiskService.evaluate`, and reloads
`RISK_APPROVED`. Risk uses empty synthetic positions/holdings/orders, sufficient
synthetic margins, and positive configured cash reserve. The account-exposure
denial case adds one separate synthetic blocker. Fault-injection cases use SQL
in the disposable fixture only, following existing integration-test conventions;
they do not introduce application state-repair capabilities.

The exact observation is:

```java
ExecutionReadiness report = operator.preflight(orderId);
```

Before arm, all gates except `RUNTIME_ARMED` and `SESSION_BOUND` pass. Overall
reason is `DISARMED`. The test then calls the real
`operator.arm(orderId, Duration.ofSeconds(30))` with synthetic identity and limits.
Fully READY preflight leaves the order `RISK_APPROVED`, without a broker ID.
**The test ends without execution.** Zero, negative, and over-maximum durations
deny; the exact expiry instant disarms; repeated disarm is harmless. An emergency
stop denies regardless of arm, and replacement session identity invalidates it.

After context recreation, the order and risk approval survive, but arm does not.
Fresh local market/session evidence is supplied again so restart's overall
denial is specifically `DISARMED`.

## Current gate mapping

The current enum has **24 gates**. `@EnumSource(Gate.class)` exercises every value;
an exhaustive switch requires an explicit case when the enum grows. Every
preflight asserts the complete enum key set. These are the real gates, not an
additional readiness implementation.

| Gate | Evidence / deterministic denial |
|---|---|
| EXECUTION_CAPABILITY_CONFIGURED | Execution properties disabled → EXECUTION_DISABLED |
| AUTHENTICATED | Synthetic session cleared → AUTHENTICATION_UNAVAILABLE |
| RUNTIME_ARMED | Disarmed/expired in-memory arm → DISARMED |
| SESSION_BOUND | Replaced session UUID → DISARMED |
| EMERGENCY_STOP_CLEAR | Stop supplier true → EMERGENCY_STOP |
| ORDER_RISK_APPROVED | Persisted VALIDATED state → INVALID_ORDER_STATE |
| RISK_DECISION_CURRENT | Missing/rejected → RISK_APPROVAL_MISSING; expired/future → RISK_APPROVAL_EXPIRED; order version → ORDER_VERSION_CHANGED; policy version → RISK_POLICY_MISMATCH |
| INSTRUMENT_ALLOWED | Empty normal allowlist → INSTRUMENT_NOT_ALLOWED; also requires registry membership |
| QUANTITY_WITHIN_CAP | Normal cap exceeded → QUANTITY_CAP_EXCEEDED |
| CORRELATION_PRESENT | Persisted correlation absent → CORRELATION_MISSING |
| RECONCILIATION_CLEAR | Other account order has blocking exposure → RECONCILIATION_REQUIRED |
| MARKET_DATA_HEALTHY | No connection, STARTING, missing active subscription, desired/active mismatch, absent/revoked tick, degraded health → MARKET_DATA_UNAVAILABLE |
| MARKET_DATA_FRESH | Receive/exchange timestamp stale or future → MARKET_DATA_STALE |
| NOTIONAL_WITHIN_CAP | Normal decimal notional exceeded → NOTIONAL_CAP_EXCEEDED |
| OPERATOR_CONTROL_ENABLED | Explicit opt-out → OPERATOR_CONTROL_DISABLED |
| LIVE_TEST_MODE_ENABLED | First-live mode disabled → LIVE_TEST_DISABLED |
| LIVE_TEST_INSTRUMENT_ALLOWED | Empty independent allowlist → LIVE_TEST_INSTRUMENT_DENIED |
| LIVE_TEST_QUANTITY_WITHIN_CAP | Independent quantity cap exceeded → LIVE_TEST_QUANTITY_CAP |
| LIVE_TEST_NOTIONAL_WITHIN_CAP | Independent decimal notional exceeded → LIVE_TEST_NOTIONAL_CAP |
| LIVE_TEST_ARM_DURATION_VALID | Zero configured duration → ARM_DURATION_INVALID |
| DATABASE_READY | Genuine V9 migration target / malformed V10 index → DATABASE_NOT_READY |
| RECONCILIATION_STORE_HEALTHY | Reconciliation trade table unavailable → RECONCILIATION_STORE_UNAVAILABLE |
| RECONCILIATION_CONFLICT_CLEAR | Persisted AMBIGUOUS, CONFLICT, BROKER_ORDER_MISSING, BROKER_STATE_UNAVAILABLE → RECONCILIATION_CONFLICT |
| TRADING_READ_AVAILABLE | Orders/trades provider missing separately or together → TRADING_READ_UNAVAILABLE |

Some faults necessarily fail more than one gate. Operator opt-out intentionally
returns its denial for all gates. An unreadable reconciliation store fails the
operational probe closed. The tests assert the relevant bounded gate reason,
rather than assuming it must always be the overall first denial.

Risk freshness and version/policy identity are one gate. Connection, subscription
consistency, health and tick presence are one gate. Tick receive and exchange
freshness share another. Session binding shares the `DISARMED` reason. Arming
configuration checks and arm duration rejection are related but separate
operations; the harness never bypasses operator arming to manufacture READY.

## Observation and zero-mutation proof

Every harness observation snapshots persisted orders, risk decisions,
authorization rows, reconciliation decisions and trades, then asserts equality.
Application/repository invocation histories reject execution/admission calls.
A fixture-only database constraint rejects **any** SUBMITTING write or attached
broker order ID, including transient changes. Gateway count and loopback POST,
PUT, DELETE counts stay zero; all loopback HTTP counts, including GET, stay zero.
Preflight also leaves synthetic read-provider invocation counts unchanged.

SQL instrumentation delegates normal JDBC transaction behavior and records only
SQL shapes, not parameters. It asserts SELECT/WITH-only statements, no row locks,
no advisory locks, and a bounded statement count. Authorization counters remain
absent. Captured denial logging is exactly the bounded action/reason message;
READY emits no authorization record or readiness denial log. No order, symbol,
instrument, broker ID, session identity, credentials, quantity or price appears
in that message, and execution metric tag keys remain bounded.

`OperatorControlArchitectureTest` traverses production preflight calls and
explicit concrete evidence implementations behind interfaces. It rejects broker
transport, gateway, authorization audit and lifecycle/execution reachability.
The integration test also inspects its own bytecode for forbidden invocation and
operator transport dependencies. Existing architecture checks reject automatic
arm/execute callers and operator web/scheduler dependencies. Static analysis is
not a universal proof against reflection or arbitrary future dynamic dispatch;
runtime counters, SQL capture and the DB constraint provide independent checks.

READY is not a reservation or execution permission. A second preflight denies
after market expiry, emergency stop, arm expiry, session replacement, order
version change, or recorded reconciliation conflict. No side-effecting
reconciliation is invoked and no conflict is automatically cleared.

Quantity caps are inclusive and tested at cap and cap+1 independently. Decimal
notional cases include the exact cap and `0.000000000000000001` above it (the
persisted monetary scale is 18). LIMIT uses the larger of accepted fresh market
price and persisted limit; MARKET uses the accepted tick. No floating point is
used. Market freshness is tested one nanosecond before expiry and at expiry;
persisted risk timestamp tests use PostgreSQL's microsecond precision.

Generation, mode and subscription cases revoke the production publication permit
and reject late publication. They exercise the store-to-preflight boundary;
existing market-data adapter tests verify that actual generation/subscription
changes revoke those permits. This harness does not start a WebSocket.

## Database and capability evidence

V10 succeeds through real Spring wiring. V9 fails without tampering with history.
Missing schema/history and wrong authoritative history are probed directly with
`PostgresOperationalReadiness`, preserving the complete V10 history. The existing
`PostgresFlywayHistoryPreflightTest` covers dual histories, authoritative search
path, migration upgrade, schema overrides, malformed history/index variants and
production context restarts, entirely in disposable containers. No SQL preflight
script or migration was changed.

Read-provider **availability** only means configured capability. It does not
prove authentication to a live account or a successful live broker read. The
harness deliberately proves that determining availability sends no requests.

## Scale and concurrency review

A successful harness preflight performs eight SQL statements with explicit
Flyway schema: order lookup, risk lookup, two account exposure checks, history
relation check, V10/schema/index validation, reconciliation-trade accessibility,
and account-wide reconciliation conflict lookup. Without an explicit history
schema, `current_schema()` adds one query. There is no per-order/per-trade query
loop or N+1 behavior, and returned evidence is bounded. No caching is added.

Order and risk lookups are keyed by `order_id` and supported by primary-key
indexes (PostgreSQL can still choose a sequential scan for tiny tables). V10's unique partial
broker-order index supports broker identity integrity; correlation has its own
unique partial index. Reconciliation's `(order_id, observed_at)` index serves
per-order history, **not** the account-wide outcome predicate. Neither the
account-wide state predicates nor reconciliation outcome predicate has a dedicated
index in V10. Clean-history scans may therefore grow with table size; V10's
duplicate-identity validation also inspects account-wide data. Constant query
count does not establish constant query cost or a production latency SLA.

These are SELECT operations, with normal PostgreSQL access-share locks rather
than `FOR UPDATE` or execution/risk advisory admission locks. The operational
probe uses its existing read-only transaction and five-second timeout; the
whole preflight is not a single database snapshot or one globally timed
transaction. Concurrent evidence can change immediately after observation;
authorization must recheck it at its own boundary.

No speculative index migration is introduced. Representative production-size
query-plan/latency evidence is still needed before claiming scale readiness.
Adding V11 merely for this harness would also require review of the explicitly
V10 readiness contract. No safety evidence is cached or weakened to improve
test performance.

## Limitations and production boundary

This verifies local application readiness with controlled evidence, not live
broker acceptance, account correctness, network health, or permission to trade.
There is no operator HTTP endpoint, browser UI, startup arm, scheduler, listener
or worker: an externally reachable invocation host would be a separate security
and operational design. Production execution/control/live-test defaults remain
disabled; allowlists remain empty and caps remain zero. Runtime arming remains
ephemeral. The real development database, encrypted token, validated universe
and broker account are outside this harness.

`execute()` is intentionally never invoked, even after READY. Future controlled
live work requires separate explicit authorization and its own reviewed scope.

## Verification record — 2026-09-28

Starting baseline was a clean `develop` at `72de980`. No production-code or
migration change was needed. Verification used the installed JDK 21.0.12,
Python 3.12.14, and Docker/Testcontainers; no live broker validation was repeated.

| Check | Result |
|---|---|
| Full `mvnw -Pintegration verify` | PASS: 970 unit/architecture + 205 integration tests; no failures/errors/skips |
| Dry-run harness within full integration run | PASS: 79 tests, all 24 gates |
| Final expanded production call-graph architecture check | PASS: 3 OperatorControlArchitectureTest tests |
| Authentication, market-data, trading-read, order, risk, reconciliation, strategy, operator, context and Flyway regressions | Included in the full Java suites |
| Existing loopback fake-broker execution suite | PASS: 51 tests; separate from the observation-only harness |
| Python pytest | PASS: 14 tests |
| Ruff (engine and repository scripts) | PASS |
| mypy (engine/tests and repository scripts) | PASS: 6 + 2 files |
| Dependency convergence with integration profile | PASS |
| Project verification, secret scan, diff whitespace checks | PASS; zero secret findings |

Local build evidence is in ignored `tmp/phase10-full-java.log`,
`tmp/phase10-architecture-final.log`, `tmp/phase10-convergence.log` and Maven
Surefire/Failsafe reports. The harness reports zero gateway calls and zero broker
HTTP requests; production Kite order mutations performed by this work: **zero**.
The development database and encrypted token were not used. Changes were left
uncommitted, with no push.
