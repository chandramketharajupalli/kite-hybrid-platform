# Phase 14.9 separate Gate A decision ledger

GATE_A_DECISION_PENDING. Real instrument-master access NOT_AUTHORIZED.
Support inquiry approval, if later granted, is a distinct decision.

| Control | Classification | Evidence / remaining gap |
|---|---|---|
| Exact GET-only route | VERIFIED synthetic | GateAHaltBoundary exact comparison and retained Phase 14.7 tests; no production transport |
| HALT enforcement | VERIFIED synthetic | Admit and final recheck after journal force; production issuer NOT_ESTABLISHED |
| Durable one-use budget | PARTIAL | Retained shared-journal/restart/concurrency tests; production authority absent |
| Session provenance | NOT_ESTABLISHED | No legitimate authenticated session inspected |
| Credential custody | NOT_ESTABLISHED | No real credentials accessed or custodian verified |
| Production egress | NOT_ESTABLISHED | PRODUCTION_EGRESS_NOT_VERIFIED, per Phase 14.8 assessment |
| External logging | NOT_ESTABLISHED | Local redaction tests do not attest sinks/dumps/backups |
| Independent witness | NOT_ESTABLISHED | Local memory/journal is not independent restore authority |
| Physical power loss | NOT_ESTABLISHED | No storage/directory durability attestation |
| All-authority rollback limit | VERIFIED negative | Phase 14.7 counterexample permits another grant after complete restoration |
| Retention/deletion controls | DESIGNED | Category-specific policy and permission PENDING |

Source references: phase-14.8-readiness-matrix.md,
../architecture/phase-14.8-production-isolation-assessment.md,
phase-14.7-validation.md and its manifest. No new executable tests or production
measurements. Pre-dispatch revocation can deny admission; after-send revocation
cannot undo a request. No universal exactly-once delivery claim.

| Gate A decision field | Value |
|---|---|
| Owner decision | PENDING |
| Terms decision | UNRESOLVED, Q1-Q9 |
| Authorized endpoint | None; proposed GET https://api.kite.trade/instruments/NSE |
| Request count / expiry | Authorized count 0; proposed maximum 1, expiry PENDING |
| Authenticated session / credential custody | PENDING / NOT_ESTABLISHED |
| Production isolation / HALT authority | PENDING; synthetic evidence only |
| Durable attempt consumption | PENDING production authority; retained fixture mechanism |
| Logging and retention | PENDING actual sinks and approved storage/deletion policy |
| Outstanding conditions | Recipient/response/terms, owner attribution, custody, egress, rollback authority and production validation |
| Real-request authorization | NOT_AUTHORIZED |

Gate B: GATE_B_NOT_AUTHORIZED. No candles, WebSockets, orders, trading DB changes,
HALT resume or capital changes. Preserve sole-operator declaration without inventing
independent reviewers/custodians. Phase 13.5K/L, V2/governance second review/four
technical reviews PENDING, NO_GO, ZERO_CALL_ABORT, Phase 12 BLOCK, H1/H2 FROZEN,
July SEALED and INR10,000 buffered-notional ceiling unchanged. Genuine mapping:
0 resolved, 1,115 MASTER_UNAVAILABLE; source universe 1140/25/1115 unchanged.
