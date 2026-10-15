# Phase1424Exposure.v1

Scope: SYNTHETIC_FIXTURE. INR monetary units, seconds measured using UTC datetime
differences under the frozen Decimal context. Calendar membership uses Asia/Kolkata.
Short positions are rejected. For a long-only snapshot, gross = net = long market value,
and short exposure is verified zero. Portfolio equity is accepted cash plus marked value.

For each explicitly covered half-open interval [start,end), capital-time is market value
times seconds. Average gross exposure is total capital-time divided by eligible session
seconds. Average gross fraction integrates market value / equity over the same intervals.
Maximum exposure is the maximum observed snapshot value. Time in market counts intervals
with any position; verified zero duration counts explicit flat snapshots, never missing ones.
Strategy time counts the union of its simultaneously open positions rather than summing
their durations. One-position-per-symbol follows the accepted portfolio contract.

Full metrics require every session boundary and interval, complete upstream accounting,
resolved positions and reservations. Partial reports expose observed subset capital-time,
duration and coverage; full averages and adjusted metrics remain null / NOT_COMPUTABLE.
All six strategies and 1,115 symbols are represented in original universe order, also
recorded explicitly as symbol_order for canonical JSON transports that sort mapping keys.

Exposure-adjusted return = (closing equity - opening equity) / opening equity divided by
average gross exposure fraction. P&L efficiency = net P&L / INR-seconds. Strategy and symbol
efficiency use attributed net P&L and capital-time. No annualization is applied. Zero
denominators yield null / NOT_COMPUTABLE. These are descriptive measurements, not evidence
of genuine qualification or future profitability. Fees remain in accepted P&L; slippage
and exposure never add a second cash charge.

Synthetic constant quote marks are explicitly declared for covered intervals. This version
does not ingest arbitrary market marks, infer prices, interpolate gaps, or treat Phase 14.23
decision benchmarks as position marks. Its measurement assumption is intentionally narrow.
