# Phase 14.0 stock-strategy qualification policy

Defaults are fixed before data inspection: OOS net win rate >=70%, >=100 OOS
trades, strictly positive net expectancy, realized and marked drawdown <=10%,
at least three chronological OOS folds each with >=30 trades, positive net
expectancy, >=60% wins and <=10% realized drawdown. Net-zero trades are breakeven,
not wins, and stay in the denominator. Insufficient samples always reject.

Use a Bonferroni-adjusted Wilson lower confidence bound above 50% as an additional
uncertainty gate, default family size 2,230 (1,115 stocks x one strategy x two
windows), alpha 0.05. Family size cannot understate all declared pairs/configurations
and windows. This approximate binomial interval is not proof under dependent market
outcomes; serial dependence and regime shifts can invalidate coverage. It is not
a 70% guarantee. Any later parameter search increases the family and needs a new
predeclared untouched evaluation. A small-sample 100% result is rejected.

All criteria apply to each individual stock/strategy/parameters/window, never a
universe average. Preserve every rejection reason and all tried configurations.
Missing history or a missing dated mapping produces a row with no performance.
Synthetic output remains SIMULATED even when arithmetic gates pass. A real-data
passing row is only RESEARCH_CANDIDATE in BACKTEST_ONLY mode. No broker permissions,
risk approvals or automatic live/paper order dispatch follow from this status.

Point-in-time universe membership, adjustment semantics, independently reviewed
corporate actions, realistic effective tariffs, liquidity and fold coverage remain
prerequisites for useful real-data conclusions. Current-member survivorship bias
must accompany every eventual report. No actual stock-wise win rate is reported now.
