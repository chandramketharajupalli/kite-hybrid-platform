# Controlled one-order procedure and terminal host

**THIS PHASE DOES NOT AUTHORIZE LIVE TRADING. NO REAL ORDER IS TO BE SENT.**

This procedure is for a future separately human-authorized exercise. Phase 10.2
validation uses disposable PostgreSQL and loopback HTTP only. Never use the real
development database, account, stored token or running application for these tests.
The [design comparison and baseline call graph](../operations/one-order-operator-design.md)
explain why a terminal host was selected instead of an execution HTTP endpoint.

## Trust and launch boundary

`OperatorConsoleApplication` is an alternate entrypoint. It requires a real
`System.console()` and exactly `--interactive-operator` before Spring starts.
Piped/redirected input, missing flags and additional flags fail before startup.
The ordinary packaged main remains `TradingCoreApplication`; normal startup does
not launch a console, arm, create an order or execute anything.

The console owns the application context in **its own JVM**. It cannot control or
arm another running instance. For a future exercise, use one reviewed operator
deployment, not an extra competing client beside the running application. Existing
authentication, instrument and market-data setup boundaries must establish usable
evidence in this same JVM before preflight. The host adds no setup, credential,
candidate-creation or order-mutation HTTP route.

Future launch shape, after deployment review (not a command run by this phase):

```text
java -cp <REVIEWED_RUNTIME_CLASSPATH> com.kitehybrid.platform.bootstrap.OperatorConsoleApplication --interactive-operator
```

`<REVIEWED_RUNTIME_CLASSPATH>` must contain the trading-core plain jar and its
resolved runtime dependencies. The executable Boot jar still launches the normal
application. No credentials or command batch belong on this command line.
Do not improvise a remote console bridge, scheduled invocation, shell pipe or
HTTP wrapper. Console input is read one command at a time; there is no history
file, autocomplete, wildcard, file input, implicit selection or batch mode.

Trust comes from controlled access to the application's controlling terminal and
OS process, not from an arbitrary username string. **There is no durable
authenticated human identity/RBAC capability here.** OS login and restricted
deployment access must be reviewed before use. Local malware or a person with
control of this OS account/JVM is inside the trust boundary. The host does not
claim resistance to a compromised JVM, debugger, database administrator or OS.
No identity is inferred from request headers, Windows username or CLI arguments.
There is no new HTTP CSRF/Origin/Host/forwarded-header/TLS/replay surface.

## Future configuration checklist

These are independent explicit prerequisites, with **no production values chosen
by this phase**:

| Setting | Required reviewed value | Default |
|---|---|---|
| KITE_ORDER_EXECUTION_ENABLED | explicit opt-in | false |
| KITE_OPERATOR_CONTROL_ENABLED | explicit opt-in | false |
| KITE_LIVE_TEST_ENABLED | explicit opt-in | false |
| KITE_ORDER_EXECUTION_ALLOWED_INSTRUMENTS | `<REVIEWED_INSTRUMENT>` platform UUID | empty |
| KITE_LIVE_TEST_ALLOWED_INSTRUMENTS | same reviewed candidate, independently configured | empty |
| KITE_ORDER_EXECUTION_MAX_QUANTITY | `<MAX_QTY>` | 0 |
| KITE_LIVE_TEST_MAX_QUANTITY | `<FIRST_LIVE_MAX_QTY>` | 0 |
| KITE_ORDER_EXECUTION_MAX_NOTIONAL | `<MAX_NOTIONAL>` exact decimal | 0 |
| KITE_LIVE_TEST_MAX_NOTIONAL | `<FIRST_LIVE_MAX_NOTIONAL>` exact decimal | 0 |
| KITE_LIVE_TEST_ARM_MAX_DURATION | `<ARM_DURATION>` within existing one-hour ceiling | 0s |

Normal risk, freshness, authentication, V10 database and reconciliation checks
also apply. Missing/zero/empty values deny; existing malformed/extreme value
validation remains. Configuration is immutable after context creation. No runtime
cap/allowlist override or reload is invented. PAPER/default emergency stop and
all existing application safety defaults are unchanged. Do not bypass existing
mode validation to make a launch succeed.

