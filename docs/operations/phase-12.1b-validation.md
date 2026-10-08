# Phase 12.1B validation

Result: **UNIVERSE_CERTIFICATION_UNRESOLVED**; acquisition **BLOCK**. All five identity components remain certified, all five corporate-action completeness components remain unresolved. Four confirmed in-window dividend boundaries are retained. No known listed-EQ segmentation event was established by the reviewed records; that is not proof of complete absence.

## Baseline and immutability

Initial required commands passed: clean `develop`; HEAD and local `origin/develop` both `9193e0b1fe60f514fb867e5d887733ef81f42bbb`; clean whitespace. No fetch, reset, restore, stash, clean, commit or push occurred. The retained `docs/operations/phase-12.1-revalidation.md` is tracked and unchanged.

Before retrieval, all ten Phase 12.1A artifact hashes and all four retained implementation/report source hashes matched. All 99 cached original NSE security-master hashes also matched. The pinned universe, policy and Phase 12.1A certification fingerprints matched the request. Both old generations replayed unchanged.

At completion, all **39 files** under protected Phase 11.4/11.5/11.6/12.0/12.1/12.1A research directories retain their starting byte hashes. All **18** H1/H2 frozen-source checks pass the existing `run_phase116.verify_freeze` method. Its verifier was imported directly; its evaluation entry point was never called. Freeze file SHA-256 remains `56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`. All 99 original master hashes and all 102 new public-download hashes were checked again.

The sole production diff is the authorized additive `phase-12.1b-g1` generation literal in `continuity.py`; default, policy, hash, classification and gate semantics are unchanged. Its old source-file byte hash necessarily changes. This intentional code extension is distinct from the unchanged parent evidence/artifact hashes and the unchanged 18 frozen H1/H2 sources. No Java main/resource/configuration, broker transport, authentication, persistence, schema, order, risk, operator or reconciliation code changed.

## Executed validation

| Check | Actual result |
|---|---|
| Full Python suite, root command `uv run --project apps/strategy-engine pytest` | **225 passed**, 0 failed, 0 skipped; 10.08 seconds |
| New Phase 12.1B cases within full suite | **27 passed** |
| Retained continuity + Phase 12.1A cases within full suite | **18 + 5 passed** |
| Java `HistoricalContinuityGateTest` | **16 passed**, 0 failures/errors/skips |
| Java `MultiInstrumentCorpusTest` | **3 passed**, 0 failures/errors/skips |
| Configured Ruff from `apps/strategy-engine` | **PASS**, including new offline runner |
| Configured strict mypy from `apps/strategy-engine` | **PASS, 36 files** |
| Additional strict mypy for new offline runner | **PASS, 1 file** |
| Project verifier | **PASS**: Maven/JDK requirements, profiles, safety defaults, JSON |
| Secret scanner | **PASS**, zero potential secret locations |
| `git diff --check` | **PASS** |
| Phase 12.1 replay | Two identical offline runs; retained outputs match |
| Phase 12.1A replay | Two identical offline runs; retained outputs match |
| Phase 12.1B replay | Two identical offline runs; classifications, boundaries, certification and gate match |

New synthetic tests cover explicit full-period no-event evidence, empty-response rejection, date gaps, wrong ISIN/series/source, missing/duplicate categories, missing negative support, conflicting/incomplete assertions, event descriptors without events, announcement versus effective dates for dividends/splits/bonuses, rehashed proof with missing boundary, duplicate/conflicting source evidence, six frozen-input tamper cases, and offline replay with network/write functions denied. They do not evaluate any actual research strategy.

The existing feature tests verify dividend opening-gap suppression with historical volume preserved, session-local VWAP/opening range unchanged, segmentation/reset behavior and no future/incomplete history. No demonstrated deterministic feature defect required a change.

The real Java acquisition path was exercised only with synthetic fixtures and a counting fake provider. Missing certificate, unresolved identity/action member, segmentation member, missing member, wrong universe/window/policy, tampered fingerprint and missing boundary all throw before acquisition: **provider calls 0; repository and registry interactions 0**. Valid fake fixtures remain supported; a five-member representable-dividend certificate is accepted by the retained gate test. No actual provider, account, database or broker transport was invoked.

Full Java unit/architecture and disposable PostgreSQL integration suites were **NOT_RUN** in this phase. No Java production code, shared transport/auth/persistence/Spring/trading-schema code changed. The retained focused gate/acquisition tests directly cover the unchanged enforcement path. No development or research database access was required.

Validation iterations: the first Ruff run caught one 101-character new test signature, corrected by wrapping it. An extra repository-root Ruff invocation misclassified imports because its package-root context differed and reported eleven import-order diagnostics; no frozen imports were edited. One supplemental runner path was initially one parent directory too high. The final package-root command with `../../scripts/research/run_phase121b.py` passes. Python/Java tests had no failures. These tool-invocation issues do not alter evidence or policy.

