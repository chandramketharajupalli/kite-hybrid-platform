# Multi-session offline runbook

Use Python -B with PYTHONDONTWRITEBYTECODE=1. Construct a bounded tuple of `Day` models, existing Policy/Control/Calendar and explicit synthetic `Limits`. Call `phase1420.multi.run` with a new external directory. Every day supplies its original scheduler requests, configuration and explicit clock commands. The complete dated calendar is authoritative only for this labeled synthetic simulation.

Retain the returned accepted-record `head` outside the disposable run state. Use `resume=True` and that trusted `expected_head` only for an incomplete run. Completed or halted runs cannot be resumed automatically. Use `verify_only=True` with the trusted head to validate existing records and all report bytes without executing completed sessions. A stale lock or pending publication requires separate inspection; this module does not delete or repair it automatically.

The output consists of the run summary plus twelve deterministic views: session status, daily P&L, equity curve, drawdown, strategy P&L, symbol P&L, risk/health, cross-session HALT, missing sessions, alerts, carry-forward and recovery. Immutable per-session outcomes contain the original eighteen scheduler reports. Publish the summary last. Missing report files or altered report bytes reject verification.

For acknowledgment, pass the alert ID, a timezone-aware simulated timestamp and evidence hash to `acknowledge`. Retain the resulting acknowledgment-chain head separately when rollback detection is required. `alert_view` returns the overlay; it does not mutate original alerts or authorize resume.

Limits: one worker, 64 declared sessions, existing per-session 1,115-symbol/six-strategy limits, 200,000 unique symbol candles per bounded run, 64 MiB per outcome and bounded aggregate publication. Oversized or ambiguous work fails closed. No universe-scale or live throughput claim is made.

Validation uses external pytest basetemp/JUnit/logs, --assert=plain -p no:cacheprovider, Ruff --no-cache and an external mypy cache. The read-only evidence verifier checks exact hashes, protected evidence, receipts and synthetic replay; run it twice for deterministic certification. No staging, commit, push, broker access or external notification is authorized by this runbook.
