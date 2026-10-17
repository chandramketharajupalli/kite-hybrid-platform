# Phase 14.27 RC acceptance policy

Scope: offline engineering assessment; no genuine qualification or operational authorization.

Mandatory gates include a committed clean baseline, preserved historical evidence, source/dependency identity, offline build checks, fresh integrated and full tests, lint/types, security/boundary checks, exact artifact inventory, separate certificate/receipt and two identical manifest replays. A source or evidence mismatch fails closed. Historical failures cannot be reconstructed or relabeled as passes.

The frozen alert family retains its historical 2 timely / 8 missed / 3 false-positive / 1 duplicate / 1 late result. The new synthetic path must retain ten timely matches and zero misses, false positives, duplicates and late alerts in that fixture. Deliberate missing-alert detection blocks certification and never increases monitored true positives. Missing analytics evidence remains unavailable, not zero.

Engineering READY_OFFLINE and OFFLINE_RC_ACCEPTED require every mandatory offline release gate to be resolved. The present recommendation is OFFLINE_RC_CONDITIONAL and engineering PARTIAL because original dependency archive provenance is UNVERIFIED and standalone wheel configuration/evidence resource binding is unresolved. Passing tests and deterministic wheels do not waive these gaps.

Required owner decisions: choose and explicitly approve a repository-bound execution contract or authorize a separate resource-packaging phase; supply or approve independently verifiable dependency artifact provenance; nominate the engineering reviewer and record formal RC acceptance. No owner acceptance is inferred from this technical recommendation. The assessment makes no dependency risk exception on the owner's behalf.

The RC identity uses canonical serialization. Its `evidence_payload` binding is the fingerprint of the manifest payload containing artifact/source/dependency/test/evidence bindings. The enclosing manifest also contains the RC identity and has its own fingerprint. This ordering avoids circular hashing. Build outputs and test receipts are separate from the certificate; the certificate binds completed replay receipts and its verifier produces a separate command receipt.

Genuine strategy stays BLOCKED until authorized mappings, data, calendar/corporate-action evidence and frozen qualification requirements are met. Operational readiness stays NO_GO until compliance, Gate A/B, custody, independent authority, isolation, logging, approved risk and owner approval are independently satisfied. OS egress, custody and power-loss limitations are explicitly outside synthetic proof. No acceptance label authorizes real trading.
