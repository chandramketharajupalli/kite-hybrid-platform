# Audit of 143ec86

Review baseline: clean develop, 143ec86, zero divergence from the local origin/develop reference. No real broker execution is authorized. Findings below were recorded before production edits.

## Confirmed findings and remediation scope

1. **High — modify/cancel bypass execution authorization.** OrderApplicationService.modify/cancel check only execution.enabled. They can reach the gateway when disarmed or halted. Modify does not persist changed terms or obtain new risk; cancellation treats a request acknowledgement as terminal cancellation. Safest minimal containment is to deny these application operations until command-specific authorization and reconciliation semantics exist. Adapter serialization can remain tested locally.
2. **High — malformed PLACE acknowledgement destroys uncertainty.** The adapter reports MALFORMED_RESPONSE; executeRiskApproved transitions to FAILED. The broker may have accepted the request. Preserve SUBMITTING, requiring reconciliation, just as with ambiguous/transport failures.
3. **High — execution freshness accepts future observations.** Policy checks only upper age bounds; future risk evaluation/tick timestamps pass, and exchange timestamp freshness is ignored. Reject future approval/ticks and stale exchange observations.
4. **Medium — universe default path escapes repository selection.** Searching for the outermost ancestor universe.csv can select an unrelated parent file. Select the repository using its Maven wrapper and module marker, then its root universe.csv; preserve explicit override.
5. **High — market-data development diagnostic lacks request guards.** The controller has no peer/Host/proxy/Origin guard; @Profile("development") permits simultaneous production activation. Requires containment before network exposure.
6. **High — strategy replay uses new signal terms.** After loading a persisted evaluation, TradeIntent still uses the newly evaluated local variable signal. A crash after claim permits changed quantity/side/instrument on replay. Use persisted deterministicSignal consistently; preserve the original decision whether an intent exists.
7. **Medium — runtime arming has no configured maximum and a race.** Any positive Duration is accepted. An expired read clears with set(null), potentially clearing a concurrently installed arm. No automatic arming caller was found.
8. **High — reconciliation coverage is incomplete.** SUBMITTING cannot transition directly to FILLED/CANCELLED under the lifecycle; partial fills take priority over CANCELLED status; duplicate trade conflicts and long sum overflow are not rejected. Identity checks omit some submitted parameters. These require dedicated lifecycle/fill regression coverage.
9. **Medium — tracked generated artifacts.** .gradle caches, Gradle build output and Python __pycache__ files are tracked despite Maven authority. Test runs modify tracked bytecode. Broad artifact removal is not part of a runtime safety fix.
10. **Medium — policy/audit independence incomplete.** ExecutionSafetyPolicy depends on KiteAuthenticationSession; configuration supplies audit NOOP fallback and constant phase9 config version. Constructor enforcement proves only PLACE policy injection, not every gateway operation.

Baseline verification: 923 Java unit tests and 39 PostgreSQL integration tests passed, V1–V9 applied to disposable PostgreSQL 17.6; dependency convergence passed; Python 14 tests, Ruff and mypy passed. These passing tests do not negate the findings above.

11. **High - production wiring silently omits control-plane services.** A real development Spring application using disposable PostgreSQL starts without OrderApplicationService. Method-level ConditionalOnBean checks run before dependent user configurations/auto-configurations are registered. Existing integration tests manually construct services and miss this. Register required durable components outside the explicitly database-free test profile; use feature configuration for optional read-dependent services, and require policy/audit dependencies instead of nullable/NOOP fallback.

12. **High - durable-store transactions were not exercised.** Once missing beans are restored, startup fails with `Cannot subclass final class PostgresOrderRepository`. Three final stores use proxy-based Transactional annotations; manually constructed integration fixtures bypass those annotations entirely. Use explicit TransactionTemplate boundaries so production and tests share atomic behavior. Reproduce with application startup and a failed order insert that must roll back its idempotency claim.

## Disposition and readiness

Findings 1-8, 11 and 12 received narrow fixes with regression coverage. Finding 10's optional audit fallback is removed; its broker-specific session port and constant configuration version remain. Finding 9 is reported, not mass-deleted. No dependency versions, production enable flags, universe memberships, or credentials were changed.

