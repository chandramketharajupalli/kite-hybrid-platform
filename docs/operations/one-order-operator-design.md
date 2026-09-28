# Phase 10.2 design decision

THIS PHASE DOES NOT AUTHORIZE OR PERFORM REAL TRADING.

Baseline: clean develop at 4fb2b2b, also verified against the remote develop ref.
The following graph and decisions were recorded before implementation.

Phase 10.5 extends the terminal with runtime halt and confirmed resume; see
[ADR-018](../adr/ADR-018-runtime-emergency-stop.md). An input-only reader accepts
halt during a blocked foreground command; it has no resume/execution capability.
The historical baseline graph below is retained as the Phase 10.2 design record.

## Existing production call graph

| Operation | Current production callers |
|---|---|
| OperatorExecutionService.arm | None |
| OperatorExecutionService.disarm | None |
| OperatorExecutionService.preflight | OperatorExecutionService.execute only |
| OperatorExecutionService.execute | None |
| OrderApplicationService.executeRiskApproved | OperatorExecutionService.execute only |
| OrderExecutionGateway.place | OrderApplicationService.executeRiskApproved only |
| OrderExecutionGateway.modify/cancel | None (application modify/cancel deny) |

Execution follows: operator execute → real preflight → application authorization
→ PostgreSQL account advisory lock and order version CAS → committed SUBMITTING
→ dispatch validation → KiteOrderAdapter.place → validation inside the session
monitor in KiteRestTransport → one POST → acknowledgement persistence.
No automatic retry is present. Reconciliation uses broker ID first, exact stored
correlation second, and cannot invoke execution. Strategy places/evaluates risk
and stops at RISK_APPROVED. Preflight observes; it neither reserves nor authorizes.

## Host alternatives

| Choice | Security and runtime implications | Decision |
|---|---|---|
| Same-JVM interactive terminal | Shares ephemeral arm/session/market data. Trust is possession of the process's controlling terminal, protected by OS login and deployment access controls. No HTTP CSRF/Origin/Host or forwarded-header trust. No credentials in commands. Explicit confirmation and exact UUID parsing. No durable authenticated human principal exists. | Select an explicitly invoked terminal entrypoint owning its application context. Normal application startup never starts this console. |
| Separate client process targeting the running application | Cannot share JVM memory; requires IPC authentication, replay protection, authorization and identity management, or a second competing application context. A standalone client cannot arm the existing server. | Reject as a client/bridge. The selected entrypoint starts its own application JVM and must be the sole reviewed operator instance. |
| Authenticated loopback administrative transport | Requires dedicated authentication, CSRF, Origin/Host, proxy-header, replay, TLS and local-process threat review. Loopback alone does not authenticate a human or defend against local malware. Adds externally reachable execution. | Reject; no HTTP/socket execution endpoint. |

The terminal launcher must require a real System.console and an exact explicit
interactive switch before booting anything. Redirected stdin, batch files,
unknown options and commands are rejected. It starts the existing application
context, then waits for manual commands; it does not arm, create orders or execute
at startup. Existing authenticated/read-only setup boundaries remain responsible
for session and market prerequisites in that same JVM. The launcher is never run
against the development environment during this task.

## One-order authorization and durable admission

Extend runtime arming with exact platform OrderId and approved order version,
besides existing session identity/from/expiry. First-live checks must deny legacy
unbound runtime arms, including direct lower-level execution attempts. Normal
configuration defaults and all existing readiness evidence remain unchanged.

Use a memory-only single-use permit: UNUSED → CLAIMED → CONSUMED. Claim is atomic
within the shared runtime arm; a claimed permit cannot be replaced by a new arm.
Disarm revokes dispatch eligibility even during an attempt. Only its owning claim
can finish it. Every execute invocation, including a denial, revokes unused arm;
the owning attempt consumes its permit in finally, on success, exception or Error.
No automatic retry or return to UNUSED. A later arm is a new human authorization,
and still requires a legitimate RISK_APPROVED order and complete fresh evidence.

No new durable permit table or migration is needed: the existing PostgreSQL
advisory account admission plus version CAS commits RISK_APPROVED → SUBMITTING
before HTTP. Two processes with independent runtime arms for the same OrderId
cannot both win that transition. All possibly-submitted outcomes remain outside
RISK_APPROVED; restart cannot recover an arm. Crash before admission cannot have
sent HTTP; crash after admission leaves a durable blocker and requires explicit
reconciliation. Do not reset state to manufacture retry eligibility.

## Pre-arm and execution contract

The host requires explicit preflight for the selected ID, with only arm/session
binding denials, then separate arm confirmation. Service arm repeats that check
before binding ID/version, and validates the newly armed state again. A separate
explicit READY preflight and execute confirmation precede execute. Execute accepts
only OrderId, repeats preflight, claims once, and follows the existing application
authorization/admission/transport rechecks. Order terms/correlation are reloaded
from persistence; no symbol, broker ID, quantity, price or generated ID is accepted.

The existing SESSION_BOUND gate will also express order/version binding; the
current 24-gate model remains authoritative. Configuration is immutable after
startup. Volatile risk/market/reconciliation evidence is never cached by the arm.

## Audit and acceptance

Use bounded event/reason enums, never raw order/broker/session payloads or secrets.
Do not claim an OS username or arbitrary caller string is authenticated human
identity. The console is trusted local process access, not a multi-user RBAC or
durable human-identity solution. Existing execution authorization records retain
their order linkage; no high-cardinality metric labels are added.

Before considering the host implemented, exercise the complete workflow with
disposable PostgreSQL, synthetic sessions/instruments/market data, fake reads and
loopback mutation transport. Prove wrong-order denials, concurrency, response loss,
all failure exits, last-moment changes, explicit reconciliation, and restart.
Normal defaults must still deny. Production Kite mutations for this task: ZERO.
