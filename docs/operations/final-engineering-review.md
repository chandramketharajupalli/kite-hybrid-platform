# Final engineering review

## Release-candidate follow-up findings (recorded before changes)

- **Medium, RC1:** PostgreSQL strategy claim compares only deterministic signal ID; changed instrument/action/quantity/reference price/reason with the same identity is silently accepted as EXISTING. The prior mock replay test encourages this weaker contract. Reproduce against PostgreSQL and reject conflicting immutable terms; unchanged replay remains valid.
- **Medium, RC2 test gap:** V10 duplicate-history failure and explicit V9 -> V10 upgrade are not independently covered. Add isolated PostgreSQL databases proving rollback, null semantics and preservation of duplicate historical rows.
- **Low, RC3 test isolation:** PostgresStrategyEvaluationStoreTest changes the JVM default timezone without restoring it. Restore only the original test-process timezone at suite teardown.
- **Low, RC4 replay metadata:** Completing an interrupted existing evaluation returns the new input timestamp while its persisted signal/evaluation retain the original timestamp. Reproduce the mismatch and return the persisted evaluation timestamp; no database rewrite is needed.

Baseline: develop at 143ec86, origin/develop divergence 0/0. The 26 existing changed/new files were inspected as candidate audit fixes: 15 production files, 10 test files and one report; no migration or unrelated user edits. All are preserved. Java 21.0.12, Maven 3.9.11, uv 0.12.10. This report supersedes readiness findings in the earlier audit only where new evidence is recorded.

## Defects recorded before remediation

1. **High: stale market-data publication.** Adapter acceptance is checked before an unlocked store.update. Lifecycle/mode revocation during a delayed update cannot stop the write. Replace this with revocable publication permits retained by the store; invalidated entries are never visible and cannot displace current-generation entries. Revocation must not wait for a delayed consumer.
2. **High: stale execution authorization.** No post-CAS check or transport-entry check exists. Recheck mutable evidence before dispatch and after transport acquires the session; denied post-CAS attempts remain durably explainable and must not retry.
3. **High: different-order submission race.** A read-only blocker query is not atomic with SUBMITTING CAS. Add account-scoped PostgreSQL admission using the same advisory-lock namespace as risk, independently committed before HTTP. Do not hold a database transaction across HTTP.
4. **High: session replacement preserves arm.** Bind ephemeral arming to a non-secret authenticated session identity; no credential-derived identity and no implicit arming.
5. **High: transaction propagation gap.** Previous TransactionTemplate fixes default to REQUIRED. An external caller's transaction can defer committing SUBMITTING until after HTTP. Execution must reject ambient transactions; audit/admission need explicit durable boundaries.
6. **Medium: untrusted authentication log text.** Redaction is not a guarantee against arbitrary sensitive upstream text. Replace it with bounded status/category diagnostics and adversarial log tests.
7. **Medium: valid long strategy event keys fail.** The hash fallback takes substring(0,96) of a 64-character SHA-256 hex string. Use the full bounded hash and exercise a 128-character event key. Replay without action must return the persisted evaluation, not a newly computed draft.
8. **High: historical fill conflicts are ignored.** Reconciliation trades use ON CONFLICT DO NOTHING; conflicting later observations can advance lifecycle while durable fill history retains different terms. Reject conflicts atomically, rolling back audit/fills/lifecycle. Broker order identity is not unique locally; add a nullable unique index for the current single-account model, failing migration rather than repairing ambiguous historical duplicates.
9. **High: uncertain HTTP errors and ambiguous acknowledgement syntax.** The order adapter classifies every 4xx as a definite rejection, including timeout/conflict statuses, and accepts duplicate JSON fields/trailing JSON. Treat non-validation broker errors conservatively as ambiguous and strictly parse acknowledgements.
10. **High: time sampled before potentially blocking evidence reads.** Even a final check can approve stale data if it compares against a clock captured before PostgreSQL/session waits. Sample time after blocking reads; use a coherent nonblocking authenticated-session identity view for arming and dispatch freshness.

No production broker operation is authorized. All HTTP execution tests must use local fake infrastructure. No commit or push.

## Disposition and evidence

All ten findings above have narrow fixes and regression coverage. Existing uncommitted audit fixes were preserved and reviewed, including disabled modify/cancel, mandatory policy/audit wiring, explicit store transactions, future timestamp denial, replay consistency, reconciliation lifecycle checks and diagnostic guards. No dependency versions, production enable flags or universe memberships were changed.

| Finding | Enforcement and regression evidence |
|---|---|
| 1 | PublicationPermit is revoked on stop/unsubscribe/mode/connection retirement; InMemoryLatestMarketDataStore retains permits and filters revoked values. Four deterministic blocked-consumer races failed before remediation and pass afterward. Late invalid writes cannot replace a valid current value. |
| 2 | OrderApplicationService revalidates after committed admission; KiteRestTransport invokes validation again after acquiring its session monitor, before request creation/dispatch. Fourteen post-authorization evidence changes and a separate gateway-entry stop test produce zero HTTP. |
| 3 | PostgresOrderRepository.beginSubmission obtains account advisory lock 606001, checks exposure and version-CASes in one transaction. Two independent services/repositories executing different approved orders produce one HTTP request. Same-order contenders also produce one. |
| 4 | RuntimeExecutionArming binds to a random non-secret verified-session UUID. Replacement, clear, rejection or expiry removes usable authorization. Restart reconstructs DISARMED; exact expiry denies. |
| 5 | Execution rejects ambient Spring transactions before authorization/submission. Admission commits before HTTP; authorization audits use REQUIRES_NEW. Rollback tests prove durable denied audit and no HTTP inside an ambient transaction. |
| 6 | Authentication failure diagnostics emit bounded fixed categories and redact arbitrary upstream text, including adversarial private text. Credentials/raw payloads are not used as log parameters. |
| 7 | Long strategy event keys use the full 64-character SHA-256 hash; 128-character replay fixture passes. Persisted evaluations and signal terms remain authoritative on replay. Arbitrary strategy IDs were removed from metric labels. |
| 8 | Conflicting historical fills roll back the whole reconciliation write. V10 enforces unique non-null broker order identity; duplicate insertion also rolls back its idempotency claim. Historical null remains supported. |
| 9 | Strict JSON parsing rejects duplicate fields/trailing JSON. Timeout/conflict/rate-limit/5xx responses remain ambiguous rather than safely retryable. Malformed acknowledgement remains SUBMITTING. |
| 10 | Execution age checks resample time after blocking reads. A clock advanced during evidence retrieval reproduces the original failure and now denies. Session identity remains readable during a blocked REST operation and denies exact expiry/rejection. |

Detailed concurrency/dispatch semantics and migration scope: [ADR-017](../adr/ADR-017-execution-admission-and-publication-fencing.md). Operational boundaries: [execution safety runbook](../runbooks/execution-safety-phase-9.md). The earlier [audit](end-to-end-audit-143ec86.md) is historical evidence; its residual execution/publication/session/logging findings are superseded here, not silently erased.

## Architecture and lifecycle review

| Boundary | Actual authority / reviewed behavior |
|---|---|
| Foundation | Maven parent and trading-core module, JDK 21 enforcement, Spring Boot 3.5.16, Flyway 11.7.2, pgJDBC 42.7.11, PostgreSQL 17.6 integration image, ArchUnit 1.4.1. Python engine is independently locked with uv. Redis is not order/auth/risk persistence authority. |
| Instrument | Shared typed identity and broker-independent registry/application ports. Kite CSV adapter constructs canonical ZERODHA broker mappings; immutable indexed snapshots publish atomically. Exchange/symbol and broker-token lookups are indexed; conflicts reject replacement. No universe resolution invents an ID. |
| Authentication | Official interactive flow; checksum exchange, encrypted token persistence, durable one-time login attempts, profile validation before usable authentication, explicit expiry/reset. No password/TOTP automation. Startup restores authentication/reference data only. |
| Market data | Kite packet/transport code stays in infrastructure. Bounded queue, bounded reconnect attempts, desired/active modes, generation/subscription checks and publication permits cover delayed callbacks. CONNECTED alone does not satisfy FRESH. Future received/exchange times deny freshness. |
| Broker reads | Orders/trades/positions/holdings/margins normalize through canonical instrument identity, bounded responses, typed validation and bounded errors. Read models do not dispatch. |
| Orders | Platform ID is primary; broker ID/correlation are persisted metadata. Idempotency and versioned lifecycle are PostgreSQL-authoritative. Only executeRiskApproved calls the execution gateway; modify/cancel fail DISABLED. No policy-free constructor remains. |
| Risk | Validated order -> atomic risk decision/state -> hard stop. Single-account advisory lock and row lock serialize approvals. Prior decisions replay immutably; a second outstanding approval/exposure is conservatively rejected. No gateway dependency. |
| Strategy | Persisted strategy/version/event identity and deterministic order idempotency prevent duplicate proposals. Replay uses persisted terms. Coordinator may propose/evaluate risk but cannot execute. Reference strategy remains reference logic. |
| Reconciliation | Exact persisted broker ID, then exact persisted correlation, otherwise ambiguous. Immutable submitted terms must match. Duplicate/conflicting fills and overfills are rejected; writes are atomic. No heuristic symbol/time/quantity matching and no dispatch. |
| Universe | CSV membership/import/lookup depends on registry only. Architecture tests prohibit order/risk/strategy/market-data/broker-infrastructure dependencies. No persistence/subscription/activation/intent/risk/arming/allowlist side effect. |
| Persistence | Explicit TransactionTemplate avoids final-class proxy assumptions. Real Spring development and production contexts with disposable PostgreSQL instantiate required control-plane beans, policy/audit and disabled gateway. Authentication/market-data transports are disabled in these contexts. |