## Obtain one legitimate candidate — no execution

The terminal intentionally has no create command. The future candidate must come
from existing application boundaries, with one explicit immutable `PlaceOrder`
input reviewed separately. The exact typed input is:

```text
PlaceOrder(
  <UNIQUE_IDEMPOTENCY_KEY>, InstrumentId(<REVIEWED_INSTRUMENT_UUID>), <SIDE>,
  <REVIEWED_QUANTITY>, <ORDER_TYPE>, <PRODUCT>, <VALIDITY>,
  <OPTIONAL_LIMIT_PRICE_BIGDECIMAL>, <OPTIONAL_TRIGGER_PRICE_BIGDECIMAL>,
  <DISCLOSED_QUANTITY>, <VARIETY>)
```

The invocation is `application.place(command)` → returned platform `OrderId` and
VALIDATED → `riskService.evaluate(orderId)` → APPROVED/RISK_APPROVED or REJECTED.
Stop on rejection. Never insert SQL, force lifecycle state, fabricate correlation,
reuse broker IDs or assume signal/risk approval is execution permission. Persisted
command and normally generated correlation are authoritative. The returned exact
OrderId must be retained by the human operator; there is no “latest approved” lookup.

In this phase this creation invocation exists only in the synthetic integration
fixture (`Fixture.approve`); no production creation CLI/input surface was added or
invoked. A future manual candidate-creation invocation host remains a separately
reviewed deployment prerequisite if no existing workflow supplies the candidate.

## Exact manual protocol

Commands accept lowercase canonical platform UUIDs, single ASCII spaces and no
unknown flags/trailing arguments. Arm duration syntax is positive integral seconds
such as `<SECONDS>s`, additionally bounded by configuration. There are no symbol,
broker-ID, strategy-ID, quantity or price inputs to execution.

```text
status
preflight <ORDER_ID>
arm <ORDER_ID> <SECONDS>s
CONFIRM arm <ORDER_ID>
preflight <ORDER_ID>
execute <ORDER_ID>
CONFIRM execute <ORDER_ID>
status
reconcile <ORDER_ID>
```

This is a **manual protocol description, not a batch script**. Each confirmation
is a separate interactive reply to a prompt. EOF ends the console and disarms.
`disarm` is accepted explicitly and is idempotent. A declined confirmation or
malformed command revokes the arm. Never feed these lines through redirected stdin.

Before arm, all 24 current readiness gates must pass except RUNTIME_ARMED and
SESSION_BOUND, which must report DISARMED. Any other denial blocks arm. The console
requires this explicit preflight, and service arm independently rereads it. The
service binds the selected ID, approved order version, session identity, creation
time and expiry, then verifies fully READY again. The console requires another
explicit READY preflight before execute; it never auto-executes after arm.

SESSION_BOUND now additionally checks exact OrderId/version. A different ID yields
ORDER_NOT_ARMED; a changed approved version yields ORDER_VERSION_CHANGED. The gate
count remains 24. Legacy session-wide runtime capability cannot satisfy production
first-live binding or claim a one-order permit. Normal callers cannot substitute
another ID or order terms after confirmation.

## One attempt and immediate disarm

Execution repeats real preflight, atomically claims the memory-only permit, invokes
the existing application safety policy, obtains PostgreSQL account admission,
commits SUBMITTING before HTTP, and validates again inside the broker session lock
immediately before dispatch. It never trusts a previous READY result.

Permit states are NONE (fresh runtime), UNUSED, CLAIMED and CONSUMED. An execute
invocation conservatively revokes an UNUSED permit even when denied before claim.
The owning attempt consumes its claim and disarms in `finally`, including on
unchecked exceptions/Errors, rejection, timeout, malformed response or persistence
failure. No outcome restores UNUSED. A disarmed CLAIMED permit can remain CLAIMED
while its owner is still in flight; it cannot be replaced/rearmed. Its owner alone
can finish it. The terminal does not run a second command concurrently with HTTP.

An explicit later arm would be a **new** human authorization, never a retry under
the old permit. It still requires complete pre-arm readiness and RISK_APPROVED.
All potentially submitted outcomes leave RISK_APPROVED, so they cannot be rearmed.
Do not reset lifecycle or audit data to make a retry possible.

