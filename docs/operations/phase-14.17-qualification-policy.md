# Frozen qualification and walk-forward policy

Authority: Phase14.15 qualification policy and Phase14.15D frozen_policy(). No new
statistical threshold is introduced. Six strategies S01-S06, versions unchanged,
1115 canonical enabled NSE stocks, six/twelve calendar months, 13380 hypotheses.

Require 100 completed OOS trades and 30 in each of three holdout folds. Observed
after-cost win rate must be at least 0.70 overall and 0.60 in every fold; breakevens
remain in the denominator. Require positive net expectancy, complete reviewed data,
valid mapping/calendar/corporate actions and no critical quality or execution issue.
Realized and conservative marked drawdown must not exceed 0.10.

Use the frozen ordinary two-sided 95% Wilson interval and Bonferroni-adjusted Wilson
lower bound with alpha 0.05 / 13380. The adjusted lower bound must exceed 0.5.
Observed 70% is not proof that true win probability exceeds 70%. Dependence between
trades, correlated hypotheses and survivor-universe selection remain limitations;
neither independence nor calibrated coverage under arbitrary dependence is asserted.
No alternate confidence method has been approved or silently substituted.

Whole IST calendar days allocate 50% training, 20% validation and 30% holdout with
existing integer rounding. Three contiguous nonoverlapping folds partition holdout.
Parameters freeze before validation; this is fixed-parameter chronological walk-forward,
not rolling optimization. Training/validation/holdout summaries remain separate.
Incomplete history is OUT_OF_SAMPLE_NOT_AVAILABLE. No split may straddle a session
or signal-to-exit trade. Warm-up stays causal through the frozen daily-reset executor.

Primary status precedence: unavailable, quarantined, synthetic insufficient evidence,
missing OOS, trade count, win rate, net profit, drawdown, confidence, unstable OOS,
other quality rejection, then qualified only if the frozen result is qualified.
Every original rejection remains in the row even when another takes precedence.
No performance data means null metrics and zero genuine qualified pairs.
