# Phase 14.0 deterministic backtest methodology

Only explicit sourced bars may be described as historical. The delivered example
is a short SYNTHETIC fixture, not six months of generated prices. Full-universe
reports have null/empty performance fields while no sourced history exists.

Universe is the complete 1,115-symbol canonical CSV. Each exchange/symbol/strategy
configuration/window is evaluated independently, with independent simulated cash.
This is not a combined 1,115-stock portfolio, capital allocation system or evidence
that all pairs could trade simultaneously. Global live ceilings remain unchanged.

Five-minute bars use interval-start UTC instants with Asia/Kolkata dated sessions.
IST has no DST. Every date must be a supplied session or explicit non-trading day;
unknown dates, conflicts, duplicate/out-of-order bars, malformed OHLCV and missing
blocks fail closed. As-of dates exclude unfinished/future bars. Java preserves
provider values with PROVIDER_UNSPECIFIED adjustment status, never locally fills
gaps or retrospectively changes prices. Corporate-action review is mandatory before
simulation; the boolean review assertion is not independently certified evidence.
Renames, splits, dividends, delistings, suspensions and token lineage need attributable
coverage before actual research qualification.

The user reference strategy is a long entry when RSI(14) crosses above 30 AND
EMA(close,21) crosses below close on the same completed bar. Equivalently:
previous RSI <=30, current RSI >30; previous close <= previous EMA, current
close > current EMA. Earlier crossings are not latched. EMA starts with a 21-close
SMA, then alpha=2/22. RSI uses a 14-change initial average and Wilder smoothing;
flat RSI=50, gain-only RSI=100. Warmup resets each dated session. At least 22
completed bars are needed for the EMA crossing comparison. No signal reads a
future bar; non-negative lag supports previous-bar references. Additional exact
conditions must be explicitly confirmed. No proprietary Streak catalogue is claimed.

Entry executes at the following bar's open with adverse configurable slippage.
No entry is allowed in the final bar. 2% stop and target are configurable. A bar
touching both exits uses the stop; a gap below the stop exits at the adverse open;
target gaps get no favorable open improvement. Intrabar exit timestamps are the
bar end, an explicit five-minute timing approximation. EOD exits use the final
bar close, not an assumed executable exchange close auction. The next open can
share a timestamp with the preceding bar's end; it remains a different bar.

Whole-unit quantity, full cash including entry fees, 1.05 default notional buffer,
maximum INR10,000 per order, adverse 5-bps slippage per side and 1% bar-volume
participation bound the fixture simulation. Real tariffs are not prepopulated.
Callers must supply the existing dated IntradayCostSchedule covering brokerage,
STT, exchange/IPFT/SEBI fees, GST and stamp charges; its exact-accrual convention
is not a contract-note rounding model. Fixture rates are explicitly hypothetical.
Participation uses realized bar volume for ex-post fill feasibility, not signal
selection. Zero-volume entry gets no fill; an unavailable exit invalidates the
simulation rather than inventing square-off. Short mode fails closed.

Metrics include net wins/losses/breakeven, fees, net expectancy/return, realized
drawdown, exposure seconds, no-fill reasons, and all trades with prices/timestamps.
A conservative marked drawdown additionally uses each held bar's low and estimated
exit fees; it can overstate drawdown after an intrabar stop and is documented as a
risk bound. It never enters the signal. Separate in-sample/OOS statistics and three
or more predeclared chronological OOS folds prevent pooled stock selection. No
parameter fitting or search occurs. Window starts use calendar subtraction for
6/12 months; declared range and explicit date coverage must match exactly. Splits
cannot cut a session. Not enough data yields INSUFFICIENT_HISTORY, never padded bars.

Reproducibility uses deterministic Decimal40 arithmetic, no PRNG, version, strategy,
input, config+fee and policy hashes. JSON contains trades/folds; CSV is the stable
per-pair summary with absent metrics empty. Imports validate Java content and
calendar fingerprints and reject overlapping/mismatched chunks. Source hashes
do not prove broker authenticity or point-in-time listing history.
