# Phase 12.1C validation

Final outcome: **UNIVERSE_CERTIFICATION_UNRESOLVED; acquisition BLOCK**. The implementation and gate are reliable under the checks below; incomplete official coverage, not a technical failure, is the primary blocker. No known material in-window listed-EQ segmentation event was established. This is a bounded research result, not a claim that all requested original questions were closed.

Baseline preflight passed before edits: clean `develop`, HEAD and local `origin/develop` both `1624d34b0e60d53e534fb7b13d37e5577462a4cf`; eight-entry log inspected; whitespace clean. No fetch, reset, restore, stash, clean, commit or push.

## Executed checks

| Check | Actual result in this run |
| --- | --- |
| `uv run --project apps/strategy-engine pytest` | 250 passed, 0 failures, 0 skips; final run 8.09 seconds. Includes 25 new Phase 12.1C tests, 27 retained Phase 12.1B tests and 18 continuity tests. All fixtures are synthetic; no real strategy study invoked. |
| `uv run ruff check . ../../scripts/research/run_phase121c.py` from `apps/strategy-engine` | PASS |
| `uv run mypy` from `apps/strategy-engine` | Strict configuration; PASS, 37 source files |
| `uv run --project apps/strategy-engine mypy --strict --follow-imports=silent scripts/research/run_phase121c.py` | PASS, 1 runner file |
| `mvnw.cmd -pl apps/trading-core -Dtest=HistoricalContinuityGateTest,MultiInstrumentCorpusTest test` | 19 passed: gate 16, corpus 3; 0 failures, errors or skips |
| `uv run python scripts/verify-project.py` | PASS: JDK/Maven requirements, profiles, safety defaults and JSON syntax |
| `uv run python scripts/check-secrets.py` | PASS; 1,643 text files scanned at the recorded pre-final-artifact run, zero potential secret locations; repeated after final files |
| `git diff --check` | PASS; new text files also checked separately for trailing whitespace |
| New offline replay, twice | PASS; identical classifications, source/event ordering, 80 coverage cells, boundaries, certification and gate |
| Prior Phase 12.1 / 12.1A / 12.1B offline replays, each twice | PASS; exact retained derived artifacts and original fingerprints |
| 18 frozen source checks / H1-H2 freeze | PASS using retained `run_phase116.verify_freeze`; no evaluation CLI invoked |
| Protected research files | All 51 initial byte hashes unchanged |
| Cached original evidence | 99 Phase 12.1A masters, 102 Phase 12.1B source snapshots and 420 new downloaded byte hashes verified; no repeated master downloads |

No Java production file changed, so full Java unit/architecture and PostgreSQL integration suites were **NOT_RUN**, not counted as passes. Retained focused Java tests exercise the unchanged acquisition boundary. No shared transport, authentication, persistence, Spring configuration, schema, order/risk/operator/reconciliation or migration changed; neither development nor disposable PostgreSQL was needed.

The Java denial parameterization covers missing certification, wrong universe/window/policy/fingerprint/member/identity, unresolved identity/actions, segmentation and missing boundary/component semantics. Each denial asserts a counting provider remains at zero and `verifyNoInteractions(repository, registry)`. Missing-policy and legacy-signature bypass attempts also fail before the delegate. Valid certificates use fake delegates only. Python tests additionally cover tampered evidence inputs, source identity/legacy ISIN, missing/duplicate originals, author versus host, conflicting corrections, subsidiary/debt versus EQ, appointed versus effective date, incomplete negative coverage, out-of-window August 3 ICICI dividend, dividend price-gap versus volume handling and split/bonus segmentation. No symbol/date strategy special case was added.

## Replay and evidence integrity

| Artifact | Verified fingerprint |
| --- | --- |
| Universe | `638f11d8805f8d45724e8ade9a74e37652564046b699acd03033988ea4150395` |
| Policy | `a350a24be35b6a844a0a769e8f14bd0821be3ca8bed44d4f8df5d32fc124f5f4` |
| Phase 12.1A certification | `e6322b88e23e85e57c4154b7907ef09f219135f918bd7510ad7e6cf9dd628c36` |
| Phase 12.1B evidence | `3397b6f843e7f7c975e109694bc6c88bde3a63b94d7748fa2f2317cd88ca59b4` |
| Phase 12.1B certification | `d23e50f52326a559119959797239eb6d19385c0a917e03f7e7dc2a6e1a34f1f6` |
| Phase 12.1B gate | `38f793940272d99758e684abcbbdd282e5cf1ff1ff745f52c6036ab9a791f8fc` |
| Closure plan | `19cb9a8fc18bedaf8a22422354cef65913b90f8f44be49f2e8d32ad90e95c679` |
| Phase 12.1C evidence | `1f7a6101102a64b29756fbd4277888bfd213f7d2de56d3e5dcd7bdd151b07a36` |
| Phase 12.1C certification | `777612acd9dffb6fc8f3ca8994e0478b77401637ecea7ce50f106b577686f662` |
| Phase 12.1C gate | `f00e764e9ecfb895f2697a746b53c3d46856c34ecf5384fa7d3cba00dd91f784` |
| H1/H2 freeze file SHA-256 | `56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656` |

The new replay reuses `continuity.py`, `certify`, `acquisition_gate`, canonical fingerprinting and the retained Phase 12.1B coverage validator. Its checks bind the exact pending ID set, original coverage lineage, parent certificate/gate and input byte hashes. The only production edit adds `phase-12.1c-g1` to the accepted generation literals; default, frozen policy, classifier and gate semantics are unchanged. Earlier source hash records for `continuity.py` are historical snapshots: they passed baseline verification; the current intentional one-line extension is explicitly disclosed. All other prior source byte hashes pass.

The first new replay exposed an inventory adapter `KeyError`: quality is canonical in Evidence, while the source inventory records author/host. The runner now reads canonical quality before validating authorship. No frozen evidence was changed to fix it, and no derived artifact existed before the successful replay. An audit helper initially used normalized text for prior B Markdown byte hashes; comparison under their actual byte-hash contract passed. No protected source mismatch was found. Final tests have zero failures.

## Operational audit and Git scope

Historical Kite GETs **0**; broker/account reads **0**; order mutations **0**; WebSockets **0**; development/research DB writes **0**; token mutations **0**; HALT resumes **0**; arm/execute calls **0**; real strategy evaluations **0**; cross-instrument P&L **0**; live/paper activity **0**. Synthetic gate fixtures are not real execution. H1/H2 remain FROZEN / UNCHANGED / NOT EVALUATED; SBIN July TEST remains SEALED. Raw bars and broker responses were not requested. `.env`, Phase 10 safeguards and the first-live ceiling are unchanged.

Expected final scope, checked against the explicit untracked inventory: **1 modified, 17 new**. Modified: `apps/strategy-engine/src/strategy_engine/research/continuity.py` (generation literal only). New: `tests/test_phase121c.py`, `scripts/research/run_phase121c.py`, the three Phase 12.1C operations documents and twelve generation JSON artifacts. No Java main/resources, configuration, migrations, unrelated production paths, bytecode, credentials or source PDFs are in the Git diff. Public originals and temporary research helpers remain under ignored `data/phase121c-reference/` and are not staged.

The [evidence report](phase-12.1c-evidence.md) contains every pending ID, all 80 prior/new cells and all 420 original source hashes. The [machine validation](../../research/phase-12.1c/generation-1/validation.json) retains integrity and test results. The [closure plan](phase-12.1c-closure-plan.md) is unchanged. No acquisition or Phase 12.2 work started; no commit or push.
