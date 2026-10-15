# Synthetic session policy

Required risk schema: `Phase1418RiskPolicyCanonical.v2`.

Required risk fingerprint: `3dd7a1cf99042ce027011f6a99ca1f298ab62c6254bc85e584171250a925c7d1`.

Required execution schema: `Phase1418BCausalExecution.v1`.

Required execution fingerprint: `2078a10ecad7df0faef638e3e6e37eb4808c4a8c33af5fb81883b427d0ed5925`.

Session schema is `Phase1419Session.v1`; cutoff schema is `SYNTHETIC_CUTOFF.v1`. No legacy fingerprint or automatic migration is accepted. The required risk fingerprint identifies the existing synthetic fixture limits, not approved genuine capital limits.

Readiness requires explicit enabled synthetic authorization, verified qualification bindings, the frozen 13,380-family numerical gate, valid dataset quality, reviewed corporate actions, matching instrument and calendar evidence, matching strategy definitions, matching capital, a prior-session resolution assertion and no paper HALT. Only synthetic fixture mode is admitted. Genuine paper readiness remains false.

The canonical universe remains 1,115 enabled NSE symbols from 1,140 source rows after 25 duplicate removals. S01-S06 and the six-/twelve-month qualification family remain unchanged. Scheduler inputs are admitted partitions, not a new research qualification family. No genuine qualified pairs or market performance are created.

Every admitted symbol must supply the next contiguous five-minute completed candle at a step. Missing, duplicate, noncontiguous, out-of-session or mismatched observations halt the session. Entry cutoff must lie strictly between open and close. At and after cutoff, entries are rejected while exits continue. Missing entry opening evidence cancels the proposal and halts. Partial fills are unsupported.

Paper HALT never changes operational HALT. Missing EOD liquidity leaves the position explicitly EXIT_PENDING, with cash and cost basis intact; no artificial close or completion is recorded. Approved risk settings, authoritative exchange calendars, genuine mapping, authorized historical data and genuine qualifications remain prerequisites for a separately authorized later phase.
