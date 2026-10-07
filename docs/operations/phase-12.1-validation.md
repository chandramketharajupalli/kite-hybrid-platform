# Phase 12.1 validation

Primary result: **UNIVERSE_CERTIFICATION_UNRESOLVED**.
Phase 12.0 acquisition gate: **BLOCK**. The specific LT Realty scheme does not
require segmentation during the window, but none of the five complete historical
identity reviews passes the frozen policy. All five remain in the universe.

## Baseline and immutable evidence

Initial required commands passed before research/editing: clean `develop`, HEAD
and `origin/develop` both `2281dfb38fd9b1ae79fefe152623945e564f30e1`, clean whitespace.
Required Phase 12.0 documents and six artifacts were inspected. The five members,
window, universe fingerprint, 99 sessions/37,125 bars per member and blocker match.
Expected totals remain 495 instrument-sessions and 185,625 bars. Retained SBIN
evidence is 99/37,125; four other corpora remain unacquired. No bars were read,
modified or recertified in this phase.

Phase 11.6 freeze-file SHA-256 remains
`56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`.
The original `run_phase116.verify_freeze` passes all 18 normalized frozen-source
hashes and its parent/registration checks. No changes to research/phase-11.4,
phase-11.5, phase-11.6 or phase-12.0. July SBIN TEST remains SEALED. H1/H2 are
unchanged and not evaluated in research; three complete untouched SBIN months
remain required for prospective confirmation.

The Phase 12.0 `MultiInstrumentCorpusAcquisition.java` source pin intentionally
changes for the mandatory Phase 12.1 gate. Its old implementation-verification
artifact is preserved, not rewritten to hide that change. The Phase 12.1
implementation-verification artifact pins the new source generation. No shared
broker/authentication/persistence/order/risk/operator/reconciliation source changes.

## Fingerprints and ordered process

| Identity | SHA-256 |
| --- | --- |
| Frozen universe | `638f11d8805f8d45724e8ade9a74e37652564046b699acd03033988ea4150395` |
| Policy v1 | `a350a24be35b6a844a0a769e8f14bd0821be3ca8bed44d4f8df5d32fc124f5f4` |
| Evidence generation 2 | `b97221c38ac0f2258a30ede00335a0b59d23ebe6a4b3d712d15cdd9fc9a8cce9` |
| Certification generation 2 | `289a3068ff6ab3723331dc8866ad40e877e84cbb9e898c9ea1f77000a662179c` |

Policy freeze preceded public review. Evidence freeze preceded classification.
Classifications and boundaries are generated from policy and normalized evidence;
the acquisition gate is derived afterward. Two offline replay passes agree on
classifications, boundaries, certificate identity and denial reasons. Repeated
replay needs no network, broker, candles, database or credentials.

Generation 1 remains archived. Generation 2 fixes publisher/type attribution for
issuer documents hosted by NSE; policy, findings and outcomes are identical.
Its new evidence fingerprint propagates to certification. Both generations are
bounded metadata, not raw PDFs or website dumps.

## Final classification table

The common window is **[2026-02-02,2026-07-01)** in Asia/Kolkata.

| Symbol | Identity | Intraday continuity | Cross-session continuity | Corporate-action completeness | Primary classification | Acquisition |
| --- | --- | --- | --- | --- | --- | --- |
| HDFCBANK | UNRESOLVED | UNRESOLVED | UNRESOLVED | UNRESOLVED | IDENTITY_UNRESOLVED | BLOCK |
| ICICIBANK | UNRESOLVED | UNRESOLVED | UNRESOLVED | UNRESOLVED | IDENTITY_UNRESOLVED | BLOCK |
| LT | UNRESOLVED | UNRESOLVED | UNRESOLVED | UNRESOLVED | IDENTITY_UNRESOLVED | BLOCK |
| RELIANCE | UNRESOLVED | UNRESOLVED | UNRESOLVED | UNRESOLVED | IDENTITY_UNRESOLVED | BLOCK |
| SBIN | UNRESOLVED | UNRESOLVED | UNRESOLVED | UNRESOLVED | IDENTITY_UNRESOLVED | BLOCK |

