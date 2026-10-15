# Phase 14.15B reconstruction validation

Baseline: develop, HEAD and local origin/develop
1cad5f0f4d587837678e41cc3c64780a065a4edd. Initial worktree and index clean.
No fetch, staging, commit, push, reset, clean or protected-file restoration performed.
All 1150 baseline tracked raw hashes remain unchanged, including the five tracked bytecode
files involved in the earlier preservation failure. Original sources and diagnostics untouched.

## Recovery provenance

Frozen Phase14.15 plan recovered with SHA256
81ddd0c02da72621806cb6ca070d5253c7b076d97b8726efbda4880217bace6c.
IST amendment recovered with SHA256
7454bbd7c2241f0c005318b0510d38abad1d136b25d4eb23c14a0944e4c73d2e.
The immutable diagnostic inventory records transcript segment, IDE Local History/cache and
exact CSV source copies, external diagnostic snapshots and all baseline hashes. Seven cache
records decoded in memory have valid IntelliJ internal length/SHA1 checks; earlier revisions
are corroboration, not final recovered source. Cache record offsets and SHA256s are retained
in the additive manifest. The stable transcript segment preserves historical commands/results,
including initial fixture failures, final edits, preliminary reports and the IST amendment.

Source and tests were reconstructed from complete creation literals and reviewed later edits;
comparison with cached earlier revisions establishes recovery provenance, not byte identity.
Reconstructed source is NEW IMPLEMENTATION. Four complete documentation literals were recovered;
their original run commands are historical and are superseded by the procedure below.
The original validation and manifest never existed and have not been fabricated.

## Implemented behavior and scope

S01-S06 version1 long-only registry; S01 reuses Phase14 RSI/EMA same-bar signal and has trade
parity tests. Other strategies are research equivalents, not verified Streak catalog entries.
Session-local indicator warmup, completed-bar evaluation, contiguous next-open entry,
conservative stop-first collisions, stop gaps, EOD exits, fees, slippage, liquidity and capital
constraints are deterministic. Unknown calendar, corporate actions, identity or history denies
qualification. Six/twelve calendar-month windows use IST midnight serialized as UTC.

Chronological train/validation/holdout and three holdout folds have fixed parameters, separate
metrics, no split-spanning trades and per-pair qualification. Minimum100 OOS trades and30/fold,
70% observed OOS wins,60% per fold, positive after-cost expectancy,10% maximum drawdown and
Bonferroni/Wilson uncertainty controls remain frozen. Synthetic results cannot qualify.
Breakeven is included in the denominator but is not a win. No optimized stock-specific claims.

Sequential bounded jobs pin software hash, exact strategy version/parameters, universe, windows,
policy, costs and dataset provenance. Restart replays only matching records. Exclusive lock,
fsynced pending writes and atomic replace retain fail-closed interrupted-job behavior. This is
local research integrity, not malicious rollback resistance or physical power-loss certification.
Offline paper events have fingerprints, expiry and idempotency; no broker transport exists.

Fresh correction: direct ResearchWindow construction now also requires IST-midnight boundaries.
Four additional tests cover direct six/twelve-month boundaries and software/strategy checkpoint
mismatch denial. This is documented in the additive reconstruction amendment.

## Fresh validation and retained failures

Initial reconstructed suite:48 passed,0 failures/errors/skips.
After stricter validation:51 passed,1 failed; an old fixture expected quarantine for an invalid
window now rejected earlier. A guarded text replacement failed without writing; the unchanged
fixture rerun again gave51 passed,1 failed. Corrected expectation retains the fail-closed rule.
Final focused suite:52 passed,0 failures/errors/skips.
Full relevant Python suite:334 passed,0 failures/errors/skips.
Ruff:PASS. Mypy:PASS,52 source files. Project verifier:PASS.
Historical48/330 and seven initial fixture failures are historical, not fresh results.
Additional reconstruction attempts retained: existing ignored cache directory prevented mkdir;
extracted amendment lacked Path import and was corrected before editing; formatting/lint findings
were fixed only in new source/tests. No protected file changed in any attempt.
JUnit reports remain in owned external output storage; hashes and exact attempt totals are in
research/phase-14.15b/generation-1/test-results.json. No test caches/logs/binaries are proposed
for Git. Java/DB tests NOT_RUN: no Java, database or shared contracts changed.

