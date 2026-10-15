# Offline qualification and paper runbook

Use Python -B with PYTHONDONTWRITEBYTECODE=1. Keep research data, stores, checkpoints,
temporary files and diagnostic receipts outside the repository. No credentials,
broker session, database, network or live activation step is needed or permitted.

1. Supply a validated Phase1416 Job with predeclared tariff/slippage, canonical family,
   dataset records and fixed six-/twelve-month windows. Missing datasets remain absent.
2. Construct phase1417.paper.Risk from reviewed explicit limits. Risk() alone records
   unresolved policy gaps and cannot authorize simulation. Do not copy test limits.
3. Call qualification.run(job, external_directory, risk.fingerprint, now=utc_time).
   It verifies/replays Phase1416 checkpoints and publishes all 13380 qualification
   rows. Reuse the same bound inputs for replay; conflicting bindings or output hashes
   fail. Preserve pending files after interruption for operator inspection.
4. Review qualification-ledger CSV/JSON, walk-forward-report and rejection-reasons.
   Status QUALIFIED means research eligibility only, never broker permission. Synthetic
   results are not genuine qualifications. Keep their experiments separate.
5. For a validated pair, construct its bound qualification Row and provide a separately
   validated forward Dataset starting after the qualification window. Create explicit
   Authorization binding Row.fingerprint, risk.fingerprint, dataset hash and time range.
6. Call paper.simulate(row, dataset, definition, settings, tariff, risk, authorization,
   external_directory). The output is an immutable paper-run.json with scope, complete
   event transcript, position/cash state and chain anchor. Check rejections or HALT before
   treating any state as a fill or close. Repeated complete runs must be byte-identical.
7. Audit.replay(binding, events) verifies transitions, idempotency and hash continuity.
   For durable complete runs, also verify the expected terminal hash and run binding;
   a local chain alone cannot establish an independent rollback guarantee.

The workflow supports a single serialized pair/account experiment per run. It does
not coordinate capital across separate directories. Partial fills are unsupported
and explicitly rejected. Unknown risk, calendar, mapping or quality prevents admission.
The supplied public full-family ledger contains unavailable genuine data only.

No genuine instrument master, authorized history, calendar/corporate-action package
or complete genuine paper risk policy has been supplied. Paper readiness remains
blocked until those evidence inputs and explicit simulation authorization exist.
Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN;
July SEALED; operational NO_GO. No staging, commit, push or operational HALT changes.
