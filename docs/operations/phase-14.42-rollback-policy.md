# Phase1442MigrationRollbackPolicy.v1

Status PROPOSED_NOT_IMPLEMENTED; owner acceptance PENDING. Rollback readiness is a specified review contract, not proof of safe activation or tested restoration.

Keep immutable Series v1, historical source/report hashes and original qualification evidence. Proposed v2 datasets, outputs and qualification decisions require separate versioned namespaces. Activation must be reversible by an explicitly named authority and recorded decision; no automatic silent v2-to-v1 fallback. Rollback disables the new route while preserving its evidence; it must not delete prior receipts, restore incompatible results, mutate accepted fills or copy v2 qualifications into legacy. Recovery after interruption requires verified namespace/version/receipt identity and a new explicit decision.

| Risk | Severity / failure mode | Detection and proposed mitigation | Required future regression / acceptance |
|---|---|---|---|
| Signal drift | High; adjusted history alters decisions | Per-bar input/signal paired diff; separate methodology | Golden signals/action boundaries; owner signoff |
| Historical PnL | High; fills/cash change | Per-fill/cash reconciliation; immutable legacy | Cash/fees/positions/EOD replay; owner signoff |
| Double adjustment | High; provider plus local factor | Source-basis lineage; reject mixed provenance | Already-adjusted fixture rejection; owner signoff |
| Token continuity | High; wrong historical token | Effective-period/source reconciliation | Gap/overlap/stale/reuse tests; owner signoff |
| Security conflation | High; unrelated securities merged | Independent security graph; reject ambiguous links | Merger/relisting/reuse cases; owner signoff |
| Future information | High; retrospective evidence leaks | Availability audit; label retrospective namespace | Future evidence mutation tests; owner signoff |
| Dataset fingerprint | High; changed content under old identity | Canonical version/source hash binding | Tamper/replay rejection; owner signoff |
| Result pooling | High; incompatible statistics merged | Family/policy guards; independent namespaces | Cross-family pooling rejection; owner signoff |
| Requalification | High; old qualification reused | New method/version requires new OOS evidence | Sample/multiple-testing invariants; owner signoff |
| Rollback failure | High; restoration uses incompatible state | Explicit activation ledger/authority | Interrupted activation and rollback rehearsal; owner signoff |
| Evidence migration | High; history rewritten | Additive references only, immutable originals | Raw hash custody replay; owner signoff |
| Legacy preservation | High; new path changes old results | Baseline/replay comparison; no implicit fallback | Full legacy suite and report identity; owner signoff |

All migration-specific mitigations above are proposed, not implemented. Current tests verify governance rejection and unchanged legacy behavior; they do not rehearse a real migration or rollback. Outstanding owner acceptance cannot be inferred from a passing assessment.
