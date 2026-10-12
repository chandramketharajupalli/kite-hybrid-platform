# Phase 14.1 frozen offline implementation plan

Baseline: develop, HEAD and local origin/develop
712db352a3e74440e7625f1af5772cc5755908aa. Initial index/worktree clean.
This plan is frozen before implementation; its raw SHA-256 is recorded in the
additive generation-1 manifest. Scope corrections require an additive amendment.

## Scope and permitted paths

Add an offline snapshot importer in broker/infrastructure/kite, reusing
KiteInstrumentCsvMapper; add provenance/resolution types in instrument/application
reusing HistoricalUniverseResolver. Add a bounded offline proposal and candle
validation wrapper in historical/application using existing plans/quality types.
Add corresponding Java unit/architecture tests and an offline evidence verifier
under scripts/research. Add Phase14.1 architecture/operations docs and
research/phase-14.1/generation-1 CSV/JSON evidence. No existing source, schema,
CSV, manifest, configuration or frozen research will be rewritten. No migration.

## Acceptance and failure gates

Validate strict UTF-8 CSV bytes, bounded size, raw SHA-256, explicit source/evidence
reference, observation time and separately optional publisher date. Treat dates
as supplied claims, not independently verified authenticity. Reuse existing
broker field interpretation; retain duplicate candidates and reject malformed
classification. Resolve exact exchange/symbol without aliases, preserve source
order and stable identities. Distinguish unavailable/stale/invalid master,
invalid input, ambiguous, missing and resolved outcomes with candidate evidence.
No snapshot supplied means 1,115 MASTER_UNAVAILABLE rows, no invented tokens.

Bound pilot planning to 2-5 unique resolved NSE cash instruments and at most five
calendar dates/25 session requests, one request/second, concurrency one, no retry,
no redirect and bounded timeouts. Planning cannot dispatch or authorize network
traffic; real dispatch always rejects in this phase. Validate complete five-minute
windows against explicit calendars and source fingerprints without filling gaps.

Stop on dirty/mismatched baseline, protected hash failure, non-newline differences
outside the accepted 14.0A attestation, secret exposure or mandatory test failure.
Use original raw hashes plus the exact path-scoped 14.0A reconciliation, never a
global normalization waiver. Preserve all baseline tracked bytes.

## Validation and provenance

Focused Java tests: strict encoding/CSV, provenance, stale/future dates, ambiguous
symbols/tokens, aliases, exchange/segment/derivative confusion, complete universe,
bounded requests, no real dispatch, completed bars, duplicate/order/gap/calendar
and source lineage failures. Run focused Python Phase14 tests, full Java
unit/architecture and Python suites, Ruff/mypy, project verifier, secret/canary
scan. Run existing disposable historical PostgreSQL integration to revalidate
idempotency/concurrency/restart/export/no trading mutation; no shared database.
Record fresh totals and failed attempts, never substitute historical results.
Replay additive evidence twice and verify exact Git inventory and preservation.

Public official documentation may be read for the unapproved proposal. Do not
access accounts, credentials, broker API routes, WebSockets or live state. Current
account entitlement, real calendar/corporate-action evidence and actual history
remain unverified. No strategy performance is generated without real candles.

## Safety and deliverables

Default OFFLINE/SYNTHETIC; production acquisition remains disabled. No order/HALT
changes, staging, commits or pushes. INR10,000 buffered ceiling, operational NO_GO,
Stage B ZERO_CALL_ABORT, Phase12 BLOCK, H1/H2 FROZEN, July SEALED and pending V2,
governance second review and technical reviews remain unchanged.
Deliver architecture, resolution policy, owner authorization draft, runbook,
validation report, full universe ledger/summary, test evidence and additive hash
manifest. Separate synthetic resolution results from actual master availability.
