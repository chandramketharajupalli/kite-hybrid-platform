# Offline runbook

Keep all genuine inputs, permission evidence and stores outside the repository.
No credential, network, database or instrument acquisition step exists here.
Use the repository Python environment with Python -B and
PYTHONDONTWRITEBYTECODE=1. Provide a UTC clock value explicitly for repeatable checks.

1. Review source authorization, retention, dated instrument resolution, authoritative
   calendars and corporate actions. Hash each supplied export and evidence file.
2. Construct `strategy_engine.phase1416.imports.Request` using the existing Java
   export chunks and `Calendar` objects. Supply six and twelve calendar-month windows
   matching `standard_window(end, months)`; end must be IST midnight expressed in UTC.
3. Call `import_local(request, external_store, now=utc_now)`. Rejected requests raise
   an error; preserve the request hash and error in operator diagnostics. Quarantined
   records carry explicit reasons and no executable candle dataset. Do not retry by
   weakening provenance, calendar or quality requirements.
4. For each returned record, locate `<SHA256(record bytes)>.json` in the store.
   Construct `Selection(symbol=..., months=..., record=Input(path=..., sha256=...))`.
   Records absent for other symbols/windows remain DATA_UNAVAILABLE.
5. Call `jobs.plan(end, Settings(...), explicit_tariff, selections, as_of=utc_now)`.
   Tariff and slippage must be predeclared and reviewed for genuine research. Test
   tariff fixtures are hypothetical and are not certified genuine cost schedules.
6. Call `jobs.execute(job, new_external_run_directory, now=utc_now)`. The API executes
   the complete 1,115-symbol family sequentially; selection is a data partition, not
   a smaller hypothesis family. No automatic parameter search or trading follows.
7. Replay the same immutable job and inputs to verify checkpoints and reports.
   Changed software, inputs, policy or configuration requires a different run directory.
   A stale lock, pending import or pending report requires inspection. Preserve all
   partial files; never remove an active lock or relabel an incomplete run complete.

Limits: 32 export chunks, 4 MiB/chunk, 32 MiB total input, 30,000 bars, 400 supplied
calendar dates, one worker, 3,600 seconds between-pair deadline checks. Store imports
are capped at 256 MiB per operation. Job checkpoints reserve room for reports and
reject oversize partitions. This is bounded offline research, not a throughput claim.

The completed output includes import, quality, coverage, checkpoint, qualification,
rejection and run reports plus CSV/JSON stock-strategy results. Both windows retain
6,690 rows each. Missing metrics are JSON null and empty CSV fields. Inspect data
status before interpreting any number; SYNTHETIC is never genuine performance.
Archive raw receipts externally. Do not publish authorized local candles in Git.

Gate A/B remain NOT_AUTHORIZED, operational NO_GO, Stage B ZERO_CALL_ABORT,
Phase 12 BLOCK, H1/H2 FROZEN, July SEALED. This workflow provides no paper-order or
live-order activation. Infrastructure, staging, commit and push are outside this phase.
