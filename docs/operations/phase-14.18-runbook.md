# Offline portfolio runbook

Use Python -B with PYTHONDONTWRITEBYTECODE=1. Research inputs, candidate transcripts,
journals, checkpoints and diagnostics must be external to the repository. No broker
session, credential, operational database or network connection is required.

1. Supply Phase1418 Request objects containing verified Phase1417 qualification Rows,
   forward Datasets, frozen registry Definitions, costs/settings and explicit pair-level
   Authorization. Keep synthetic and authorized local modes separate. Inconsistent
   histories for the same symbol reject; unsupported directions reject.
2. Construct Policy from reviewed base Risk, strategy/sector limits, sector evidence
   and correlation controls. Defaults intentionally do not authorize simulation.
3. Construct Control with enabled paper authorization, attributable authorization
   evidence and the exact policy fingerprint. Paper HALT is isolated and persistent.
4. Call run(requests, policy, control, external_directory). The deterministic order is
   independent of request tuple order; no worker concurrency changes allocations.
5. Inspect run-summary.json and all twelve referenced ledgers/reports. A summary can
   describe a halted simulation: require simulation_complete and no unresolved positions
   or reservations before describing a session as reconciled. Paper profit is not real.
6. Replay exactly the same inputs in the same external directory. Complete replay must
   be byte-identical; interrupted valid prefixes append only missing immutable events.
   Changed software, policy, datasets or strategy versions reject. Do not delete pending
   files, rewrite a corrupt journal or repair a truncated completed run automatically.
7. Preserve any recovery error and obtain separate operator review. No auto-resume from
   HALT. Local chain verification requires the expected binding and terminal anchor;
   it does not establish independently rollback-resistant custody.

delivery_end explicitly models a feed interruption in an offline experiment. It never
fills absent data. cancel_signals contains explicit simulated signal fingerprints;
cancellation releases the valid reservation before fill. interrupt_after is a synthetic
crash-injection control for between-event recovery tests, not an operational trade switch.

Bounded inputs: 6690 requests, 100000 aggregate bars, 30000 per dataset, one writer,
200000 events and 256 MiB output. Process authorized larger experiments as separately
declared simulations; do not merge their capital or qualification claims implicitly.

No genuine qualified pairs, instrument master or authorized history/risk package is
supplied here. Synthetic examples do not grant paper readiness for genuine inputs.
Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN;
July SEALED; operational NO_GO. No staging, commit, push or live activation.