ArchUnit checks gateway place/modify/cancel call sites by assignability, requires policy construction and prohibits automatic arm/execute callers. The execution policy now depends on shared ExecutionSession, not Kite. No scheduler/event listener bridges strategy or risk to execution. No production execute HTTP endpoint was added.

## Exact execution contract

Safe startup remains **DISABLED + DISARMED**, emergency stop on, empty execution allowlist, zero quantity/notional/age defaults. Configuration can permit explicit arming but does not arm. Successful authentication, fresh ticks, universe membership, strategy signals and risk approval cannot enable execution.

```
Signal -> TradeIntent -> VALIDATED -> Risk -> RISK_APPROVED -> HARD STOP
explicit session-bound arm -> explicit execute -> policy + durable authorization audit
-> PostgreSQL account admission / version CAS -> committed SUBMITTING
-> safety recheck -> gateway -> transport-session safety recheck -> local HTTP
```

The actual integration path uses real OrderApplicationService, ExecutionSafetyPolicy, PostgreSQL orders/risk/authorization stores, KiteOrderAdapter and KiteRestTransport. Only sessions, reference data, ticks and broker HTTP responses are synthetic. Loopback URI guards reject production/external hosts.

| Initial denial case | Bounded reason |
|---|---|
| Capability disabled | EXECUTION_DISABLED |
| Runtime disarmed / arm expired | DISARMED |
| Emergency stop | EMERGENCY_STOP |
| Authentication unavailable | AUTHENTICATION_UNAVAILABLE |
| Missing / rejected risk decision | RISK_APPROVAL_MISSING |
| Expired or future risk decision | RISK_APPROVAL_EXPIRED |
| Risk/order version mismatch | ORDER_VERSION_CHANGED |
| Risk policy mismatch | RISK_POLICY_MISMATCH |
| Unknown / unallowlisted instrument | INSTRUMENT_NOT_ALLOWED |
| Quantity exceeds independent cap | QUANTITY_CAP_EXCEEDED |
| Conservative current notional exceeds cap | NOTIONAL_CAP_EXCEEDED |
| Missing tick / unhealthy or incomplete subscriptions | MARKET_DATA_UNAVAILABLE |
| Stale/future received or exchange tick timestamp | MARKET_DATA_STALE |
| Missing persisted correlation | CORRELATION_MISSING |
| Dangerous unresolved local exposure | RECONCILIATION_REQUIRED |
| Wrong lifecycle | INVALID_ORDER_STATE |

The 18-case initial application denial matrix asserts persisted DENIED reason, unchanged lifecycle, zero gateway calls and zero fake HTTP requests. Additional unit/integration tests cover policy mismatch and future timestamps. The safe allowed case observes committed SUBMITTING from the HTTP handler, one request, unchanged correlation tag, broker ID attached and SUBMITTED. Repeated execution adds no request. Restart reconstructs a disarmed service and leaves the persisted approval unexecuted; deterministic arm-boundary tests use no sleeps.

Initial authorization checks ambiguous SUBMITTING exposure. Admission/final dispatch additionally block other SUBMITTING, SUBMITTED, ACKNOWLEDGED, OPEN, PARTIALLY_FILLED, FILLED and CANCEL_PENDING orders. This is a precise single-account state rule, not an 'any audit exists' rule. FILLED is deliberately conservative until a reservation/release protocol exists. Historical audit rows do not block.

ALLOWED audits mean authorization passed, never broker submission success. An audit may survive a later failed CAS; the losing attempt records a bounded denial and produces zero HTTP. Post-CAS safety denial records the submitting version and transitions to FAILED/PRE_DISPATCH_DENIED where its CAS still succeeds. If storage/state changes prevent that transition, unresolved state remains; no automatic retry is created. Configuration fingerprints distinguish changed caps, allowlists and age/policy settings without persisting credentials.

Kill switch before admission denies. Revocation observed after SUBMITTING but before final dispatch denies without HTTP. Revocation after dispatch is an in-flight operation: no recall guarantee and no blind cancellation. Checks are snapshots with a defined dispatch boundary; they cannot make PostgreSQL, in-memory state and the remote exchange one atomic transaction.

## Risk arithmetic and crash recovery

Cash BUY/CNC risk values MARKET at lastPrice * priceBuffer * quantity; LIMIT uses max(limitPrice,lastPrice) * priceBuffer * quantity. BigDecimal comparisons accept equality at the cap. Execution independently recomputes max(submitted limit,lastPrice) * quantity from fresh data. This is an authorization estimate, not a guarantee of a MARKET fill price.

Usable cash is conservatively min(cash, openingBalance, liveBalance, net) minus max(debits,0), max(payout,0), max(holdingSales,0). Negative liabilities never create credit. Order notional plus reserve must fit; missing/invalid account evidence denies. Mathematical boundary tests cover both order types and margin deductions. This assumes coherent trusted broker snapshots; it does not infer leverage/collateral or coordinate manual broker activity.

SUBMITTING committed before process death/HTTP remains unresolved. Accepted request with lost/malformed response remains possibly sent. Acknowledgement received before local persistence failure also remains unresolved. None automatically resubmit. The response-loss integration test recreates service/repository, obtains a synthetic local GET orders/trades observation, matches the exact persisted tag with consistency checks, attaches identity and confirms only one place request ever occurred.

Validation/policy/CAS failures are definitely unsent. Transport failures and uncertain broker/acknowledgement failures conservatively remain possibly sent. A parsed success with persisted identity is acknowledged. Connection-refused errors are intentionally not optimized into automatic retry eligibility.

## Universe, configuration, security and operations

Canonical universe is unchanged: **1111 rows, 1111 normalized unique, zero duplicates, 1111 enabled, zero disabled**. Only repository-root universe.csv exists. Synthetic duplicate/conflict/normalization/unresolved tests remain. Registry resolution expectation is 1111/0; this final pass did not contact a live registry or refresh Kite, so it does not claim a new live resolution measurement. Deterministic repository-marker path selection and UNIVERSE_DIAGNOSTIC_PATH override are retained.

Production settings were not enabled. application.yml retains loopback server binding and false defaults for Kite REST, market data, trading reads, execution, risk and development diagnostics. Production DB credentials remain explicit. Secret/API/encryption values were not copied into this report. AES key format/length validation, token expiry, callback replay/restart and environment-loading regressions remain covered. Actuator exposure is limited to health/info/prometheus, with sensitive value/detail endpoints suppressed. Metrics use bounded operations/results/reasons; no order/instrument/symbol/token/correlation identifiers label execution metrics.

Development diagnostics remain development-only and opt-in where configured, with loopback/Host and forwarded-header protections; market-data diagnostic now excludes production profile and checks Origin. Universe responses contain only reference data. Remaining consistency limitations are listed below. Port 8080 conflicts, existing JDK requirement, dotenv precedence, Docker availability and PostgreSQL UTC testing are operationally documented/tested rather than hidden by auto-reconfiguration.

V1-V9 were reviewed in order; V10 adds only a unique nullable broker-order index. Empty-database migration, V8 -> V10 upgrade and repeated validation/no-op migration are integration-tested. Duplicate historical non-null broker IDs intentionally fail V10 and require operator investigation. No migration alters encrypted credential contents or invents identity.

Production Kite order endpoint contacts initiated by this review: **0**. No new read-only broker calls, interactive authentication or local live-app diagnostic calls were made in this final pass. Broker-shaped URLs in unit tests are intercepted by MockRestServiceServer; actual network execution tests use guarded loopback servers. This is test/configuration evidence, not a machine-wide packet capture of unrelated processes.

## Remaining limitations

| Severity | Limitation / impact |
|---|---|
| Medium | Current ledger, locks, broker uniqueness and arming are single-account. Multi-account support needs account-scoped tables/keys/locks/session identities; external/manual trading is not coordinated. |
| Medium | Conservative FILLED/open exposure blocking sacrifices throughput and can require future reservation/release design. No final broker-account refresh is performed during execution; authorization relies on bounded risk age plus exclusive local admission. |
| Medium | Cash/holdings/margins reads are separate broker snapshots. MARKET notional cannot guarantee execution price; no unconditional live-readiness claim follows from these tests. |
| Medium | Risk holds the account lock while bounded broker reads occur. Per-request timeouts/body limits are not a global wall-clock deadline; slow upstream behavior can reduce availability, while denying further activity. |
| Medium | Universe diagnostics lack the stricter account-read diagnostic's Origin/fetch-site, HEAD rejection and no-store consistency. Lookup fragment length is bounded but result count is not independently capped. Public reference data only; no trading side effects. |
| Medium | Repository already tracks historical Gradle output/caches and Python bytecode. They are not authoritative build inputs. Broad removal was deliberately not mixed into safety remediation. |
| Low | Universe validation can span registry refresh versions; per-lookup resolution never invents identities but the whole report is not pinned to one snapshot. |
| Low | A conflicting historical fill is currently surfaced by the store as a failed application/CAS result rather than a separate fill-conflict reason. Atomic rejection is enforced, diagnostics could be more specific. |
| Low | Arming accepts a trusted caller's Instant. No production operator endpoint/caller exists; a future boundary should own its Clock and operator/session authentication. |
| Low | Dependency convergence is not dependency vulnerability attestation. Secret scanning is heuristic. Mockito dynamic-agent warnings concern future JDK behavior; supported JDK 21 verification passes. |

