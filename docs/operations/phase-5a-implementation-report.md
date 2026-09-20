# Phase 5A — Kite trading read APIs

Implemented on `develop` in `D:\Yogendra\kite-hybrid-platform`. No commit or push.
Automated validation uses fake Kite responses; no real Kite API was called.

## 1. Architecture discovered

The existing modular Java application has one `KiteSession`, owned by the
interactive authentication use case and encrypted PostgreSQL token store. Profile
validation and instrument initialization already occur through that owner. Existing
REST transport fixes the broker origin, permits only named GET routes, bounds
compressed/decompressed bodies, and sanitizes errors. Market data has its own
existing session view; that architecture and `KiteSession` are unchanged.

`BrokerAdapter.submit(OrderIntent)` is an unwired execution port; `OrderState`
belongs to internal order processing. No position/portfolio implementation or
shared Money/Quantity classes existed. This phase uses the existing BigDecimal,
integral quantity, UTC Instant and shared InstrumentId conventions. Risk, order
state/repository, instrument identity/registry, authentication and market-data
implementations remain unchanged. See [architecture](../architecture/kite-trading-read.md).

## 2. Files created

All Java paths below are under `apps/trading-core/src/`.

Under `main/java/com/kitehybrid/platform/broker/application/read/`:

- `BrokerOrdersProvider.java`
- `BrokerTradesProvider.java`
- `BrokerPositionsProvider.java`
- `BrokerHoldingsProvider.java`
- `BrokerMarginsProvider.java`

Under `main/java/com/kitehybrid/platform/broker/domain/read/`:

- `BrokerOrder.java`
- `BrokerTrade.java`
- `BrokerPosition.java`
- `BrokerPositions.java`
- `BrokerHolding.java`
- `BrokerMargins.java`
- `TradingReadTypes.java`
- `ReadModelValidation.java`

Under `main/java/com/kitehybrid/platform/broker/infrastructure/kite/`:

- `KiteTradingReadAdapter.java`
- `KiteTradingReadMapper.java`
- `KiteTradingReadConfiguration.java`
- `KiteTradingReadDiagnosticController.java`

Under `test/java/com/kitehybrid/platform/`:

- `TradingReadArchitectureTest.java`
- `broker/infrastructure/kite/KiteTradingReadAdapterTest.java`
- `broker/infrastructure/kite/KiteTradingReadMapperTest.java`
- `broker/infrastructure/kite/KiteTradingReadFixtures.java`
- `broker/infrastructure/kite/KiteTradingReadConfigurationTest.java`
- `broker/infrastructure/kite/KiteTradingReadDiagnosticControllerTest.java`

Documentation:

- `docs/architecture/kite-trading-read.md`
- `docs/runbooks/kite-trading-read.md`
- `docs/operations/phase-5a-implementation-report.md`

Total: 26 files (17 production Java, 6 test/fixture Java, 3 documentation).

## 3. Files modified

- `.env.example`: documents two disabled opt-in flags.
- `README.md`: links the new read phase/runbook.
- `config/README.md`: configuration, limits and operational controls.
- `apps/trading-core/src/main/resources/application.yml`: disabled read/diagnostic defaults.
- `apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteRestTransport.java`: five additional fixed GET routes and per-route byte budgets.
- `apps/trading-core/src/test/java/com/kitehybrid/platform/KiteApplicationSafetyTest.java`: diagnostics absent under ordinary safe startup.
- `apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteAuthenticationRestartIntegrationTest.java`: fake read transport and restored-session integration coverage.

No dependency, migration, token source or execution path was added.

## 4. Ports and models

Five parameterless read ports return immutable order/trade lists, net/day positions,
holdings and typed account margin segments. Nested records represent position side
totals, margin-funded holdings and available/utilised margin components. Types cover
side, order type/status, product, validity, variety and margin segment. Broker
observations remain separate from platform orders/positions and trading permission.

## 5. Kite endpoints

| GET endpoint | Operation | Maximum compressed and decompressed body |
| --- | --- | --- |
| `/orders` | Current-day order book | 4 MiB |
| `/trades` | Current-day fills | 4 MiB |
| `/portfolio/positions` | Net/day positions | 4 MiB |
| `/portfolio/holdings` | Holdings | 4 MiB |
| `/user/margins` | Equity/commodity account balances | 64 KiB |

