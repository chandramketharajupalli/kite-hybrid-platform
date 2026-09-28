# Phase 10.3 operational rehearsal

**THIS PHASE DOES NOT AUTHORIZE LIVE TRADING.** All order mutation traffic is to
an in-process loopback fake broker. Real Kite order mutations and real development
database mutations for rehearsal data are both **ZERO**. Do not launch the real
operator console, load `.env`, restore a real token, or change deployment limits
to reproduce this rehearsal.

Starting baseline: clean `develop`, HEAD and `origin/develop` both
`75d0707f567a3eb6b6c95f68e973a2ae808915cc`. The implementation was inspected directly;
the Phase 10.2 design's call graph describes its earlier baseline, not the current
callers. No new execution route, transport, migration or configuration is needed.

## Phase 10.5 changes to the current rehearsal

The [runtime halt procedure](runtime-emergency-stop.md) adds a default-HALTED latch
and explicit synthetic resume to these fixtures. Production uses
`TrustedOperatorConsole.runInteractive`: an input-only reader handles halt while
the foreground thread performs manual commands. The test terminal supplies input
only when prompted; separate tests inject halt during blocked execution. No input
is queued for later execution. The child-JVM matrix also covers RUNNING before
arm, and recreated contexts assert HALTED/DISARMED. The historical Phase 10.3
validation record below describes its original baseline and counts.

Emergency-stop denial after committed SUBMITTING retains that state for explicit
recovery. Admission checks volatile safety before CAS and before commit. An
already-started commit/send cannot be atomically undone; ADR-018 records the
remaining boundary and its outstanding acceptance.

## Actual production call graph

```text
Explicit OperatorConsoleApplication.main --interactive-operator
  System.console + exact argument check (before Spring)
  SpringApplication.run(TradingCoreApplication.class), no execution overrides
  TrustedOperatorConsole.runInteractive, one command at a time
    input-only reader -> halt / halt-status (also while foreground command blocks)
    resume + CONFIRM resume -> release only runtime latch; stay DISARMED
    status -> OperatorExecutionService.status
    preflight OrderId -> OperatorExecutionService.preflight
      OrderRepository.find -> ExecutionSafetyPolicy.inspect (observation only)
    arm OrderId duration + exact separate confirmation
      OperatorExecutionService.arm -> disarm old grant -> preflight
      RuntimeExecutionArming.arm(exact persisted order/version/session/time)
      preflight again, deny and revoke unless READY
    execute OrderId + separate READY preflight and exact confirmation
      OperatorExecutionService.execute -> fresh preflight -> atomic permit claim
      OrderApplicationService.executeRiskApproved
        independent-transaction check, reload persisted order
        ExecutionSafetyPolicy.evaluate -> durable authorization audit
        PostgresOrderRepository.beginSubmission
          transaction + pg_advisory_xact_lock(606001)
          account exposure check + order version CAS
          COMMIT SUBMITTING
        ExecutionSafetyPolicy.validateDispatch
        OrderExecutionGateway.place -> KiteOrderAdapter.place
          KiteRestTransport.postRegularOrder -> orderRequest
            session monitor -> final validateDispatch callback
            one HTTP POST (LOOPBACK ONLY IN THIS REHEARSAL)
        broker acknowledgement parsing + atomic broker ID/SUBMITTED persistence
      finally: disarm, consume owning permit
  console finally: disarm
  launcher finally: close context, preserve caller interrupt status
```

The terminal is the only production caller of operator arm/execute. Operator arm
and execute also call preflight internally. Console disarm/EOF/error/reconcile
paths invoke operator disarm; the operator alone invokes runtime arm/claim/complete.
The operator alone calls `executeRiskApproved`; the application alone calls
gateway place. Application modify/cancel deny and have no gateway call. The
adapter's protocol mutation methods are not directly exposed by the terminal.
Normal `TradingCoreApplication.main` only starts Spring; it never starts this host.

Reconciliation is a separate, explicit path:

```text
TrustedOperatorConsole: disarm -> refuse in-flight CLAIMED -> RECONCILE_REQUEST
  OrderReconciliationService.reconcile(platform OrderId)
    OrderRepository.find
    BrokerOrdersProvider.orders + BrokerTradesProvider.trades (fake reads here)
    persisted broker ID first; exact persisted correlation only if ID absent
    identity/terms checks, deduplication, permitted lifecycle transition
    PostgresReconciliationStore.apply: row lock + audit + trades + order CAS
```

