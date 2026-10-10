# Phase 13.5F decision and approval matrix

2026-10-10, baseline e7868c479779636e6dc9e749a2e9440a2369e492.
Operational **NO_GO**. Previous Stage B **ZERO_CALL_ABORT** preserved.
Preferred future architecture C is recommended, not deployed or approved.

| Requirement | Evidence/source | Outcome | Independent owner / next evidence |
|---|---|---|---|
| DB-free noninterference | DiagnosticAssuranceContract.v1 distinguishes own authority from global state | Design specified; not a deployed proof | Security/packaging reviewer: minimal artifact and absence of all direct/indirect DB authority |
| Existing synthetic ownership | Handoff/epoch/replay/lease tests, fresh counts in validation | Synthetic controls validated only | Authentication authority: legitimate owner origin and account/session binding |
| Real session ownership | No isolated authenticated owner or transfer mechanism | NOT_ESTABLISHED | Review separate interactive-auth origin; no token loader or restoration shortcut |
| Independent live HALT | External issuer protocol proposed; local knownHaltedAt tested | NOT_ESTABLISHED | Runtime controller: authenticated all-instance epoch, freshness/revocation and key trust |
| Global trading DB nonmutation | NOLOGIN, drain, lock and write/restore counterexamples | NOT_PROVEN | Infrastructure owner if old assurance retained; DB-free v1 expressly makes no global claim |
| Existing observer/integrity | Restricted role and fingerprint fixture suite | Synthetic only; no live baseline | DB authority for old contract; no bypass or silent removal |
| Minimal packaging | Current Boot/JDBC/Redis/Flyway/general transport dependency inventory | Unresolved, design only; LIVE NOT_VERIFIED | Build/security owner: narrow reuse/extraction and loadability/transitive audit |
| Egress and budget | Exact wire/one execute/no retry tests; JVM-local official reservation | Synthetic only; LIVE NOT_VERIFIED | Runtime/network owner: deny-by-default deployment plus cross-process one-use ledger |
| Logs and dumps | Marker/serialization/wire DEBUG tests; sink policy proposed | LIVE_LOGGING_NOT_VERIFIED | Host/log owner: complete sink allowlist, ACLs, retention, dumps/agents and change control |
| Four collateral/cash terms | No broker/account evidence collected | UNKNOWN; prior cash descriptions CONFLICTING | Broker evidence owner: eligible adjusted/free collateral, exact NSE MIS cash rule/field |
| Revised assurance contract | Old-vs-v1 guarantee table | APPROVAL_PENDING; CONTRACT_CHANGE_PENDING_APPROVAL | Independent explicit review of narrower postcondition |
| Real account read | No v1 deployment/prerequisite/request authorization | NOT_AUTHORIZED under revised contract | Separate exact bounded authorization after independent certification |

Design acceptance, synthetic validation, live prerequisite verification and request
authorization are four separate decisions. No row is upgraded by a UUID, caller
flag, synthetic source label or repeated test success. No live timestamp, account
binding or attestation fingerprint exists here. Future evidence must specify
authenticated source, scope, effective/observation time, expiry and revocation.
Unknown/stale/conflicting evidence denies. No operational approval switch is added.

Review sequence: approve/reject v1 changes; review ownership origin and trust;
review minimal artifact and preventive controls; implement/test only under a new
engineering scope; independently verify deployment; obtain separate real-read
authorization. Contract approval alone does not authorize any subsequent step.
The prior one-GET approval cannot authorize v1 or authentication acquisition.
