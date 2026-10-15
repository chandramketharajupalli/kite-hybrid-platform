# Offline paper risk policy and unresolved settings

Existing config/README.md documents disabled risk and zero/unconfigured operational
limits. It explicitly defers daily P&L protection. These settings are preserved.
They are not replaced by convenient positive paper limits.

Risk() defaults to missing values. Admission requires attributable reviewed evidence,
simulated capital, planned monetary risk per trade, daily loss guard, simultaneous
position limit, symbol exposure, portfolio exposure, quantity cap and correlation
review. Unknown or zero mandatory values produce RISK_POLICY_GAP reasons and deny
paper admission. No approved genuine values for these fields are supplied in this
phase; the readiness report remains blocked. Synthetic fixtures contain explicit
test-only numbers and cannot be used as genuine-mode policy.

Sizing respects requested quantity, quantity cap, entry price, frozen stop fraction,
buffered symbol/portfolio/order value, available cash including buy costs, volume
participation and planned stop loss including slippage/fees. Gap losses can exceed a
planned stop budget. The daily realized-loss guard blocks new entries after the
limit is reached; it is not a guarantee against gaps. Only one position is simulated
at a time, so a configured concurrent-position cap is never expanded into permission
for overlapping positions. Separate runs do not share an account or portfolio.

Stop and target percentages remain the frozen strategy values. EOD exits and
conservative collisions come from the frozen executor. Missing liquidation liquidity
halts and records the outstanding simulated position. Partial-fill requests reject
as unsupported; no guaranteed fill is assumed. Paper HALT is a simulation input only.

Authorization binds qualification, risk and forward-data fingerprints and a validity
window. Genuine mode requires a genuine qualified pair, forward data beyond the
qualification window and reviewed provenance. Synthetic test mode requires synthetic
proof/data and fixture-only risk. Both remain offline with no broker execution.
