# Runtime emergency stop

**HALT IS NOT BROKER CANCEL. THIS PHASE DOES NOT AUTHORIZE LIVE TRADING.**

The existing trusted same-JVM console now supports:

```text
halt
halt-status
```

HALT requires no OrderId or confirmation. It publishes the in-memory halt first,
revokes active arm eligibility and disarms. Repetition is safe. It works without
PostgreSQL, a Kite session or market data after the host has started. It makes no
broker request and never starts reconciliation or cancellation. A host that
cannot start because its database is unavailable does not execute orders either.

The launcher still requires a real controlling terminal and exactly
`--interactive-operator`. There is no HTTP halt/resume endpoint, browser control,
remote socket or scheduler. The production input reader can accept exact `halt`
while the foreground command is waiting on database/HTTP work. Other commands
during an in-progress operation are rejected, activate halt, and are not queued.

`HALT_ACTIVE`/`HALT_ALREADY_ACTIVE` acknowledge the safety action. `halt-status`
reports runtime state, startup halt and effective halt. Inspect for HALTED and
effectiveHalted=true. Status output is bounded and contains no session identity,
broker identifier, price, quantity, credentials or provider exception payload.
Safety does not wait for durable audit. Bounded logs record halt and resume events;
they are not authenticated named-human authorization records.

## Startup and deliberate release

Every context/process starts runtime HALTED and execution DISARMED. Effective
halt is startup `EMERGENCY_STOP` OR runtime halt. The startup property defaults
true and cannot be overridden by a runtime command. False startup configuration
alone never releases the runtime halt. Do not change real limits or opt-ins for
this phase.

For a future separately reviewed deployment only, releasing the runtime latch
requires two manual inputs:

```text
resume
CONFIRM resume
```

Resume is denied when operator control is disabled, startup halt is true, an
attempt remains CLAIMED, the confirmation is wrong/missing, or another halt has
invalidated the confirmation. Success prints `RESUME_SUCCESS DISARMED`. This
releases only the runtime halt: it creates no order, risk approval, arm or
authorization. A new explicit preflight, exact-order arm and second READY
preflight are still required by the existing one-order procedure. Never execute
that workflow against the real environment as part of Phase 10.5.

HALT during a confirmation invalidates it. EOF, input failure and console cleanup
halt/disarm; context destruction also reactivates the latch. A crash may skip
finally, but a new JVM still starts HALTED/DISARMED. RUNNING is never persisted.

## Expected behavior at each boundary

| Boundary where halt is observed | Durable state / required action |
|---|---|
| Before arm or execute | Arm denied/revoked; order unchanged; no broker request |
| Before admission, after lock wait, before CAS, or before-commit callback | Admission aborts/rolls back; RISK_APPROVED can remain; old permit is consumed, never automatically retried |
| Database commit already underway | May be SUBMITTING; do not infer no submission from halt timing; inspect and reconcile explicitly |
| SUBMITTING committed, before final dispatch validator passes | Emergency-stop denial leaves SUBMITTING; no HTTP when the fence observes halt; explicit inspection/reconciliation |
| Final dispatch fence already passed / HTTP in flight | At most the existing one attempt may finish; halt cannot recall it; do not retry |
| Acknowledgement received | Persist SUBMITTED safely if possible; halt does not rewind it |
| Response lost or acknowledgement persistence failed | Conservative SUBMITTING/ambiguous state; disarmed and consumed; explicit reconciliation only |

The memory latch is not atomic with PostgreSQL commit or network dispatch.
See [ADR-018](../adr/ADR-018-runtime-emergency-stop.md) for the precise fence
semantics and outstanding acceptance of the commit-race interpretation. No claim
is made that halt can roll back a commit or transport operation already underway.

## Abort and recovery

1. Type `halt`; verify effective halt is active. Do not wait for evidence reads
   before activating it.
2. Confirm DISARMED using the existing status/disarm operation when the foreground
   command returns. An in-flight CLAIMED attempt can remain CLAIMED until its owner
   finishes; it cannot be reused or replaced by resume.
3. **DO NOT RETRY** any request that may have reached the broker.
4. Inspect local lifecycle/authorization evidence and perform explicit broker
   reads. Never reset SUBMITTING, fabricate broker identity or clear conflicts.
5. Explicit `reconcile <OrderId>` remains available while halted. It reads orders
   and trades, prefers persisted broker ID, uses exact correlation as fallback,
   verifies terms, deduplicates fills and may update local lifecycle/audit.
6. Stop the application if evidence is inconsistent. Stopping it does not cancel
   an accepted broker order. Modify/cancel remain unavailable through this host.

Authentication and market data are separate read/observability capabilities and
are not stopped by halt. Risk rejects while halted; strategy evaluation may record
signals but does not deliberately propose an order while halt is observed. A halt
does not undo already-started local proposal/risk transactions. Execution remains
subject to the independent runtime/arm/admission/dispatch fences.

Halt affects this application context/JVM, not another deployment or external
account activity. Keep one reviewed operator deployment. A second process has
its own default-HALTED latch; PostgreSQL admission remains shared authority.

## Disposable validation

Use the installed JDK 21 and Docker. Do not load `.env` or start the real host.

```powershell
.\mvnw.cmd -pl apps/trading-core '-Dtest=RuntimeTradingHaltTest,HaltConsoleTest,OperatorControlArchitectureTest' test
.\mvnw.cmd -pl apps/trading-core -Pintegration -DskipUnitTests=true '-Dit.test=RuntimeTradingHaltIntegrationTest,OneOrderOperatorIntegrationTest,OperatorPreflightDryRunTest,OperatorRehearsalIntegrationTest,LocalKiteOrderExecutionIntegrationTest' verify
.\mvnw.cmd -Pintegration verify
```

The new tests exercise real production operator/policy/repository/adapter wiring,
controlled barriers before admission/commit/final dispatch, halt during a blocked
command and an in-flight request, explicit halted reconciliation, and restart.
The child-JVM crash probe also covers RUNNING before arm. Broker mutation counts
are asserted only against guarded loopback infrastructure, never real Kite.

### Phase 10.5 validation result

Validated on 2026-09-28 from `develop` baseline
`e0c1b5ba9277748392452bd315a5c5fa17d147ba`, with the Phase 10.5 changes uncommitted:

- Full Java unit/architecture suite: 1,015 passed; no failures or skips.
- Full PostgreSQL/Testcontainers integration profile: 364 passed; no failures or skips.
- Python: 14 passed. Ruff, mypy, dependency convergence, project verification,
  secret scan and `git diff --check` passed.
- Production jar retains `bootstrap.TradingCoreApplication` as its entrypoint;
  operator host and halt classes are present, test/integration helpers absent.
- Real Kite mutations, real development database test mutations, token changes,
  real-runtime resume/arm/execute, commits and pushes: zero.

The ignored machine-readable evidence is
`apps/trading-core/target/runtime-halt/evidence.json`. It includes baseline,
test totals, artifact/source hashes, safety scope and the outstanding section 12
commit-race acceptance limitation. Local Maven logs are under `tmp/phase105-*`.
These results do not authorize live trading or claim the literal atomic
halt-before-physical-COMMIT guarantee discussed above.
