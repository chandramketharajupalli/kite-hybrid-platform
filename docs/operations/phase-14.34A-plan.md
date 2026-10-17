# Phase 14.34A installer attestation preparation

Baseline: c33ce219344b81bc93cf31c6e4afdea8b5a5507b. Read-only preflight passed; 1,472 tracked hashes captured externally. Phase 14.22 exception unchanged. Protected history including 13 newline reconciliations, 18 source hashes, 63 research files and K/L records passed. Legacy three-file Phase 14.30 reconciliation uses verified historical blobs in memory only; current authorized hashes separately checked.

Exact installer: pip-25.0.1-py3-none-any.whl; SHA-256 c46efd13b6aa8279f33f2864459c8ce587ea6a1a59ee20de055868d8f7688f7f. Source: local CPython 3.12.14 Windows AMD64 ensurepip bundle. Raw identity and self-asserted RECORD checks do not independently establish provenance. Python distribution itself is not independently attested here. Do not execute pip or any acquired code.

Approved custody search: retained Phase 14.32 metadata-only evidence and Phase 14.33B receipt custody; no independent pip 25.0.1 record found. This is a bounded custody search, not a search of unrelated private storage.

Prepare one metadata request: HTTPS GET https://pypi.org/pypi/pip/25.0.1/json, maximum 1 request, 2 MiB, 20 seconds, no redirects/retries/credentials/archive download. Retrieval requires separate approval; preserve raw bytes and transport receipt if later approved. Expected exact filename, version, SHA-256 and size must match, artifact must not be yanked. Index agreement is not publisher authentication or proof of package safety.

Create an installation decision with status NOT_AUTHORIZED, exact nine installation inputs, installer/runtime hashes, quarantine certificate and a fresh proposed external destination. Bind provenance evidence separately; absence blocks approval validation. No destination creation, installer execution, package extraction or installation in this phase.

Permitted additions: this plan, provenance findings, validation report, metadata retrieval request, installation authorization packet, evidence manifest, offline verifier and synthetic tests. No historical or trading changes. Test budget: bounded synthetic JSON and temporary directories; focused pytest, Ruff and strict mypy; no network or full trading regression because trading code is unchanged. Capture failures and corrections, preserve protected hashes after gates, seal evidence and replay twice.

Stop on protected hash mismatch or unexpected repository state. Any later plan correction must be additive. Gate A/B NOT_AUTHORIZED; operational NO_GO; installation NOT_AUTHORIZED.
