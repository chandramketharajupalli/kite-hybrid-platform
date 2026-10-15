# Phase 14.18 frozen offline portfolio plan

Baseline: 777de800b6ad20acebaa22925f8b34d29020868b, develop = local origin/develop.
Clean index/worktree; all 22 Phase14.17 additions committed byte-identically.
Preservation passed: prior 1211 files, G1, 18 frozen sources, 63 research files,
K/L and historical manifests, 13 newline reconciliations, 1140/25/1115 universe.
Phase14.15D/14.16/14.17 fingerprints and 95 external evidence references verified.
Phase14.17A additive certificate and 37/498 historical test receipts verified.
New baseline: 1233 files; external baseline snapshot SHA-256:
5a7b6932f04a33b8bcdb024df00f2dca5dfea22370c2bd4a18d20fe5a830fc93.

## Scope and reuse

Add phase1418 Python research modules, tests, five requested documents, synthetic
reports and evidence verifier. All baseline source/evidence remains immutable.
Reuse Phase14.17 admission, qualification, single-pair paper candidate events;
Phase14.15 frozen executor and event interface; existing costs, precision, sizing,
dataset, canonical serialization and atomic external publication. No Java changes.

Portfolio input is a bounded collection of qualified pair requests. Each request
produces the existing validated isolated paper transcript in an external directory.
Merge these candidate event streams by timestamp, exits before entries at a shared
boundary, canonical symbol order, strategy ID and stable signal fingerprint.
This is a conservative candidate-stream portfolio model: rejecting a candidate does
not regenerate additional signals while the frozen reference executor was occupied.
No indicator, signal, execution or qualification redesign; no parameter optimization.
Only prices at their event timestamp may affect portfolio decisions. Future exits
cannot fund entries, and future marks cannot affect earlier events.

## Accounting and risk

One serialized coordinator, one owned position per symbol across strategies.
Decimal PRECISION everywhere. Available = cash - reservations; equity = cash + marked
position value; realized net P&L includes entry/exit fees. Fees and position basis are
recorded separately. Reserve full buy value plus fees before fill; release once on
fill/cancellation. Atomicity is within this single-writer simulator, not distributed.
Portfolio quantities may shrink frozen reference quantities but never exceed them.

Use existing Risk and sizing plus explicit strategy/sector/concentration limits.
Missing mandatory limits or reviewed sector/correlation evidence block admission.
Unknown genuine project policies remain missing; synthetic limits only in test mode.
Risk hash, qualification, data, calendar, registry, software, costs, settings and
delivery configuration bind each run. Frozen 13380 hypothesis family is unchanged.
Daily realized/marked loss guards, cash, symbol/portfolio/strategy/sector exposure,
integer quantity and participation limits apply. Gap losses can exceed planned risk.

## Execution, failure and recovery

Explicit PROPOSED, RISK_CHECKED, RESERVED, ACCEPTED, REJECTED, FILLED, EXIT_PENDING,
CLOSED, CANCELLED and PAPER_HALTED transitions. Duplicate proposals reject without
reusing order ownership. Partial fills reject; all-or-none frozen execution only.
Cancel unfilled orders and reconcile at session end. Missing delivered exit events
retain unresolved positions and HALT, never fabricate a close. HALT blocks entries;
known valid exits may still reduce existing positions. HALT never automatically resumes.
Bounded delivery cutoffs model interruption/missing forward market events explicitly;
they do not alter the historical qualification data or fabricate market prices.

Publish immutable sequenced journal events externally. Resume verifies a contiguous
prefix against deterministic recomputation with identical bindings before appending.
No completion marker before all events and reports are published. Crash injection
occurs between atomic events; ambiguous/corrupt prefixes reject with a paper-HALT
error. Local hashes do not provide independent rollback protection. Report hashes
and terminal anchors detect truncated completed runs.

## Budgets and validation

At most 6690 pair requests, 30000 bars per dataset (existing cap), one worker,
200000 portfolio events, 256 MiB serialized journal/report budget. Deterministic
event ordering, no thread scheduling. Fixture benchmarks are not live throughput.
External data/checkpoints/receipts only; no new production dependencies.
Tests: simultaneous/conflicting/duplicate/expired signals, ownership, reservations,
cash/risk sizing, exposure and missing sector/correlation, Decimal invariance, frozen
fills/collisions/gaps, EOD and missing delivery, cancellation, partial fills, HALT,
interrupted prefixes, changed bindings, tamper and no-lookahead/no-broker imports.
Run focused and full Python suites, Ruff no-cache and mypy external cache.
Python -B, PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain -p no:cacheprovider,
unique external temporary/JUnit outputs, 20-minute gate timeout, hash checks each gate.
Retain initial failures and corrective reruns; scope changes need additive amendment.
Stop on preservation failure or a frozen methodology defect; never edit frozen code.

## Evidence and restrictions

Produce deterministic ledgers, equity/P&L/exposure/rejection/HALT/EOD/run reports and
an evidence manifest; two independent deterministic evidence replays. Exact new-file
inventory, LF/Git bytes, secret/canary checks, receipts and baseline hashes required.
Capture this plan's hash externally before implementation. Keep raw logs external.
Genuine qualified pairs remain zero; genuine portfolio readiness blocked by absent
history/mapping/risk approvals. Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT;
Phase12 BLOCK; H1/H2 FROZEN; July SEALED; operational NO_GO. No credential access,
Kite requests, downloads, WebSockets, orders, operational HALT changes, infrastructure,
staging, commit or push.