No known unresolved Critical or High safety defect remains in the reviewed supported path after these fixes. This is a scoped engineering conclusion, not proof of bug-free software or approval to enable live trading. Default-off behavior and the missing production operator boundary remain intentional.

## Comprehensive-run verification (historical; RC results below supersede)

Completed 2026-09-27, using the supported local toolchains. No tests were skipped in the final Java run.

| Verification | Result |
|---|---|
| `mvnw.cmd -Pintegration verify` | PASS: 955 unit tests, 59 PostgreSQL integration tests, zero failures/errors/skips |
| LocalKiteOrderExecutionIntegrationTest | 30 test invocations; includes the 18-case denial matrix, allowed request, restart/expiry, post-authorization changes, same/different-order concurrency, rollback and response-loss recovery |
| Auth restart / token / login-attempt PostgreSQL suites | 3 / 4 / 8 tests |
| Migration / orders / reconciliation / risk / strategy PostgreSQL suites | 1 / 3 / 5 / 4 / 1 tests |
| Dependency convergence | PASS: Maven Enforcer dependencyConvergence |
| Python strategy engine | PASS: 14 tests; Ruff clean; mypy clean (4 source files) |
| `uv run --project apps/strategy-engine --locked python scripts/verify-project.py` | PASS: Maven/JDK artifacts, profiles, safe defaults and contract JSON |
| `uv run --project apps/strategy-engine --locked python scripts/check-secrets.py` | PASS: 581 text files, zero potential secret locations |
| `git diff --check` | PASS |
| Git revision | develop, 143ec86; local origin/develop comparison 0 ahead / 0 behind; no fetch needed for this final pass |

The unit run includes universe/importer/diagnostic/instrument, authentication, REST/trading reads, market data/health/packet/lifecycle, orders/execution, risk, strategy, reconciliation and architecture regressions. Generated Python bytecode modified by verification was restored only for the five paths confirmed clean at the start; all pre-existing audit edits remain.

Reproduction and final logs are retained under ignored `apps/trading-core/target/final-*.log`, including the full `final-comprehensive-verification.log`. Surefire/Failsafe XML contains per-test results. These generated logs are not proposed repository artifacts.

