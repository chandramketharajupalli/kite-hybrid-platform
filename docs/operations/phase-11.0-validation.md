# Phase 11.0: intraday historical market-data foundation

Result: foundation implemented and synthetic/disposable validation completed;
**real Kite acquisition remains blocked** pending authoritative timestamp semantics.
No certified real dataset was produced. This phase does not authorize trading.

## Scope and baseline

Baseline verified before inspection/editing: branch `develop`, HEAD and local
`origin/develop` both `3bb42037ce47326efbe9dd0680c55585a340c97c`, subject
`Document intraday-first funding contract`; clean working tree. No commit/push.

Reviewed README, system overview, intraday-first contract, Phase 10.8D validation,
registry/market-data/strategy architecture and persistence/timezone conventions.
Inspected registry/identity/snapshot, latest market store, gateway, strategy
coordinator, bootstrap, broker session/transport, PostgreSQL adapters and migration
tests. The boundary decision was written in
[historical architecture](../architecture/historical-market-data.md) before code.

NSE cash-equity INTRADAY/MIS research is primary. Phase 10 trading controls and
INR 10,000 full-notional ceiling are unchanged. No strategy/backtester/paper
engine, F&O, CNC expansion, Forex or Commodity support is added.

## Implemented

- Immutable broker-independent one-minute OHLCV bars, optional OI, exact decimal
  canonicalization, UTC start timestamps, bounded numeric/time validation.
- Explicit versioned Asia/Kolkata calendar evidence with confirmed sessions,
  non-trading and unknown dates; split/special session windows, no weekday fill.
- Half-open bounded queries/chunks, duplicate normalization/conflict detection,
  fixed-cutoff incomplete-bar exclusion, bounded quality reports and content hashes.
- Read-only acquisition port, explicit ingestion service and PostgreSQL repository.
  No Spring lifecycle bean, controller, scheduler, strategy or execution dependency.
- Research-only V11 migration outside default trading Flyway location. Unique
  canonical key, precise numeric columns, immutable bars and first-observation
  provenance, atomic per-chunk inserts/conflicts, deterministic restart/concurrency.
- Explicit decision and dataset cutoffs plus pinned manifest replay verification.
  Provenance available by chunk ID without raw broker data.
- Kite GET-only minute adapter and bounded transport route; authentication failure
  does not invalidate/clear the token. Existing order/read behavior is unchanged.
  Production normalization fails before HTTP on unverified timestamp semantics.

## Official evidence and unresolved provider semantics

Sources rechecked 2026-10-05:

- [Kite historical API](https://kite.trade/docs/connect/v3/historical/)
- [Kite rate limits](https://kite.trade/docs/connect/v3/exceptions/#api-rate-limit)
- [NSE timing/calendar source](https://www.nseindia.com/resources/exchange-communication-holidays)

The API documents GET historical candles, interval names, request/response shape,
offset timestamps, optional OI and continuous futures. Initial support is minute
only. Other documented intervals require explicit future alignment/session work;
none are silently locally derived. Acquisition uses continuous=0, oi=0 for cash.

Its example includes the to boundary, so canonical filtering is `[from,to)`.
The page does not explicitly settle timestamp-start semantics or current maximum
request range. Community statements were not promoted to authoritative proof.
`TIMESTAMP_SEMANTICS_UNVERIFIED` blocks the production factory; only package-private
synthetic tests exercise a stipulated start convention. No enabling config switch.
One-day chunks are an application bound, not a claimed broker maximum. One
process-wide request start/second is stricter than the documented 3/second;
cross-process credential coordination is required before real use. No retries.

Canonical semantics are precise independent of this provider limitation:
09:15 one-minute bar means [09:15,09:16); at 09:17 only closed bars through 09:16
may be consumed. Canonical closure does not prove vendor finality/availability.
Raw provider adjustment status is UNSPECIFIED; the platform makes no local
corporate-action corrections and does not claim point-in-time adjusted data.

## Isolation and real evidence

Real broker calls: **0**. Real order mutations: **0**. Real DB mutations: **0**.
Token accesses/mutations: **0**. Real application starts/arms/resumes/executions:
**0**. WebSocket connections: **0**. No `.env` or persistent environment changes.
No real SBIN probe, account review or live quantity. There is no need to read real
HALT/auth state when no runtime or real broker is used.

Unit HTTP uses MockRestServiceServer with a literal loopback origin, synthetic
credentials and request assertions allowing only GET historical paths. Existing
execution regressions use their disposable PostgreSQL/fake-broker fixtures.
Historical integration tests obtain connection coordinates only from their own
Testcontainers PostgreSQL 17.6 and opt in to both migration locations there.
The development DB is not opened, migrated or modified.

Test child processes clear ambient deployment/JVM variables and never source
dotenv/diagnostic helpers. JDK/Docker/global timezone settings are unchanged.
Integration fixture temporarily selects UTC for pgJDBC and restores it as per
existing conventions. No normal trading startup selects the research migration.

## Verification

Commands, run through an ignored child-process environment-isolation launcher:

```powershell
.\mvnw.cmd '-Dtest=HistoricalDataTest,HistoricalArchitectureTest,KiteHistoricalAdapterTest' test
.\mvnw.cmd -Pintegration '-DskipUnitTests=true' '-Dit.test=PostgresHistoricalBarRepositoryTest' verify
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

Ignored logs: `tmp/phase110-focused-unit.log`,
`tmp/phase110-focused-integration.log`, `tmp/phase110-unit.log`,
`tmp/phase110-integration.log`. No test loads real credentials.

| Check | Result |
| --- | --- |
| Focused unit/architecture | Initial 27 passed; two later parser regressions reproduced and fixed, with all 29 historical cases passing in the final full unit suite |
| Focused disposable PostgreSQL | Final rerun: 7 passed, zero failures/errors/skips, including default-migration isolation; completed 2026-10-05 21:07:26 +05:30 |
| Full unit/architecture | Final 1184 passed, zero failures/errors/skips, including strict-parser regressions |
| Full integration verification | BUILD SUCCESS: 1182 unit/architecture + 500 integration tests, zero failures/errors/skips; 14m29s, completed 2026-10-05 21:03:22 +05:30, before the final parser-only hardening |
| Project verifier / secret scan | PASS; 685 files scanned, zero findings |
| Diff check | PASS |

The first focused unit run caught double URL encoding of request timestamps.
The transport now leaves encoding to RestClient once; the exact decoded query
is asserted. The corrected focused run passed. No test was relaxed to accept
incorrect timestamps. No real provider request was made during diagnosis.

Final review added two malformed-input tests. Both reproduced acceptance of an
invalid calendar date (SMART date resolution) and a trailing JSON document. The
new historical adapter now uses strict date resolution and rejects trailing
tokens. The reproduction log is `tmp/phase110-parser-reproduction.log`. The full
unit suite and focused historical integration suite are rerun after this change;
the completed 500-test execution/database integration run above preceded it.
An additional assertion verifies default Flyway reaches V10 without creating
historical tables. Existing execution code and shared transport are unchanged
by this final parser correction.

Coverage includes exact/scale-independent decimals; invalid OHLC/volume/OI and
alignment; half-open chunk boundaries; confirmed gaps, unknown/holiday/special
dates; sorted identical duplicates and conflicting duplicates; incomplete bars;
GET route/zero mutations; authentication, 429, timeout, server/redirect/malformed
failures with one attempt; no token invalidation; HALT independence; migration
repeatability; exact storage; immutable replay/provenance; whole-chunk rollback;
concurrent identical/conflicting workers; restart after second chunk failure;
instrument/range isolation; both query cutoffs; late-backfill manifest rejection.

## Remaining certification work

1. Authoritative Kite candle timestamp convention and current provider range
   contract must be confirmed before enabling real normalization; no guess gate.
2. Supply and retain an authoritative versioned NSE calendar for each dataset.
   Unknown dates cannot certify completeness. Investigate actual gaps/unexpected
   bars rather than fabricating replacements.
3. Establish historical instrument continuity, corporate-action/adjustment policy
   and vendor correction/finality limitations before research claims.
4. Save query/hash/calendar provenance with experiments; use `replay` to detect
   late backfill. Time cutoffs alone are not immutable database snapshots.
5. Full strategy/backtest/features/optimization/paper execution remain Phase 11.1+
   work. No historical component can authorize trading.

This foundation is synthetic/disposable validated, not a certified real research
dataset or permission to trade. The optional real probe is intentionally deferred.

## Production changes and compatibility

New production package: `historical.domain/application/infrastructure`.
New broker adapter: `KiteHistoricalAdapter`. The existing `KiteRestTransport`
adds only a fixed historical GET route and an internal read overload that retains
the token for historical failures. Existing callers retain the previous default
authentication-invalidation behavior; mutation paths were not edited.

New opt-in migration: `db/historical/V11__historical_market_data.sql`.
Default trading migrations and exact-V10 operational preflight are unchanged.
Research requires its own datasource with explicit migration locations; applying
V11 to a trading database is not part of this phase and would deny that preflight.
No dependencies, Spring/config defaults, instrument universe, risk rules, order
valuation/sizing, HALT, token-store, arming or strategy-execution code changed.

New tests: `HistoricalDataTest`, `HistoricalArchitectureTest`,
`KiteHistoricalAdapterTest`, `PostgresHistoricalBarRepositoryTest`.
Documentation: architecture contract, this report and README links.

## Final Git inventory

Branch remains `develop`; HEAD and `origin/develop` remain
`3bb42037ce47326efbe9dd0680c55585a340c97c`. No staged changes, commit or push.
`git diff --check` is clean. Four tracked files modified and 19 new files;
tracked-only `git diff --stat`: 36 insertions, 3 deletions (new files excluded).

Tracked `git diff --name-status`:

```text
M README.md
M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteRestTransport.java
M docs/architecture/system-overview.md
M docs/operations/README.md
```

New files comprise 11 historical domain/application/repository classes, the Kite
historical adapter, V11 research migration, four test classes and two documents.
All are explicitly described above; no unrelated work was changed.