**Do not approve live execution readiness.** Local verification is useful evidence but does not establish unconditional credential safety, account-wide concurrency safety or a complete runtime kill-switch protocol. The following limits remain:

| Severity | Remaining issue / scope | Evidence and consequence |
|---|---|---|
| High | Authorization-to-HTTP window | `OrderApplicationService.executeRiskApproved` evaluates/audits, CASes, then calls the gateway. Emergency-stop/arming/freshness can change after evaluation; there is no second safety check at transport entry. Order-version CAS protects competing attempts for the same order, not every changing input. A runtime safety protocol is needed before live activation. |
| High | Market-data generation fencing window | `KiteMarketDataAdapter` checks event acceptance separately from `LatestMarketDataStore.update`. A stop/mode-generation change between those operations can still publish an old event. Existing generation tests do not establish atomic store publication under that interleaving. Health gates reduce exposure but are not a proof that the store cannot change. |
| Medium | Account-wide execution boundary | The reconciliation blocker is a query for any local `SUBMITTING` order, not a lock shared with all execution CASes. Same-order execution is protected; two different already-approved orders can both pass before either becomes SUBMITTING. Ordinary risk serializes approvals conservatively, but restored/manual data and external account activity need separate treatment. |
| Medium | Arm/session binding | Arms are ephemeral, expire exclusively at the deadline and now have a one-hour maximum. They are not bound to an authentication generation; a replacement authenticated session does not itself disarm. `arm` still accepts a caller-supplied Instant. No production caller or HTTP arming endpoint exists. |
| Medium | Broker-independent policy incomplete | The policy imports the application-level `KiteAuthenticationSession`, not infrastructure. This passes existing architecture checks but is still a broker-named port. |
| Medium | Audit configuration identity | Production audit policyVersion remains `phase9`; different cap/allowlist configurations are not independently identifiable from that field. Audit persists authorization, never submission success. |
| Medium | Authentication failure text | `KiteAuthenticationAdapter.logExchangeFailure` bounds/redacts known secrets but still emits broker-supplied message/error_type text. Tests cover known token redaction, not all encodings or arbitrary sensitive upstream text. No observed leak is asserted, but a universal no-sensitive-text claim is unsupported. |
| Medium | Diagnostic consistency | Universe GET endpoints lack the trading-read diagnostic's Origin/fetch-site, HEAD rejection and no-store policy. Lookup bounds fragment length, not result count. Responses are public reference data only and mutation paths were not found. |
| Medium | Metrics cardinality | `strategy.evaluations` labels by strategy identifier. No order/instrument/symbol/token/correlation labels were found, but arbitrary strategy IDs can still grow cardinality. |
| Medium | Historical/generated repository content | Tracked `.gradle` caches, module `build` output and five Python bytecode files remain. Maven is authoritative; these historical artifacts are not build inputs. |
| Medium | Risk scope | Current cash BUY/CNC MARKET/LIMIT rules serialize a single account and conservatively block outstanding local exposure, including FILLED local orders. This is not a multi-account or externally coordinated reservation ledger. Broker account reads are separate observations. |
| Low | Diagnostic snapshot consistency | Universe import resolves against the registry for each entry. A simultaneous refresh can mix versions across the result; no IDs are invented or written. |

These are review findings/limitations, not permission to activate any feature. Broader lifecycle and runtime-control redesign is intentionally outside the narrow fixes in this audit.

## Baseline and build (A)

- Started on clean `develop`, `143ec86 Add unique instrument universe`. Fetched origin/develop and verified zero ahead/behind. No commit or push performed.
- Existing Oracle JDK 21.0.12, Maven wrapper 3.9.11, Spring Boot 3.5.16. One Maven application module: `apps/trading-core`. Python `apps/strategy-engine` uses uv with its lockfile.
- Resolved packaged libraries: Flyway 11.7.2, pgJDBC 42.7.11, Spring Data Redis 3.5.13. ArchUnit 1.4.1; PostgreSQL integration image 17.6. Dependency-convergence Enforcer check passed; this is not a vulnerability scan.
- Profiles: development, test, paper, production. Test profile explicitly excludes database/Flyway/Redis auto-configuration. Durable service beans now exist in non-test application profiles; read-dependent risk/reconciliation services require trading-read enabled. Missing required dependencies fail startup rather than silently removing the execution policy.
- Redis is infrastructure/ephemeral capacity, not order/risk/auth ledger authority. PostgreSQL readiness remains required. No tracked `.env` was found. No second universe.csv exists. Historical Gradle caches/build output are tracked even though Gradle is no longer authoritative.