No reconciliation execution edge exists. ArchUnit verifies these boundaries,
including strategy/risk/reconciliation/market-data exclusions, no operator web or
scheduler dependency, and no normal-bootstrap dependency on the console.

## Isolation and launch rehearsal

`OperatorRehearsalIntegrationTest` uses the existing one-order fixture, a new
database per case in Testcontainers PostgreSQL 17.6, current Flyway migrations,
real production order/operator/risk/reconciliation beans and stores, synthetic
`KiteSession`, instrument registry, generation-fenced market store and fake reads.
Candidate creation uses real `place` and `RiskService.evaluate`. Controlled local
risk rules supply synthetic evidence; this is not a live risk/account assessment.

The fixture excludes system environment and system properties from Spring's
property sources. The normal application rehearsal loads only classpath
configuration and explicit container datasource coordinates; all execution,
operator and live-test defaults remain off. It asserts zero orders, risk decisions,
authorizations, reconciliations, strategy evaluations and requests, then closes.

`RehearsalIsolation` accepts only `http://127.0.0.1:<port>` broker targets. It checks
both base URL and actual connection, rejects credentials/query/fragment/external
hosts (including api.kite.trade and kite.zerodha.com), and disables redirects.
There are no DNS aliases, real authentication requests or WebSocket connections.

The launcher rehearsal runs an isolated copy of the **real launcher bytecode**.
A test-only Byte Buddy adapter substitutes only the `System.console()` result;
Mockito supplies a scripted Console and returns the already constructed disposable
context from the exact `SpringApplication.run(TradingCoreApplication.class)` call.
That static call is verified to receive no execution configuration or arguments.
Production bytecode/guard is not changed to accept pipes or a test flag. Missing
terminal/switch and extra switches fail before boot. The normal Spring startup is
also exercised separately against disposable PostgreSQL, without this interception.
This is a scripted terminal rehearsal, not a claim that a human operated a native
terminal or that a named human was authenticated.

## Primary sequence and evidence

The primary case starts with zero rows, records Flyway V10 and counts, and creates
exactly one candidate via `place -> VALIDATED -> evaluate -> RISK_APPROVED`.
There is one normally generated persisted correlation and no broker ID. It then
supplies these commands to the real parser, including both confirmations:

```text
status
preflight <synthetic-platform-OrderId>
arm <same-OrderId> 30s
CONFIRM arm <same-OrderId>
preflight <same-OrderId>
execute <same-OrderId>
CONFIRM execute <same-OrderId>
reconcile <same-OrderId>
```

The test stops on any unexpected pre-arm gate: exactly RUNTIME_ARMED and
SESSION_BOUND must be DISARMED, all other current enum gates NONE. Arm is bounded
and UNUSED; post-arm preflight requires all **24** current gates NONE. These
observations preserve RISK_APPROVED, zero authorization rows and zero requests.

Real method spies record observation, repeated policy evaluation, durable
admission/commit, CLAIMED at gateway entry, final dispatch validation and HTTP.
The fake broker independently reads committed SUBMITTING on another connection
before returning acknowledgement. The captured HTTP correlation must equal the
persisted correlation. One POST produces SUBMITTED and an attached synthetic
broker ID; arm is immediately DISARMED and permit CONSUMED. PUT/DELETE remain zero.
A repeated execute is denied without another request.

Explicit fake OPEN reconciliation advances to OPEN. A subsequent complete/fill
observation (normalized broker status FILLED) omits correlation, proving persisted
broker-ID selection. Duplicate identical fills produce one stored trade, quantity
one and exact BigDecimal value ten. Repeating reconciliation adds an observation
audit but does not change order version or duplicate the trade. Final state is
FILLED, disarmed, consumed; one authorization and three reconciliation audits.

Machine-readable evidence is generated at:

```text
apps/trading-core/target/operator-rehearsal/evidence.json
```

