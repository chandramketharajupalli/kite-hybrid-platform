# Phase 14.15 predeclared qualification policy

Frozen before evaluation in plan81ddd0c02da72621806cb6ca070d5253c7b076d97b8726efbda4880217bace6c;
IST-boundary correction in additive amendment1 changes no statistical thresholds.

Each stock/strategy/version/parameter/window stands alone. Six and twelve calendar months
are distinct tests;1115*6*2=13380 family size. No pooled stock win rate qualifies a pair.

Defaults:100 completed OOS trades minimum,30 per each of3holdout folds,observed OOS wins>=70%,
positive after-cost expectancy and profitability,max10%realized and conservative marked
whole-run drawdown,each fold>=60%wins with positive expectancy and acceptable drawdown.
Require complete reviewed calendar/corporate actions/mapping and no quarantined data.
Breakeven means exact net0,counts in denominator but neither wins nor losses. No trade
is a win until costs/slippage.100 is a conservative predeclared evidence floor,not a power
calculation or guarantee; dependence between trades remains a statistical limitation.

Report ordinary two-sided95%Wilson interval and existing Bonferroni-adjusted Wilson lower
bound for family13380,alpha0.05. Adjusted lower must exceed0.5; it is not a claim that the
true win rate is at least70%. Correlated tests/trades and survivor universe bias remain;
family correction is conservative and does not eliminate selection bias. Reject low-sample
100%win rates. Parameters must remain frozen before validation; no per-stock same-period
optimization. Synthetic results always fail overall historical qualification.

Whole IST calendar days define first50%training,next20%validation,last30%holdout,rounded
down to whole days. Three contiguous non-overlapping folds subdivide holdout. UTC represents
all instants; month subtraction occurs in Asia/Kolkata,including leap/month-end clamping.
Calendar sessions may not straddle split boundaries; no signal-to-exit spans a split.
This is fixed-parameter walk-forward evaluation,not an optimizer or rolling parameter refit.
Training and validation metrics remain separate from OOS selection. Daily reset warmup does
not carry future information or positions across any partition. Cash constraints apply in
chronological order; whole-run marked drawdown is an additional conservative rejection.

Summary includes completed/winning/losing/breakeven trades,gross/net,fees,average per-trade
net return,profit factor,drawdown,holding duration,session-time exposure and streaks.
Profit factor is null without negative net trades (no finite denominator),not infinity.
No-trade average return/holding are null; unavailable history has all performance null.
Every rejection is retained. No data -> OUT_OF_SAMPLE_NOT_AVAILABLE,not a zero-trade market
backtest. Qualified local data would yield a research candidate only,never broker permission.
