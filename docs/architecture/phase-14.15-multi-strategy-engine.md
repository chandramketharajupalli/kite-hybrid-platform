# Phase 14.15 multi-strategy offline architecture

Additive package apps/strategy-engine/src/strategy_engine/phase1415. No historical code,
Java contract, ingestion, trading control, dependency file or broker transport changed.

| Existing capability audited | Reuse / extension |
|---|---|
| phase14/engine.py Candle,Series,Session | Reuse immutable OHLCV,UTC,calendar/order checks and explicit quality gaps |
| phase14 indicator/Operand/signal | Reuse SMA-seeded EMA,Wilder RSI and exact S01 conjunction |
| backtest/costs.py IntradayCostSchedule | Reuse side-specific fees/taxes,dated cost provenance,rounding |
| phase14 Trade,metrics,qualify,Policy,Settings | Reuse accounting,OOS/fold/Wilson gates,slippage/cash/participation/ceiling |
| phase14/reporting.py months_before | Reuse calendar subtraction in IST,then serialize UTC |
| phase14/imports.py FiveMinuteExport,compose | Preserved Java fingerprint/calendar/export composition boundary |
| HistoricalUniverseResolver,OfflineUniverseResolution | Preserved offline mapping/stale/token checks and KITE/ZERODHA projection; no mapping invented |
| Phase14.2 OfflineDataVerification | Retain whole-session quarantine principle; Python extension rejects incomplete windows |

Existing simulate has a closed close-only signal evaluator. Modifying it would change frozen
source evidence. New execution.py therefore supplies a small session loop for volume/range
signals while reusing price models,costs,Trade and metrics. Tests compare nonempty S01 trades,
fees and drawdown with the preserved executor. New execution envelope has its own version,
phase1415-session-executor-v1. This extension does not claim old code was generalized.

Flow: validated local Dataset -> quality/identity/calendar/action gates -> immutable registry
-> session-local completed-bar signals -> next contiguous open -> conservative execution
-> separate training/validation/holdout metrics -> three fixed-parameter OOS folds -> per-pair
qualification -> deterministic reports. PaperEvent is BACKTEST_ONLY metadata, no transport.

All indicators reset each dated session. This matches S01 Phase14 behavior but differs from
platforms using overnight warmup. No cross-session indicator state or overnight positions.
Prices are provider-unspecified with no local adjustment. Corporate-action review and calendar
provenance are mandatory; hashes establish consistency, not publisher authenticity. Loader
verification flags are explicit trusted local review attestations, not independently signed
broker/calendar evidence. Genuine data is absent in this phase.

Dataset includes serialized-Series SHA256,source/raw source hash,token,acquisition timestamp,
calendar version/source/hash,corporate-action evidence,missing sessions,duplicates,out-of-order
and quarantine/mapping flags. Existing Series validates all included local dates, session
bounds,UTC timestamps and unique ascending bars. Missing prices are never filled. Any gap,
identity or provenance failure yields no qualified metrics. Invalid execution/illiquid exit
quarantines the pair; next-bar missing data is rejected rather than skipped opportunistically.

jobs.run normalizes duplicate universe entries preserving first order, holds one dataset per
symbol/window,one worker,max30000bars and288bars/session. A bounded local loader must enforce
input file-size limits before parsing. No whole-universe candle map is required. Configuration
pins universe,definitions,windows,settings,tariff,policy,engine version and package source hash.
An exclusive job.lock excludes competing writers. Each pair uses a SHA-keyed checkpoint,
input binding,result checksum,fsync then atomic replacement. Matching checkpoints replay;
changed input/config/corruption denies. Process crashes may leave lock/pending files: explicit
operator inspection required, no automatic deletion or resume past uncertainty. No claim of
power-loss durability or privileged rollback resistance; no operational authority involved.

Rich export: CSV plus JSONL retaining trades,metrics and fold evidence. Unavailable generator
uses compact JSON columns+row arrays plus CSV; shared windows/config/registry/coverage/OOS
metadata apply to every row. CSV must travel with its JSON/config for exact timestamps.
No runtime import of broker/network/order packages. Paper event keys bind symbol,strategy
fingerprint and completed candle; expiry is one five-minute interval; always rejected from
execution by OFFLINE_ONLY_NO_TRANSPORT or EXPIRED.
