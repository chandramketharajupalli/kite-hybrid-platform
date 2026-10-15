# Phase 14.21 additive amendment 1

The frozen plan remains unchanged. Audit of backtest/costs.py found IntradayCostSchedule.quote and Charges, permitting exact component reconstruction from accepted fills. Reuse that API and reconcile its sum to recorded fees; do not duplicate tariff formulas. Separate reference-price slippage remains NOT_COMPUTABLE. No shared source changes are authorized or needed.

Strengthen source validation with the complete Phase 14.20 run binding and summary/session consistency. Accept optional existing-format acknowledgment envelopes, validating chain and timing without inferring resolution or resume approval. Add zero-trade-session and acknowledgment regressions. Initial focused attempt: 29 passed, one test failed because Decimal zero formatting was compared as a literal string; numeric comparison corrected it. Subsequent 30/33-test receipts and initial full/lint/type receipts are retained externally. Rerun final gates after changes.
