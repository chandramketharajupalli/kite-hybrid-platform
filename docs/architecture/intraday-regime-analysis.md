# Intraday regime diagnostics, Phase 11.5

The [registered plan](../operations/phase-11.5-analysis-plan.md) fixes dimensions,
boundaries and minimum samples before diagnostics. This phase explains retained
baseline failures and registers future hypotheses; it implements no new strategy.
All Phase 11.4 source, parameters, report and specification remain unchanged.

`research/opening_features.py` accepts only validated `CompletedSession` prefixes
and historically earlier completed sessions. Opening observations become available
at 09:20, 09:30 and 09:45 for the regular 09:15 session. Early relative volume
becomes available at 09:30, with five prior references required and twenty maximum.
These raw observations are tagged TRADABLE_AT_TIME_T. They carry availability times,
not full-session regime labels. No baseline consumes the new observations.

`research/diagnostics.py` is a separate EX_POST_DIAGNOSTIC_ONLY namespace. It consumes
development-only corpus data and already-closed simulated results. Neither the
engine, baseline strategies, original features nor opening features import it.
Architecture regression tests enforce that direction and the Decision API contains
no excursion/report/regime inputs. Python remains an offline research process,
not an access-control sandbox against arbitrary code.

The analysis entry point accepts only [February 2, July 1) local bounds, rejects
out-of-window bars before statistics, validates the corpus, requires every parent
evaluation, verifies result fingerprints and binds each result's dataset artifact
to the corresponding development slice. It also checks fill boundaries before
session analysis. The runner verifies all original shard/schema/calendar pins,
including permitted July integrity checks, then removes the full corpus reference
before calling diagnostics. There is no final-test CLI switch.

Every result retains the parent fold, partition, strategy and fingerprint; every
trade is nested in that result. Each trade reconciles reference-price gross minus
slippage to original engine gross, then minus fees to original net. Summaries
reconcile to all retained parent metrics. The baseline engine gross already includes
adverse slippage. Edge cases A/B/C refer to that gross; pre-slippage gross is also
explicitly reported. A zero gross or zero net is a boundary case, not misclassified.

MAE/MFE use the entry candle through the last held completed candle and the terminal
exit reference open. Exit-candle high/low are excluded because liquidation occurs
at its open. Excursions are nonnegative price amounts from slipped entry, not cash
P&L; quantity multiplication is explicit for giveback/cost coverage. First-five
observations are censored at actual exit and carry their observed duration. They
do not estimate what would happen if the strategy held longer. Minute OHLC cannot
establish high/low order or an executable perfect exit. Exit-giveback observations
must not be interpreted as a take-profit backtest.

Full-session volatility/efficiency and development-wide tertile labels are ex-post.
Even labels on historically available gap, opening-range or relative-volume raw
values use a hindsight development distribution and cannot enter a strategy.
A future strategy needs separately frozen or historically rolling thresholds.
Full-session directional efficiency includes open-to-first-close in path length,
making its [0,1] range valid even when the first candle moves substantially.

Summaries are one dimension at a time and preserve all folds. Eligible-session
denominators include no-trade days; the sample threshold counts distinct traded
sessions. Time/duration summaries use all sessions as the denominator and describe
trade allocation, not a counterfactual strategy. Duration is an outcome-dependent
variable; its relationship to profit does not establish that waiting longer helps.
Overlapping TRAIN totals are never added. All financial calculations use Decimal;
diagnostic divisions/square roots use private precision 40 HALF_EVEN. Undefined
ratios are null. Closed-trade drawdown differs from original marked-equity drawdown,
which is retained separately. No Sharpe, annualization or significance claim.

Exactly three fixed-fill repricings (0/5/10 bps) recompute the same dated fees from
changed notionals. They preserve trade timing/quantity and do not rerun cash admission
or pretend to model liquidity. The 5-bps result must reconcile to the parent.

The report contains plan/source, parent report/corpus, feature/cost/slippage pins,
development and excluded TEST windows. Canonical serialization is inherited from
the engine. Its SHA-256 is its filename and exposed fingerprint property, avoiding
self-reference. Plan/source changes generate a different identity. Writers refuse
conflicting content rather than overwrite evidence. Cached complete replay results
are in ignored local data storage and must match the original result hash before
reuse. They contain development data only and preserve every losing result.

Only pinned local evidence is used. The dated cost scenario and Phase 11.4
corporate-action, dividend, source-finality, idealized-fill and full-cash limitations
remain applicable. No Java/live routing, accounts, execution, runtime halt, funding
safeguards, environment configuration or reconciliation behavior changes.
