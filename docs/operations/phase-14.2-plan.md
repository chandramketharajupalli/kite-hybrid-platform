# Phase 14.2 frozen offline readiness plan

Baseline: develop, HEAD/local origin/develop
88e70b0f682f00c0383e76cfe5e623a8961d9112. Initial porcelain inventory empty.
The committed Phase14.1 change contains exactly its 20 reviewed additions and
matches its manifest and Git blob bytes. No fetch occurred.

This plan is frozen before implementation. Its raw SHA-256 is recorded in the
new evidence manifest; corrections require a separate additive amendment.

## Exact allowlist

- apps/trading-core/src/main/java/com/kitehybrid/platform/historical/application/OfflineDataVerification.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/Phase142ReadinessTest.java
- scripts/research/verify_phase142.py
- docs/architecture/phase-14.2-decisions.md
- docs/operations/phase-14.2-plan.md
- docs/operations/phase-14.2-master-approval-draft.md
- docs/operations/phase-14.2-pilot-approval-draft.md
- docs/operations/phase-14.2-runbook.md
- docs/operations/phase-14.2-validation.md
- research/phase-14.2/generation-1/resolution-ledger.json
- research/phase-14.2/generation-1/resolution-ledger.csv
- research/phase-14.2/generation-1/summary.json
- research/phase-14.2/generation-1/candle-quality.json
- research/phase-14.2/generation-1/test-results.json
- research/phase-14.2/generation-1/validation-attempts.json
- research/phase-14.2/generation-1/git-inventory.json
- research/phase-14.2/generation-1/evidence-manifest.json

All changes are additive. Preserve existing source, CSVs, schemas, migrations,
configuration and historical evidence byte-for-byte. No source refactor or new
broker transport. Reuse existing mapper, resolver, pilot, quality and exporter.

## Measurable acceptance gates

Verify 1,140 source rows, 25 duplicates, 1,115 unique enabled NSE symbols and order.
Revalidate Phase14/14.0A/14.1 hashes, the exact 13 normalized committed blobs and
all protected research/governance. New text must be LF and Git-clean byte stable.
Produce every actual resolution row; without a supplied real master, all 1,115
remain MASTER_UNAVAILABLE with null tokens/performance. Translate existing
resolver outcomes to the requested ledger vocabulary without guessing identities.
Add structured candle quarantine results around existing validation; no gap fill.
Exercise existing strategy and persistence contracts with synthetic fixtures only.

The new class has no I/O, broker, credentials, approval-enabling or order capability.
Separate UNAPPROVED packets: A proposes at most one master GET; B at most two named,
verified symbols and one completed session, <=2 historical GETs. Actual budgets
are ZERO until a new explicit owner decision and independent containment review.
Keep one/second pacing, concurrency one, zero retries/redirects, bounded bytes/time.

## Risk register and stop conditions

Missing authentic master/token lineage: no real mappings or pilot symbols.
Unknown actual calendar/corporate actions: no real completeness certificate.
Terms/account entitlement/retention uncertainty: no real access/storage approval.
Credential custody, strict egress, one-use budget and HALT/log containment unverified:
no dispatcher or full application start. Existing order-capable transport must not
be used as an approved narrow process merely because its route is read-only.
Malformed candles: quarantine complete window, no partial promotion or filling.
Git/protection/security failure: stop edits and report BLOCKED. Failed tests must
be retained and corrected/rerun before an offline readiness success classification.

## Tests, resources and cleanup

Use JDK21, offline Maven/uv, PYTHONDONTWRITEBYTECODE=1; no downloads/installations.
Focused Java: Phase142ReadinessTest, Phase141OfflineTest, KiteHistoricalAdapterTest.
Focused Python: tests/test_phase14.py. Then full Java unit/architecture and Python,
Ruff/mypy on Python and new verifier; disposable historical PostgreSQL integration
classes PostgresHistoricalBarRepositoryTest,KiteHistoricalPipelineTest. Project
verifier, secret/canary scan, exact Git inventory, hashes and two sealed replays.
Commands follow the existing Phase14.1 runbook with the new test/verifier names.
Retain fresh totals/failures separately from historical totals. All generated test
logs/reports stay ignored under target. Testcontainers owns only its disposable
containers; no shared DB, manual destructive cleanup, new hardware or cloud.

Finish with an additive deterministic manifest, actual unavailable-data report,
clearly labeled synthetic quality evidence and separate owner decision packets.
No staging/commit/push, tokens, broker requests, WebSockets, order or HALT changes.
Operational NO_GO, Stage B ZERO_CALL_ABORT, V2/governance reviews pending,
Phase12 BLOCK, H1/H2 FROZEN, July TEST SEALED and INR10,000 ceiling remain unchanged.
