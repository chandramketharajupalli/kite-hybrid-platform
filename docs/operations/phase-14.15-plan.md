# Phase 14.15 frozen implementation plan

Baseline develop HEAD/local origin/develop:1cad5f0f4d587837678e41cc3c64780a065a4edd.
Clean index/tree; eight Phase14.14 artifacts committed. Before edits replayed Phase14.0-14.14
manifests,13 CRLF/LF reconciliations,G1/18sources/63research/K-L and1140-25=1115 NSE universe.
Capture all1150 tracked raw hashes in final manifest; never modify these historical bytes.

Add isolated phase1415 Python package and tests; preserve phase14 and frozen engines.
Reuse Candle/Series/Session,indicator/Operand,metrics/qualify/Policy,Trade/Settings,cost tariff,
months_before and universe normalization. Existing simulate has no signal-provider port;
add a narrowly scoped session executor for new volume/range rules, reuse financial primitives
and prove S01 parity. No Java contracts, startup, broker transport or existing files changed.

Definitions v1: S01 user supplied RSI14 above30 AND close above EMA21 crosses on same bar;
S02 EMA9 aboveEMA21 cross; S03 RSI14 above50 cross AND close>EMA21; S04 close above lower
BB20 population-standard-deviation band with2 deviations cross; S05 close above session
HLC3-volume VWAP cross with positive current volume and cumulativevolume>=1000;
S06 first15minutes regular09:15IST range, later close crosses above rangehigh. S02-S06 are
RESEARCH_EQUIVALENT, not official Streak definitions. All long-only,2%stop/2%target/EOD.
Frozen registry metadata and parameter fingerprints; no stock-specific optimization.

Indicators reset each session, matching Phase14 S01; SMA seeds EMA,Wilder RSI,Decimal40
precision and population BB sqrt. Previous<=reference,current>reference defines above;
below inverse. Warmup bars never trade early. No gap filling, no cross-session entry.
Signals on completed bars; next contiguous open,entry-bar OHLC conservative stop-first,
adverse stop gaps,target at limit,existing5bps slippage/costs/liquidity/cash/10000 ceiling.
No-fill on final-bar entry or illiquidity; unfillable exit quarantines entire result.

Explicit six/twelve calendar months ending at supplied UTC timestamp; dated session/holiday
calendar and source evidence mandatory. Preserve unspecified provider adjustment; require
reviewed corporate actions, verified calendar/provenance and complete bars to evaluate.
Missing data/calendars/mapping produces unavailable rows with null metrics. Local datasets
must carry acquisition time,raw hash,calendar version/source,corporate-action evidence.
No genuine market data available by assumption until validated; synthetic never qualifies.

Chronological training first50%,validation next20%,holdout last30% using calendar-day UTC
boundaries checked not to cut sessions; three contiguous OOS folds within holdout. Parameter
freeze at/before validation. No trade signal/entry/exit spans split. Six/twelve windows
separate.100 OOS trades minimum,30/fold,70%OOS wins,60%eachfold,positive after-cost expectancy,
max10%drawdown,complete data,Bonferroni Wilson family13380 alpha0.05 lower>0.5. Report
ordinary95%Wilson interval too.100 is a predeclared evidence floor,not guarantee; dependence
and selection bias remain limitations. Breakeven included denominator,not wins/losses.

Streaming one symbol/window series at a time,one worker,max30000bars,session<=288bars.
Content-addressed result job directory,atomic per-pair file replacement,verified existing
records and pinned dataset identities; crash leftovers fail closed,never silently overwrite
conflicting results. Ordered CSV+JSON export,coverage/rejection/folds/config/registry/manifest.
All1115*6*2=13380 rows even when no data; do not store fake six-month candles. Benchmark
labeled synthetic only with measured time/memory,not production throughput.

Offline paper event includes symbol,strategy/version,signal candle hash,timestamp,direction,
reference,stop/target,expiry,idempotency and rejection; BACKTEST_ONLY,no order capability.

Tests:registry immutability,RSI/EMA parity,SMA/BB/VWAP/range edges,equality,warmup,volume,
session reset,OHLC/duplicates/gaps,future bars,entry/collision/EOD/fees/cash,per-pair policy,
confidence bounds,split leakage,missing calendars/actions,full rows,CSV/JSON determinism,
restart/conflict/corrupt checkpoint,no network imports. Focused then full relevant Python,
Ruff/mypy; Java NOT_RUN absent Java edits. Preserve failures and fixes in validation.
Use offline installed tools,no dependencies downloaded; one test process,bounded temporary
fixtures. No broker calls,credentials,vendor contact,provisioning,HALT/live changes or staging.

Deliver six named Phase14.15 documents,manifest,necessary package/tests and compact reports.
Freeze hashes,exact inventory,secrets/canaries,LF/Git bytes,baseline preservation,twice replay.
Stop on preservation/safety failure. Partial if mandatory engineering remains incomplete.
Gate A/B NOT_AUTHORIZED,NO_GO,ZERO_CALL_ABORT,Phase12BLOCK,H1/H2FROZEN,JulySEALED unchanged.