## Actual architecture map (B)

| Boundary | Dependencies and authority |
|---|---|
| shared/domain | IDs, immutable values, correlation; no broker transport |
| instrument/domain + application | BrokerInstrumentId, Instrument, registry/import/refresh ports; infrastructure supplies atomic in-memory registry |
| broker reads/account | Normalized provider ports/models; Kite REST mappers/transports implement them |
| authentication | Application lifecycle and persistence ports; KiteSession, REST exchange, encrypted PostgreSQL stores, web callback in infrastructure |
| marketdata | Broker-independent Tick/health/store/gateway; Kite packet decoding/session/transport confined to broker infrastructure |
| order | Commands, lifecycle and repository/gateway ports; application explicit execution requires policy; PostgreSQL and Kite implement ports |
| risk | Domain engine/rules; application consumes instrument/market/account/order ports, writes decision plus lifecycle under PostgreSQL account lock |
| reconciliation | Broker read ports + order/store; exact identity observations only; no execution gateway |
| strategy | Definitions/input/signals/intents, durable evaluation store; coordinator may propose place and request risk, never execute |
| universe | CSV importer + initialized InstrumentRegistry; no strategy/order/risk/market gateway dependencies |
| diagnostics | Infrastructure controllers with development enable conditions; security differs by controller as noted above |

ArchUnit checks cover layer dependencies, mandatory policy constructor, gateway callers and absence of production execute/arm calls. The new call-site check permits gateway calls only inside `OrderApplicationService.executeRiskApproved`. There is no alternate policy-free constructor. These checks do not cover reflection or arbitrary future external callers; the production source contains none. Startup's ApplicationRunner restores authentication/reference data only.

## Authentication and identity (C-D, H, Z, AE)

Authentication follows interactive browser login, request-token exchange and SHA-256 checksum. No username/password/TOTP automation was found. Tokens are AES-256-GCM encrypted with a validated 32-byte Base64 key, unique nonce, integrity tag and API-key-derived store identity. Only ciphertext and timestamps are persisted; wrong keys/ciphertext fail closed. Expiry is the next 06:00 Asia/Kolkata; session use checks expiry. Restore loads the persisted token, validates profile, then refreshes instruments. Unverified/invalidated/expired sessions cannot authorize execution.

Login attempts persist a nonce digest with expiry and consume via an atomic delete; replay fails, including across restart. The dedicated host-only nonce cookie is HttpOnly, SameSite=Lax and Secure when HTTPS. Duplicate/mismatched callback values are rejected. Callback responses use bounded categories; credentials are not returned. Reset requires its confirmation header; it is not a substitute for operator authentication if the server is exposed beyond loopback.

`Use-DevelopmentInfrastructure.ps1` clears its managed inherited environment values before resolving dotenv through Compose, restores temporary values on failure, retains Base64 padding, and does not print resolved secrets. Existing key-validation tests and callback/restart regressions were run. Port-8080 conflicts and alternate port use are documented. This audit did not run interactive login, reset the running session or revalidate live credentials. The submitted checklist ends at `AE.4 callback`; no unseen requirements after that fragment can be claimed covered.

Canonical instrument namespace is `KiteBrokerIdentity.BROKER_ID = "ZERODHA"`. CSV mapper, holdings/order/trade/position resolution, WebSocket subscriptions and packet normalization use that value. Remaining production `"KITE"` strings are public authentication/status labels, not BrokerInstrumentId construction. Profile response parsing uses literal `"ZERODHA"` for the upstream broker identity, also not an alternate instrument namespace. Tests contain synthetic IDs and historical namespace rejection cases. Registry replacement builds and validates an immutable snapshot before swapping it, with conflict rejection and token + exchange/symbol indexes. Unresolved universe entries retain empty resolution, never synthetic IDs.

