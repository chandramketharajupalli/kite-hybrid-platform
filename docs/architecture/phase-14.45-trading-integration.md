# Phase 14.45 integration design

This is an additive foundation, not a complete automated intraday service. The only implemented route is opt-in static status metadata. Eleven remaining routes are contract-only. No application has been started. The metadata does not attest to existing runtime state or independently disable other historical code paths.

## Verified application boundaries and build inventory

Java Spring Boot trading-core owns control, authoritative orders, positions, risk and PostgreSQL persistence. Python strategy-engine owns research and emits SignalEvent.v1 proposals. Its Strategy protocol and SignalSink already separate strategy evaluation from delivery. SignalEvent uses UUID instrument identity, integral quantity, decimal strings and UTC timestamps. Preserve its existing schema; a future versioned envelope must bind strategy version, timeframe, methodology and dataset evidence without silently changing v1.

Reuse OrderApplicationService.place for validated durable command recording; it is not a paper fill engine. executeRiskApproved is a distinct guarded operation and must never be invoked by these new routes. ExecutionSafetyPolicy, RuntimeExecutionArming, durable authorization audit, OrderRepository submission checkpoints, reconciliation and DisabledOrderExecutionGateway remain unchanged. Existing KiteOrderAdapter and KiteOrderConfiguration are historical code, not new authority. Do not create another broker adapter.

InstrumentRegistry provides snapshot-aware instrument UUID and broker-token resolution. Tokens may be reused. GET instruments must read a verified local snapshot with identity evidence and freshness, not refresh from Kite. No invented tokens or merging by symbol alone.

KiteAuthenticationController handles broker login/callback state. This does not establish an authenticated operator session authorized to issue API commands. The proposed operator session in OpenAPI is a requirement, not an implemented authenticator. Before any data route, require account-scoped authorization; before every mutation require authentication, permission, CSRF for cookies, atomic idempotency and strict body validation. Reject unknown fields and missing durable dependencies. Never use a broker access token as an API bearer token.

Existing root POM specifies Spring Boot 3.5.16, Java21, Maven3.9.11+, compiler3.14.1, Surefire3.5.4, JUnit via Boot, networknt schema validator1.5.9. Existing Python requires3.12 with Pydantic2, pytest8, Ruff and strict mypy. No dependencies changed or installed. README commands include mvnw.cmd compile/verify and uv run --locked pytest/ruff/mypy. These broad commands are not run: full-suite discovery includes excluded workloads and runner qualification is unresolved. Future targeted Java validation: offline Maven test selecting TradingFoundationTest, SignalContractTest and individually audited order/risk tests, with already approved JDK and dependency cache. No automatic downloads or broker diagnostics.

## API behavior

docs/api/trading-v1.openapi.json freezes twelve paths and request contracts. Version1 appears in response/body schema; URI remains /api/trading as requested. Money is decimal-string transport, BigDecimal/Decimal internally, quantity integral. Required future validation additionally rejects nonpositive prices, stop/target fractions outside (0,1), naive/non-UTC timestamps and unknown configuration versions. Limits on payload, candle count, pagination and job concurrency need an adopted policy before enabling mutations.

Status is public static nonsensitive metadata and no-store, disabled unless trading.foundation-api.enabled=true. That property registers only this read route. It does not activate live execution. Other GET routes need paginated account-scoped read models with explicit evidence-unavailable responses; never represent unavailable records as successful empty portfolios or zero risk. Backtest/evaluate admission must return a durable job identity only after admission commits. No fictional job success is implemented here.

POST idempotency is principal + operation + account + key, backed by a unique PostgreSQL claim and canonical payload digest. Same authorized payload returns the original response; changed payload gives409. Recheck permissions for replayed responses. A crash after admission must recover from the durable job/order journal, not resubmit. Validation/auth failures cause no side effects. Neither paper stop nor order admission may use a live gateway. Exact success envelopes and migrations remain future implementation gates.

## Broker-independent strategy interface

The proposed evaluation boundary is evaluate(versioned configuration, verified immutable completed candles, evaluation UTC time) -> signal proposals with evidence hashes. Python never owns authoritative orders. Freeze exact indicator initialization, rounding precision, warm-up, lookback, session reset and equality/crossover semantics per strategy version. Unknown strategy/configuration versions reject rather than fall back.

Every configuration binds instrument UUID plus dated reference/identity evidence, five-minute timeframe initially, entry and exit definitions, stop fraction, target fraction, dated NSE EOD calendar, strategy version and methodology version. Signal time must be at or after the final completed candle; reject open candles, duplicate or out-of-order bars, missing required warm-up and data beyond evaluation time. SignalEvent delivery needs deterministic proposal identity and duplicate-consumption protection in Java. Actual fills occur only after signal availability under the adopted execution methodology; no same-bar look-ahead.

| Candidate | Existing verified research reuse | Remaining boundary |
|---|---|---|
| RSI | phase1415 S01 RSI14/EMA21 reversal and S03 RSI momentum | Preserve exact combined rules; not generic standalone RSI |
| EMA | S02 EMA9/EMA21 crossover | Preserve warm-up and session rules |
| MACD | No implementation verified in this audit | New versioned candidate and golden vectors required |
| VWAP | S05 HLC3 cumulative session VWAP reclaim | Preserve volume and dated session requirements |
| Supertrend | No implementation verified | ATR initialization and reversal rules need frozen specification |
| Bollinger Bands | S04 BB20 population-two-SD reversion | Preserve population variance and crossing definition |
| Opening Range Breakout | S06 first15minute range | Missing opening bars must reject |
| Moving Average Crossover | S02 is an EMA crossover variant; research EMA_CROSS exists | Do not relabel SMA or duplicate S02 as independent evidence |
| Volume Breakout | No implementation verified | Freeze baseline-volume window, multiplier and price condition |

