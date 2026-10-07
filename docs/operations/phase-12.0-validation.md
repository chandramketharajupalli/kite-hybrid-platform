# Phase 12.0 validation

Primary status: **MULTI_INSTRUMENT_CORPUS_NOT_CERTIFIED**.
The research foundation is implemented and tested, but the real five-member
corpus/study is not validated. Complete corporate-action and identity-continuity
evidence could not be established for all declared members. No member was
removed, replaced, evaluated early or selected by performance. No real historical
acquisition or real multi-instrument strategy evaluation occurred.

## Baseline and preservation

Before inspection/editing: clean `develop`; HEAD and origin/develop both
`f09abd04adb0f2eec99189ac0e0485ed71532cc8`; whitespace clean. All required
Phase 11.5/11.6 documents were read. Implementation freeze file SHA-256:
`56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`.
The original `run_phase116.verify_freeze` independently verifies the retained
registration, parent report, all 18 normalized source hashes and G1 freeze body.
Three Windows byte hashes differ from normalized text hashes solely due to line
endings; the original freeze verifier's text hashing passes without modification.

The Phase 11.4/11.5/11.6 evidence and every frozen source remain unchanged.
H1/H2 prospective confirmation still requires THREE COMPLETE UNTOUCHED MONTHS.
No candidate evaluation ran; **H1/H2 NOT evaluated cross-instrument**.
**SBIN July TEST remains sealed**; only February–June v1 shard files were opened
for Phase 12 export integrity. **No strategy selected for live use**.

## Frozen universe and dates

