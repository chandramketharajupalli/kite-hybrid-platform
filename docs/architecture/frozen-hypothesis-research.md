# Frozen H1/H2 implementation and development reuse

Phase 11.6 adds research callbacks, a completed-prefix feature, a bounded evaluation
API and an offline freeze/reuse tool. Existing strategy, feature, backtest, cost,
corpus and live modules remain unchanged. No application startup or signal routing
imports either candidate. These callbacks emit only the existing simulated Intent.

`CandidateSpec` is immutable, version G1, binds the Phase 11.5 registration and
source fingerprint, and rejects comparators other than quantity-one EMA 9/21 for
H1 or quantity-one VWAP_CROSS for H2. Its engine identity records the fixed rule
parameters. The wrapper supplies the original comparator parameter binding when
delegating to BaselineStrategy; no comparator crossover/sell rule is duplicated.

H1's `signed_efficiency_30` revalidates CompletedSession, uses exactly the last
31 closes/30 absolute changes and private Decimal precision 40 HALF_EVEN. Missing
history and a zero denominator return None. Only an otherwise valid original BUY
is gated at >=0.20; no queue/state is introduced. A future/patched invalid prefix
fails validation. No ex-post diagnostic module is imported by this feature/callback.

H2 has an explicit immutable PendingEntry(session_open, crossover_at) inside a
fresh run-local CandidateStrategy. Original upward crossing while flat creates only
pending state. The next chronological completed callback confirms strictly above
its own prefix VWAP, or consumes/discards the pending entry. A subsequent attempt
requires a fresh original crossover. The BUY decision at confirmation fills at the
following open under the unchanged engine, two bar starts after the crossover bar.
No extra waiting, volume threshold, buffered VWAP level or delayed exit exists.

Pending state clears on session change/end, entry expiry, position becoming long,
failed confirmation or invalid chronology. Missing bars/future-prefix corruption
raise and clear pending state; skipped/reversed/repeated callbacks discard and emit
no entry. These cannot occur in a valid sequential engine replay. Ordinary long
position exits delegate unchanged, and scheduled forced liquidation stays entirely
engine-owned. One fresh instance is created for each candidate evaluation; no
module/global pending state or reuse across research runs exists.

Both callbacks reject session overlap with local [2026-07-01,2026-08-01).
The development API rejects overlapping evaluation windows before constructing
either comparator or candidate, checks contained bars, and accepts only February–
June development bounds. There is no prospective or final-test API/CLI branch.
Full parent-corpus integrity loading is permitted; July is removed before simulation.
As elsewhere, this is a regression-enforced research boundary, not a sandbox against
arbitrary Python code bypassing the APIs.

The CLI has separate `freeze` and `reuse` modes. Freeze verifies the registration
document, embedded rules/criteria, parent report/corpus references and diagnostic
identity. It hashes normalized UTF-8/LF source text for all relevant dependencies
and the evaluation protocol, records configuration/comparator specs and fixed monthly
boundaries, and writes a conflicting-content-protected freeze artifact. Candidate
implementation identity binds the source map, hypothesis identifier and registration.
Absolute filesystem paths, random IDs and execution clocks do not enter these hashes.

Reuse requires that pre-existing freeze, recomputes it before every evaluation and
after the complete run, and stops if any source/config/protocol differs. Sources are
not edited after the first real candidate evaluation. A changed implementation needs
an explicit new identity/generation audit, not replacement of G1 evidence. Existing
freeze/report files are never silently overwritten. Tests are correctness evidence;
they are outside the candidate dependency fingerprint.

Five non-overlapping monthly partitions start flat/cold at the same INR 100000,
using the original quantity, entry/exit policy, 5-bps fills and dated fee schedule.
All twenty monthly candidate/comparator evaluations are retained. April–June
comparator result fingerprints must equal the Phase 11.4 validation results. February
and March are separate reused monthly partitions, not original fold results.

Full results are stored under ignored `data/phase116-reuse` in envelopes marked
DEVELOPMENT_REUSE_ONLY and bound to the freeze/result fingerprint. Compact reports
retain per-trade price-movement/slippage/fee attribution, daily P&Ls including idle
sessions, <=5-minute round trips, raw gross/trade, friction/session, original marked
drawdown, concentration and fixed-trade repricing at 0/5/10 bps. The primary scenario
remains 5 bps. Monetary reconciliation reuses the engine's exact Decimal context;
undefined diagnostic ratios are null. Development reports explicitly prohibit
confirmation claims and do not score prospective success criteria or select winners.

Part B stops at INSUFFICIENT_PROSPECTIVE_DATA as documented in the
[feasibility assessment](../operations/phase-11.6-prospective-feasibility.md).
No historical/account/DB/streaming/live/paper execution activity is introduced.
