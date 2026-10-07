# Multi-instrument NSE intraday research foundation

Phase 12 adds a research-only composition layer. Java retains historical
planning, ingestion, persistence and bounded v1 export ownership. Python
validates each instrument separately and runs independent cash ledgers. No
application bean, scheduler, execution port, authentication change or migration
is introduced. Current real-universe certification is blocked; implemented
capabilities have synthetic unit and disposable PostgreSQL verification.

## Identity and certification boundary

`ResearchUniverseSpec` records dated eligibility evidence, the actual protocol
registration date, fixed current reference identities, criteria, common window
and calendar pins. Members are immutable and canonically sorted by venue,
symbol and platform ID. Duplicate IDs, symbols or broker IDs fail. NSE CASH IDs
use the existing Java length-prefixed semantic identity contract, independently
verified by the offline Java plan harness. Broker IDs are ZERODHA-scoped public
reference identifiers, never authentication tokens or production constants.

The original Phase 11.4 calendar fingerprint is retained; a second pin binds
the ordered February–June bounded calendar segments derived from that source.
Per-instrument v1 exports retain original schema, artifact/content fingerprints,
source provenance, no-look-ahead cutoffs and interval-start semantics. Raw bars,
broker CSV and full simulated trade envelopes remain under ignored `data/`.

`MemberCertification` distinguishes absent observations (`null`) from measured
zero errors. Certification requires complete expected sessions/bars, zero gaps,
duplicates, conflicts and unexpected timestamps, no excluded sessions, first/last
timestamps, independent content/corpus pins, provenance and reviewed corporate
continuity. A valid bar corpus alone cannot resolve a corporate-action review.
The cross-instrument manifest always retains the entire original member set.
Its evidence fingerprint exists even when failed; a **certified aggregate
corpus fingerprint is null until every member certifies**.

`MultiInstrumentResearchCorpus` accepts only the full certified manifest,
revalidates all underlying Corpus/v1 objects, and enforces identity, calendar,
window, actual counts, endpoints and independent hashes. Its immutable tuple
keeps separate timelines and ledgers. No convenient missing-member filtering,
latest-data lookup or cross-instrument bar concatenation exists.

## Acquisition and replay

`MultiInstrumentCorpusPlan` composes 2–10 existing `HistoricalCorpusPlan`
objects, canonically orders their immutable platform IDs, checks common dates
and original calendar fingerprint, and bounds the sum of all request ceilings.
Its identity includes the frozen universe and every per-member plan hash.
The offline Java verifier reconciles each of the 495 recorded daily chunks,
counts and platform IDs against these production plan classes before any
provider is needed.

`MultiInstrumentCorpusAcquisition` invokes the existing acquisition sequentially.
It introduces no HTTP transport or downloader. Session commits, provenance
checks, conflict denial and complete-local-session reuse remain in the existing
service. A member exception propagates immediately; no partial universe result
is returned. Restart re-verifies completed members and continues missing daily
work. A partial persisted session still fails rather than being silently repaired.
Replay excludes call/insert counts from aggregate content identity.

This phase has **no runnable real multi-instrument acquisition harness** while
corporate-action certification is unresolved. The conditional plan is not an
execution permit. A later reviewed invocation must bind the already-declared
route/query allowlist, one-start/second pacing, exact request counters, read-only
development/token checks, active HALT and isolated research V11 database to the
existing Phase 11 acquisition adapters. It must not invoke the original SBIN
February–July acquisition script, which has a broader window than Phase 12.

## Development evaluation boundary

`DevelopmentWalkForwardSpec` is additive. It reuses Fold, Window, baseline specs,
engine and configuration semantics while providing only TRAIN and VALIDATION.
It has no final-test window, dummy dates, TEST result discriminator or callback
that accepts arbitrary strategies. All Phase 12 windows end no later than the
start of July. Existing Phase 11 APIs and frozen source files are unchanged.

Before baseline parsing, a recursive guard rejects H1/H2 names, candidate
envelopes and both frozen G1 implementation hashes. Only `BaselineStrategy` is
constructed by the evaluator. The multi-study type requires all four original
Phase 11.4 parameterizations and identical configuration, folds and implementation
identity across every member. The runner additionally takes exact costs,
slippage, cash, cutoffs, folds and original baseline identities from the retained
Phase 11.4 specification. It verifies the complete Phase 11.6 source freeze
before any mode runs.

The runner's ordered gates are: registration, certified-manifest verification,
independent bounded-export validation, separate `freeze` command, then
`evaluate`. Evaluation refuses absent/different study pins or changed study
source. The study source pin covers all three new Python modules and the runner;
the pre-existing engine/features/baselines are protected by the unchanged
Phase 11.6 source verification. There is no network, authentication or DB path
in the Python runner. A failed manifest blocks before any strategy computation.

Each atomic result retains instrument, corpus, strategy, fold, partition,
window, exact engine result fingerprint and descriptive accounting. Raw gross
minus slippage reconciles to engine gross; subtracting fees reconciles to net.
Fold identity binds instrument, corpus, parameters, dates, cash/cost/slippage
configuration and implementation. Full result envelopes are content-addressed
locally. Reports reject incomplete evaluation matrices, wrong member identities,
noncanonical member order, altered summaries and dropped limitations.

## Interpretation and verification

Results are retained per instrument before summaries are calculated. Validation
summaries use exact counts and Decimal medians, with explicit 40-digit,
ROUND_HALF_EVEN context for ratios, independent of caller arithmetic settings;
financial accounting uses the existing exact engine and 80-digit reconciliation.
Insufficient samples take precedence over profit labels. Positive/negative raw
counts remain descriptive; sufficient positive evidence is reported separately.
Concentration is the largest absolute instrument net divided by sum of absolute
instrument nets, not a portfolio weight, return, ranking or promotion rule.

The same month's stocks share market-wide exposure. Cross-instrument breadth is
not independent temporal confirmation. No expanding TRAIN totals are pooled,
no portfolio cash competition is modelled, and no winner is selected. Prices
remain unadjusted under the existing unspecified-source-adjustment policy;
per-member corporate-action notes remain embedded in every report.

Tests cover canonical identities, duplicate/future-evidence denial, complete
member certification, common calendar/window pins, changed corpus propagation,
future partition and other-instrument isolation, unchanged fixed parameters,
H1/H2 denial, absence of TEST, missing-result rejection and deterministic report
replay. Existing Java historical architecture guards cover the added classes
and prohibit order/risk/operator/broker execution dependencies. Python AST
guards retain the no-network/no-I/O boundary with narrowly scoped deterministic
hashing/UUID/serialization/statistics helpers. These are regression protections,
not a sandbox for arbitrary hostile Python.
