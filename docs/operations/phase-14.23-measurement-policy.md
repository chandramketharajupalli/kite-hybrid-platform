# Phase1423DecisionQuote.v1

This is a causal synthetic decision-quote convention. Reference observation <= reference availability <= measurement cutoff <= fill time. The measurement cutoff must exactly equal accepted execution evidence: entry data_availability_cutoff for FILLED, or the accepted completed-evidence exit evaluation timestamp for CLOSED. A caller cannot move the cutoff or fill time forward to admit future evidence. Observation and fill belong to the same NSE trading date.

Positive signed slippage is adverse. BUY: (fill-reference)*quantity. SELL: (reference-fill)*quantity. Absolute slippage is the absolute signed amount per fill; group absolute totals sum those amounts rather than taking the absolute net. Per-share is the signed price difference; bps is signed difference/reference*10000. Reference prices must be finite and positive. Use frozen Decimal precision independent of caller context.

A reference is not an assertion of an executable quote or available liquidity. The supplied synthetic examples deliberately choose benchmark prices independently of unchanged accepted fills. Favorable, adverse and zero values are arithmetic demonstrations, not market measurements. POST_TRADE_BENCHMARK is outside this version and rejected; no future observation can justify an earlier decision.

Slippage is attribution of accepted execution price, never an additional fee or cash debit. Fees and original cash deltas are reported separately and remain unchanged. Do not subtract measured slippage from accepted P&L again.

Missing references and every rejected linkage leave NOT_COMPUTABLE. Partial coverage exposes measured-subset totals and null full totals. No accepted fills means NOT_COMPUTABLE with null coverage. Duplicate evidence IDs or duplicate fill links reject all conflicts. Reports distinguish these conditions rather than invent zero movement.

Run locally using Python -B and PYTHONDONTWRITEBYTECODE=1. Call strategy_engine.phase1423.quality.run with an individual validated Phase 14.20 scenario JSON, its trusted raw SHA-256, a reference-record list JSON and its trusted SHA-256, plus a new external directory. Preserve input custody and separately retain completion/report hashes. Never overwrite prior reports. Tests use external pytest/JUnit/temp paths, no pytest cache/assertion rewriting, Ruff --no-cache and an external mypy cache.

Phase 14.22 plan remains an independent untracked preservation exception, not part of this phase's inventory. Phase 14.21 remains PARTIAL; this additive synthetic evidence does not rewrite its historical claims. Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN; July SEALED; operational NO_GO.
