# Phase 13.5D certification matrix

Review date 2026-10-10; source baseline db3e5d697b80b95a806010258426b14d590ba831.
Test counts and exact artifact hash are in phase-13.5d-validation.md. Synthetic
observations are dated test evidence, not live attestations. No live freshness
timestamp or account fingerprint was collected. Operational certification: NO_GO.

| Prerequisite | Threat | Evidence source | Synthetic/disposable result | Independent live evidence | Freshness | Future accountable owner | Unresolved gap / decision |
|---|---|---|---|---|---|---|---|
| Real session provenance | Caller-created state mistaken for broker auth | Handoff/session tests; composition review | Local fixture is SYNTHETIC_AUTHENTICATED only | NOT_ESTABLISHED | Fixture clock only | Authentication authority | No reviewed real ownership origin; NO_GO |
| Recipient/owner and replay | Theft, confusion, concurrent consume | Handoff tests | Private references, one winner, expiry/revoke/session binding | NOT_ESTABLISHED | Bounded fixture lease/clock | Diagnostic owner and security reviewer | No cross-process mechanism or hostile-JVM isolation; NO_GO |
| Independent HALT | Unknown fallback, stale epoch, competing runtime | RuntimeTradingHalt and handoff tests | Known vs unknown, epoch/response invalidation deny | NOT_ESTABLISHED | Synthetic epoch only | Independent runtime controller | No authenticated all-instance attestation; NO_GO |
| All writer exclusion | Old/new/non-cooperative/privileged clients | Isolation tests; NOLOGIN and drain integration tests | Specific writer drained and reconnection blocked; privileged alternative still possible | NOT_ESTABLISHED | Disposable window only | Deployment/database controller | Global role/host/network exclusion absent; NO_GO |
| Observer rights and visibility | Inherited rights, hidden sessions, functions | Integration ACL/statistics cases | SELECT-only denials and unexpected privilege/session aborts | NOT_VERIFIED | Rechecked fixture captures | Database security reviewer | Real role/catalog/session visibility unmeasured; NO_GO |
| Integrity baselines | Same-count/schema changes; transient restoration | Content/schema and permission fingerprint tests | Stable captures; drift detected; restoration blind spots demonstrated | NOT_VERIFIED | Non-atomic fixture snapshots | Independent evidence observer | No live baselines or preventive proof; NO_GO |
| Sensitive-data containment | Logs, dumps, args, environment, agents | Redaction/serialization/logging tests; deployment review | Marker containment and late unsafe logging denial | NOT_VERIFIED | Test log configuration only | Host/container security owner | Sink ACLs/retention/dumps not inspected live; NO_GO |
| Exact HTTP authority | Retry, redirect, retained request replay | RequestFactory/harness tests | Exact route, one execute, failure budget, strict parsing; official requests never executed | NOT_VERIFIED live | Fixture execution only | Network/runtime reviewer | No approved deployed egress proof; NO_GO |
| Trading startup exclusion | Runner restores token; execution bean activation | ControlledEquityReadArchitectureTest; route inventory | Diagnostic dependency/call exclusions pass; no new wiring | NOT_ESTABLISHED deployed | Baseline bytecode review | Packaging/release reviewer | Full application jar is not minimal distribution; NO_GO |
| Funding eligibility | Observed net/utilised cash mistaken for authority | Existing V1 contract and synthetic evidence tests | Four required terms remain UNKNOWN; NOT_READY | NOT_ESTABLISHED | No current account evidence | Broker evidence reviewer | Eligible adjusted/free collateral, exact cash rule/field unproven; NO_GO |

No synthetic result can change a live column. Future VERIFIED requires independently
authenticated, dated, scoped evidence and fresh go/no-go authorization. Prior Stage B
ZERO_CALL_ABORT is preserved; this phase dispatches zero real requests.
