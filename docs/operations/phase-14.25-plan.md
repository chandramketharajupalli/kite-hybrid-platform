# Phase 14.25 frozen offline reliability plan

Baseline: 311469c88810904955ba3468faf4daf7fede8e53 on develop, equal to local
origin/develop. Phase 14.24 is committed as exactly twelve additions. Preflight
verified 1,326 tracked files, 1,314 prior raw hashes, eleven sealed artifacts,
244 external evidence files, all thirteen Phase 14.0A newline reconciliations,
frozen G1/eighteen source hashes/sixty-three research files/K/L records,
1,115 enabled unique NSE symbols and 13,380 combinations. Prior receipts are
56 focused and 822 full-suite tests. Phase 14.24 manifest fingerprint:
3bead92b1a1384583bc3d0477fb66c18348421057962006a45349b61334e1b63.

## Existing capabilities and reuse
Phase 14.17 supplies paper risk/admission/accounting; 14.18 coordinates ownership,
reservations and replay; 14.18A freezes risk and 14.18B causal execution. The
14.19 scheduler already supplies explicit offline crash hooks and journal replay.
14.20 carries cash/drawdown/HALT across sessions and requires explicit recovery
with a head anchor. 14.21/14.23/14.24 provide preserved analytics and independent
synthetic monitoring ground truth. Reuse these components without changing them.

## Incident taxonomy and boundaries
Bounded registry includes crash before session, after readiness, before/after
reservation, after fill, during EOD; missing EOD liquidity/candle, duplicate
candle/session, clock rollback, risk/execution mismatch, corrupt/truncated journal,
missing completion report/alert, duplicate/late alert, invalid acknowledgment,
and complete local-authority rollback. Fault injection only uses existing offline
hooks or mutates synthetic artifacts in unique external temporary directories.
No production transport, daemon, real data, notification or operational HALT access.

## Authority and HALT
Synthetic recovery requests bind request/run/incident/action/evidence/identity,
authorization time/expiry/scope and approval fingerprint to a separately trusted
synthetic approval record. Acknowledgment is never approval. Approval allows only
explicit replay evaluation; it cannot clear HALT. No clearance procedure is added.
HALT and unresolved positions must survive replay; new entries remain blocked.
Complete rollback of all local evidence and authority cannot be detected by local
hashes; retain the counterexample and never claim independent rollback resistance.

## Metrics and certification
Count scenarios, recoveries, failures, duplicate replay rejections, reconciliation
failures, incomplete sessions, HALT continuity and evidence failures. Monitoring
reuses the versioned 14.24 matching policy and independently authored truth: TP,
FP, FN, duplicates, late alerts, delays and missed critical incidents. No production
uptime/SLA/MTBF/MTTR. A deliberately missing alert blocks that scenario's monitoring
certification even if the verifier detects the omission. Final engineering status
is PARTIAL if mandatory recovery/monitoring guarantees remain unresolved.
Genuine qualification stays zero/BLOCKED and operational readiness stays NO_GO.

## Allowed changes, budgets and gates
Only additive phase1425 source/tests, verifier, six requested documents and
research/phase-14.25/generation-1 evidence. All 1,326 tracked files are immutable.
The separate untracked phase-14.22-plan.md is immutable and excluded; SHA-256:
e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31.
At most 64 scenarios, 64 sessions, 200,000 input bars and existing bounded journals;
small synthetic fixtures exercise six-strategy/1,115-symbol existing contracts.
Budget: focused tests then full relevant Python suite, Ruff and strict mypy;
corrective reruns only as needed. No Java changes/tests. Use Python -B,
PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain -p no:cacheprovider,
Ruff --no-cache and unique external temporary/cache/receipt directories.
Verify protected hashes after every major gate. Preserve failures and corrections.

## Evidence and stop conditions
Freeze this plan's SHA-256 externally before implementation. Reports include
scenario ledger, recovery/HALT/detection/missed-critical/accounting/cross-session/
integrity outcomes and three independent readiness assessments. Seal exact new
inventory, source/policy/dataset/calendar/truth/fault fingerprints, fresh raw test
receipts, protected hashes, scans and two deterministic replay receipts separately
from certification. Use LF and Git-byte-equivalent additions. Stop on baseline,
preservation or safety failure. Do not repair historical evidence or redefine a
negative scenario as production success. Scope corrections must be additive.
Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN;
July SEALED. Zero real Kite requests. No stage, commit, push or Git configuration.
