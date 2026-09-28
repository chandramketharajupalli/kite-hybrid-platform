# ADR-018: process-local runtime emergency stop

Status: implemented for disposable validation; see the commit-boundary limitation below.

## Context and decision

Phase 10.4 identified that `TradingProperties.emergencyStop` was a startup-only
snapshot. Editing an environment variable cannot halt a running JVM. The trusted
console was also synchronous: while a database or HTTP operation blocked, it could
not read a halt command. This phase adds no HTTP or remote control surface.

Use one broker-independent `RuntimeTradingHalt` per application context. Its
effective value is `startupEmergencyStop OR runtimeHalt`. Every instance starts
HALTED, including when the startup setting is false. There is no stored RUNNING
state, automatic resume, new configuration override or schema migration. Context
destruction activates halt. The `application.yml` placeholder uses
**EMERGENCY_STOP** for the `trading.emergency-stop` property; its binding is unchanged.

The six former immutable-property consumers now use this effective value:

| Consumer | Behavior |
|---|---|
| Foundation risk rule | Rejects signals while halted |
| Risk service | Rejects the cash risk evaluation; reads halt again after broker observations |
| Strategy coordinator | Can record evaluation/signals; rereads halt before proposing an order |
| Execution safety policy | Existing emergency-stop readiness gate plus admission/dispatch fences |
| Operator service | Denies arm; exposes trusted halt/resume operations |
| Trading status endpoint | Reports effective halt and bounded runtime state; still not HTTP-exposed by default |

Only `RuntimeTradingHaltConfiguration` reads the startup property to construct
the effective latch. Authentication, market data and explicit reconciliation do
not depend on it. Reconciliation can still update local state from broker reads.

## Halt, resume and memory visibility

An atomic reference holds an immutable epoch and HALTED/RUNNING state. Every halt,
including an already-active halt, replaces the epoch. HALT publishes that state
first and then disarms. No database, broker/session, market-data or durable audit
call belongs to halt. Bounded operational logging is best-effort after safety.
An observability failure cannot release the latch.

Runtime arms carry the halt epoch captured at the start of the arm request.
Thus an arm blocked in preflight cannot become usable after a concurrent
halt/resume cycle. Claimed permission remains owned by the in-flight attempt;
halt does not recycle it. Completion consumes it normally.

An explicit `resume` plus separate exact `CONFIRM resume` is supported. A halt-only
design combined with mandatory HALTED-on-every-start would leave no legitimate
way to release the runtime latch. Resume requires enabled operator control,
startup halt false, no CLAIMED attempt, and the unchanged HALTED epoch observed
before confirmation. Concurrent halt invalidates that confirmation. Resume
disarms and releases only the runtime latch. It does not authenticate, create an
order, evaluate risk, authorize execution or arm. All other controls still apply.

The trust boundary remains the controlling terminal and OS/JVM access. No named
human identity, RBAC or durable human authorization is invented.

## Console concurrency

The production launcher retains the exact opt-in argument and real controlling
terminal requirement. It uses an input-only daemon reader. Manual commands still
execute on the calling thread, one at a time; there is no execution worker, command
queue, file input or background execution mode. Only exact `halt` and `halt-status`
are handled while another operation is in progress. Other input during that time
is rejected and halts the runtime; it is never saved for later execution.

The reader cannot resume, arm, execute or reconcile. Architecture tests enforce
this distinction. EOF/input failure activates halt and wakes a waiting console.
The owner also halts/disarms in finally. Native terminal reads need not respond
to thread interruption; the reader is a daemon with no execution capability.
The deployment supports one console owner per JVM, not repeated hot-swapping of
terminal readers. The interrupted Spring-close fix remains intact.

## Admission, dispatch and unavoidable external boundaries

The existing application execution path supplies a mandatory admission validator.
PostgreSQL admission invokes it before database work, after the account advisory
lock and before version CAS, and in Spring's `beforeCommit` callback. A halt
observed there aborts/rolls back the transaction. Durable account admission and
CAS remain the authority across processes; synchronization alone is insufficient.

**There is no atomic transaction spanning an in-memory latch and PostgreSQL's
physical commit.** A halt after the last before-commit check, while COMMIT is
already entering the driver/server, may coexist with a committed SUBMITTING row.
Holding a halt lock across database I/O would make the kill switch wait on the
very unavailable database it must not depend on. This implementation does not
claim the literal guarantee that every halt timestamp before physical commit
prevents that commit. The user has been asked to confirm this conservative
interpretation of Phase 10.5 section 12; that acceptance remains outstanding.

After durable admission the existing policy rechecks, including inside the Kite
session lock at the final transport validator. HALT observed before that validator
passes prevents HTTP. Emergency-stop denial after admission leaves SUBMITTING
for explicit inspection/reconciliation instead of rewriting it to a pre-submit
state. Other pre-dispatch failure handling remains unchanged.

The final validation is not atomic with an external network send. Once that
dispatch fence has passed, treat the request as potentially in flight, even if
the first byte has not yet been observed at the broker. HALT cannot recall it.
No lock is held that would make halt wait for the broker response. Valid late
acknowledgements may still persist SUBMITTED; response loss remains ambiguous.
Neither outcome grants retry permission.

## Scope and consequences

Halt is local to this JVM. It cannot halt another separately running application
or cancel an accepted order. One reviewed operator deployment remains required;
PostgreSQL account admission prevents duplicate submission but is not a distributed
kill switch. No cancellation, reconciliation or market-data shutdown is triggered.

No durable halt audit is added because safety must remain available during storage
failure. Logs use bounded action/reason values and no identifiers or credentials.
No high-cardinality metrics are introduced. The absence of durable authenticated
human identity remains an operational limitation.

Tests use synthetic sessions, disposable PostgreSQL and guarded loopback broker
transport. No real environment resume/arm/execute, order creation, token update,
database test data, commit or push is permitted by this phase.