Classification counts: IDENTITY_UNRESOLVED 5; all other primary classes 0.
The precedence rule does not conceal incomplete actions: their component status
is separately UNRESOLVED for every member. Known dividend mechanics do not grant
an unresolved security a continuous-corpus certificate.

Missing evidence is specific: dated NSE security/symbol/series coverage for the
entire window, reconciliation of HDFCBANK/SBIN action-feed versus listing ISINs,
and remaining complete review of effective actions/earlier announcements. See the
[per-instrument evidence report](phase-12.1-evidence.md) for exact sources and
what each source proves. No absence is inferred from failed/empty web results.

## Confirmed continuity boundaries

| Symbol | Boundary date | Action | Price continuity | Volume continuity | Gap feature | Segment reset |
| --- | --- | --- | --- | --- | --- | --- |
| HDFCBANK | 2026-06-19 | Cash dividend INR 13 | Invalid across boundary | Retained | INVALID_BOUNDARY / unavailable | No |
| LT | 2026-05-22 | Cash dividend INR 38 | Invalid across boundary | Retained | INVALID_BOUNDARY / unavailable | No |
| RELIANCE | 2026-06-05 | Cash dividend INR 6 | Invalid across boundary | Retained | INVALID_BOUNDARY / unavailable | No |
| SBIN | 2026-05-15 | Cash dividend INR 17.35 | Invalid across boundary | Retained | INVALID_BOUNDARY / unavailable | No |

These four observed boundaries are not an assertion that no other boundary can
exist. They preserve raw OHLC. The individual dividend event does not invalidate
same-session bars, conditional on separate security/bar certification. ICICIBANK's
August record date is outside this window; no in-window boundary is invented.

