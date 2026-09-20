# Phase 4 implementation and validation

Implemented on `develop`, 2026-09-20, using the installed Java 21.0.12 and Maven
Wrapper 3.9.11. Phase 4 is market data only. No real Kite WebSocket was opened.

## Result

- Extended the existing `Tick`, `MarketDataGateway`, normalizer boundary and
  `InstrumentId`; introduced no parallel market identity or authentication flow.
- Added exact decimal normalization, optional quote/OHLC/depth, and an atomic
  in-process latest store with immutable, per-instrument-consistent snapshots.
- Added the Java 21 asynchronous Kite WebSocket transport, bounded fragmentation,
  independently tested binary decoder, typed modes and deterministic subscriptions.
- Added a guarded connection lifecycle, capped exponential reconnect with jitter,
  desired-state restoration, timeouts, heartbeat handling and graceful shutdown.
- Added bounded nonblocking tick handoff, drop counters, persistent quality
  degradation, per-subscription freshness and passive Actuator/Micrometer status.
- Added explicitly gated development diagnostics and the manual live runbook.

Authentication changes are confined to `KiteSession`: existing synchronized
mutations publish an immutable private streaming view, and broker rejections are
fenced by credential generation. Streaming reads never wait on the REST session
monitor. Existing authentication controllers, token exchange, encryption,
persistence, restoration, login-attempt handling and migrations are unchanged.

No Maven dependencies were added. Production uses the JDK WebSocket client and
existing Jackson/Micrometer libraries. Tests use deterministic fixtures, fake
timers/transports and a real loopback WebSocket peer. No strategy, risk, order,
position or P&L implementation was added.

## Executed checks

| Check | Result |
| --- | --- |
| `./mvnw.cmd verify` on final implementation | BUILD SUCCESS; 514 tests, zero failures/errors/skips; bootable and plain JARs built |
| Explicit PostgreSQL integration profile | 15 tests, zero failures/errors/skips; migrations, encrypted token/login-attempt storage and authentication restart covered |
| `uv run --project apps/strategy-engine --locked python scripts/verify-project.py` | Passed |
| `uv run --project apps/strategy-engine --locked python scripts/check-secrets.py` | Zero potential secret locations |
| `git diff --check` | Passed |
| Real Kite streaming | NOT RUN; requires explicit manual action |

The ordinary suite includes actual loopback WebSocket connect, fragmented
binary/text, ping/pong, subscription/mode commands, normalized state, reconnect,
resubscription and graceful close. Authentication rejection tests cover HTTP
401/403, text errors, expiry and stale-generation failures. Concurrency tests hold
the processing worker or existing session monitor while verifying bounded receive
completion. Decoder fixtures cover supported packet shapes, malformed framing,
depth, timestamps, unsigned fields and segment-specific scaling.

JUnit reports are under `apps/trading-core/target/surefire-reports` and
`apps/trading-core/target/failsafe-reports`. Windows commands can use `.\mvnw.cmd`;
run PostgreSQL tests explicitly with `-Pintegration verify`, or
`-Pintegration '-DskipUnitTests=true' verify` after unit verification.

## Operational limits

Streaming is disabled by default and never automatically connects on application
startup. Diagnostic HTTP controls require the development profile and both
market-data flags; the existing loopback-only deployment boundary applies.

CONNECTED records command delivery, not a broker subscription acknowledgement or
freshness guarantee. Every desired instrument must produce current data. No
exchange calendar, backfill or lossless-delivery guarantee is introduced.
Malformed data or overload latches quality degradation until explicit stop/start.
Historical latest ticks remain readable after disconnect; consumers must consult
health. Snapshots are immutable but do not claim a simultaneous cross-instrument
market observation.

See the [market-data architecture](../architecture/kite-market-data.md),
[configuration reference](../../config/README.md#market-data), and
[manual market-data runbook](../runbooks/market-data.md).
