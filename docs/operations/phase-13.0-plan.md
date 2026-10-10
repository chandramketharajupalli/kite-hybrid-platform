# Phase 13.0 implementation and test plan

Plan frozen before production edits, 2026-10-10. Baseline: clean `develop`, HEAD
and `origin/develop` both `ac87a96df4ff0ab0484cc08f9ac3a1aec7be5b61`.

## Scope and invariants

NSE cash INTRADAY/MIS read reliability only. Reuse the existing transport,
session, encrypted store, market-data gateway, funding domain and historical
gate. No second client, execution surface, policy relaxation or migration.
Real Kite request budget is **zero**, including profile, calculator, candles
and WebSocket. Public documentation reads are separate from account API calls.
Only synthetic credentials, fake HTTP/loopback sockets and Testcontainers DBs
are permitted. Do not load `.env` or connect to the development DB/token store.

No operational order dispatch (live or paper), HALT resume, arm, permit creation
or strategy execution. The explicitly requested existing regression suites
include simulated execution/permit/HALT fixtures in isolated processes; those
are test coverage, never an operational authorization. The new read rehearsal
must stay halted with zero dispatch/permits. Preserve production defaults,
cash reserve, limits and the INR 10,000 first-live buffered-notional ceiling.
Do not access sealed July bars or acquire any real corpus. Preserve all tracked
Phase 11.4 through 12.1C research evidence, H1/H2 G1 and the 18 source hashes.
Phase 12 five-member certification remains unresolved and acquisition BLOCK.

## Inspection and gap matrix

Paths below are under `apps/trading-core/src/main/java/com/kitehybrid/platform`.

| Component / current path | Observed behavior or gap | Minimal change / safety effect | Acceptance checks |
| --- | --- | --- | --- |
| `broker/application/auth/KiteLoginAttemptUseCase`, `broker/infrastructure/kite/KiteAuthenticationController`, `PostgresKiteLoginAttemptStore` | Browser nonce/state binding; 10-minute attempt; SQL atomic DELETE consume; existing missing-cookie/replay/restart/concurrency tests | Retain interactive login, CSRF, expiration and one-use semantics | Auth cookie/use-case and disposable store/restart suites |
| `KiteAuthenticationUseCase`, `KiteSession`, `PostgresKiteAccessTokenStore` | AES-256-GCM; restore validates profile; transient restore preserves DB token; `status()` calls reconciliation that can delete it | Make status a passive observation; keep deliberate restore/reset cleanup; no token values in diagnostics | Status after rejection/expiry performs no store writes; restore cleanup retained; restart, wrong key, expiry, concurrent session tests |
| `KiteRestTransport` | Fixed GET routes; separate calculation POST; 10s connect / 30s read; compressed and decoded limits; no redirects/retries. Token envelope detector uses permissive mapper and ignores status field | Strict duplicate/trailing parsing and require error envelope before auth invalidation. Preserve explicit 401/403 and valid TokenException contract, including existing unexpected-status behavior | Duplicate/trailing/false-success envelopes cannot invalidate; 401/403, 429, 5xx, malformed UTF-8/gzip, timeout/connectivity, payload bounds; exactly one allowed request |
| `KiteTradingReadAdapter`, `KiteTradingReadMapper`, `KiteProfileAdapter` | Strict account mapping; net/day distinct; segments equity/commodity; MIS/CNC normalized; COMPLETE -> FILLED; `CashAccountCapacity` excludes FILLED/CANCELLED/REJECTED | Retain mapping; supplement terminal/summary assertions where missing | Null/missing amounts, precision, dates, duplicate IDs, empty trades, partial/rejected orders, safe summaries |
| `KiteOrderMarginAdapter`, `risk/domain/IntradayAccountCapacity` | Calculation-only `/margins/orders`; exact quote; no authoritative collateral terms | Add pure, redacted funding evidence view using existing equations; label OBSERVED/DERIVED/BROKER_AUTHORITATIVE/UNKNOWN. No new HTTP endpoint; NOT_READY when evidence unresolved/stale/auth lost | Cash equality/paisa, charges/margin increase, stale/auth loss, unknown terms, no account values in view, unchanged CNC; existing notional/exposure tests |
| `KiteMarketDataAdapter`, `JdkKiteWebSocketTransport`, latest store | Bounded queue and reconnect, epoch/session fencing, subscription restoration, idle watchdog and stale health. Text auth detector also uses permissive JSON | Strict JSON auth-envelope parsing; malformed text must not delete session; retain handshake/close rejection behavior | Loopback handshake/reconnect, dedup/modes, malformed/oversize, stale/out-of-order, heartbeat, overflow/backpressure, max subscriptions, exhaustion and preflight denial |
| `KiteHistoricalAdapter`, `historical/application/HistoricalContinuityGate`, ingestion/repository | Strict minute-start, ZERODHA identity, half-open windows, 1 start/s process pacing, one-day chunks, historical auth preserves session; multi-instrument gate before delegates | Retain contracts; regress shared transport changes against historical auth and certification denials | Fake provider, UTC/IST, exact decimals, duplicates/trailing, immutable replay/provenance/cutoffs, unresolved gate zero provider and repository interactions |
| `health/TradingStatusEndpoint`, `KiteStatusEndpoint`, market-data status, configuration | tradingstatus always ready=false; separate health probes; diagnostics default off; loopback/dev guards | Retain default exposure and hard false readiness; document actual routes and status side effects | Default unavailable endpoints, profile mismatch, missing DB, unauthenticated/stale/HALT status; no resume |
| Runtime HALT, risk/preflight and reconciliation | Existing denial/revalidation/response-loss tests, strategy and read-only architecture fences | Reuse suites; add read-only boundary guard if needed; no execution implementation changes | Full unit/architecture and full disposable integration including operator/reconciliation suites |

