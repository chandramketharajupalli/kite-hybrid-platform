# Phase 14.1 validation

Classification: PHASE_14_1_OFFLINE_RESOLUTION_VALIDATED. This establishes offline
engineering and synthetic checks only, not real master/history availability.

Baseline: develop; HEAD/local origin/develop
712db352a3e74440e7625f1af5772cc5755908aa; clean initial worktree and index; no fetch.
Frozen plan raw SHA-256:
0203c612aec6750c1a80932f655587576000a88bb10ab0d877310809ad1b0b3f.
All pre-existing tracked bytes remain unchanged. New artifacts use LF consistently
so Git clean filtering does not recreate the Phase14 raw-hash discrepancy.

Original Phase14 envelope fingerprint:
55eb6903e91f0afb255d285b13b409e6749c9b14a7e091abfb6bfad29fcffc07.
Phase14.0A reconciliation fingerprint:
a8d58511d4a9e9181392c71ff4e743f3f82badc137cba49dea1cbdb7591d09a6.
All 13 accepted path-specific newline equivalences were checked against committed
Git blob bytes; no general normalization waiver or original-manifest rewrite.
Preservation checks cover 978 Phase14 hash entries, 63 protected research files,
25 K/L files, 17 plans, 8 Phase13.20 documents, 18 registered normalized frozen
sources, 15 parent envelopes and 11 governance envelopes. Earlier research/G1
evidence remains within the unchanged baseline inventory.

## Implementation

Four new Java classes add immutable dated master provenance, strict offline import
using the existing Kite mapper, per-source resolution using the existing resolver,
and a bounded offline historical proposal/quality wrapper. One new Java test class
has 40 cases. The read-only verify_phase141.py reuses frozen preservation logic
and consumes Phase14.0A explicitly. No existing source, schemas, database migration,
configuration, input CSV, strategy implementation or evidence was edited.

Initial focused Java tests exposed a pre-existing KITE versus ZERODHA namespace
mismatch. A new wrapper-only comparison projection now preserves original tokens,
stable IDs and returned ZERODHA instruments. Initial failures and lint/type
corrections are retained in validation-attempts.json; none are reported as passes.

## Fresh executed validation

| Scope | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| Focused Java final | 71 | 0 | 0 | 0 |
| Full Java unit/architecture | 1,555 | 0 | 0 | 0 |
| Focused Python Phase14 | 32 | 0 | 0 | 0 |
| Full Python | 282 | 0 | 0 | 0 |
| Disposable historical PostgreSQL/mock pipeline | 12 | 0 | 0 | 0 |

Focused counts are subsets, not additional full-suite tests. Ruff and mypy pass
for the 43 Python source/test files and separately the one new verifier script.
Project verifier passes. Final secret/canary counts, exact Git inventory and two
read-only replay results are recorded in the additive manifest/replay record.

Integration covered transactional conflict rollback, concurrent idempotency,
repository/application restart and checkpoint reuse, source lineage and pinned
exports. Only the two relevant existing historical integration classes ran;
full unrelated integration and physical PostgreSQL process-restart testing are
NOT_RUN. No existing persistence code or schema changed. Synthetic/mock HTTP
responses are not proof of real network behavior, account entitlement or candles.

## Actual universe and availability

Exactly 1,140 original data rows reconcile to 25 duplicate physical CSV lines and
1,115 first-occurrence unique enabled NSE symbols, including six symbols with &.
Java's tested resolver emits the full ledger; an independent verifier checks every
symbol/order/status against the original CSV. Actual counts: RESOLVED=0,
AMBIGUOUS=0, NOT_FOUND=0, INVALID_SOURCE=0, STALE_MASTER=0,
MASTER_UNAVAILABLE=1,115. This does not claim the symbols are absent at Kite.
There are no actual instrument-token assignments or performance values.

Pilot planner caps are 2-5 resolved symbols, <=5 dates, <=25 requests, one/second,
concurrency one, no retries or redirects. Real dispatch always rejects. The owner
draft proposes a smaller TWO-stock, ONE-session pilot (TWO historical GETs) only
after exact identifiers, dates, provenance, eligibility and safety approval are
bound. An optional one-request master acquisition is a separate owner decision.
No automatic transition to a real provider exists.

Remaining prerequisites: genuine dated master, historical mapping/corporate-action
lineage, verified NSE calendar, eligible stock subtype/liquidity selection, actual
account subscription/terms and credential custody, signed bounded authorization,
independently reviewed production read wiring and actual source candles. Existing
database keys use platform instrument ID with token provenance on chunks; this
phase does not claim a new raw token-keyed storage model or verified token history.

Operational NO_GO, Stage B ZERO_CALL_ABORT, Phase12 BLOCK, H1/H2 FROZEN, July SEALED,
INR10,000 ceiling, collateral MIS NOT_READY and unproven funding eligibility remain
unchanged. V2, governance second review and four technical reviews remain PENDING.
Agent broker requests, live credential access, WebSockets, orders, HALT mutations,
staging, commits and pushes: zero. Public documentation reads are separate from
broker API requests and confer no account access or authorization.

Evidence: [generation-1](../../research/phase-14.1/generation-1/summary.json),
[test results](../../research/phase-14.1/generation-1/test-results.json),
[failed attempts](../../research/phase-14.1/generation-1/validation-attempts.json),
[pilot draft](phase-14.1-pilot-authorization-draft.md).
