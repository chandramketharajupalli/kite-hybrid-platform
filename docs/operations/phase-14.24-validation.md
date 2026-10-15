# Phase 14.24 offline validation

Baseline: 6d33bc50709a613e92feb462a71a8d52983615aa on develop, matching local
origin/develop. Phase 14.23's ten artifacts are committed and match their sealed hashes.
All 1,314 baseline tracked raw hashes remain protected. G1, eighteen frozen source hashes,
sixty-three research files, K/L governance, historical manifests and thirteen Phase 14.0A
newline reconciliations verify. The universe remains 1,115 enabled unique NSE symbols and
the research family remains 13,380 combinations with null genuine performance and zero
genuine qualified pairs.

The separate untracked Phase 14.22 plan remains excluded and unchanged at
e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31.
The Phase 14.24 plan was hashed before implementation:
f6a015a6dca34673a2b400c24b0b36d038abf475614c03b688bd3982a489e4bd.

Fresh focused tests: 56 passed. Fresh full Python suite: 822 passed, zero failures/errors/
skips. Ruff source/tests and verifier pass. Strict mypy passes across 83 files. After the
full suite, a test-only timezone.utc alias was changed to datetime.UTC for lint; the affected
UTC test was rerun and passed (one additional targeted test). Implementation bytes were
unchanged. No Java contracts changed or Java tests ran.

All commands used Python -B, PYTHONDONTWRITEBYTECODE=1 and external caches/temp/logs.
Pytest used --assert=plain -p no:cacheprovider. Ruff used --no-cache, and mypy's cache was
external. Raw tracked and excluded-plan hashes were checked before and after each gate.
Receipts are under C:/Users/CHANDR~1/AppData/Local/Temp/phase1424-efqraee3.

Initial failures remain preserved: focused-1 had 38 passes and one test expectation using
default rather than frozen Decimal precision; focused-2 passed 47 tests. Expanded focused-3
had 55 passes and one test assuming direct IST transport was accepted; the existing contract
requires UTC. The corrected test verifies rejection then explicit conversion. Focused-4
passed 56. Initial lint receipts preserve import/line-length and UTC-alias issues, followed
by successful reruns. No protected source was changed to resolve any failure.

The complete synthetic two-session example covers 3,600 seconds with 100% evidence coverage.
Capital-time is 1,224,000 INR-seconds; average gross/net/long exposure is INR 340; maximum
exposure is INR 1,020. Time in market is 1,200 seconds; explicit zero exposure is 2,400
seconds. Average exposure fraction is approximately 0.03400330723. Exposure-adjusted return
is approximately -0.00638049936 (not annualized). Accepted net P&L remains -2.16958080.
S06/SBIN account for observed exposure and P&L; the remaining strategies and symbols have
verified zero exposure in this fully observed fixture. Partial and missing examples retain
observed subsets but null full exposure and adjusted returns. No missing evidence becomes
false zero exposure. All six strategies and 1,115 symbols are represented and reconcile.

Synthetic incident ground truth contains ten independently authored incident labels; it is
not generated from observed alerts. The deliberately imperfect emitted ledger gives two
timely true positives, three false positives and eight false negatives. One duplicate and
one late alert are explicit false positives. Precision 0.4, recall 0.2 and F1 0.266666...;
timely delays 10 and 15 seconds, maximum 15 seconds. Eight critical incidents are missed.
No resolution is inferred from acknowledgment; HALT is never cleared. Without independent
truth, effectiveness is NOT_EVALUATED. These are measurement tests, not proof of production
detector effectiveness or successfully resolved incidents.

Coverage includes missing/duplicate/overlapping intervals, missing marks, equity/ownership/
policy checks, causal prefix invariance, Decimal-context invariance, holidays/weekends,
special-session zero exposure, IST/UTC conversion, unresolved EOD, attribution, upstream
corruption, changed truth and alert hashes, one-to-one competing matches, acknowledgment,
resolution, interrupted publication, replay and absence of broker imports.

The three report cases replay identically in independent external publication directories.
The read-only evidence verifier recomputes exposure and monitoring, validates raw receipts,
baseline hashes, policy/software fingerprints, exact inventory, historical preservation,
LF/Git-byte equivalence and secret/canary scans. Certification requires two identical
manifest replay outputs and a separate external final certificate and command receipt.

Engineering: VALIDATED_OFFLINE within the explicit synthetic constant-mark convention.
Genuine strategy: BLOCKED by genuine data/mappings/calendar/corporate-action/qualification
gaps. Operational: BLOCKED / NO_GO. Local hashes do not establish independent rollback
resistance, production durability, genuine monitoring effectiveness or future profitability.
The earlier Phase 14.21 PARTIAL and Phase 14.22 pending evidence remain unchanged.

Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN;
July SEALED; operational NO_GO. Real Kite requests: zero. No credentials, network,
downloads, orders, notifications, infrastructure, operational HALT changes, staging,
commit or push.
