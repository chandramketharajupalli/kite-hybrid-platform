# Phase 14.17 offline validation

Baseline: `9b3c7f13d4e5c7cbcd29f74b54d1b390d6f7dd64`, develop and local
origin/develop. All 22 Phase14.16 additions were committed before this work.
All 1211 baseline tracked files remain immutable, including protected bytecode.
The exact additive inventory, raw SHA-256 hashes, dependency inventory, commands,
external receipt hashes and prior-attempt archive are in generation-1/evidence-manifest.json.
No staging, commit, push, broker access or operational configuration change occurs.

Frozen plan SHA-256:
`7437b901e18cc4038cfd5ce37ec20517e3f82706d31274441ddf4038c069e4a6`.
Additive amendment 1 SHA-256:
`9d47f668af5a6259dbc2184eea47c010584ead542c773d5d7014b37dc818f3ca`.

## Fresh validation and retained attempts

Final receipt identities are focused-certified (37 tests), full-certified (498 tests),
ruff-certified, mypy-certified-full (62 source/test files), generate-corrected and
ruff-verifier-final. Acceptance requires zero exit codes, zero test failures/errors/skips,
matching raw receipt hashes and matching test counts. The read-only verifier enforces
these requirements; this document does not substitute for executable receipt checks.
Use Python -B and PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain -p no:cacheprovider,
external basetemp/JUnit, Ruff --no-cache and external mypy cache. No Java code changed.

Earlier runs are retained separately: 31 focused tests, then 36 focused and 497 full
tests before the final Decimal correction. Initial Ruff E501 and mypy local-variable
type errors were corrected only in additive source. The new precision regression
failed once under differing caller Decimal contexts, then passed after the paper
simulator adopted the existing PRECISION context. The failed receipt and passing
rerun are retained. The final full suite includes this regression.

The 20 pre-correction draft files were archived with exact hashes before correction.
Only unsealed new Phase14.17 report drafts were refreshed, as amendment 1 specifies.
No Phase14.0-14.16 evidence or prior external receipt was overwritten. External logs
and archive live under the receipt directory declared in the evidence manifest.

## Results and limitations

The genuine-data ledger contains exactly 13380 rows: 6690 six-month and 6690
twelve-month combinations, with the canonical 1115 stocks and S01-S06 unchanged.
All 13380 are DATA_UNAVAILABLE with null performance; genuine qualified pairs are zero.
There are no observed genuine rejections or wins to infer from unavailable data.
CSV/JSON equality, ordered full-family coverage, deterministic replay and report-tamper
rejection are checked. Synthetic proof and paper events remain explicitly separate.

Qualification reuses the frozen 70% target, minimum sample, Wilson/Bonferroni confidence,
drawdown and fixed-parameter chronological split rules. Tests cover threshold boundaries,
rejection precedence, insufficient data, train/holdout separation and parameter binding.
The confidence method retains correlated-trade limitations and is not proof of a 70%
true win probability. No optimization or new statistical policy is introduced.

The synthetic paper example has nine chained events through EOD close. Tests also cover
authorization, idempotency, invalid transitions, tamper detection, cash/position sizing,
stop-first collisions, target fills, daily-loss denial, missing liquidity, isolated HALT,
forward-window separation and future-candle invariance. Partial fills explicitly reject
as unsupported by the frozen all-or-none executor. Each run is an isolated single-pair
account; separate runs do not share portfolio capital. A local hash chain supplies
tamper detection against a retained anchor, not independent rollback resistance.

Genuine mapping, authorized historical data, reviewed calendar/corporate-action evidence
and complete approved paper risk limits are absent. Genuine paper readiness is BLOCKED.
Missing risk values fail closed; synthetic limits are never promoted to genuine policy.

## Final certification

The manifest binds all 21 other additions; its canonical fingerprint avoids a self-hash.
The external post-seal certification named in the manifest records the manifest's raw
hash, exact 22-file Git inventory and blobs, two byte-identical verifier replay outputs,
negative verification cases and final preservation checks. Certification requires both
replays to pass; missing external evidence is an error, never a fabricated PASS.

The verifier checks all 1211 baseline hashes, 13 Phase14.0A newline reconciliations,
G1, frozen source/research records, K/L and historical manifest fingerprints. It checks
LF/Git-byte equivalence, exact untracked inventory, no index/tracked changes, and
secret/canary scans. Original Phase14.15D/14.16 receipts remain historical evidence.

Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN;
July SEALED; operational NO_GO. Real Kite requests: zero. No genuine trading results.