Two threads share an atomic claim. Separate JVMs have separate ephemeral arms,
but share PostgreSQL advisory account admission and version CAS. Only one can
commit the selected order's RISK_APPROVED → SUBMITTING transition. A losing CAS
does not dispatch. A different order cannot use the same exact-order grant.
There is no durable arm/permit restoration or new migration.

## Failure and crash matrix

| Boundary/outcome | Durable order | Arm / permit after return or restart | HTTP possible? | Retry / required action |
|---|---|---|---|---|
| Pre-arm evidence/confirmation denied | RISK_APPROVED (or existing state) | Disarmed; no usable grant | No | Correct evidence; separate human preflight/authorization only |
| Execute denied before claim | unchanged | Disarmed; prior UNUSED grant consumed | No | Old grant cannot be reused |
| Claimed; authorization/admission denied | usually RISK_APPROVED | Disarmed / consumed | No | Inspect audit; new explicit authorization only if no submission and all evidence valid |
| SUBMITTING committed; final validation denies | FAILED when existing CAS can mark pre-dispatch denial; otherwise SUBMITTING | Disarmed / consumed | No in deterministic tests | Inspect state/audit; no reset or automatic retry |
| Broker 400/401 rejection | FAILED under existing mapping | Disarmed / consumed | One request sent | No retry under grant; inspect explicit broker reads |
| Accepted + valid acknowledgement persisted | SUBMITTED with broker ID | Disarmed / consumed | One | Explicit reconciliation |
| Accepted + response lost/reset/timeout | SUBMITTING, no safe acknowledgement | Disarmed / consumed | May have accepted one | NO RETRY; reconcile by exact persisted correlation |
| Malformed acknowledgement | SUBMITTING | Disarmed / consumed | May have accepted one | NO RETRY; explicit reconciliation |
| Response received but local persistence fails | SUBMITTING | Disarmed / consumed | One may be accepted | NO RETRY; explicit reconciliation |
| Unexpected exception/Error after admission | SUBMITTING unless existing failure handling can safely advance | Disarmed / consumed | Treat as possible unless evidence proves otherwise | NO RETRY; inspect/reconcile |
| Process crash before admission (unused, claimed, or audit recorded) | RISK_APPROVED | New JVM DISARMED / NONE; old grant lost | No via this path | Fresh human authorization required; old permit never restored |
| Process crash after admission or immediately before HTTP | SUBMITTING | New JVM DISARMED / NONE | Cannot infer dispatch from local state | NO RETRY; explicit broker reads/reconciliation |
| Crash after HTTP response but before acknowledgement persistence | SUBMITTING | New JVM DISARMED / NONE | One may be accepted | NO RETRY; explicit reconciliation |
| Crash after acknowledgement persistence | SUBMITTED | New JVM DISARMED / NONE | One | Explicit reconciliation |

There is deliberately no cross-system transaction between PostgreSQL and broker
acceptance. Correlation recovery, not resubmission, addresses that ambiguity.

## Explicit reconciliation and abort

After the attempt, remain disarmed. `reconcile <ORDER_ID>` calls the existing
`OrderReconciliationService` explicitly, uses broker ID first and exact persisted
correlation second, validates terms, deduplicates trades and atomically applies
permitted lifecycle changes. No heuristic matching, automatic conflict clearing,
reconciliation side effect in preflight, or automatic execution is added.

For abort: activate emergency stop through an already reviewed boundary **if one
is available**, disarm, and do not retry. This codebase's TradingProperties snapshot
is immutable; editing an environment variable does not toggle the running JVM.
No new stop HTTP endpoint is introduced here. Inspect durable order state and
authorization audit, perform explicit broker reads and reconcile. Stop the
application if evidence is inconsistent. If a request is currently blocking the
single command thread, terminal/process shutdown loses the arm but does not undo
the request. **Stopping the JVM is NOT cancellation of an accepted broker order.**
This host has no modify/cancel capability.

## Audit and limitations

