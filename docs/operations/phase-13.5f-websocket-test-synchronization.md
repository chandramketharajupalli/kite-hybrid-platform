# Phase 13.5F follow-up: loopback test synchronization

2026-10-10. User-authorized narrow test correction; not a contract or production
change. Baseline develop at e7868c479779636e6dc9e749a2e9440a2369e492. Initial
working tree contained the six untracked Phase 13.5F documents from the preceding
task. They were preserved byte-for-byte; their historical PARTIAL outcome and
recorded failures are not rewritten. No other initial changes existed.

## Failure evidence and cause

Previous Phase F standalone full runs both completed with 1507 passed, 0 assertion
failures, 1 error and 0 skipped. Each error was NoSuchElementException at original
JdkKiteWebSocketTransportTest line 84, in
actualTransportGatewayDecoderStoreAndHealthRecoverTogetherAfterLoopbackReconnect.
Original logs remain tmp/phase135f-unit.log and tmp/phase135f-unit-rerun.log.
The prior integration-build unit phase passed, demonstrating timing dependence.
This follow-up's unmodified focused class run also passed all 16 cases; no fresh
pre-edit failure is falsely claimed (tmp/ws-sync-before.log).

Source evidence: KiteMarketDataAdapter transitions CONNECTED after subscription
command completion, while drain() publishes ticks through a separate worker.
InMemoryLatestMarketDataStore.latest returns Optional.empty before publication
(or when the publication permit is invalid). CONNECTED therefore does not guarantee
a present tick. The original Awaitility untilAsserted callback dereferenced the
optional via orElseThrow, raising a non-assertion exception instead of a retryable
assertion when the worker had not published yet.

## Minimal correction

Only the two price assertions inside the initial/reconnected untilAsserted blocks
now use Optional.hasValueSatisfying with the unchanged exact BigDecimal price
assertion. Absence is an AssertionError retried by existing Awaitility polling;
wrong price still fails until the same deadline. Both five-second deadlines remain.
No sleeps, increased timeouts, ignored exceptions, catches, assumptions, skips or
production scheduling changes. Presence remains required; all health, reconnect,
subscription, volume, metric and shutdown assertions remain unchanged. The final
post-wait snapshot assertion is untouched. One explanatory comment was added.

Changed source:
apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/JdkKiteWebSocketTransportTest.java.
Production source/resources, dependencies, configuration and test peers unchanged.

## Validation commands and results

Unmodified baseline: `./mvnw.cmd -Dtest=JdkKiteWebSocketTransportTest test`:
16 passed, 0 failures/errors/skips.
Corrected class: the same command executed five times in separate Maven runs:
16 passed each, 0 failures/errors/skips in every run (80 successful executions,
not 80 unique tests). Logs tmp/ws-sync-focused-1.log through -5.log.
Full `./mvnw.cmd test` result is recorded in the final audit below.

No production/integration semantics changed; disposable integration not rerun for
this test-only correction. Python unchanged; pytest/Ruff/mypy not applicable.
Project verifier and diff check passed. No new test cases were added or counts
inflated. Repeated success supports the correction; it is not proof against all
possible asynchronous failures.

## Preservation and safety

Six original Phase F docs hashed before edits and rechecked unchanged. G1 manifest
remains 56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656;
existing verifier passes 18 frozen sources; all 63 protected research hashes match
the prior initial inventory and normalized HEAD blobs. Phase 12 acquisition/evaluation
BLOCK, H1/H2 FROZEN, July TEST unopened/SEALED. No .env, migration, safety default,
risk/funding/CNC/reserve or INR 10,000 notional change.

No real Kite request, token load/restoration, development DB access, operational
HALT resume/arm/permit/order execution, commit or push. The focused test uses a
local RFC6455 peer and synthetic session only. Operational NO_GO and collateral
MIS NOT_READY remain unchanged. This follow-up does not authorize the revised
assurance contract or any real operation.

## Final audit

Full unit/architecture completed 2026-10-10T23:49:57+05:30: **1508 passed,
0 failures, 0 errors, 0 skipped**, BUILD SUCCESS (1m07s), tmp/ws-sync-unit.log.
All five corrected focused runs and the full suite passed without reruns or further
edits. The two historical pre-correction full-suite errors remain recorded above
and in the unchanged Phase F validation document. Project/secrets/diff checks PASS.

Actual work in this follow-up: one modified test file (5 insertions, 2 deletions)
and this new document. The six initially untracked Phase F documents remain present
and unchanged; their exact inventory is in phase-13.5f-validation.md. No staged
files or production/resource diffs. Final preservation hashes and XML totals checked.
No commit/push. Test synchronization correction validated; this does not certify
live deployment, funding readiness or the pending assurance contract.