Trading-read adapters use `/orders`, `/trades`, `/portfolio/positions`, `/portfolio/holdings`, `/user/margins`; required fields, numerical bounds, timestamp parsing, enums, duplicates, malformed/oversized bodies and transport/auth errors have regression coverage. Exact instrument identity is checked. Diagnostic read responses contain normalized account data, so their loopback/Origin/proxy/no-store guards matter. Error responses do not expose upstream payloads.

## Universe and diagnostics (E, X)

The canonical root file is unchanged by this audit: 1,111 data rows, 1,111 normalized unique exchange/symbol keys, zero duplicates, 1,111 enabled, zero disabled. Module shadow file is absent. Importer normalization-before-deduplication, identical duplicate collapse, conflicting duplicate rejection and synthetic unresolved fixtures remain tested.

Default path now chooses an ancestor repository by Maven wrapper + root POM + `apps/trading-core` markers, then root `universe.csv`; an unrelated outer ancestor CSV cannot override it. `UNIVERSE_DIAGNOSTIC_PATH` still takes precedence. Search is bounded to eight ancestors; packaged deployment outside a repository requires an explicit path.

An already initialized application was listening on 127.0.0.1:8080. A GET to `/api/development/universe/validate` returned:

```json
{"inputRows":1111,"unique":1111,"duplicatesRemoved":0,"enabled":1111,"disabled":0,"resolved":1111,"unresolved":0}
```

This proves resolution against that running process's snapshot, not that it runs the audit edits or that every instrument is currently exchange-tradable. No live instrument refresh was requested. Validation calls only importer/registry reads; it neither persists membership nor subscribes/evaluates/proposes/risk-approves/arms/executes. Returned summary, unresolved exchange/symbol and lookup exchange/symbol/segment expose no broker tokens or account credentials.

| Development diagnostic | Activation | Access/behavior |
|---|---|---|
| `/api/development/universe/{validate,unresolved,lookup}` | development and not production; universe.diagnostic.enabled=false default | Loopback peer/local server name; rejects forwarded headers; GET/reference reads. Browser/cache/result-size caveats above. |
| `/api/development/trading-read/{orders,trades,positions,holdings,margins}` | development and not production; explicit read + diagnostic flags | Strict local Host/peer, proxy/Origin/fetch-site checks, GET-only, no-store; normalized account reads, no orders sent. |
| `/api/development/market-data/{start,subscriptions,subscriptions/{id},stop,status,latest}` | development and not production after fix; explicit enabled + diagnostic flags | Local peer/name, proxy/Origin/fetch-site guarded after fix; no-store. POST start/subscriptions/stop and DELETE subscriptions/{id} mutate desired subscriptions; GET status/latest read data, unlike universe validation which is wholly read-only. |

No development execute endpoint exists. Authentication endpoints are separate from development diagnostics and are not profile-gated in the same way.

## Market data and health (F-G, AB-AC)

Kite credentials remain inside infrastructure and URL logging is avoided. The decoder accepts implemented LTP/QUOTE/FULL packet lengths (8/28/32/44/184 bytes), validates a frame before publishing, handles fragmentation and bounds frame/packet/queue sizes. Defaults: 4,096 queued events, at most 3,000 desired subscriptions; reconnect backoff 1-30 seconds, eight consecutive attempts. Successful connects reset the attempt counter, so this is not a lifetime retry cap. Close cancels scheduled work; old connection epochs and mode revisions are checked, with the remaining publication race described above.

Subscription/packet normalization is symmetric through the canonical broker namespace and registry identity. Desired/active subscription sets, latest tick timestamps and degradation reasons govern health. Heartbeats update last-message time, not tick freshness. CONNECTED alone cannot be FRESH. Missing/stale/degraded/stopped data fails risk/execution gates. Full universe import does not change subscriptions. No production domain/application direct `Instant.now()`, `LocalDateTime.now()` or `System.currentTimeMillis()` calls were found in the scan; deterministic paths use Clock (arming takes Instant explicitly).

## Orders, risk, strategy and recovery (I-Q)