Bounded events are PREFLIGHT_DENIED, ARM_REQUEST, ARM_SUCCESS/ARM_DENIED,
EXECUTE_REQUEST, EXECUTE_DENIED, EXECUTE_ATTEMPT, DISARM and RECONCILE_REQUEST.
Operator logs contain action/reason enums only. Terminal replies contain bounded
readiness, arm metadata, permit state, order lifecycle state or reconciliation
outcome, never raw provider exception messages, broker IDs, session UUIDs, tokens,
API credentials, prices or quantities. The input OrderId is typed by the operator
and used to confirm exact selection; it is not emitted as a structured log label.
No human identity is invented, and no high-cardinality metric labels are added.
Existing durable authorization records contain their existing order/version/policy
linkage. Terminal confirmation itself is not durable proof of a named human.

Freshness/capability/configuration, risk, fencing, reconciliation and last-dispatch
checks remain mandatory. Immutable configuration is tested via context creation;
volatile evidence changes are injected immediately before the transport callback
in tests. This is not a guarantee that emergency stop can undo a request once
bytes have been sent. Production-scale latency and external account correctness
remain outside synthetic validation.

## Disposable validation

Use installed JDK 21 and Docker; these commands do not launch the real host:

```powershell
.\mvnw.cmd -pl apps/trading-core '-Dtest=OneOrderPermitTest,TrustedOperatorConsoleTest,OperatorControlArchitectureTest' test
.\mvnw.cmd -pl apps/trading-core -Pintegration -DskipUnitTests=true '-Dit.test=OneOrderOperatorIntegrationTest,OperatorPreflightDryRunTest,LocalKiteOrderExecutionIntegrationTest' verify
.\mvnw.cmd -Pintegration verify
```

The one-order fixture creates its own database in PostgreSQL 17.6 Testcontainers,
uses actual production Spring order/operator/risk/reconciliation configurations,
and uses synthetic account snapshots/session/market evidence. The real Kite order
adapter/transport points only to `127.0.0.1`. It counts POST/PUT/DELETE and observes
committed SUBMITTING before accepting POST. No real broker route is contacted.

Crash tests spawn an integration-test-only child JVM and use `Runtime.halt` at
seven checkpoints. The child accepts only a loopback JDBC URL with the generated
`one_order_...` database name and a loopback broker URL. Parent tests verify the
exit checkpoint, durable state, HTTP counts, and restart denial. These are actual
abrupt child-process exits, in addition to context restart tests. Crash helper
classes are not packaged in production artifacts.

Do not run the production terminal launcher or live diagnostics as part of these
tests. Passing tests and a synthetic READY result do not authorize a live order.

## Phase 10.2 validation record

Validated on 2026-09-28 from `develop` baseline `4fb2b2b`, initially clean and
synchronized with remote develop. Installed JDK 21 and Docker/Testcontainers were
used; no real operator host or live broker diagnostic was launched.

- Full Maven integration-profile verification: 989 unit/architecture tests and
  252 PostgreSQL integration tests, zero failures/errors/skips. This includes
  authentication/restart, market data, trading reads, orders, risk, reconciliation,
  strategy, development/production contexts, migrations, 79 preflight dry-run
  cases, 51 existing fake-broker cases and 47 new one-order cases.
- Final focused rerun after strengthening the permit and captured-correlation
  assertions: all 47 one-order integration tests passed again.
- Python: 14 tests passed; Ruff and mypy passed for the engine and project scripts.
- Dependency convergence passed for both Maven modules; project verification,
  secret scanning and `git diff --check` passed.
- Production artifacts retain the normal application entrypoint and exclude the
  integration-only abrupt-crash helper and test fixtures.

The new tests exercise one POST on successful execution and zero PUT/DELETE,
wrong-order denial, permit claim at gateway entry, final transport evidence
changes, concurrent calls/contexts, response-loss recovery using the correlation
captured from the actual HTTP request, local persistence failures, and seven
actual child-JVM crash checkpoints. A READY result is not execution permission.

Production Kite order mutations during this work: **ZERO**. No real development
DB/token data, execution environment settings or migration history were changed.
No commit or push was performed. This validation does not establish live account
correctness, named-human authentication, or authorization to perform a live test.