**LT segmentation required for the Phase 12.0 Realty concern: NO.** The proposal
transfers an undertaking to a wholly owned subsidiary, with subsidiary shares
issued to L&T. June-quarter official results still report pending scheme approvals.
April 1 is the proposed appointed date, not an established trading effective date.
The nine required answers and primary links are in the dedicated
[LT discussion](phase-12.1-evidence.md#lt). This narrow finding does not change the
overall unresolved LT security/series classification.

## Acquisition denial and feature responsibility

The Java gate runs before all delegate/provider/repository access and checks
artifact presence, pinned policy, content hash, exact universe/window/member IDs,
component/primary classifications and boundary semantics. Legacy invocation
without certification denies. There is no force/ignore/skip option. Counting
fake-provider denial tests require zero calls and no repository/registry interaction.
Synthetic valid certificates preserve acquisition ordering and replay.

New Python feature APIs consume generic instrument-scoped continuity metadata.
Gap/previous-close features become unavailable at invalid price boundaries.
Dividend volume history remains eligible; quantity/security segmentation resets
ineligible volume references. Session-local indicators and strategies are unchanged.
Retained Phase 11 diagnostics remain frozen; future corrected research must use
the additive APIs and a new study generation. Strategy evaluation is disabled in
every Phase 12.1 gate artifact, including synthetic acquisition-allow cases.

## Validation and integration decision

Final Python suite: **193 passed**. Final Java unit/architecture suite:
**1,221 passed**, zero failures/errors/skips. Focused disposable integration:
**11 passed**, zero failures/errors/skips. Ruff passes; configured strict mypy
passes **34 files**. Project verification passes, secret scan reports **0 findings**,
and `git diff --check` is clean. Offline replay and original frozen-source
verification pass. Test inputs are synthetic;
the requested full regression suite necessarily executes synthetic strategy/unit
fixtures, not research studies or retained market-corpus performance. Research
baseline/H1/H2 evaluations and cross-instrument research P&L calculations are zero.

The first focused disposable integration run failed because its runtime classpath
does not include unit-test helper classes. An integration-local synthetic fixture
corrects that setup; no production bypass or gate weakening was introduced.

Complete trading-core integration is not required: only research metadata and the
historical composition gate changed. Full Java unit/architecture and focused
disposable PostgreSQL historical suites cover the touched boundary. Normal
persistence, broker transport/authentication, Spring configuration, trading schema
and execution controls are unchanged. No development or real research DB was used.

Reproduction from the repository root:

```powershell
$env:PYTHONDONTWRITEBYTECODE = '1'
uv run --project apps/strategy-engine python scripts/research/run_phase121.py
uv run --project apps/strategy-engine pytest apps/strategy-engine/tests
.\mvnw.cmd -pl apps/trading-core test
.\mvnw.cmd -pl apps/trading-core -Pintegration '-DskipUnitTests=true' '-Dit.test=PostgresHistoricalBarRepositoryTest,KiteHistoricalPipelineTest' verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
# From apps/strategy-engine:
uv run ruff check src tests ../../scripts/research/run_phase121.py
uv run mypy
```

## Safety and handoff

Public evidence manifest: 27 sources. Current broker reference/profile reads: 0.
Historical candle reads: 0. Research baseline evaluations: 0; H1: 0; H2: 0;
cross-instrument research P&L calculations: 0. No corpus export/bar file was opened
by Phase 12.1 research. Synthetic test fixtures are not observations of the corpus.

Real order mutations, account trading reads, WebSocket connections, development
DB accesses/mutations, real research DB access, token accesses/mutations, HALT
resume, execution arm, execute calls and live/paper activity: all zero. No runtime
HALT/DB state was queried, so this is isolation evidence, not a claimed live-state
measurement. No .env, emergency-stop or first-live safeguard change. No commit/push.

Tests changed only header timestamps in five previously tracked bytecode files;
payload equality was verified and those generated header changes were corrected.
No bytecode changes remain. Subsequent Python runs disable bytecode writes.

Next phase should address only the missing reference/coverage evidence listed
per instrument. Do not proceed to Phase 12.2 acquisition until a new, matching,
fully permitting certification generation exists. No live strategy is selected.

## Exact changed/new file inventory

4 modified tracked files and 22 new files. No staged files, deletions, commits or pushes.
Git diff/stat includes tracked edits only; the new-file list is included below.

```text
M apps/strategy-engine/tests/test_research.py
M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresHistoricalBarRepositoryTest.java
M apps/trading-core/src/main/java/com/kitehybrid/platform/historical/application/MultiInstrumentCorpusAcquisition.java
M apps/trading-core/src/test/java/com/kitehybrid/platform/MultiInstrumentCorpusTest.java
A apps/strategy-engine/src/strategy_engine/research/continuity.py
A apps/strategy-engine/src/strategy_engine/research/continuity_features.py
A apps/strategy-engine/tests/test_continuity.py
A apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/IntegrationContinuityFixtures.java
A apps/trading-core/src/main/java/com/kitehybrid/platform/historical/application/HistoricalContinuityGate.java
A apps/trading-core/src/test/java/com/kitehybrid/platform/ContinuityFixtures.java
A apps/trading-core/src/test/java/com/kitehybrid/platform/HistoricalContinuityGateTest.java
A docs/architecture/corporate-action-continuity.md
A docs/operations/phase-12.1-evidence.md
A docs/operations/phase-12.1-validation.md
A research/phase-12.1/access-log.json
A research/phase-12.1/acquisition-gate.json
A research/phase-12.1/certification-policy-v1.json
A research/phase-12.1/continuity-boundaries.json
A research/phase-12.1/evidence-manifest.json
A research/phase-12.1/generation-1/acquisition-gate.json
A research/phase-12.1/generation-1/continuity-boundaries.json
A research/phase-12.1/generation-1/evidence-manifest.json
A research/phase-12.1/generation-1/instrument-certifications.json
A research/phase-12.1/implementation-verification.json
A research/phase-12.1/instrument-certifications.json
A scripts/research/run_phase121.py
```
