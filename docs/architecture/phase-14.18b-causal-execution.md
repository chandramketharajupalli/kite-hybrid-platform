# Causal execution correction

The frozen strategy registry and indicators remain unchanged. Phase1415 execution centralizes the versioned execution policy, causal liquidity observation and decision timing. Research evaluation quarantines unresolved executions. Phase1417 paper sizing and Phase1418 portfolio sizing reuse the same liquidity observation. Existing cash, exposure, cost and risk calculations remain authoritative within the offline simulation.

At an eligible five-minute boundary, the preceding signal candle is complete. A separate opening-price observation supplies the next candle open. Only that opening price is usable from the new candle; its high, low, close and total volume are unavailable until its end. The deterministic proxy uses the immediately preceding candle's completed volume for order sizing. Missing, stale or zero-volume evidence provides no permissive fallback.

The fixed integer quantity is reserved and accepted before the all-or-none opening fill. The reservation and fill carry identical decision timing and quantity. No later volume resizes that fill. Stops, targets and EOD exits are confirmed at the relevant candle end; close-time volume may veto the whole exit. A veto retains the position and debited cash, emits paper HALT and prevents successful completion. The executor exposes an unresolved entry separately from completed trades, with no fabricated exit price or P&L; research qualification fails closed.

Paper and portfolio journals bind the execution policy version and fingerprint. Portfolio checkpoint schema is v3 and paper binding schema is v2. Unknown, missing or legacy execution-policy bindings fail closed. The research job configuration explicitly binds the execution version, hash and changed software dependency inventory. No checkpoint migration is provided.

This remains offline synthetic/local research. The preceding-volume proxy is an execution assumption, not observed opening liquidity or guaranteed real-world execution. No partial fills or broker adapters are introduced. Local journal chains do not establish independent rollback resistance.