These are Streak-style research candidates, not a verified proprietary Streak inventory. Unverified candidates are unavailable, not evaluated placeholders. Each eventual implementation needs exact numeric vectors, insufficient-history and invalid-configuration cases, deterministic repeated evaluation, future-data perturbation tests proving causality, session/EOD/stop/target tests and transport contract tests. None of the nine candidates gains qualification from this design.

## Backtesting and qualification

Reuse backtest.dataset/costs/engine, phase14.engine, phase1415.research and phase1417.qualification. Preserve the canonical1,115-symbol universe and frozen13,380-combination family; no expanded candidate family is mixed into sealed Series v1 results. Series v2 remains PROPOSED_NOT_ADOPTED.

Historical acquisition is a separate, disabled operation requiring authorization; admission here accepts only already verified local dataset identities. Require raw/canonical dataset hashes, source/date/timezone, dated exchange calendar, candle OHLC consistency and nonnegative volume, gap/duplicate handling, corporate-action adjustment policy and provenance, instrument lineage and quarantine on ambiguity. Backtest stock-wise and strategy-wise views retain methodology/version, dataset, strategy, fees, slippage, capital, holding-window, OOS split and execution-model hashes. Do not aggregate incompatible methodologies or treat research cashflows as authoritative account positions.

Report trade count, wins/losses/breakevens, explicitly defined win-rate denominator, gross/net profit, fees/slippage, equity drawdown, cost-adjusted expectancy and OOS results. Undefined metrics stay null with reasons. No invented returns. Preserve phase1417 frozen-policy gates, confidence and walk-forward constraints.

New qualification key: instrument identity + strategy version + timeframe + methodology version, additionally bound to source result and policy hashes. Historical win-rate minimum70%; require independently approved minimum trade count, positive cost-adjusted expectancy, verified OOS evidence and drawdown ceiling. Missing policy values reject, not zero/default approval. Synthetic results are INSUFFICIENT_EVIDENCE regardless of performance. QUALIFIED_RESEARCH_ONLY grants no execution authorization. Genuine independently verified qualified pairs established by this phase:0.

## Isolated Java paper architecture

Existing phase1417.paper is a research simulation and remains sealed; do not expose it as an authoritative trading ledger. Reuse Java command validation, identity types, order transitions and risk interfaces. A future Java PaperExecutionGateway must have no transport, broker-session or credential dependencies and a distinct durable paper-account namespace. Do not wire historical KiteOrderAdapter into the paper route.

Persist command idempotency, order revision, synthetic fill identity, positions and audit events transactionally in PostgreSQL. A fill uses unique (paper account, order, fill sequence) identity; replay must not double quantity or cash. Restart rebuilds projections from the committed journal and verifies balances/revisions before accepting commands. Reconciliation compares journal, orders, fills and positions; ambiguity halts admission. Existing OrderState cannot alone account for fills; preserve it and add separately reviewed fill accounting instead of silently changing semantics.

Fill model is explicit, deterministic and labeled SYNTHETIC, with adopted slippage/fees and market-calendar hashes. Validate quantity, precision and cash/position constraints. If stop and target are crossed in one candle, apply a frozen conservative stop-first rule; gaps use adopted gap-fill semantics, never pretend unavailable prices traded. EOD square-off uses the dated exchange calendar and stops on unavailable exit evidence. Emergency stop durably rejects new paper admissions; cancellation and paper liquidation require distinct audited policy, not an implied live cancel call. No authoritative paper engine is implemented in this phase.

## Live fail-closed boundary

There is no new live endpoint or execution adapter. All foundation routes lack any reference to a live gateway. Existing live transport remains behind its historical checks; an environment variable alone does not supply owner authority, runtime arming, risk approval or readiness evidence. Future live admission must independently verify owner authority, exact scope, operational readiness, risk approval, daily-loss and position limits, kill switch, source identities, trusted expiry and replay controls. Missing evidence rejects.

Persist dispatch intent before transport, retain correlation identity, and reconcile ambiguous timeout/network/broker outcomes before any retry. Never automatically retry placement after an unknown result. Broker order-status reconciliation, restart recovery and duplicate prevention require separately authorized validation. Historical 18 supervisor cases and31 database tests remain excluded, as do the46 enforcement tests. No real Kite request or live order is sent.

## Remaining implementation priorities

1. Establish an approved targeted Java unit/contract runner without invoking blocked families; compile and execute the new tests.
2. Implement and test API operator authentication/authorization and durable request admission, independent of broker login.
3. Add read-only projections for verified instruments, strategies, signals and qualifications, preserving unavailable states.
4. Implement Java isolated paper persistence, fills, accounting, stop/restart and reconciliation with approved pure/contract tests.
5. Add verified strategy adapters and missing candidates in separately versioned methodology families; never adopt Series v2 implicitly.

Infrastructure owner selection remains deferred. Owner trust NOT_ESTABLISHED; Gate A/B NOT_AUTHORIZED; operational readiness NO_GO.
