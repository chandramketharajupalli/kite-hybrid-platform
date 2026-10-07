# Corporate-action continuity

Phase 12.1 adds public research metadata and a mandatory acquisition gate. It
does not change bars, strategy rules, baseline parameters, the backtester, or
the retained Phase 11 feature implementations. Prices remain provider OHLC:
no local split adjustment, dividend adjustment, or synthetic total-return series.

## Evidence and identity

`CorporateActionContinuityPolicy.v1` was written and fingerprinted before this
phase's instrument review. Its pin is enforced by Python and Java. The evidence
manifest records reviewed findings and explicit coverage limitations; it is not
a scraper output promoted automatically to certification. Current ZERODHA
reference IDs are copied from the frozen universe. They do not prove historical
ISIN, symbol, or NSE series continuity.

`EvidenceManifest` rejects duplicate evidence/instruments, unknown action types,
wrong-instrument references and broker-only certification. Instruments and the
source catalog are canonically sorted; action IDs order each instrument's actions.
Evidence-reference sequences retain their frozen review order. Coverage assertions
must identify their supporting sources. Hashes make reviewed content reproducible;
they do not authenticate a publisher or make an unsupported assertion true.

The classifier derives all seven component statuses, primary classifications and
boundaries from the frozen policy and evidence. Missing historical identity takes
precedence over incomplete action evidence, followed by segmentation, dividend
notes and continuous use. All members remain present. A missing or conflicting
dimension never defaults to certification. Access date is operational metadata
outside the evidence fingerprint. Corrections require a new evidence generation.

## Intraday and cross-session semantics

A confirmed cash dividend invalidates ordinary opening-gap interpretation into
the ex-date. It does not invalidate the session's internal OHLC sequence or
automatically invalidate historical same-clock volume observations. Boundary
`intraday_session_valid=true` describes the action's mechanics **conditional on
separate identity/bar certification**; it does not certify an unresolved corpus.

Splits, bonuses and face-value changes require segments. Other reorganizations
need reviewed listed-security mechanics. A subsidiary business transfer or
buyback announcement is not automatically a split of the listed parent's shares.
For quantity/security changes, volume history also resets where required.
Segments are deterministic half-open intervals. No splicing or adjustment exists.

New `continuity_features.py` provides an explicit `ContinuityContext` per
instrument/window. `opening_gap` returns `value=null, status=INVALID_BOUNDARY`
across an invalid price boundary, never zero. `previous_close` similarly returns
unavailable. `historical_early_volume` filters only volume-ineligible history;
ordinary dividends retain it. Future/incomplete sessions are rejected before
filtering. The context cannot be applied to another instrument or outside its
window, including July in the real Phase 12 context.

These are additive feature APIs for the next research generation. The old
diagnostics and H1/H2 feature callers remain frozen and are **not** retrofitted.
The new metadata supports the required behavior, but no existing Phase 11 result
has been corrected or recomputed. Phase 12.1's gate always reports strategy
evaluation disabled. A later study must explicitly use the new APIs before
cross-session diagnostic evaluation. Strategies remain unaware of symbols,
dividend dates, splits and certification policy.

## Feature inventory

Source inspection covers all research feature functions, not merely their names.

| Feature/source | Session-local | Previous session | Price boundary | Volume boundary | Required action |
| --- | --- | --- | --- | --- | --- |
| `features.simple_return`, SMA, EMA, RSI | Yes | No | No | No | Unchanged; `CompletedSession` is a contiguous prefix starting at confirmed open |
| ATR, rolling high/low | Yes | No | No | No | ATR's prior close is within the same session |
| Session VWAP, volume SMA, `features.relative_volume` | Yes | No | No | No | Unchanged; relative volume here compares bars within the session |
| Opening range; `opening_observation` 5/15/30 | Yes | No | No | No | Unchanged; completed prefix only |
| `candidate_features.signed_efficiency_30` | Yes | No | No | No | Frozen; no candidate execution in research |
| `opening_features.early_volume` | No | Last 20 confirmed sessions, minimum 5 | Ordinary dividend: no reset | Yes | New metadata-aware wrapper filters volume-ineligible sessions |
| `diagnostics.session_records` prior close, gap, gap percent/direction | No | Last confirmed close | Yes | No | New opening-gap/previous-close API; retained diagnostics unchanged |
| Full-session volatility, efficiency, VWAP distance, range | Yes | No | No | No | Ex-post diagnostic observations; not decision-time state |
| Diagnostic tertiles across sessions | No | Development distribution | Potentially via input gaps | Potentially via input volume ratios | Future generation must consume boundary-aware inputs; no recomputation here |
| Trade excursions/holding/churn diagnostics | Same instrument/trade | No prior-close reference | No direct overnight feature | No | No calculation in Phase 12.1 |

Synthetic tests verify dividend gap unavailability with retained volume history,
split state/history separation, deterministic segments, future-boundary isolation,
unchanged session VWAP/opening range, and wrong-scope rejection. Existing complete
test suites retain session-prefix, candidate quarantine and sealed-July regressions.

## Java acquisition enforcement

`MultiInstrumentCorpusAcquisition.acquire(plan, policyArtifact, certificateArtifact)`
calls `HistoricalContinuityGate.require` before the first delegate/repository/
provider operation. The legacy one-argument method now denies missing evidence.
There is no bypass flag. The gate requires the pinned policy body and hash,
certificate content hash, exact universe/window/member IDs, permitting primary
and component statuses, and representable dividend boundaries. Parsing is bounded
to 1 MB per artifact and rejects duplicate JSON keys. JSON keys are sorted and
ASCII-escaped consistently with Python when hashing; operational paths/times are
absent. A certificate's evidence fingerprint binds the reviewed manifest; Python
replay re-derives it before handoff. Reviewers must approve evidence, not just hashes.

Only the historical acquisition composition changes. No new transport, credentials,
Spring bean, database schema or execution capability is introduced. Existing
historical architecture tests prohibit dependencies on order/risk/operator and
execution controls. Counting fake-provider tests prove denial before provider or
repository access. Allowed synthetic acquisition preserves restart/replay behavior.

The real result remains BLOCK. There is still no real multi-instrument acquisition
harness in this phase. Even future ALLOW metadata would not authorize candles
here; Phase 12.2 requires the separately reviewed historical-only invocation.
