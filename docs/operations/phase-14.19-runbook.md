# Offline scheduler runbook

Use the strategy-engine Python environment with `PYTHONDONTWRITEBYTECODE=1` and Python `-B`. Construct validated `Request`, `Policy`, `Control`, `Calendar`, `Configuration` and `Command` models; call `phase1419.scheduler.run` with a new explicitly selected external directory. No network service or command-line broker client is required.

Supply preflight before session open, open exactly at the declared start, one step per completed five-minute batch, and close at the declared end. Each step includes completed observations for every admitted symbol and any available next-bar opening-price observations. The clock never advances automatically. Full final OHLCV belongs only in the completed observation.

Use `resume=True` only for an inspected incomplete run with the identical bindings and command prefix. Recovery reconstructs accepted events before continuing. A stale ownership lock, pending publication, corrupt/missing event, changed binding or terminal summary blocks continuation. Do not delete locks or migrate checkpoints automatically. Use `verify_only=True` to replay a completed or halted session and compare every persisted report byte-for-byte.

Reports include the twelve existing portfolio reports plus readiness, session events, candle processing, session signals, session EOD and replay verification. `summary.json` is published last. A checkpoint-only return has no completion marker. A halted report may contain an unresolved position and must not be presented as a completed day.

Resource limits are explicit: one worker, 1,115 symbols, 6,690 unique pair requests, bounded input histories and 1,115 observations per batch, 1,000 commands, 200,000 events, 4 MiB per command bundle, 128 MiB aggregate journal and 256 MiB reports. Reject an oversized run; do not infer live throughput from the synthetic examples.

Run focused tests with `pytest --assert=plain -p no:cacheprovider`, external `--basetemp` and JUnit paths. Use Ruff `--no-cache` and an external mypy cache. The additive manifest records raw receipt hashes, source inventory and protected baselines. Run `scripts/research/verify_phase1419.py` twice with Python `-B` for deterministic certification replay. Verification requires the recorded external receipts and fails closed if they are unavailable.

Gate A/B remain NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase 12 BLOCK; H1/H2 FROZEN; July SEALED; operational NO_GO. No staging, commit, push or broker access is part of this runbook.