## Deterministic reporting

Universe reconciliation:1140 source rows,25 duplicates,1115 enabled unique NSE symbols, preserved
ordering and special characters. Six strategies give6690 rows per window,13380 across both.
All13380 corrected rows are UNAVAILABLE with null performance,0 qualified. Genuine mappings0;
1115 MASTER_UNAVAILABLE. No sourced market candles or actual stock-wise performance exists.

Preliminary JSON regenerated SHA256:
16ff163b78ed62a7b47a0d62baec2b60c4df7d97076a0a54de74379f2c9d4926.
Corrected IST JSON regenerated SHA256:
ca4cbfd33e1b9bd07233f36cce445247fa2f89500982853fbd82b7d50856db6e.
Both match their historical expected values. Both CSVs match
63b46725ca1decf52a6ea812b0d1b575d6170b378cc6c294416919d2d5a6a749.
Root reports are SUPERSEDED_WINDOW_CONFIGURATION evidence. Only ist-corrected is authoritative.
Hash identity verifies generated bytes, not that the reconstructed source is the original.

Fresh benchmark:60 evaluations,76 synthetic bars,3.068314799806103seconds,
227085 peak Python allocation bytes. This is a fresh microbenchmark, not historical reproduction
or production/full-universe throughput. No synthetic candle history is represented as genuine.

## Safe reproduction procedure

Use installed apps/strategy-engine/.venv/Scripts/python.exe. Set PYTHONDONTWRITEBYTECODE=1 and
PYTHONPYCACHEPREFIX to a new owned external directory. Invoke every Python command with -B.
From apps/strategy-engine, use:

```text
python -B -m pytest tests/test_phase1415.py tests/test_phase1415b.py --assert=plain -p no:cacheprovider --basetemp=<new-external-focused-dir> --junitxml=<external-focused.xml> -q
python -B -m pytest --assert=plain -p no:cacheprovider --basetemp=<new-external-full-dir> --junitxml=<external-full.xml> -q
python -B -m ruff check --no-cache src tests
python -B -m mypy --cache-dir <external-mypy-cache>
```

Choose never-before-used owned basetemp paths: pytest can clear an existing basetemp directory.
Verify every baseline hash before and after each suite; stop on any difference. The external
cache prefix also avoids loading surviving ignored bytecode. Do not run the historical uv test
commands without these protections. Reports use create-exclusive outputs and a new destination.

From repository root, with the same environment, run twice:
`python -B scripts/research/verify_phase1415b.py`.
Each replay checks baseline, original13 newline reconciliations,18 frozen sources,63 research,
K/L/governance and historical envelopes; new hashes, exact inventory, LF, Git clean-filter
byte equivalence, whitespace, repository secret patterns, canaries and full result matrix.
The manifest fingerprint excludes itself. Replay outputs are retained outside the repository.

## Restrictions and disposition

Gate A/B NOT_AUTHORIZED; operational NO_GO. Real Kite calls0, credential reads0, vendor messages0,
orders0, deployment0, HALT changes0. Stage B ZERO_CALL_ABORT,Phase12BLOCK,H1/H2FROZEN,JulySEALED,
INR10000 ceiling and pending governance/reviews unchanged. No live-data readiness or win-rate
promise. Final acceptance requires both deterministic manifest replays to return PASS.

Replay correction: the first new scanner applied repository regexes to whole documents,
allowing whitespace matches across lines in .env.example. The repository scanner is line-based.
Corrected the replay to identical line-based semantics; no secret values were printed.
First candidate manifest and failed attempt remain retained externally. This is a scanner
implementation correction, not a secret exclusion or change to any protected file.
