# Trading halt and recovery

Phase 1 cannot trade. Default emergency stop is a startup configuration value,
not a production runtime kill switch. Clearing it does not enable execution.

If startup rejects LIVE or ENABLE_LIVE_TRADING=true, restore PAPER and false.
Do not work around the guard. No live adapter exists.

Future runtime kill switch must durably prevent new dispatch first, record actor
and reason, then apply a separate explicit policy to existing orders.
Halting new orders does not imply existing broker orders are cancelled or positions
flattened. Cancellation/flattening policies require broker-aware risk handling.

Future restart: begin halted, restore PostgreSQL state, reconcile ambiguous/open
orders and fills, rebuild positions, verify freshness and broker health, then
require explicit resumption. Do not automatically retry ambiguous submissions.