It contains the base commit, working-tree source hashes, bounded case results,
Flyway version, counts, all pre-arm/READY gates, permit/order states, dispatch
ordering and bounded terminal output. It contains no OrderId, correlation value,
broker ID, session identity, credentials, raw requests/responses, datasource URL,
account payload or production limits. A serialization guard rejects synthetic
secret/identifier leakage. Runtime evidence is ignored build output; do not commit
it. `PASSED_EXECUTED_CASES` describes only the tests executed in that invocation;
check `fullRehearsalPresent`, test counts and the matching Maven exit status.
An interrupted build can leave IN_PROGRESS; never treat that as a pass.

## Abort, denial and recovery matrix

| Case | Expected state/evidence | Broker mutation |
|---|---|---|
| Abort before arm / EOF | RISK_APPROVED, DISARMED, no authorization | zero |
| Abort after arm / disarm / EOF | RISK_APPROVED; UNUSED revoked to CONSUMED; restart NONE | zero |
| Input failure / controlled interruption | console finally disarms; launcher closes context; bounded reply | zero |
| Wrong/missing confirmation, whitespace, invalid UUID/command/argument/case/duration | deny, revoke arm, no exception payload | zero |
| Second candidate / second instance | real risk store rejects concurrent exposure; no second arm | zero |
| Unknown order | bounded ORDER_NOT_FOUND; arm/execute deny | zero |
| Expiry minus 1 ms / exact / plus 1 ms | READY just before, DISARMED at/after; tick refreshed to isolate arm gate | zero |
| Session replacement / emergency stop after READY | confirmed execute denies and disarms | zero |
| Market tick/health/connection/generation/subscription/exchange change | confirmed execute or final callback denies | zero |
| Risk expiry/version/policy/rejection change | confirmed execute or final callback denies | zero |
| Historical AMBIGUOUS/CONFLICT/MISSING/UNAVAILABLE reconciliation | account-wide denial persists | zero |
| Broker 400 / 401 | FAILED, consumed, disarmed; 401 invalidates synthetic session | one POST |
| Malformed acknowledgement / response loss | SUBMITTING without fabricated broker ID, consumed, disarmed | one POST |
| Persistence failure after response | SUBMITTING, consumed, disarmed; restart then explicit correlation recovery | one POST |
| Two instances, same order | PostgreSQL admission/CAS permits only one submission | one POST |

The current risk checkpoint prevents a second legitimate RISK_APPROVED order while
the first is approved/in flight. The two-order case deliberately proves this
restriction; it does not fabricate a second approval to obtain a competing arm.
Existing account-admission tests additionally inject historical exposure to check
the final dispatch defense. SQL fault injection for order version or immutable risk
evidence is confined to disposable tests and is never a lifecycle-repair procedure.
Reconciliation blocker fixtures use the real reconciliation store and are not
automatically cleared. Configuration is immutable; no runtime limit override exists.

For lost/malformed responses and failed acknowledgement persistence, restart has
no arm and performs no retry/reconciliation automatically. Explicit reconciliation
uses the exact tag captured by the fake broker, attaches broker ID atomically,
then advances through the existing state machine. There is still only one POST.

## Abrupt crash checkpoints

The integration-only child process accepts only a generated disposable database
URL and literal loopback broker URL. It runs real operator/application/PG/adapter
code. `Runtime.halt(73)` bypasses finally at selected boundaries; the in-flight
case is forcibly terminated only after the fake server has received the request.

| Checkpoint | Durable state on restart | POST count | Recovery |
|---|---|---|---|
| unused, claimed, authorization recorded | RISK_APPROVED | 0 | prior grant lost; fresh human authorization required |
| admission committed, final callback before HTTP | SUBMITTING | 0 | no inference of safe retry from local state; explicit reads/reconcile |
| HTTP received, response still pending | SUBMITTING | 1 | NO RETRY; explicit correlation reconciliation |
| response received, acknowledgement not persisted | SUBMITTING | 1 | NO RETRY; explicit correlation reconciliation |
| acknowledgement persisted, before operator finally | SUBMITTED | 1 | NO RETRY; explicit broker-ID reconciliation |
| execute returned after normal cleanup | SUBMITTED | 1 | explicit reconciliation |

Every fresh context starts DISARMED; durable state survives; no automatic request
is made. The old `acknowledged` probe was after cleanup. Phase 10.3 adds a distinct
`persisted` checkpoint **inside** acknowledgement persistence return, and an
`http-in-flight` termination. An OS kill does not promise finally execution.