## Synthetic error matrix and expected results

* Missing/expired/mismatched/replayed login state: deny before exchange. Concurrent
  consume: one winner. Invalid encryption key: bounded storage error, no secret.
* 401/403 or a valid error TokenException: ordinary read invalidates in-memory
  session; passive status preserves durable row. Explicit restore retains cleanup
  semantics. Historical read failure preserves shared session and durable token.
* 429/5xx without valid auth rejection, timeout, connection loss or malformed
  JSON: fail closed without token invalidation or automatic retry. The existing
  valid TokenException override is retained and documented, not treated as a
  generic transient error. Duplicate/trailing envelopes are not trusted.
* Margin/charge growth, stale/mismatched quote, missing authentication, unknown
  collateral/cash eligibility or oversized buffered notional: no readiness or
  dispatch. Cash-only eligibility remains a separate existing funding decision.
* Missing ticks, stale data, overflow, malformed frames or reconnect exhaustion:
  unhealthy market data and denied preflight; heartbeat is not freshness.
* Missing/mismatched/unresolved certificate: zero historical provider calls and
  writes. Valid synthetic certificate permits fake-provider work only.

## Validation commands and isolation

Record installed JDK 21/Maven wrapper/uv/Docker versions. Run commands in a child
PowerShell with ambient deployment/JVM-option environment variables removed,
without printing their values; never source a diagnostics/dotenv helper.

```powershell
.\mvnw.cmd '-Dtest=KiteRestReadTest,KiteAuthenticationUseCaseTest,JdkKiteWebSocketTransportTest,KiteHistoricalAdapterTest,IntradayFundingEvidenceTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

Full integration is mandatory because shared auth/transport changes. Reuse
Testcontainers PostgreSQL/Redis and fake transports; report actual XML counts,
failures/errors/skips, and infrastructure failures distinctly. Python sources
are not planned to change; if they do, run full pytest/Ruff/strict mypy.
Run `run_phase116.verify_freeze` directly, never its evaluation CLI. Recheck
freeze SHA, all 18 sources and protected tracked evidence against baseline.

## Rollout, recovery and stop conditions

This is an uncommitted local review; no deploy, commit or push. No application
startup against real configuration. Synthetic test contexts own shutdown of
sockets, schedulers and containers. Stop on any baseline/freeze mismatch,
unexpected external request, development DB target, secret disclosure, safety
default change or unexplained failure. Do not weaken acceptance to obtain PASS.
Record incomplete work as PARTIAL or a blocking safety/technical issue as BLOCKED.
Any future real read needs a separate endpoint/call-budget/token-preservation
plan and explicit operator authorization; none is needed for this plan.