The [pre-acquisition protocol](phase-12.0-universe-plan.md) was written before
the one broker reference GET, historical acquisition or strategy execution.
Selection is a purposive five-member infrastructure sample, using June 30, 2025
Nifty 50 membership as a dated liquidity proxy. All five appear in Exhibit 10
of the [NSE 2025 whitepaper](https://niftyindices.com/docs/default-source/indices/nifty-50/nifty-50-whitepaper_2025.pdf).
The [official index page](https://www.niftyindices.com/indices/equity/broad-based-indices/nifty--50)
labels that paper Series 4 September 2025; September 30 is the conservative
month-end evidence-availability bound, not an invented exact publication day.
Actual retrospective registration is October 7, 2026. Current membership is not
used to assert historical population representativeness. The rolling factsheet
was rejected because its currently retrieved content is September 2026.

Universe fingerprint:
`638f11d8805f8d45724e8ade9a74e37652564046b699acd03033988ea4150395`.
Exact current reference mappings, independently checked with Java identities:

| NSE CASH symbol | Platform InstrumentId | ZERODHA reference ID |
| --- | --- | --- |
| HDFCBANK | 0dce64b8-7a7f-3960-bb69-725f2c4a456a | 341249 |
| ICICIBANK | b940f917-be88-36d4-85dc-89d747386c53 | 1270529 |
| LT | 12d53f0d-2e49-3374-83f9-82c91bf01a50 | 2939649 |
| RELIANCE | 31b3a1e4-ab49-3447-8b8c-fbef31c1b7af | 738561 |
| SBIN | 050f94dd-e639-364f-97a6-595594de6543 | 779521 |

The reference snapshot was obtained once from `GET https://api.kite.trade/instruments`,
without authentication, profile, account or streaming calls. Raw CSV remains in
ignored data/phase120-reference. SHA-256:
`86a03cd5f5cdeda7c3f1c2d2bc1280e25e10b0abcfd2850fd7adc8a7e4a797c2`.
Public instrument IDs are not access tokens. A current mapping does not prove
historical token continuity or historical index membership.

Common window: **[2026-02-02, 2026-07-01), Asia/Kolkata**, minute interval-start,
regular sessions 09:15–15:30. Parent calendar fingerprint:
`22c28c857547dbc81d7796a4b083edb84b0239bbf76cc9b4cc9fcfd04eb77f3f`.
Ordered February–June calendar-segment pin:
`1b672acfb1f7f9d79b55069d355a4cbee0776dae6ed6e527d107a5dc3e40e390`.
Expected per member: 99 sessions / 37,125 bars; full universe 495
instrument-sessions / 185,625 bars. These are not 495 independent exchange days.

## Corporate actions and observed corpus quality

All sources accessed October 7, 2026. Each symbol's official NSE corporate-actions
page returned its dynamic shell without action rows; bounded API queries for
February 2–June 30 were unavailable through the web research tool. That is
missing evidence, not a claim of no corporate actions. Issuer sources provide
partial corroboration:

| Member | Reviewed evidence | Certification consequence |
| --- | --- | --- |
| HDFCBANK | [Issuer calendar](https://www.hdfc.bank.in/about-us/corporate-governance/financial-calendar): dividend record date June 19, 2026 | Complete split/bonus/merger/symbol/identity review unresolved |
| ICICIBANK | [April 18 issuer filing](https://nsearchives.nseindia.com/corporate/ICICI2022_18042026145407_NSEBSE_18042026.pdf): recommended INR 12 dividend | Recommendation alone does not establish effective action dates or full continuity |
| LT | [FY2026 results](https://investors.larsentoubro.com/upload/Quarterly/FY2026QuarterlyLTResultMarch2026.pdf): INR 38 dividend recommendation and Realty Undertaking scheme with appointed date April 1 | Effective scheme/continuity treatment needs review |
| RELIANCE | [FY2026 report](https://www.ril.com/reports/RIL-Integrated-Annual-Report-2025-26.pdf) references the 2024 bonus | Outside-window event does not establish complete February–June continuity |
| SBIN | [Retained issuer filing](https://nsearchives.nseindia.com/corporate/SBIN_08052026154613_BSE_NSE_DividendRecordDate_08052026.pdf): INR 17.35 dividend, May 16 record date | Dividend known; full corporate-action continuity review remains unresolved |

No local price adjustment, dividend entitlement, silent segmentation or member
removal occurred. Per-member status is **CORPORATE_ACTION_UNRESOLVED**, even
where previously certified bar integrity can be verified independently.

| Member | Expected sessions/bars | Locally verified sessions/bars | Bar-quality observation |
| --- | --- | --- | --- |
| HDFCBANK | 99 / 37,125 | Not acquired | Unmeasured, not zero gaps |
| ICICIBANK | 99 / 37,125 | Not acquired | Unmeasured |
| LT | 99 / 37,125 | Not acquired | Unmeasured |
| RELIANCE | 99 / 37,125 | Not acquired | Unmeasured |
| SBIN | 99 / 37,125 | 99 / 37,125 reused export shards | No gaps, duplicates or unexpected timestamps in verified exports; retained canonical content unchanged |

SBIN endpoints: `2026-02-02T03:45:00Z` through `2026-06-30T09:59:00Z`.
Development-only corpus fingerprint:
`9a239482994d9198686712d49cb19447a2f1a33283a97ba9cc8d73f2fc4f59ac`.
No Phase 12 session exclusions. No research DB conflicts were newly measured;
SBIN's zero-conflict evidence is inherited from retained Phase 11 certification,
with exact export/content pins independently checked. Four other members retain
null counts/fingerprints rather than fabricated quality measurements.

Certified members: **0/5**. Failed-manifest evidence fingerprint:
`c465d9fdde18fe19d7c1213323c21c44c288c2a9272893e054a4b7a856b422b5`.
Certified aggregate corpus fingerprint: **null / unavailable**. The failed
manifest identity must not be presented as a certified cross-instrument corpus.

## Acquisition, replay and research outcome

The complete conditional plan contains 99 daily chunks per member. Historical
ceiling 495 GETs (at most 396 new if existing SBIN chunks can be verified/reused),
one profile and one reference GET. The current executable historical budget is
zero because continuity gates failed. No retry or automatic extension.
[Official Kite limits](https://kite.trade/docs/connect/v3/exceptions/#api-rate-limit)
were checked: historical three/second; declared pacing is one start/second.

Python plan identity:
`db95ab96ff884e46264c94f2d662f5be86d023e1ce5f7778195f83bd7f9e3f30`.
Offline Java aggregate plan identity (different documented serialization):
`cacacce97d94379da2655bc10b8c66d5226afb1ea9a6a87ca41ca014767f8de4`.
The Java harness verifies all member identities, daily dates/windows and derived
counts through the existing HistoricalCorpusPlan; no network or DB connection.

Actual real requests: **historical 0, profile 0, instrument-reference 1**.
Real acquired/reused/failed acquisition chunks: **0/0/0**; acquisition was not
started. Separately, 99 existing SBIN export shards were verified offline twice,
with unchanged fingerprints and zero network/DB operations. No real acquisition
replay is claimed. Synthetic PostgreSQL restart/replay completed with zero replay
provider calls, zero new rows and unchanged per-member/aggregate fingerprints.

Declared development folds are the unchanged Phase 11.4 expanding TRAIN
February–March/February–April/February–May and April/May/June VALIDATION.
EMA 9/21, VWAP crossover, opening range 15 bars, RSI 14 recovery 30/exit 50;
one share; initial cash INR 100000 per independent partition; next-open fills;
entry 09:15–14:45; forced exit 15:15; 5-bps adverse slippage; unchanged dated
FIXED_AS_OF cost scenario. Minimum sample 20 trades and five traded sessions.
Every category/reporting boundary was declared before results in the protocol.

Real study freeze/evaluations/results: **not reached / 0 / none**. Planned count
is 120 atomic evaluations, of which 60 VALIDATION. There are no per-instrument
performance results, profitability categories, medians or robustness conclusions
to report. The real runner's `verify`, `freeze` and `evaluate` modes fail closed
on the retained manifest; no study/report hash is fabricated. Synthetic replay
tests exercise a separate two-member fixture twice, checking identical complete
report identity and 48 per-instrument result fingerprints. They are software
verification, not market evidence.

Synthetic report fingerprint:
`59dcbea7524fdb77d07f95c133f8d4599ec39913cfd9f78e56783cfe332791b8`.
The bounded implementation-verification artifact retains all 48 synthetic result
pins and the seven new implementation/script source hashes. Artifact SHA-256:
`18a7bd5e23365a3496e85d437c520ebd966143f137670b02292710bb23cf773f`.

## Validation and integration decision

Python: full strategy-engine suite, **175 tests**, zero failures. Ruff for src,
tests and new runner passes; configured strict mypy passes **31 files**.
The final regression also changes the caller's Decimal precision/rounding and
requires identical report identity; summary arithmetic uses an explicit context.
Java: full unit/architecture suite **1,205 tests**, zero failures/errors/skips.
Focused disposable integration: **11 tests**, zero failures/errors/skips
(`PostgresHistoricalBarRepositoryTest`, `KiteHistoricalPipelineTest`). This includes
a new two-instrument interruption/resume/replay test using synthetic prices and
provider, plus existing transport/export/provenance/conflict tests. Real broker
credentials or historical endpoints are not used by tests.

Full trading-core integration was not required: shared transport, authentication,
normal persistence, Spring settings, migrations, order/risk/operator/reconciliation
code are unchanged. Production Java additions are only historical plan/acquisition
composition; full architecture checks and focused disposable persistence tests
cover that boundary. Normal Flyway remains V10; test research V11 is explicit.

Project verification and secret scan passed with zero secret findings; whitespace
checks passed. The handoff repeats these checks after final evidence/documentation.
No retained Phase 11 fingerprint changed. Final source-freeze verification uses
the original verifier, not a replacement hashing convention.

Reproduction commands (from repository root unless stated):

```powershell
$env:PYTHONDONTWRITEBYTECODE = '1'
uv run --project apps/strategy-engine python scripts/research/run_phase120.py register
# Same local reference and SBIN exports reproduce retained evidence; no network.
uv run --project apps/strategy-engine python scripts/research/run_phase120.py verify
# Expected exit 1: UNIVERSE_NOT_CERTIFIED_NO_STRATEGY_EVALUATION
uv run --project apps/strategy-engine pytest apps/strategy-engine/tests -q
# From apps/strategy-engine:
uv run ruff check src tests ../../scripts/research/run_phase120.py
uv run mypy
# From repository root:
.\mvnw.cmd -pl apps/trading-core test
.\mvnw.cmd -pl apps/trading-core -Pintegration '-DskipUnitTests=true' '-Dit.test=PostgresHistoricalBarRepositoryTest,KiteHistoricalPipelineTest' verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

The offline Java harness compiles against target/classes and a classpath generated
by Maven dependency:build-classpath; running `VerifyPhase120Plan` reads only the
three frozen research JSON inputs and writes its bounded verification artifact.

## Safety and limitations

Outside synthetic tests: real order mutations, account trading reads, market-data
WebSockets, development DB accesses/mutations, token accesses/mutations, HALT
resume, execution arm, execute calls and live/paper execution are all **zero**.
No real runtime or DB was started or inspected; active real-runtime HALT state
was therefore not newly observed, and no before/after DB equality claim is made.
Isolation prevented access. No .env, emergency-stop, Phase 10 or INR 10000
first-live safeguard changes. No execution permits, authorizations, OrderRecords,
RiskDecisions or reconciliation changes outside isolated fixtures. No commit/push.

Remaining limitations: purposive historical-member sample, 2025 eligibility is
not a 2026 membership guarantee, incomplete corporate actions and token continuity,
four unacquired corpora, common-time market exposure, unadjusted prices, dated
cost scenario, fixed slippage/quantity and no liquidity or portfolio capital model.
The next necessary evidence is a complete authoritative per-member corporate-action
and continuity review; it cannot be replaced by strategy performance or a different
stock. A reviewed real acquisition entry point is still needed after those gates
pass. This handoff does not claim the real multi-instrument pipeline was validated.

## Exact changed/new file inventory

Two modified tracked files and eighteen new files; no deletions, staging,
commits or pushes. New research artifacts total about 57 KB; minute bars, raw
reference CSV and synthetic full report remain ignored under data/.

```text
M apps/strategy-engine/tests/test_research.py
M apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/PostgresHistoricalBarRepositoryTest.java
A apps/strategy-engine/src/strategy_engine/research/development.py
A apps/strategy-engine/src/strategy_engine/research/multi_instrument.py
A apps/strategy-engine/src/strategy_engine/research/universe.py
A apps/strategy-engine/tests/test_multi_instrument.py
A apps/trading-core/src/main/java/com/kitehybrid/platform/historical/application/MultiInstrumentCorpusAcquisition.java
A apps/trading-core/src/main/java/com/kitehybrid/platform/historical/application/MultiInstrumentCorpusPlan.java
A apps/trading-core/src/test/java/com/kitehybrid/platform/MultiInstrumentCorpusTest.java
A docs/architecture/multi-instrument-intraday-research.md
A docs/operations/phase-12.0-universe-plan.md
A docs/operations/phase-12.0-validation.md
A research/phase-12.0/acquisition-plan.json
A research/phase-12.0/corpus-manifest.json
A research/phase-12.0/implementation-verification.json
A research/phase-12.0/java-plan-verification.json
A research/phase-12.0/readiness.json
A research/phase-12.0/universe.json
A scripts/research/VerifyPhase120Plan.java
A scripts/research/run_phase120.py
```

`A` denotes new/untracked here, not staged additions. Git diff/stat reports only
the two tracked edits until new files are staged; the above inventory also
includes `git ls-files --others --exclude-standard` output.
