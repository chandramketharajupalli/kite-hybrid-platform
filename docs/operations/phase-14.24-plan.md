# Phase 14.24 frozen offline measurement plan

Baseline: 6d33bc50709a613e92feb462a71a8d52983615aa, develop and local origin/develop.
All ten Phase 14.23 files are committed. All 1,314 tracked files are preserved.
The sole untracked exception is docs/operations/phase-14.22-plan.md, SHA-256
e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31.
It is outside this phase. Baseline hashes are captured in external private validation storage.

Phase 14.20 outcomes supply immutable accepted session, order, cash and alert records.
Phase 14.21 validate independently replays them; Phase 14.23 accepted fills and canonical
serialization are reused. Phase 14.18A risk v2 and Phase 14.18B causal execution v1 remain
unchanged. Synthetic engineering evidence cannot authorize genuine data or trading.

Exposure convention: explicitly evidenced synthetic constant-mark intervals, valued using
information available at interval start. Interval end is a coverage boundary, not permission
to interpolate missing prices. Explicit boundary snapshots are required, including zero
positions. Each interval must be supported by a predeclared constant synthetic quote
segment; accepted position changes split intervals. Market values and equity reconcile
against accepted cash/fills. No future candle or reference-price benchmark supplies a mark.
UTC instants and Asia/Kolkata calendar membership determine Decimal seconds; holidays have
no session and special-session durations come from the existing calendar. Partial coverage
has subset metrics but null full-session metrics. No short positions are supported.

Capital-time units are rupee-seconds. Average gross exposure is capital-time divided by
covered seconds. Exposure fraction is integrated market-value/equity. Complete net return
divided by average gross fraction and net P&L divided by capital-time are descriptive,
nonannualized synthetic measurements. Missing boundaries, unresolved positions, invalid
accounting or zero denominators yield NOT_COMPUTABLE. All six strategies and 1,115 symbols
remain represented; missing evidence is distinct from verified zero exposure.

Monitoring ground truth is a separately authored synthetic incident schedule, never derived
from observed alerts. Versioned matching uses run/date/category, start through deadline,
incident start then ID and alert timestamp then ID. One alert matches one incident. Late
alerts do not count as timely true positives; unmatched and duplicate emitted alerts count
as false positives. Unmatched incidents count as false negatives. Zero denominators are
NOT_COMPUTABLE; absent truth is NOT_EVALUATED. Acknowledgment, resolution and HALT clearance
remain separate. Ground-truth independence is a fixture provenance claim, not production
detection certification.

Allowed changes: additive phase1424 Python package, focused tests, these phase documents,
synthetic evidence and a read-only verifier. No existing source or evidence changes.
Budgets: at most 252 sessions, 50,000 observations/incidents/alerts, 64 MiB input, bounded
category matching and deterministic single-worker processing. Full-family qualification
remains 13,380 combinations, zero genuine qualified pairs.

Validation: focused exposure/monitoring/integration tests, full Python suite, Ruff no-cache,
strict mypy external cache, deterministic report and manifest replays, raw baseline hashes
after each gate, historical newline verification, LF/Git-byte equivalence and secret/canary
scans. Use Python -B, PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain -p no:cacheprovider,
external temporary/JUnit/diagnostic paths. Preserve failures and corrective runs externally.

Stop on protected evidence change, unexpected Git inventory, causal/accounting ambiguity
requiring frozen-source edits, or unauthorized data. Scope corrections require additive
amendments. Evidence records exact inventory, policies, source fingerprints, test receipts,
reconciliation, fixture provenance, limitations and two deterministic manifest replays.
Deliverables are the requested six documentation files, evidence manifest, minimal source,
tests, synthetic reports and verifier. No staging, commit, push, broker/network requests,
credentials, notifications, infrastructure or operational HALT changes.

Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN;
July SEALED; operational NO_GO.