Current official documentation reviewed: [orders/trades](https://kite.trade/docs/connect/v3/orders/),
[portfolio](https://kite.trade/docs/connect/v3/portfolio/),
[user margins](https://kite.trade/docs/connect/v3/user/),
[errors](https://kite.trade/docs/connect/v3/exceptions/).
No margin-calculation POST, per-order history or trading mutation is integrated.

## 6. Response normalization

JSON stays inside Kite infrastructure. Required field types are strict; missing
values never become zero. Unknown enum strings become UNKNOWN; unknown account
margin segments fail. Exact decimals are bounded to precision 36, scale -18..18 and
magnitude below 10^18. Quantities are exact int64 values, positive/nonnegative where
appropriate; positions can be signed. Multiplier is a positive integer. Prices
are nonnegative. Nullable IDs/timestamps and MTF data retain explicit absence.

Kite local timestamps become UTC; time-only trade order timestamps are omitted.
Each mapped collection uses one registry snapshot and cross-checks uint32 broker
token against exchange/symbol. No identity is generated. Duplicate natural rows,
conflicting mappings and inconsistent order counters fail the entire response.
Documented pending/cancelled overlap is allowed. Broker P&L is copied, never computed.

Empty order/trade/holding lists and empty net/day position lists are valid. Margins
require both complete equity/commodity segments, including explicit disabled ones.
JSON depth/string/number/node/array limits, duplicate-key and trailing-document
checks supplement transport byte limits. See the architecture document for values.

## 7. Error handling

Existing safe categories: AUTHENTICATION, CONFIGURATION, BROKER_API, TRANSPORT,
INVALID_RESPONSE. No upstream messages, credentials, payloads or causes escape.
Missing/unverified/expired session fails before HTTP. Existing 401/403 and token
error handling invalidates the same session. No redirects, automatic retries,
partial results or silent row skipping occur. Durable session ownership is retained.

## 8. Observability

Every adapter call records `kite.rest.operations` counter and `kite.rest.duration`
timer, including rejected authentication and normalization failures. Tags are only
the five bounded operation names and success/safe error category. Logs contain
operation/result only; no user, order, trade or instrument IDs, values, credentials
or raw exception details. Latency includes session wait, transport and mapping.

## 9. Diagnostic endpoints

GET `/api/development/trading-read/{positions,holdings,margins,orders,trades}`.
Requires `development` with `production` absent, plus both
`KITE_TRADING_READ_ENABLED=true` and `KITE_TRADING_READ_DIAGNOSTIC_ENABLED=true`.
Both default false. Numeric loopback peer and local Host are required. Origin,
cross-site and forwarded/proxy requests are rejected. Non-GET methods do not call
providers. Responses have no-store headers and normalized models/safe errors only.
There is no startup read or background polling.

## 10. Executed validation

Final verification used installed JDK 21.0.12, Maven Wrapper 3.9.11, Python 3.12.14
and working Docker. No JDK or Docker installation/global environment change occurred.

| Check | Final result |
| --- | --- |
| Java full unit suite, including architecture | 875 tests; 0 failures, errors or skips |
| Named Java architecture suites | 5 tests across ArchitectureTest, MarketDataArchitectureTest and TradingReadArchitectureTest; all passed (included above) |
| PostgreSQL integration suite | 16 tests; 0 failures, errors or skips |
| Python full suite, including architecture | 14 tests; all passed |
| Project/configuration verification | Passed |
| Secret scanner | 0 potential secret locations |
| `git diff --check` | Passed |
| Real Kite API | Not run |

The new Java suites contain 338 cases: adapter 158, mapper 106, diagnostics 69,
configuration 2 and architecture 3. Coverage includes valid and empty snapshots,
malformed JSON, missing fields, unknown enums, numeric bounds/integer multipliers,
mapping conflicts, duplicates, authentication failures, broker 4xx/5xx, transport
errors, compressed/uncompressed body limits, safe logging, HTTP guards and module
boundaries. Integration adds one restored-session test spanning all five read ports,
including valid and incomplete margins. There are no real broker calls in tests.

Commands executed from the repository root:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd -Pintegration verify
uv run --project apps/strategy-engine --locked pytest apps/strategy-engine/tests
uv run --project apps/strategy-engine --locked python scripts/verify-project.py
uv run --project apps/strategy-engine --locked python scripts/check-secrets.py
git diff --check
```

The final integration-profile run repeated the complete unit suite against the
final code, then ran integration tests and built both executable/plain JARs.
Reports: `apps/trading-core/target/surefire-reports` and
`apps/trading-core/target/failsafe-reports`. Validation log: `tmp-phase5a-final.log`
(ignored). Python-generated tracked bytecode was restored to its initial content;
no unrelated generated files remain in the diff.

## 11. Controlled real read-only test

Exact PowerShell startup, authentication check, all five reads, metrics and shutdown
commands are in the [manual runbook](../runbooks/kite-trading-read.md). It uses the
existing encrypted token and official login; there is no token copying or second
access-token source. This live procedure has not been executed in this phase.

## 12. Remaining limitations

- No real account validation was performed; all broker automated tests are mocked.
- Current-day broker observations are not a durable historical order/trade ledger
  or authoritative platform position state. Five reads are not an atomic snapshot.
- Models intentionally project supported core fields, not every broker field.
  Unknown enums remain UNKNOWN; new schema/segment shapes may require an update.
- Registry mapping is required. Expired/delisted instruments absent from its current
  snapshot fail safely; no historical lookup or registry-age guarantee is added.
- Fixed response/row bounds may reject unusually large accounts. No pagination,
  backfill, polling, retry, local P&L, currency conversion or margin calculation exists.
- Diagnostics are local developer tooling without multi-user authorization; keep
  loopback binding and avoid reverse proxies or storing account output in logs.

## 13. Final working-tree status

Changes are uncommitted and unpushed on `develop`.

```text
 M .env.example
 M README.md
 M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteAuthenticationRestartIntegrationTest.java
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteRestTransport.java
 M apps/trading-core/src/main/resources/application.yml
 M apps/trading-core/src/test/java/com/kitehybrid/platform/KiteApplicationSafetyTest.java
 M config/README.md
?? apps/trading-core/src/main/java/com/kitehybrid/platform/broker/application/read/
?? apps/trading-core/src/main/java/com/kitehybrid/platform/broker/domain/
?? apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteTradingReadAdapter.java
?? apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteTradingReadConfiguration.java
?? apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteTradingReadDiagnosticController.java
?? apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteTradingReadMapper.java
?? apps/trading-core/src/test/java/com/kitehybrid/platform/TradingReadArchitectureTest.java
?? apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteTradingReadAdapterTest.java
?? apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteTradingReadConfigurationTest.java
?? apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteTradingReadDiagnosticControllerTest.java
?? apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteTradingReadFixtures.java
?? apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteTradingReadMapperTest.java
?? docs/architecture/kite-trading-read.md
?? docs/operations/phase-5a-implementation-report.md
?? docs/runbooks/kite-trading-read.md
```