Platform OrderId is authoritative; broker ID is attached metadata, and correlation is generated/persisted before submission. BrokerCorrelationId is bounded 20-character alphanumeric, random, immutable in the aggregate, unique in PostgreSQL and mapped unchanged to Kite tag. Broker tags are trusted only on exact equality with a persisted correlation; absent historical tags remain absent. No symbol/quantity/time heuristics are used to recover orders.

Place validates then claims idempotency with a deterministic command fingerprint. PostgreSQL owns uniqueness and version CAS. The explicit transaction fix makes idempotency claim + order insertion atomic even for manually constructed fixtures; a failed order insert rolls the claim back. Public repository/record constructors remain trusted internal boundaries, not user-facing arbitrary setters. Application modify/cancel now always return DISABLED: their previous path lacked command-specific safety and cancellation acknowledgement was incorrectly terminal. Adapter serialization tests remain, but adapter availability is not permission to call it.

Risk evaluates VALIDATED orders and atomically records APPROVED/REJECTED plus lifecycle under a REQUIRES_NEW transaction, account advisory lock and order-row lock. It never executes. Cash rules check activation/emergency stop, registry/tick health/age, cash instrument constraints, lot/tick alignment, quantity/value/position/exposure/open-order/margin limits. BigDecimal valuation uses the conservative reference with configured buffer; spendable margin uses the minimum available cash/opening/live/net less nonnegative debits, payout and holding-sales adjustments. This can understate margin; it does not deliberately add collateral as spendable cash. Separate broker snapshots and external orders prevent a global instantaneous guarantee. The 100-capacity/two-60-order scenario is rejected conservatively by serialized outstanding-order checks, not a general allocation engine.

Strategy identity is `(strategy_id, strategy_version, event_key)` with durable replay. The replay fix uses the persisted signal's instrument/side/quantity/time and original intent decision. Signal, intent, order proposal, risk approval and execution remain distinct. ReferenceThresholdStrategy is reference logic, not a production strategy; no scheduler/listener executes approved orders. Strategy may request risk only when the optional risk service is present.

Reconciliation matches persisted broker ID first, then exact persisted correlation; otherwise ambiguous. It checks order characteristics, now including disclosed/trigger/regular-variety, and fill instrument/side/product. Conflicting duplicate fills and total quantity overflow/excess fail closed before fill persistence. A terminal cancellation with partial fills remains CANCELLED. Recovery may advance SUBMITTING directly to observed filled/partial/cancelled states; terminal orders still cannot be reopened. The store's audit/fill/state update is now explicitly transactional. Across different historical observations, the trade table's ON CONFLICT DO NOTHING still retains the first observed fill; immutable upstream trade identities remain an assumption.

Crash windows: death after SUBMITTING but before HTTP leaves ambiguity; accepted request with lost/malformed reply remains SUBMITTING; death after reply but before identity persistence also remains recoverable by persisted correlation. No automatic resubmission occurs. Reconciliation performs reads/state recovery only. Emergency stop does not issue panic cancellations.

## Execution authorization (R-V)

Production PLACE path: `OrderApplicationService.executeRiskApproved -> ExecutionSafetyPolicy -> durable authorization audit -> order version CAS to SUBMITTING -> KiteOrderAdapter -> acknowledgement -> broker identity/SUBMITTED CAS`.

All gates are independent: enabled; runtime arm; emergency stop off; RISK_APPROVED state; existing APPROVED risk; matching order ID and pre-approval version; current risk policy version; unexpired/nonfuture approval; enabled/authenticated/usable session; known allowlisted platform InstrumentId; quantity cap; persisted correlation; no local SUBMITTING exposure; FRESH health with NONE reason; positive latest price; nonfuture/nonstale receive/exchange timestamp; positive computed notional within cap. Missing caps/age/allowlist deny. No automatic risk reevaluation occurs. Notional is quantity times limit price or current market reference; MARKET has no guaranteed fill-price ceiling/slippage bound.

Risk age and arm expiry are exclusive: exactly at expiry is denied. Authentication success, fresh ticks, strategy output and risk approval do not arm. Arms are memory-only, explicitly set, bounded to one hour and absent after recreation/restart. CAS prevents a stale authorization for the same order from submitting, including when state/version changes between audit and CAS.