## Deterministically reproduced shutdown defect

The first focused rehearsal passed 53 cases and failed controlled interruption:
after launcher return, `context.isActive()` was still true. The installed Spring
6.2.19 `AbstractApplicationContext.close()` calls an interruptible shutdown lock;
an already interrupted thread causes it to return without closing. This is an
operational shutdown/resource-lifecycle defect, not a new execution permission.

The smallest production fix clears the existing interrupt flag only around context
close and restores it afterward. The real-launcher regression checks DISARMED,
inactive context, preserved interrupt status, unchanged order, zero authorization
and zero HTTP. No operator service, permit, policy, risk, admission or transport
behavior changes. IntelliJ could not create a run configuration for the integration
source; the diagnosis used deterministic Maven runtime assertions and the installed
Spring bytecode instead. The temporary debugger logpoint was removed.

## Abort procedure and limitations

Remain disarmed after any attempt. Use `halt` and verify `halt-status`; do not
assume editing environment variables changes immutable startup configuration.
Disarm, retain evidence, inspect local order and
authorization state, perform explicit broker reads and reconcile. Stop the process
if evidence is inconsistent. **Stopping the JVM is not cancellation of an accepted
order.** Never reset SUBMITTING, erase audit/history, clear conflicts automatically
or retry a request that may have reached the broker.

The same-JVM controlling-terminal trust model has no durable authenticated human
identity/RBAC. The launcher test verifies bounded host output for propagated startup
failure; it does not certify every third-party logging backend or arbitrary
future Spring failure message. Native console/OS signal delivery, production-sized
latency, live account correctness and broker behavior remain outside this synthetic
rehearsal. Neither READY nor this evidence package authorizes live trading.

## Run and verify

Use installed JDK 21 and Docker. Run from the repository without loading `.env`:

```powershell
.\mvnw.cmd -pl apps/trading-core -Pintegration -DskipUnitTests=true '-Dit.test=OperatorRehearsalIntegrationTest,OneOrderOperatorIntegrationTest' verify
.\mvnw.cmd -Pintegration verify
```

The full run also reruns the existing dry-run, loopback, authentication, read,
market-data, risk, strategy, reconciliation, Spring context and Flyway tests.
Inspect ignored Maven reports and the generated JSON; retain only bounded evidence.

## Validation record: 2026-09-28

| Check | Result |
|---|---|
| Focused unit/architecture + one-order/rehearsal run | 23 unit tests + 131 integration cases passed |
| Full `mvnw -Pintegration verify` | **990 unit/architecture + 336 integration tests passed**, no failures/errors/skips |
| Operational rehearsal within full run | 82 cases passed, including nine actual child-process crash checkpoints |
| Existing preflight / one-order / loopback suites | 79 / 49 / 51 cases passed respectively |
| Python tests | 14 passed |
| Ruff and mypy | engine and scripts passed (mypy: 6 + 2 files) |
| Dependency convergence | both Maven modules passed |
| Project verification, secret scan, diff checks | passed; zero secret findings |
| Artifact packaging | normal entrypoint retained; rehearsal/crash helpers excluded |
| JSON evidence verification | 82 passing cases, 24 gates, nine crash checkpoints; key source hashes match; no identifiers/synthetic secrets |

The primary artifact records one candidate, one correlation (presence/count only),
one authorization, three explicit reconciliation audits and one deduplicated fill.
Final state is FILLED / DISARMED / CONSUMED with POST=1, PUT=0, DELETE=0. Normal
startup is default-off and creates no candidate. Controlled aborts write no
authorization and send no HTTP request. Recovery and crashes never resubmit.

Ignored local logs: `tmp/phase103-rehearsal.log` (original interruption failure),
`tmp/phase103-rehearsal-fixed.log`, `tmp/phase103-focused-final.log`,
`tmp/phase103-full-java.log` and `tmp/phase103-convergence.log`.
The final full run regenerated `target/operator-rehearsal/evidence.json` in the
trading-core module; its JSON and source hashes were independently checked.

No real broker mutation, real development DB/token change, live diagnostic,
production limit selection, migration/history repair, commit or push was performed.
These results authorize no future live order.