## Replay commands

```powershell
$env:PYTHONDONTWRITEBYTECODE = '1'
uv run --project apps/strategy-engine python scripts/research/run_phase121.py
uv run --project apps/strategy-engine python scripts/research/run_phase121a.py
uv run --project apps/strategy-engine python scripts/research/run_phase121b.py
uv run --project apps/strategy-engine pytest
.\mvnw.cmd -pl apps/trading-core '-Dtest=HistoricalContinuityGateTest,MultiInstrumentCorpusTest' test
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
# From apps/strategy-engine:
uv run ruff check . ../../scripts/research/run_phase121b.py
uv run mypy
# From repository root:
uv run --project apps/strategy-engine mypy --strict --follow-imports=silent scripts/research/run_phase121b.py
git diff --check
```

The new runner has no network/provider imports. Default mode reads frozen inputs and compares retained derived files. `--write` creates missing outputs only and refuses divergent existing outputs. The supporting coverage check validates report consistency before invoking the unchanged classifier; it cannot manufacture authoritative evidence.

## Fingerprints and gate

- Universe: `638f11d8805f8d45724e8ade9a74e37652564046b699acd03033988ea4150395`
- Policy: `a350a24be35b6a844a0a769e8f14bd0821be3ca8bed44d4f8df5d32fc124f5f4`
- Parent certification: `e6322b88e23e85e57c4154b7907ef09f219135f918bd7510ad7e6cf9dd628c36`
- Research plan: `ba79ad7e485b7bbfcbe9c1ea8b46ad82d8b0acf0723c1da726e43435db56f54a`
- Evidence: `3397b6f843e7f7c975e109694bc6c88bde3a63b94d7748fa2f2317cd88ca59b4`
- Certification: `d23e50f52326a559119959797239eb6d19385c0a917e03f7e7dc2a6e1a34f1f6`
- Gate: `38f793940272d99758e684abcbbdd282e5cf1ff1ff745f52c6036ab9a791f8fc`

The gate wrapper fingerprints the unchanged existing gate dictionary. Its exact reasons are:

```text
HDFCBANK:CORPORATE_ACTION_UNRESOLVED
ICICIBANK:CORPORATE_ACTION_UNRESOLVED
LT:CORPORATE_ACTION_UNRESOLVED
RELIANCE:CORPORATE_ACTION_UNRESOLVED
SBIN:CORPORATE_ACTION_UNRESOLVED
```

Acquisition and strategy evaluation are both false. All five members remain in the frozen universe. Further authoritative completeness research is required; Phase 12.2 was not started.

## Safety and Git scope

Actual real activity: historical candle GETs **0**; broker/account reads **0**; order placements/modifications/cancellations **0**; WebSockets **0**; database access/mutations **0**; token access/mutations **0**; HALT resume **0**; execution arming/calls **0**; real strategy evaluations **0**; cross-instrument P&L calculations **0**. Synthetic tests do not count as real execution. H1/H2 G1: **FROZEN / UNCHANGED / NOT EVALUATED**. SBIN July TEST: **SEALED**. No `.env`, Phase 10 safeguards or first-live ceiling changes.

Git scope: **1 modified tracked file, 17 new files**. Ordinary diff shows only the generation-literal extension (3 insertions, 1 deletion); untracked files are explicitly inventoried below. No Java production/resource changes, bytecode, raw minute bars, credentials or downloaded PDF/archive payloads are proposed. Ignored public source downloads are not Git changes. No commit or push.

Modified:

```text
apps/strategy-engine/src/strategy_engine/research/continuity.py
```

New:

```text
apps/strategy-engine/tests/test_phase121b.py
docs/operations/phase-12.1b-evidence.md
docs/operations/phase-12.1b-research-plan.md
docs/operations/phase-12.1b-validation.md
research/phase-12.1b/generation-1/access-log.json
research/phase-12.1b/generation-1/acquisition-gate.json
research/phase-12.1b/generation-1/announcement-review.json
research/phase-12.1b/generation-1/continuity-boundaries.json
research/phase-12.1b/generation-1/corporate-action-coverage.json
research/phase-12.1b/generation-1/corporate-action-events.json
research/phase-12.1b/generation-1/evidence-freeze.json
research/phase-12.1b/generation-1/evidence-manifest.json
research/phase-12.1b/generation-1/instrument-certifications.json
research/phase-12.1b/generation-1/research-plan.json
research/phase-12.1b/generation-1/source-inventory.json
research/phase-12.1b/generation-1/validation.json
scripts/research/run_phase121b.py
```

See [evidence report](phase-12.1b-evidence.md) for the five-member table, source coverage, original URLs, LT decision and exact residual attachment queues. Next action: a separately scoped continuation of authoritative category completeness and older pending-event reconciliation for all five members. Do not acquire or evaluate the common corpus while these gaps remain.