Audit is a separate durable insert before CAS, with decision/reason/time/order/version/policy version. If insertion fails, execution does not reach CAS/HTTP. If CAS fails, ALLOWED remains valid evidence of the earlier authorization only. It does not claim submission, acceptance or success. A broker acknowledgement likewise is not a fill. See [official order semantics](https://kite.trade/docs/connect/v3/orders/). Current correlation/tag, form fields and route mapping were reviewed against those docs without calling the API.

The single-account reconciliation gate is precisely `EXISTS orders WHERE state='SUBMITTING'`. Historical audit rows and terminal orders do not block this gate. It does not cover every conceivable unresolved order state or multiple accounts. Safe startup stays DISABLED + DISARMED, emergency stop true, empty allowlist, zero caps/ages. No source production call arms or automatically executes.

### Local HTTP-boundary evidence

Real PostgreSQL, real OrderApplicationService/ExecutionSafetyPolicy/audit store/KiteOrderAdapter, synthetic sessions/market/reference data, persisted risk/correlation, loopback fake HTTP server. No production host is accepted by the fixture.

| Denial case | Persisted bounded reason |
|---|---|
| disabled | EXECUTION_DISABLED |
| unarmed / expired arm | DISARMED |
| emergency stop | EMERGENCY_STOP |
| missing authentication | AUTHENTICATION_UNAVAILABLE |
| absent/rejected risk | RISK_APPROVAL_MISSING |
| expired risk | RISK_APPROVAL_EXPIRED |
| risk version mismatch | ORDER_VERSION_CHANGED |
| empty allowlist | INSTRUMENT_NOT_ALLOWED |
| quantity cap | QUANTITY_CAP_EXCEEDED |
| notional cap | NOTIONAL_CAP_EXCEEDED |
| missing/degraded market data | MARKET_DATA_UNAVAILABLE |
| stale market data | MARKET_DATA_STALE |
| missing correlation | CORRELATION_MISSING |
| unresolved SUBMITTING exposure | RECONCILIATION_REQUIRED |
| wrong lifecycle | INVALID_ORDER_STATE |

Matrix assertions verify DENIED audit, expected reason, no SUBMITTING transition, zero gateway interactions and zero fake HTTP requests. Allowed case verifies authorization audit, SUBMITTING visible before HTTP, exactly one request, unchanged tag, synthetic broker identity and SUBMITTED; repeat execution adds no request. Restart reconstructs arming and denies persisted approval. Deterministic clock proves just-before versus exact expiry. TOCTOU test mutates PostgreSQL version after authorization: CAS fails, zero HTTP, ALLOWED audit remains. Concurrent test uses a barrier after both ALLOWED audits, then PostgreSQL CAS admits only one HTTP request.

## Configuration inventory (W)

Defaults below are from application.yml, not the local environment. No credential values were inspected or copied.

| Variables | Default | Requirement/sensitivity/failure behavior |
|---|---|---|
| SPRING_PROFILES_ACTIVE | development default profile | Optional/nonsecret; profile selection does not grant trading |
| SERVER_ADDRESS | 127.0.0.1 | Optional/nonsecret; exposing it increases auth/actuator reachability |
| DB_URL / DB_NAME / DB_USER / DB_PASSWORD | local PostgreSQL / trading / trading / empty | Database required for normal start; password secret; production requires explicit URL/user/password |
| REDIS_HOST / PORT / PASSWORD | localhost / 6379 / empty | Ephemeral integration; password secret; Redis health excluded |
| TRADING_MODE / ENABLE_LIVE_TRADING / EMERGENCY_STOP | PAPER / false / true | Nonsecret; live trading setting rejected by foundation safety configuration |
| KITE_REST_ENABLED | false | Optional/nonsecret; enables read/auth infrastructure, not order permission |
| KITE_API_KEY / KITE_API_SECRET / KITE_ACCESS_TOKEN | empty | Sensitive; required as appropriate for explicit auth/legacy diagnostics; API secret/token secret; application restores encrypted token |
| KITE_REDIRECT_URL | localhost:${server.port:8080}/api/broker/kite/auth/callback | Nonsecret; validate/match registered callback |
| KITE_TOKEN_ENCRYPTION_KEY | empty | Secret; valid Base64 32 bytes needed for persistence; invalid configuration unavailable/fail-closed |
| KITE_TRADING_READ_ENABLED / DIAGNOSTIC_ENABLED | false / false | Optional/nonsecret; reads only, explicit local diagnostic |
| KITE_MARKET_DATA_ENABLED / DIAGNOSTIC_ENABLED | false / false | Optional/nonsecret; no universe auto-subscription |
| KITE_MARKET_DATA_CONNECT_TIMEOUT / STALE_AFTER / IDLE_TIMEOUT | 10s / 30s / 30s | Nonsecret; validated duration settings |
| KITE_MARKET_DATA_QUEUE_CAPACITY / MAX_SUBSCRIPTIONS | 4096 / 3000 | Nonsecret; bounded positive capacity |
| KITE_MARKET_DATA_RECONNECT_INITIAL_DELAY / MAX_DELAY / MAX_ATTEMPTS | 1s / 30s / 8 | Nonsecret; bounded retry configuration |
| KITE_ORDER_EXECUTION_ENABLED | false | Nonsecret capability only; never implicitly arms |
| KITE_ORDER_EXECUTION_ALLOWED_INSTRUMENTS | empty | Nonsecret platform UUIDs; empty denies, malformed UUID fails binding |
| KITE_ORDER_EXECUTION_MAX_QUANTITY / MAX_NOTIONAL | 0 / 0 | Nonsecret; zero denies, negative invalid |
| KITE_ORDER_EXECUTION_RISK_DECISION_MAX_AGE / MARKET_DATA_MAX_AGE | 0s / 0s | Nonsecret; zero denies, negative invalid |
| RISK_ENABLED | false | Nonsecret; disabled cannot approve |
| RISK_MAX_ORDER_QUANTITY / MAX_ORDER_VALUE / MAX_POSITION_QUANTITY / MAX_EXPOSURE | 0 each | Nonsecret; unconfigured denies |
| RISK_MARKET_DATA_MAX_AGE / REGISTRY_MAX_AGE | 0s / 0s | Nonsecret; unconfigured denies |
| RISK_PRICE_BUFFER / CASH_RESERVE | 1.0 / 0 | Nonsecret; independent limits still required |
| UNIVERSE_DIAGNOSTIC_ENABLED / PATH | false / empty | Optional/nonsecret; empty path means deterministic repository root; override explicitly selects file |

There is no persisted runtime ARM variable. The development dotenv helper manages only a subset of Kite variables: it must not be mistaken for a complete execution-configuration sanitizer. Ordinary defaults remain deny even if REST authentication succeeds. No dependency upgrade was performed.

## Migrations, errors, logging and tests (Y-AD)

V1 namespace; V2 encrypted tokens; V3 hashed login attempts; V4 order/idempotency ledger; V5 risk decisions; V6 reconciliation audit/fills; V7 nullable historical correlation with uniqueness; V8 strategy evaluation identity; V9 execution authorization audit. Audit/decision tables reference orders. Idempotency is claimed before order creation and relies on the transaction rather than an immediate foreign key. Order versions/timestamps are persisted. No raw broker bodies or plaintext credentials belong in these schemas.

Disposable PostgreSQL tests apply all migrations from empty databases, validate checksums/restart, and now explicitly migrate through V8 then V9. They do not run migrations on the user's database. Authentication restart uses real PostgreSQL with fake broker endpoints. Test cleanup truncates dependent audit/fill/decision tables together before orders. PostgreSQL JDBC timezone is set to UTC only inside isolated integration fixtures and restored afterward.

Bounded authentication/read/order/reconciliation failure categories distinguish invalid response, authentication, transport, broker rejection, ambiguity and conflict. Malformed PLACE acknowledgements now preserve uncertainty. Generic broker payloads/exception causes are not exposed through read diagnostics. The remaining auth-text concern is called out separately, not hidden behind scanner success.

Order/risk/reconciliation/execution metrics use bounded outcome/reason/operation labels. Execution authorization, denied, armed and disarmed counters exist. No instrument/order/token/correlation labels found; strategy identifier caveat remains. Resource review covered scheduler shutdown, bounded queue worker, epoch/revision fencing, reconnect-stop handling and the nonblocking immutable session view used by WebSocket callbacks. Absolute thread-leak freedom and every possible callback interleaving were not established by this suite.

## Verification record

Baseline: 923 unit + 39 PostgreSQL integration tests, no failures/errors/skips. Added regressions first reproduced order mutation bypass/malformed acknowledgement, future freshness/unbounded arm, repository-root path, diagnostic access/profile, strategy replay and three reconciliation failures. A real application startup additionally reproduced missing beans and final-class proxy failure. Narrow fixes were followed by targeted tests and full verification. Final counts and git status follow.


Final validation of the edited tree:

| Check | Result |
|---|---|
| `./mvnw.cmd -Pintegration verify` | PASS: 937 unit + 40 PostgreSQL integration tests, zero failures/errors/skips |
| Local policy-enabled fake broker suite | PASS: 13 integration tests including denial matrix, concurrent barrier/CAS, restart, expiry, malformed-response uncertainty |
| Fresh database + V8-to-V9 upgrade + real Spring startup | PASS; required order/policy/audit/store/coordinator beans present, runtime disarmed |
| Dependency convergence | PASS (Enforcer standalone check); no dependency changes |
| `uv run --locked pytest` in strategy-engine | 14 passed |
| `uv run --locked ruff check .` in strategy-engine | PASS |
| `uv run --locked mypy src` in strategy-engine | PASS: 4 source files |
| `uv run --project apps/strategy-engine --locked python scripts/verify-project.py` | PASS |
| `uv run --project apps/strategy-engine --locked python scripts/check-secrets.py` | PASS: 575 text files, zero potential secret locations |
| `git diff --check` | PASS |
| Existing local initialized registry validation | 1111 resolved, 0 unresolved; canonical CSV unchanged |

Local verification logs are under `apps/trading-core/target/`: `audit-verification.log` (baseline), `audit-dependencies.log`, `audit-repro.log`, `audit-freshness-repro.log`, `audit-diagnostic-repro.log`, `audit-strategy-repro.log`, `audit-wiring-repro.log`, `audit-wiring-fix.log`, `audit-reconciliation-repro.log`, `audit-reconciliation-fix.log`, and `audit-final-verification.log`. Build logs are ignored local artifacts; repro logs intentionally contain failing test runs before correction. Final Surefire/Failsafe reports correspond to the passing run.

Safety scope: audit-triggered broker operations used fake/mock/loopback infrastructure. The only request to the existing user application was the local read-only universe validation. Official documentation was consulted on kite.trade; no production Kite API/order request was issued by the audit. This is tool/fixture evidence, not a machine-wide packet capture or an assertion about pre-existing process traffic. No real execution flag was enabled, no real order placed/modified/cancelled, no new subscriptions/strategies activated, no commit, and no push. The existing running app was not restarted onto these edits.

Five tracked Python bytecode files changed by test execution were restored to their initial versions; no unrelated user changes existed at baseline or were discarded. HEAD remains 143ec86; committed ahead/behind remains 0/0. Working tree intentionally contains the documented uncommitted fixes and tests.

## Final git status --short (also the complete changed-file inventory)

```text
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresMigrationTest.java
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresOrderRepositoryTest.java
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/LocalKiteOrderExecutionIntegrationTest.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteMarketDataDiagnosticController.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/instrument/application/UniverseValidationService.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/ExecutionSafetyPolicy.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/OrderApplicationService.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/application/RuntimeExecutionArming.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/domain/OrderState.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/order/infrastructure/OrderConfiguration.java
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
 M apps/trading-core/src/test/java/com/kitehybrid/platform/instrument/application/UniverseValidationServiceTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/order/OrderApplicationServiceTest.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/reconciliation/OrderReconciliationServiceTest.java
?? apps/trading-core/src/test/java/com/kitehybrid/platform/order/ExecutionFreshnessAuditTest.java
?? apps/trading-core/src/test/java/com/kitehybrid/platform/strategy/StrategyReplayAuditTest.java
?? docs/operations/end-to-end-audit-143ec86.md
```