Official reference pages consulted for authentication/order semantics: [Kite user/authentication](https://kite.trade/docs/connect/v3/user/) and [Kite orders](https://kite.trade/docs/connect/v3/orders/). Documentation retrieval is not a broker order call.

## Comprehensive-review working-tree status (historical)

Existing and new audit changes below are intentionally uncommitted. This is the complete file inventory, including new files.

```text
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresMigrationTest.java
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresOrderRepositoryTest.java
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresReconciliationStoreTest.java
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/LocalKiteOrderExecutionIntegrationTest.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/application/auth/KiteAuthenticationSession.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteAuthenticationAdapter.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteMarketDataAdapter.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteMarketDataDiagnosticController.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteOrderAdapter.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteRestTransport.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteSession.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/instrument/application/UniverseValidationService.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/marketdata/application/LatestMarketDataStore.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/marketdata/infrastructure/InMemoryLatestMarketDataStore.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/ExecutionSafetyPolicy.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderApplicationService.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderExecutionGateway.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderExecutionProperties.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderRepository.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/RuntimeExecutionArming.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/domain/OrderState.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/infrastructure/OrderConfiguration.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/infrastructure/PostgresExecutionAuthorizationAuditStore.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/infrastructure/PostgresOrderRepository.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/reconciliation/application/OrderReconciliationService.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/reconciliation/infrastructure/PostgresReconciliationStore.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/reconciliation/infrastructure/ReconciliationConfiguration.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/risk/infrastructure/RiskConfiguration.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/strategy/application/StrategyOrderCoordinator.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/strategy/infrastructure/PostgresStrategyEvaluationStore.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/strategy/infrastructure/StrategyConfiguration.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/MarketDataConfigurationTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/OrderArchitectureTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/UniverseValidationArchitectureTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteAuthenticationAdapterTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteMarketDataBackpressureTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteOrderAdapterTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteSessionMarketDataTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/instrument/application/UniverseValidationServiceTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/order/ExecutionSafetyPolicyTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/order/OrderApplicationServiceTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/reconciliation/OrderReconciliationServiceTest.java
 M docs/runbooks/execution-safety-phase-9.md
?? apps/trading-core/src/main/java/com/kitehybrid/platform/marketdata/application/PublicationPermit.java
?? apps/trading-core/src/main/java/com/kitehybrid/platform/shared/application/ExecutionSession.java
?? apps/trading-core/src/main/resources/db/migration/V10__unique_broker_order_identity.sql
?? apps/trading-core/src/test/java/com/kitehybrid/platform/order/ExecutionFreshnessAuditTest.java
?? apps/trading-core/src/test/java/com/kitehybrid/platform/risk/domain/CashRiskArithmeticTest.java
?? apps/trading-core/src/test/java/com/kitehybrid/platform/strategy/StrategyReplayAuditTest.java
?? docs/adr/ADR-017-execution-admission-and-publication-fencing.md
?? docs/operations/end-to-end-audit-143ec86.md
?? docs/operations/final-engineering-review.md
```

## Release-candidate diff review

Scope: the complete uncommitted diff from develop/143ec86, including all untracked source/test/documentation files. Initial inventory was 52 files; RC adds focused coverage in two previously unchanged test files. No reset/restore/clean, dependency upgrade, production enabling, commit or push was performed. Existing candidate fixes were reviewed, not accepted merely on prior test results.

RC1 reproduced five PostgreSQL failures (instrument, side, quantity, reference price and reason), then received a narrow immutable-term comparison. Numeric prices compare by value, not BigDecimal scale. RC2 adds explicit V9 -> V10 and duplicate-history migration tests. RC3 restores the original JVM timezone in the strategy store test. Synthetic auth logging checks now include state/nonce/cookie/session/header material, and real Spring beans are used for order rollback and independently committed audit checks. No trading feature was added.

### Complete changed-file classification

A = production bug fix; B = production safety hardening; C = migration; D = regression/integration test; E = architecture test; F = documentation/audit evidence; G = unrelated/accidental. Primary category is assigned once even where a fix also hardens safety.

| File | Category |
|---|---|
| `apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresMigrationTest.java` | D |
| `apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresOrderRepositoryTest.java` | D |
| `apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresReconciliationStoreTest.java` | D |
| `apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresStrategyEvaluationStoreTest.java` | D |
| `apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/LocalKiteOrderExecutionIntegrationTest.java` | D |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/broker/application/auth/KiteAuthenticationSession.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteAuthenticationAdapter.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteMarketDataAdapter.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteMarketDataDiagnosticController.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteOrderAdapter.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteRestTransport.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteSession.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/instrument/application/UniverseValidationService.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/marketdata/application/LatestMarketDataStore.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/marketdata/infrastructure/InMemoryLatestMarketDataStore.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/ExecutionSafetyPolicy.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderApplicationService.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderExecutionGateway.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderExecutionProperties.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderRepository.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/RuntimeExecutionArming.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/order/domain/OrderState.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/order/infrastructure/OrderConfiguration.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/order/infrastructure/PostgresExecutionAuthorizationAuditStore.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/order/infrastructure/PostgresOrderRepository.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/reconciliation/application/OrderReconciliationService.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/reconciliation/infrastructure/PostgresReconciliationStore.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/reconciliation/infrastructure/ReconciliationConfiguration.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/risk/infrastructure/RiskConfiguration.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/strategy/application/StrategyOrderCoordinator.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/strategy/infrastructure/PostgresStrategyEvaluationStore.java` | A |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/strategy/infrastructure/StrategyConfiguration.java` | A |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/MarketDataConfigurationTest.java` | D |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/OrderArchitectureTest.java` | E |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/UniverseValidationArchitectureTest.java` | E |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteAuthenticationAdapterTest.java` | D |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteAuthenticationHttpTest.java` | D |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteMarketDataBackpressureTest.java` | D |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteOrderAdapterTest.java` | D |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteSessionMarketDataTest.java` | D |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/instrument/application/UniverseValidationServiceTest.java` | D |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/order/ExecutionSafetyPolicyTest.java` | D |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/order/OrderApplicationServiceTest.java` | D |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/reconciliation/OrderReconciliationServiceTest.java` | D |
| `docs/runbooks/execution-safety-phase-9.md` | F |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/marketdata/application/PublicationPermit.java` | B |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/shared/application/ExecutionSession.java` | B |
| `apps/trading-core/src/main/resources/db/migration/V10__unique_broker_order_identity.sql` | C |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/order/ExecutionFreshnessAuditTest.java` | D |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/risk/domain/CashRiskArithmeticTest.java` | D |
| `apps/trading-core/src/test/java/com/kitehybrid/platform/strategy/StrategyReplayAuditTest.java` | D |
| `docs/adr/ADR-017-execution-admission-and-publication-fencing.md` | F |
| `docs/operations/end-to-end-audit-143ec86.md` | F |
| `docs/operations/final-engineering-review.md` | F |

Totals: A=14, B=15, C=1, D=18, E=2, F=4, G=0; total 54 files. **G=0**. Ignored target logs/scripts are verification artifacts, not proposed changes. Tracked bytecode remains untouched in this RC pass.

### Production changes reviewed line by line

For every row below: the change is considered the smallest coherent fix within its stated contract; there is no speculative refactor. Reproduction/regression evidence is named explicitly. No new fail-open branch was found. Concurrency uses the stated authority; residual snapshot/network limitations are not represented as atomic remote guarantees. Documentation is required for each behavior/port/transaction change and is covered by this review, ADR-017 and the execution runbook. Existing unit-only mocks are not treated as proof of PostgreSQL/Spring/HTTP behavior.

| Production file | Defect / evidence | Public/domain semantic change | Race / fail-closed assessment | Transaction change | Broker / eligibility change | Persistence compatibility |
|---|---|---|---|---|---|---|
| `KiteAuthenticationSession.java` | Broker-named policy port; architecture tests | Extends non-secret ExecutionSession | Default identity empty denies | None | No transport change; identity required | No schema change |
| `KiteAuthenticationAdapter.java` | Arbitrary upstream text logging; adapter/HTTP adversarial tests | Only fixed diagnostic vocabulary survives | Unknown text redacted, no exception causes | None | Exchange protocol unchanged | None |
| `KiteMarketDataAdapter.java` | Delayed tick publication; four latch-controlled races | Retires visible ticks on connection/mode removal; future health timestamps deny | Permits revoked under existing adapter monitor; no consumer I/O in monitor | None | Streaming lifecycle unchanged; stale evidence unavailable | Memory only |
| `KiteMarketDataDiagnosticController.java` | Remote/browser mutation guard missing; diagnostic tests | Development AND not production; loopback/Host/proxy/Origin guards | Guard runs before gateway; no execution port | None | Only explicit local stream actions remain | None |
| `KiteOrderAdapter.java` | Uncertain status/JSON acknowledgement; parameterized protocol tests | Callback-bearing place only; legacy place disabled | Strict duplicate/trailing JSON rejection; uncertain statuses ambiguous | None | 400 rejection; other non-auth errors ambiguous; no retry | Uncertain lifecycle retained |
| `KiteRestTransport.java` | Stale authorization while waiting for session; transport-entry test | Required place validation callback | Session monitor acquired before final policy callback; denial rethrown | Reads policy DB outside DB transaction; session lock spans HTTP | Same fixed routes, no retries/redirects; final denial sends nothing | None |
| `KiteSession.java` | Arms survive replacement; session/expiry tests | Opaque UUID identity present only when verified and usable | Immutable volatile view, atomic invalidation; identity read nonblocking | None | Session replacement invalidates prior arm eligibility | Identity deliberately not persisted |
| `UniverseValidationService.java` | Unrelated ancestor CSV selection; path regression | Repository markers required unless explicit override | Reads only; missing root fails closed | None | No broker or execution linkage | Root CSV unchanged |
| `LatestMarketDataStore.java` | Store contract could not fence delayed writes; backpressure tests | Adds mandatory permit-aware update contract | Every read must filter revoked values | None | No transport; invalid ticks unavailable | Memory only; implementations migrated |
| `InMemoryLatestMarketDataStore.java` | Old writes visible after retirement; lifecycle regressions | Revoked values invisible to latest and snapshot | ConcurrentHashMap.compute per key; invalid incoming permit cannot displace valid value | None | No dispatch; freshness source narrowed | Memory only |
| `ExecutionSafetyPolicy.java` | Stale/insufficient authorization; real policy HTTP denial matrix and late-evidence tests | Adds dispatch revalidation and bounded admission denial; broker-independent session port | Read evidence then sample clock; snapshot boundary documented, exceptions do not dispatch | Durable audit delegated; no enclosing DB transaction | Tightens all execution gates; never calls gateway | V9 audit schema unchanged; new config fingerprint |
| `OrderApplicationService.java` | Mutation bypass, ambiguous ack state and CAS/admission gap; application + PostgreSQL tests | Modify/cancel fail DISABLED; explicit place execution requires independent admission | CAS then rechecks; uncertain outcome not retryable; no mutex-only authority | Reject ambient transaction; committed SUBMITTING before HTTP | Only protected explicit place reaches gateway | Uses existing lifecycle fields; pre-dispatch failure category |
| `OrderExecutionGateway.java` | Old callback-free place route; adapter/application/architecture tests | Both place defaults deny; adapter supports callback-bearing overload only | Unsupported implementations fail closed | None | Mutation interface retained but production callers prohibited | No schema change; source implementers must opt in |
| `OrderExecutionProperties.java` | Unidentifiable audit config/extreme BigDecimal; freshness audit tests | Immutable fingerprint and bounded numeric scale/precision | Set copied; malformed values reject construction | None | Defaults unchanged; invalid config cannot authorize | Fingerprint fits V9 varchar(128) |
| `OrderRepository.java` | Unsafe coordination defaults; real repository concurrency tests | New admission/ambient checks default deny | Unknown implementations cannot execute | Defines committed-admission contract | Tightens execution eligibility | No schema change |
| `RuntimeExecutionArming.java` | Unbounded/non-session arm and expired-reader race; restart/boundary/session tests | One hour max; session supplier mandatory for successful arm | AtomicReference CAS only clears observed arm; exclusive expiry | None | Arm itself has no dispatch; replacement requires re-arm | Never persisted |
| `OrderState.java` | Read-only crash recovery could not advance completed orders; reconciliation tests | Adds legitimate SUBMITTING/acknowledged recovery transitions | No arbitrary state setter; transitions remain centralized | Persisted by existing CAS/store transaction | No new execution transition | Existing enum values; no schema change |
| `OrderConfiguration.java` | Conditional bean ordering and optional policy/audit; real Spring context tests | Durable beans mandatory outside database-free test profile | Missing dependencies fail startup; no NOOP policy/audit fallback | Wires explicit transaction stores | Runtime arm session-bound; default gateway disabled | No schema change |
| `PostgresExecutionAuthorizationAuditStore.java` | Audit lost with caller rollback; standalone and Spring rollback tests | Authorization durable independently, not submission evidence | Database failure prevents subsequent dispatch; timeout five seconds | REQUIRES_NEW explicit template | No broker call; initial ALLOWED not guarantee of HTTP | Same V9 table |
| `PostgresOrderRepository.java` | Final proxy transaction failure / cross-order race; rollback and concurrent service tests | Explicit durable create/admission behavior | Account advisory lock + blocker query + version CAS; five-second lock wait | Template create; independent admission committed before return; ambient rejected | No broker I/O inside transaction | Existing columns; V10 separately adds broker uniqueness |
| `OrderReconciliationService.java` | Identity/fill overflow/cancel handling; domain and response-loss tests | Reject conflicting/overfilled observations; correct terminal recovery | Exact IDs/tags plus immutable terms; no heuristic matching | Store remains atomic authority | Read-only; cannot resubmit | Uses existing lifecycle/audit categories |
| `PostgresReconciliationStore.java` | Non-atomic proxy/self-instantiation and ignored historical fill conflict; PostgreSQL rollback tests | Identical trade replay accepted; changed terms rejected | Row lock, version check, trade uniqueness; entire conflict rollback | Explicit template; failed result marks rollback-only | No execution dependency | Same V6 schema; identical conflict performs no-value-change update |
| `ReconciliationConfiguration.java` | Service silently missing under Spring; development/production context tests | Explicit read-enabled condition and non-test store | Missing required dependencies fail startup | Wires actual transactional store | Reads optional; never auto-reconcile/execute | No schema change |
| `RiskConfiguration.java` | Read-dependent risk bean omitted by ordering; Spring tests | Non-test durable store; service requires reads enabled | No optional execution fallback | Existing explicit REQUIRES_NEW risk store unchanged | No gateway; approval hard stop | None |
| `StrategyOrderCoordinator.java` | Replay mutated proposal/long-key substring; replay tests | Use persisted signal/no-action; full SHA-256 fallback | Deterministic idempotency; store conflict halts before order; no execution | Claim and attach remain separate recoverable checkpoints | Proposal/risk only; bounded metrics | Long hash now usable; existing short keys unchanged |
| `PostgresStrategyEvaluationStore.java` | Final proxy transaction gap and RC1 changed-term replay; PostgreSQL tests | Same identity with changed economic terms returns CONFLICT | Unique tuple/signal ID; immutable terms compared numerically; no row overwrite | Explicit transaction templates for claim/attach | No execution; conflicts cannot propose through coordinator | Same V8 schema; stricter replay behavior |
| `StrategyConfiguration.java` | Coordinator silently missing; real context tests | Non-test durable coordinator; risk optional as before | Mandatory order/persistence dependencies | Wires real template store | No automatic evaluation/dispatch | None |
| `PublicationPermit.java` | Revocation bridge for delayed publication; lifecycle race tests | One-way valid/revoked value | AtomicBoolean visibility; never reactivated | None | No dispatch | Memory only |
| `ExecutionSession.java` | Policy broker coupling; architecture/session tests | Non-secret broker-independent usable identity port | Absent identity denies; contract nonblocking coherent view | None | No credentials or dispatch | No persistence |

### V10 release analysis

Exact DDL: `CREATE UNIQUE INDEX orders_broker_order_id_unique ON trading.orders(broker_order_id) WHERE broker_order_id IS NOT NULL`. It is a partial unique index, not a new column or a table rewrite. NULL rows are excluded and multiple historical NULLs remain legal; each non-NULL text value may identify only one local order in the current single-account schema. No trim/case normalization/ownership inference is performed. V1-V9 files/checksums are unchanged.

Clean migrations through V10 pass in the repository suites. Explicit V9 -> V10 upgrades now seed two NULLs and one known ID, migrate exactly one version, preserve rows, accept another NULL, reject a duplicate non-NULL insertion and validate/restart without further migration. A separate V9 database with two identical historical broker IDs causes V10 to fail; the test compares every order column before/after, verifies current Flyway version remains 9 and verifies no V10 index/history row survives. No historical order is deleted, merged or nulled.

Normal CREATE INDEX takes a table lock that blocks writes while building the index; it is transactional on PostgreSQL. This is intentionally not CREATE INDEX CONCURRENTLY, which would require a different nontransactional failure protocol. Upgrade needs a maintenance window, backup, free disk and read-only duplicate preflight. The [runbook procedure](../runbooks/execution-safety-phase-9.md#v10-duplicate-history-upgrade-procedure) tells operators to stop on duplicates, preserve evidence, obtain a separate reviewed correction plan and never blindly repair Flyway history. This audit migrated only disposable Testcontainers databases.

### Exact final execution ordering

1. `OrderApplicationService.executeRiskApproved(id)` calls `repository.requireIndependentExecution()` before any side effect, then reads the authoritative order.
2. `safety.evaluate(current)` checks current eligibility and commits an authorization record via `PostgresExecutionAuthorizationAuditStore` (REQUIRES_NEW). Initial denial throws before SUBMITTING.
3. `repository.beginSubmission(current, submitting)` rejects ambient transactions, takes PostgreSQL account advisory lock 606001, checks blocking exposure and performs the order version CAS. Transaction completion commits SUBMITTING before returning. There is no account/database lock held over HTTP.
4. `safety.validateDispatch(current, submitting)` reloads the current state/version/terms/correlation and rechecks policy evidence, including the account blocker excluding itself. Ages use a clock sample taken after potentially blocking evidence reads.
5. `gateway.place(submitting, validationCallback)` enters KiteOrderAdapter, validates capability/identity and serializes submitted terms. It does not yet send HTTP.
6. `KiteRestTransport.orderRequest` acquires the session monitor and verifies authentication. It invokes the supplied policy callback again before creating/dispatching the HTTP request. The callback rechecks the session-bound arm, auth identity, stop, persisted risk/version/policy/age, current market evidence, notional and reconciliation blocker.
7. On a valid acknowledgement, identity is attached and SUBMITTED persisted with CAS; uncertainty remains SUBMITTING. There is no automatic resend, cancellation or repair.

Thus gateway entry precedes the final transport-session validation; the report does not put the gateway after that validation. A callback is not an unforgeable capability: architecture rules and production wiring constrain who can supply it. No arbitrary production caller/reflection/SpEL dispatch was found. Direct adapter tests intentionally pass no-op callbacks to test protocol only; they are not safety-authorization evidence.

The final validation is a snapshot/dispatch boundary, not a remote distributed transaction. An emergency stop/session/health change observed before it denies. A change after the final observation can overlap in-flight dispatch; no software-only guard can recall bytes accepted by the broker. There is no production operator execute/arm endpoint in this diff.

### Execution failure-state matrix

No row permits automatic retry of PLACE, including definitely unsent failures. A later explicit attempt at an unchanged RISK_APPROVED order must pass every gate again. FAILED/terminal/uncertain orders cannot re-enter execution through this service.

| Failure point | Local state | Authorization audit | Broker may have received? | Automatic retry? | Recovery |
|---|---|---|---|---|---|
| Initial safety denial before CAS | Original state, normally RISK_APPROVED | DENIED with bounded reason | No | No | Correct evidence; separate explicit request |
| Ambient transaction detected | Original state | No evaluation took place | No | No | Invoke outside caller transaction |
| Initial audit write fails | Original state | Cannot promise durable audit during storage outage | No | No | Restore storage; investigate failure |
| Admission blocker / CAS conflict | Candidate unchanged or concurrent winner's state | Initial ALLOWED plus attempted bounded DENIED | Losing attempt: no | No | Read authoritative state; do not infer submission from ALLOWED |
| Admission commit response uncertain | May be RISK_APPROVED or SUBMITTING | Initial ALLOWED | This attempt has not reached gateway | No | Inspect committed DB state/reconcile; no blind retry |
| Post-CAS policy denial | FAILED/PRE_DISPATCH_DENIED if CAS succeeds; otherwise unresolved current state | DENIED at submitting version; initial ALLOWED retained | No | No | Investigate reason/state; never reinterpret as broker rejection |
| Post-CAS audit/storage/check failure | SUBMITTING/current concurrent state | Initial ALLOWED; later audit may be unavailable | No HTTP from failed validation | No | Storage recovery and reconciliation/operator investigation |
| Connection refused before send | SUBMITTING conservatively | Initial ALLOWED | Normally no, but code treats transport uncertainty conservatively | No | Reconcile/verify absence; no safe-resend inference |
| Timeout / connection reset | SUBMITTING | Initial ALLOWED | Yes/unknown | No | Exact ID/tag observation and consistency checks |
| Response lost after acceptance | SUBMITTING, usually no broker ID | Initial ALLOWED | Yes | No | Exact persisted correlation recovery |
| Malformed / duplicate-field / trailing JSON acknowledgement | SUBMITTING | Initial ALLOWED | Yes/unknown | No | Read-only reconciliation |
| HTTP 400 validation rejection | FAILED/BROKER_REJECTED if CAS succeeds | Initial ALLOWED | Request arrived; treated as rejected | No | Bounded rejection investigation |
| HTTP 401/403 or local auth unavailable | FAILED/AUTHENTICATION if mapped directly; final policy denial uses PRE_DISPATCH_DENIED | Initial ALLOWED, plus DENIED when policy detects it | Remote status means request arrived; local denial did not send | No | Reauthenticate explicitly; no retry |
| Other HTTP 4xx (including 408/409/429) | SUBMITTING | Initial ALLOWED | Possible | No | Reconcile; no generic 4xx safe-retry assumption |
| HTTP 5xx | SUBMITTING | Initial ALLOWED | Possible | No | Reconcile |
| Valid acknowledgement and successful persistence | SUBMITTED with broker ID | Initial ALLOWED; audit is still not acknowledgement evidence | Yes | No | Normal read-only reconciliation |
| Crash after SUBMITTING commit, before HTTP | SUBMITTING | Initial ALLOWED | Actual window: no; after restart indistinguishable from response loss | No | Reconciliation/operator review |
| Crash after HTTP, before identity/state persistence | SUBMITTING or atomically SUBMITTED if commit completed | Initial ALLOWED | Possible/yes | No | Exact broker ID or persisted tag; no heuristics |

### Publication, reconciliation and replay proofs

PublicationPermit uses AtomicBoolean; a retired permit cannot become valid again. The adapter's existing monitor protects connection epoch, desired modes, subscription revisions and permit replacement; immutable pending events carry the exact permit. The queue is bounded. The store performs per-instrument ConcurrentHashMap.compute and checks the incoming permit before changing the entry. latest/snapshot filter revoked values; a revoked prior value may be replaced by a valid current value even with a lower old timestamp. The store does not take the adapter/session monitor, and revocation never waits for a delayed consumer. There is no new global store lock.

Stop, detach/reconnect, unsubscribe and mode replacement revoke permits. A decoded/queued old event also fails epoch/revision checks. Four blocked-consumer tests pause after dequeue and retire the source before releasing the write; no old tick becomes visible. Existing generation/mode/queue tests cover old callbacks, delayed frames, unsubscribe, malformed frames and overflow. An already returned Tick is an immutable snapshot and cannot be recalled; execution independently reacquires current data. This is the precise memory-visibility boundary, not a claim of zero contention.

Reconciliation tests cover exact identity/correlation recovery, duplicate trade idempotency, conflicting same-response and historical trades, overfill/long overflow, partial/full fill, cancel-after-partial-fill and changed submitted terms. PostgreSQL row locking and CAS protect the lifecycle; any historical trade conflict rolls back audit, fills and state together. Identical ON CONFLICT updates leave terms unchanged but can create MVCC churn. Recovery never imports a foreign/malformed tag as trusted identity and has no execution dependency. Response-loss integration recreates components and uses local GET orders/trades; PLACE count remains one.

Strategy replay economic terms are instrument, side, quantity, reference price and reason under the persisted strategy/version/event/signal identity. RC1 rejects changed terms; same numeric reference price with a different scale is not a change. Persisted timestamps/intent/order metadata remain authoritative on replay rather than being overwritten with wall-clock recomputation. A changed term is a CONFLICT before proposal. The coordinator's bounded conflict branch is tested with zero order interactions; PostgreSQL independently tests conflict detection across recreated stores. Long event keys use full SHA-256 UTF-8 bytes. Strategy ID/version exclude ':'; event-key delimiters therefore do not ambiguously split identity components. Short keys remain unchanged. No JVM-dependent hashCode/random value is used for logical event identity.

Auth adapter tests inject secrets through both parsed error fields and IOException messages. Unknown upstream text becomes REDACTED; malformed/oversized/unreadable responses use fixed fallback text. HTTP tests inject API secret, access/request token, Authorization, checksum, encryption key, state/nonce and cookie/session material in a gateway exception and verify bounded callback responses and captured log exclusion. This does not ban the intentional login protocol's state cookie/redirect; it bans accidental error/log disclosure. Production log calls do not pass arbitrary upstream exceptions/payloads.

Real Spring development and production contexts instantiate OrderApplicationService, RiskService, OrderReconciliationService, StrategyOrderCoordinator, ExecutionSafetyPolicy, RuntimeExecutionArming and durable audit store with reads configured but REST/stream/execution disabled. Tests now use the actual context repository bean to prove failed create rolls back its idempotency claim, and the actual context audit bean plus Spring transaction manager to prove REQUIRES_NEW audit survives caller rollback. Explicit templates do not need final-class proxies or self-invocation interception.

### Scale and retention assessment

This release has functional/safety validation, not a measured throughput certification. Safe supported scope is one account, one authoritative PostgreSQL ledger, explicit low-volume execution, and the canonical 1111-member universe. No evidence establishes a safe 1111-instrument live subscription rate.

| Component | Complexity, contention and first limits | Before expanding scale |
|---|---|---|
| InstrumentRegistry | Expected O(1) indexed reads; O(N) validation/copy/replacement. Three immutable maps share Instrument objects. Old/new maps and parsing candidates coexist during replacement; peak memory exceeds steady-state. Reads use atomic snapshot reference; refresh writers serialize. | Benchmark full master size/peak heap/GC; preserve atomic replacement |
| Universe | O(U) parse/deduplicate/resolve, U=1111; lookup scans master O(N) and sorts matches O(M log M). No subscription, strategy or execution activation. | Bound diagnostic output/file size if exposed to larger local datasets; no automatic subscribe-all |
| Market data | Default queue 4096 ticks, max subscriptions 3000, reconnect ceiling 8 with bounded delays. Decoder normalizes whole frames before mutation. Existing adapter monitor surrounds queue admission/state work; worker drains serially and store compute is per instrument. 1111 active instruments with one tick each consume about 27% of default queue capacity; bursts can exceed capacity quickly. | Synthetic load test target packet modes/rate, measure drops/latency/GC. Drops degrade health. Partition work only with preserved per-instrument ordering and fencing |
| PostgreSQL orders | UUID PK, idempotency unique keys, correlation partial uniqueness, V10 broker ID partial uniqueness. Admission/risk blocker EXISTS queries filter state without a state index and may scan historical orders. | Measure plans before adding partial state indexes; size connection pools and monitor lock waits |
| Risk | One account advisory lock plus order row lock; broker reads can extend transaction time. Very conservative one-outstanding-exposure policy dominates throughput before CPU. | Account-scoped reservations and coherent snapshot design; no arbitrary parallel approval |
| Execution | Brief same-account advisory-lock admission, then HTTP outside transaction. Session monitor serializes that session's REST calls; latency, blocker scans and exposure retention limit throughput. | Preserve independent commit/ambiguous outcome discipline; design account-specific admission and session ownership |
| Strategy | Unique(strategy_id,strategy_version,event_key) and unique(signal_id); indexed claim/find. Claim and attach are separate durable checkpoints; hot identity contention is intentional. | Per-account/strategy partitioning only after identity/retention semantics are specified |
| Reconciliation | For a single order the service obtains broker lists and scans for matching ID/tag/trades: O(B+T); repeated per-order reconciliation can repeat full reads. Fill dedup HashMap is O(T), DB trade PK handles duplicates. Audit writes/identical conflict updates add storage churn. | Reuse bounded observed snapshots/indexed matching in a separately reviewed design; measure broker quotas and DB growth |
| Authorization audit | Append per evaluation/denial with UUID PK and (order_id,evaluated_at) index. REQUIRES_NEW consumes a connection independently of caller; saturation may deny execution. | Monitor pool/storage/latency; no unbounded identifier labels or silent audit dropping |

Likely first execution bottleneck is deliberate account/exposure serialization and REST latency, followed by historical state scans; likely first streaming bottleneck is the bounded single drain path under bursts. Horizontal replicas share PostgreSQL risk/admission locks, but in-memory auth/arms/market state are process-local. Production horizontal scaling needs account identity in uniqueness/lock scopes, authenticated operator routing/session ownership, compatible policy/config versions, reservation release and measured resource budgets. More replicas alone do not grant more safe account capacity.

| Table | Growth / retention behavior |
|---|---|
| risk_decisions | At most one immutable decision per order; grows with order history. No archive/retention job. FK to orders requires coherent archival. |
| reconciliation_decisions | Append per observation including no-change/conflict observations; (order_id,observed_at) index. Can grow with polling frequency. No retention job. |
| reconciliation_trades | Unique trade/order pair, FK to reconciliation decision; observations deduplicate, but identical upserts can generate dead tuples/WAL. No archival policy. |
| strategy_evaluations | One row per logical evaluation; order attachment updates it. Identity records are necessary for replay protection. No safe independent deletion policy exists. |
| execution_authorizations | Append per authorization and later denial; order/time index, no general age index/partitioning or retention job. Never delete just to reduce denial volume. |
| kite_login_attempts | Successful atomic consume deletes a row; each login start opportunistically deletes up to 100 expired rows for the store. No periodic cleanup when idle and no global rate limiter. |
| orders / order_idempotency | Durable history and dedup authority; no archival policy. Deleting keys could allow replay. |

Retention is an operational gap, not permission to delete. Before sustained use, define backup/restore, disk/row/WAL/autovacuum monitoring and a reviewed archive policy preserving FKs, broker correlation, idempotency and replay evidence. No unsafe retention feature is added here.

### Complete project configuration inventory

Inventory covers project-declared environment placeholders, the documented Spring profile/port controls, Compose credentials and Python Settings. Generic arbitrary Spring property overrides are not a finite project variable list. Defaults are source defaults, not values read from a developer .env. No secret values were inspected. 'Conditional' means required only for the named enabled capability; optional never means permission to bypass its gates. FC = fail closed for the controlled capability; N/A = not an execution gate.

| Name | Default | Secret? | Required? | Failure/default behavior | Module / effect |
|---|---|---|---|---|---|
| SPRING_PROFILES_ACTIVE | development default profile | No | Optional | N/A; production DB placeholders mandatory | Spring profile selection; does not arm |
| SERVER_ADDRESS | 127.0.0.1 | No | Optional | Local binding; invalid binding fails startup | HTTP reachability |
| SERVER_PORT | 8080 (Spring) | No | Optional | Port conflict fails startup | HTTP port; callback registration must match |
| DB_URL | jdbc:postgresql://localhost:5432/{DB_NAME} | No, unless credentials embedded (do not embed) | Required in production | FC on unavailable authoritative DB | PostgreSQL datasource |
| DB_NAME | trading | No | Optional for development/Compose | Helper validates database-name format | Compose/default URL |
| DB_USER | trading in development | No | Required in production | DB auth/connect failure prevents readiness | Datasource/Compose |
| DB_PASSWORD | empty in app; no Compose fallback | Yes | Required production/Compose | Compose fails missing; DB auth fails closed | Datasource/Compose |
| REDIS_HOST | localhost | No | Optional | N/A execution; Redis not durable authority | Ephemeral infrastructure |
| REDIS_PORT | 6379 | No | Optional | Invalid binding fails; no execution fallback | Redis |
| REDIS_PASSWORD | empty in app; required by Compose | Yes | Required Compose | Compose rejects missing | Redis authentication |
| TRADING_MODE | PAPER | No | Optional | LIVE rejected at configuration construction | Legacy trading-mode safety guard |
| ENABLE_LIVE_TRADING | false | No | Optional | true rejected at configuration construction | Legacy live-mode guard |
| EMERGENCY_STOP | true | No | Optional | FC; strategy/risk/execution stop | Trading safety |
| RISK_ENABLED | false | No | Optional | FC, disabled decisions reject | Risk |
| RISK_MAX_ORDER_QUANTITY | 0 | No | Conditional risk | Zero denies; invalid limits reject | Risk quantity |
| RISK_MAX_ORDER_VALUE | 0 | No | Conditional risk | Zero denies | Risk BigDecimal notional |
| RISK_MAX_POSITION_QUANTITY | 0 | No | Conditional risk | Zero denies | Risk position cap |
| RISK_MAX_EXPOSURE | 0 | No | Conditional risk | Zero denies | Risk exposure cap |
| RISK_MARKET_DATA_MAX_AGE | 0s | No | Conditional risk | Zero provides no usable fresh interval | Risk tick age |
| RISK_REGISTRY_MAX_AGE | 0s | No | Conditional risk | Zero provides no usable fresh interval | Risk registry age |
| RISK_PRICE_BUFFER | 1.0 | No | Optional | Invalid limits reject; no collateral inferred | Risk valuation multiplier |
| RISK_CASH_RESERVE | 0 | No | Optional | Invalid negative reserve rejects | Risk cash deduction |
| KITE_REST_ENABLED | false | No | Optional | FC; no authenticated broker session | Kite REST/auth reads |
| KITE_API_KEY | empty | Credential, redact | Conditional REST/auth | Missing/invalid prevents configured auth | Broker identity/header/checksum |
| KITE_API_SECRET | empty | Yes | Conditional auth | Missing/invalid exchange configuration denies | Authentication checksum |
| KITE_ACCESS_TOKEN | empty | Yes | Legacy standalone diagnostic only | Spring session ignores environment token | No automatic authenticated Spring session |
| KITE_REDIRECT_URL | http://localhost:{server.port:8080}/api/broker/kite/auth/callback | No | Conditional auth, default provided | Validated; must match broker registration | Interactive callback |
| KITE_TOKEN_ENCRYPTION_KEY | empty | Yes | Conditional persisted authentication | Invalid Base64/length or wrong key fails closed | AES-256-GCM persistence |
| KITE_TRADING_READ_ENABLED | false | No | Optional | Read-dependent risk/reconciliation services absent when false | Broker account-read providers |
| KITE_TRADING_READ_DIAGNOSTIC_ENABLED | false | No | Optional | FC; endpoint absent | Development read diagnostics |
| KITE_MARKET_DATA_ENABLED | false | No | Optional | Disabled gateway; no streaming | Market data |
| KITE_MARKET_DATA_DIAGNOSTIC_ENABLED | false | No | Optional | FC; endpoint absent | Development streaming control |
| KITE_MARKET_DATA_CONNECT_TIMEOUT | 10s | No | Optional | Invalid range fails binding/construction | Bounded connect |
| KITE_MARKET_DATA_STALE_AFTER | 30s | No | Optional | Stale data cannot be FRESH | Health |
| KITE_MARKET_DATA_IDLE_TIMEOUT | 30s | No | Optional | Idle connection fails/degrades/reconnects | Lifecycle |
| KITE_MARKET_DATA_QUEUE_CAPACITY | 4096 | No | Optional | Bounded; overflow counted/degrades | Backpressure |
| KITE_MARKET_DATA_MAX_SUBSCRIPTIONS | 3000 | No | Optional | Explicit subscriptions only; cap enforced | No universe auto-subscription |
| KITE_MARKET_DATA_RECONNECT_INITIAL_DELAY | 1s | No | Optional | Validated bounded schedule | Reconnect |
| KITE_MARKET_DATA_RECONNECT_MAX_DELAY | 30s | No | Optional | Delay bounded | Reconnect |
| KITE_MARKET_DATA_RECONNECT_MAX_ATTEMPTS | 8 | No | Optional | Exhaustion stops reconnect attempts | Reconnect |
| **KITE_ORDER_EXECUTION_ENABLED** | **false** | No | Optional | **FC; disabled gateway/capability** | Execution; true is not ARMED |
| **KITE_ORDER_EXECUTION_ALLOWED_INSTRUMENTS** | **empty** | No | Conditional execution | **Empty denies all; malformed UUIDs fail** | Stable InstrumentId allowlist |
| **KITE_ORDER_EXECUTION_MAX_QUANTITY** | **0** | No | Conditional execution | **Zero denies** | Independent hard execution cap |
| **KITE_ORDER_EXECUTION_MAX_NOTIONAL** | **0** | No | Conditional execution | **Zero denies; malformed/extreme values fail** | BigDecimal execution cap |
| **KITE_ORDER_EXECUTION_RISK_DECISION_MAX_AGE** | **0s** | No | Conditional execution | **Zero denies** | Approval validity |
| **KITE_ORDER_EXECUTION_MARKET_DATA_MAX_AGE** | **0s** | No | Conditional execution | **Zero denies** | Execution tick validity |
| UNIVERSE_DIAGNOSTIC_ENABLED | false | No | Optional | FC; endpoints absent | Development reference validation |
| UNIVERSE_DIAGNOSTIC_PATH | empty | No | Optional in repo; required outside repo | Deterministic root search or explicit failure | Read-only CSV override |
| STRATEGY_LOG_LEVEL | INFO | No | Optional | Invalid Literal rejected | Python research logging only |
| POSTGRES_DB / POSTGRES_USER / POSTGRES_PASSWORD | Derived from DB_* by Compose | Password yes | Container-only derived | Not independent Java settings | PostgreSQL image environment |
| REDISCLI_AUTH | Derived from REDIS_PASSWORD by Compose | Yes | Container health command | Not an independent app setting | Redis health check |

There is deliberately **no runtime-armed environment variable**. Every new process constructs an empty arm. The legacy PAPER/live-mode fields and independent Kite execution capability are separate controls; PAPER alone must not be interpreted as a simulator guarantee if a future operator intentionally configures the Kite capability and arm. No such configuration was enabled here. REST host/routes and 10s connect/30s read timeouts are fixed in production transport; there is no project KITE_REST_BASE_URL override. Generic JVM/Maven/Docker settings are toolchain controls, not trading authorization.

### Development endpoint inventory

All three controllers require `development & !production`; every flag defaults false. Request guards are explicitly inventoried rather than described as identical when they differ. No controller references RuntimeExecutionArming, RiskService, execution allowlist or OrderExecutionGateway.

| Endpoint | Method / activation | Request-level protection / response |
|---|---|---|
| /api/development/universe/validate | GET; UNIVERSE_DIAGNOSTIC_ENABLED | Loopback peer + local server Host, rejects Forwarded/X-Forwarded-*; initialized registry required; summary only |
| /api/development/universe/unresolved | GET; same flag | Same guard; exchange/symbol only |
| /api/development/universe/lookup | GET; same flag | Same guard; 1-32 printable-character fragment; exchange/symbol/segment only |
| /api/development/trading-read/orders | GET; read-enabled AND diagnostic-enabled | Loopback/local Host including raw Host validation, proxy/Origin/cross-site rejection, exact GET, no-store; normalized orders |
| /api/development/trading-read/trades | GET; same flags | Same guard; normalized trades |
| /api/development/trading-read/positions | GET; same flags | Same guard; normalized positions |
| /api/development/trading-read/holdings | GET; same flags | Same guard; normalized holdings |
| /api/development/trading-read/margins | GET; same flags | Same guard; normalized margins |
| /api/development/market-data/start | POST; market-enabled AND diagnostic-enabled | Loopback/local server Host, proxy/Origin/cross-site rejection, no-store; explicitly subscribes one resolved instrument then starts stream |
| /api/development/market-data/subscriptions | POST; same flags | Same guard; explicit one-instrument mode subscription |
| /api/development/market-data/subscriptions/{id} | DELETE; same flags | Same guard; unsubscribe only |
| /api/development/market-data/stop | POST; same flags | Same guard; stop streaming only |
| /api/development/market-data/status | GET; same flags | Same guard; health only |
| /api/development/market-data/latest | GET; same flags | Same guard; normalized Tick/InstrumentId, not broker token |

Trading-read endpoints explicitly reject HEAD/OPTIONS and other methods. Universe and market-data GET mappings retain Spring's HEAD semantics; universe also lacks Origin/fetch-site/no-store consistency and an independent lookup-result cap. These known Medium/Low reference-data limitations remain documented; this review does not add another diagnostic framework. No endpoint can mutate broker orders or arm execution. Authentication endpoints are separate `/api/broker/kite/auth/**`, not development execution endpoints.

### Test-quality and negative-review findings

- Fixed the misleading changed-term strategy mock expectation: ordinary replay uses unchanged terms; a store conflict must produce zero proposal interactions. Five independent PostgreSQL cases establish that the store actually rejects changed terms after recreation.
- Added real-context transaction assertions, beyond bean existence and manually constructed fixtures. Final stores use explicit templates in both contexts; there is no reliance on proxyable final classes.
- The application unit fixture mocks authorization to isolate command/lifecycle tests; it is not used as safety proof. Real ExecutionSafetyPolicy, session, adapter, durable audit and PostgreSQL are exercised by the loopback suite. Protocol-only tests explicitly use no-op callback and MockRestServiceServer; they are not production bypass paths.
- Concurrency tests use two worker threads, a latch after both durable ALLOWED audits, independent repository/service instances for different orders, and exact HTTP/state assertions. Runtime exceptions from losing contenders are tolerated only alongside one successful request and the expected durable states; zero requests cannot pass.
- Fixed clocks intentionally control boundary tests; session expiry has separate nonblocking exact-boundary tests. Slow-evidence-read regression advances injected time during retrieval. No expiry proof relies on sleeping.
- Denial matrix asserts durable bounded reason, no SUBMITTING transition and no gateway/HTTP interactions. CAS and late-denial tests also inspect PostgreSQL state/audit, not just an exception. Response-loss recovery counts PLACE independently of local GET observations.
- Controlled child-process verification removes inherited broker/risk/universe/trading/DB/Redis/Spring/server/logging overrides and JVM/Maven/Python injection options without printing values. It explicitly disables real REST/stream/execution, uses UTC and sets PYTHONDONTWRITEBYTECODE. Test-local fixtures opt into synthetic behavior through stronger test properties. No .env loader is invoked.

Mental mutation review (not a claim of a PIT mutation run):

| Removed/weakened control | Test expected to fail |
|---|---|
| Runtime arm check | Initial disarmed matrix case and restart-disarm test would reach a later gate/HTTP rather than DENIED/DISARMED |
| Risk expiry | Expired-risk matrix and post-ALLOW risk-age test |
| Market age/health | Stale/degraded matrix cases and market-age/health post-ALLOW cases |
| Persisted correlation | Missing-correlation matrix case; expected zero gateway/HTTP would fail |
| Reconciliation blocker | Initial blocked case's single DENIED audit and zero gateway assertion; admission/final blocker independently tested |
| Version CAS | PostgreSQL version mutation before submission and same-order concurrency assertions |
| Account admission | Different-order race must leave one RISK_APPROVED and one SUBMITTED with exactly one HTTP; replacing admission with uncoordinated CAS violates that contract |
| Publication generation/permit fence | Paused consumer then stop/unsubscribe/mode/restart would publish a forbidden tick |
| Strategy execution prohibition | ArchUnit forbids executeRiskApproved callers and gateway dependencies; strategy-to-risk HTTP count remains zero |
| Modify/cancel containment | Application + local HTTP tests require DISABLED and unchanged request count/state |

Manual searches found no reflection/MethodHandles/SpEL bypass, production automatic arm call, scheduler or event listener submitting orders. Architecture tests also constrain direct concrete gateway calls, not just interface imports. Compiler/configuration source inspection complements these tests; arbitrary future code can still violate a port contract and must pass review.

### Release-candidate verification and decisions

Final Java verification was repeated after the RC4 one-line timestamp fix, from the controlled child environment. No final suite is represented by an earlier failing reproduction log. The changed-term reproduction failed 5/5 negative cases before RC1; interrupted replay timestamp failed both short/long cases before RC4. Both now pass in the full suite. Migration rollback and both real Spring profiles pass.

| Final check | Exact result |
|---|---|
| Maven full unit suite (including architecture/diagnostics/auth/market/order/risk/strategy/reconciliation) | **955 tests; 0 failures, 0 errors, 0 skipped** |
| Maven PostgreSQL integration suite | **66 tests; 0 failures, 0 errors, 0 skipped** |
| Local policy-enabled fake-broker suite (included above) | **30 invocations**, including multi-case denial assertions |
| PostgreSQL migration/real-context suite (included above) | **3 tests**; empty/previous upgrade paths, explicit V9 -> V10, duplicate-history rollback, two Spring profiles and bean transaction checks |
| PostgreSQL strategy store suite (included above) | **6 tests**, including five changed-term conflicts |
| Other PostgreSQL suites (included above) | Auth restart 3; encrypted token 4; login attempts 8; order repository 3; reconciliation 5; risk 4 |
| Python | **14 passed**; bytecode writes disabled |
| Ruff | **All checks passed** |
| mypy | **No issues in 6 source files** |
| Maven dependency convergence | **PASS** |
| Project verification through uv | **PASS** |
| Secret scanner through uv | **581 text files; 0 findings** |
| git diff --check | **PASS**; Git's CRLF normalization notice is not a whitespace error |
| Inventory cross-check | All **46** source-declared application environment variables covered; **54** changed/new files classified; **G=0** |

Evidence is in ignored `apps/trading-core/target/rc-maven.log`, `rc-convergence.log`, `rc-python.log`, `rc-ruff.log`, `rc-mypy.log`, `rc-project.log`, `rc-secrets.log`, `rc-diff.log`, and Surefire/Failsafe XML. No real broker operation, external account read, subscription or interactive authentication was introduced. Production Kite ORDER requests initiated by this review = **0**, established from disabled production contexts, mock interception and guarded loopback-only HTTP fixtures; not a machine-wide packet-capture claim. No reset, restore, clean, discard, commit or push occurred in this release-candidate pass.

**CODEBASE_RELEASE_CANDIDATE = APPROVED.** The final diff is coherent and safe to commit with its documented disabled defaults and migration prerequisites. No unresolved Critical/High defect in the supported protected path is known. RC1-RC4 are resolved. The earlier Medium/Low scope, diagnostic, retention and scalability limitations remain explicit; approval does not silently remove them.

**LIVE_EXECUTION_READINESS = NOT_APPROVED.** Exact operational blockers, distinct from code-commit approval:

1. There is no reviewed production operator arm/execute caller or authenticated operator-control boundary. Architecture intentionally forbids automatic production callers. A separately reviewed bounded first-live procedure/control boundary is required; a no-op callback/direct adapter or ad hoc debugger invocation is not an approved substitute.
2. The first-live account admission assumptions are not operationally established: single-account ownership/exclusion of external activity, fresh account evidence, explicit one-instrument/quantity/notional/age limits, and response-loss/kill-switch operator handling must be concretely reviewed. Current local reservation/exposure serialization does not coordinate external/manual broker trades or provide a coherent final broker-account snapshot. No production configuration was populated to bypass this gap.
3. Target-environment deployment preflight is outstanding: verified backup/restore, actual V9 duplicate-ID inspection and a successful V10 upgrade on a restored target copy, with rollback/reconciliation ownership. Only disposable databases were migrated here; production data is deliberately untouched.

These blockers require a separate controlled-live milestone, not additional speculative features in this diff. Real execution remains disabled and disarmed, and no real order has been tested.

### Exact release-candidate git status --short

```text
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresMigrationTest.java
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresOrderRepositoryTest.java
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresReconciliationStoreTest.java
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresStrategyEvaluationStoreTest.java
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/LocalKiteOrderExecutionIntegrationTest.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/application/auth/KiteAuthenticationSession.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteAuthenticationAdapter.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteMarketDataAdapter.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteMarketDataDiagnosticController.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteOrderAdapter.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteRestTransport.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteSession.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/instrument/application/UniverseValidationService.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/marketdata/application/LatestMarketDataStore.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/marketdata/infrastructure/InMemoryLatestMarketDataStore.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/ExecutionSafetyPolicy.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderApplicationService.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderExecutionGateway.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderExecutionProperties.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderRepository.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/RuntimeExecutionArming.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/domain/OrderState.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/infrastructure/OrderConfiguration.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/infrastructure/PostgresExecutionAuthorizationAuditStore.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/infrastructure/PostgresOrderRepository.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/reconciliation/application/OrderReconciliationService.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/reconciliation/infrastructure/PostgresReconciliationStore.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/reconciliation/infrastructure/ReconciliationConfiguration.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/risk/infrastructure/RiskConfiguration.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/strategy/application/StrategyOrderCoordinator.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/strategy/infrastructure/PostgresStrategyEvaluationStore.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/strategy/infrastructure/StrategyConfiguration.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/MarketDataConfigurationTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/OrderArchitectureTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/UniverseValidationArchitectureTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteAuthenticationAdapterTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteAuthenticationHttpTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteMarketDataBackpressureTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteOrderAdapterTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteSessionMarketDataTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/instrument/application/UniverseValidationServiceTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/order/ExecutionSafetyPolicyTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/order/OrderApplicationServiceTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/reconciliation/OrderReconciliationServiceTest.java
 M docs/runbooks/execution-safety-phase-9.md
?? apps/trading-core/src/main/java/com/kitehybrid/platform/marketdata/application/PublicationPermit.java
?? apps/trading-core/src/main/java/com/kitehybrid/platform/shared/application/
?? apps/trading-core/src/main/resources/db/migration/V10__unique_broker_order_identity.sql
?? apps/trading-core/src/test/java/com/kitehybrid/platform/order/ExecutionFreshnessAuditTest.java
?? apps/trading-core/src/test/java/com/kitehybrid/platform/risk/domain/
?? apps/trading-core/src/test/java/com/kitehybrid/platform/strategy/StrategyReplayAuditTest.java
?? docs/adr/ADR-017-execution-admission-and-publication-fencing.md
?? docs/operations/end-to-end-audit-143ec86.md
?? docs/operations/final-engineering-review.md
```

Branch develop; HEAD 143ec86; local origin/develop divergence 0/0. All changes above remain uncommitted.
