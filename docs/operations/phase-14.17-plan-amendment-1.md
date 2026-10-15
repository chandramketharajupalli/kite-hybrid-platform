# Additive amendment 1: explicit paper arithmetic context

The frozen plan remains unchanged. Final review identified that the new paper cash
ledger performs arithmetic outside the frozen engine's explicit Decimal context.
Caller precision can therefore affect new paper cash values, despite deterministic
frozen execution prices. Add a low/high caller-precision regression, retain its
initial failure, and wrap the new simulator in the existing PRECISION context.
No policy, tariff, signal, strategy or frozen implementation change is authorized.

Retain the first 36-focused / 497-full passing receipts and initial draft generation
externally as pre-correction evidence. The initial generation-1 reports are unsealed
drafts: archive their exact bytes and hashes externally before refreshing them with
corrected runs. Do not overwrite any prior-phase evidence or the initial external
run directories. Use uniquely named corrective receipts and fresh external directories.
Rerun focused/full, Ruff, mypy and report generation, then seal only the final bytes.
All 1211 baseline files must remain unchanged throughout.
