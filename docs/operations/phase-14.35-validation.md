# Phase 14.35 validation

Final focused tests: 37 passed. Relevant full audit regression set (Phase 14.33B, 14.34A and 14.35): 105 passed, including the 37 focused tests; totals are not additive. Ruff passed; strict mypy passed two new scripts. No trading implementation or Java contracts changed, so the trading-engine/Java suites were not rerun and their historical totals are not presented as fresh results.

Initial focused tests passed 37. Initial Ruff found one import-order issue and initial mypy found one optional-duration narrowing error. Import grouping and an explicit post-validation type assertion corrected these; initial and final receipts remain separate. No failed evidence was removed. Tests use synthetic sources, tokens and candles only. No broker requests, genuine performance claims or network imports in the new validator.

External command receipts and JUnit files are under phase1435-1e9dc75_ in the local temporary evidence root, with exact paths/hashes in the manifest. Python -B, PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain -p no:cacheprovider, external basetemp, Ruff --no-cache and external mypy cache were used. All 1,472 baseline raw hashes and the nine pre-existing untracked files were rechecked after each final gate.

Historical preservation passed: 13 newline reconciliations, 63 research files, 18 frozen sources, K/L governance and parent manifests, canonical 1,115-symbol universe and 13,380 research rows with zero genuine qualified pairs. The frozen plan documents the three already-committed Phase 14.30 changes reconciled only in memory for the legacy historical verifier. The existing eight Phase 14.34A additions remain untracked and unchanged, separate from this package.

Retained installation certificate/evidence fingerprints match the requested values. Nine application/dependency raw archive hashes, retained installer hash and official-index metadata, 55 acquisition custody receipt files, 33 installation receipt files and 902 installed-file inventory hashes were verified. The historical certificate records nine installed distributions, 886 RECORD hashes, six synthetic strategies and UTC/IST conversion. Two retained final runtime outputs and two evidence replay results agree. No new installation/runtime execution was performed.

Original Zerodha response custody remains unavailable in the identified inputs. No exact clauses, sender metadata or timestamps were invented. The user's reported topics remain unverified paraphrases; acquisition and retention stay unauthorized. External correspondence and private directories were not accessed.

The final seal requires all 15 new files to pass LF/Git-byte equivalence, whitespace, secret/canary checks and two read-only manifest replays. Separate external final certificate and command receipt record completion without a circular manifest hash. This is partial readiness evidence, not data-use permission or production acceptance.
