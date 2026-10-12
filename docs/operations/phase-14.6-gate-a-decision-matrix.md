# Gate A owner decision matrix — PENDING / NOT_AUTHORIZED

This is an engineering decision packet, not owner acceptance. Authorized real calls0.

| Required field | Current evidence/decision | Closure requirement |
|---|---|---|
| Contract/terms | TERMS_UNRESOLVED | Attributable permitted-use/retention basis |
| Endpoint | GET https://api.kite.trade/instruments/NSE | Exact one-route policy, no queries/alternates |
| Maximum requests | Proposed1; authorized0 | One-use scope-bound approval, no retry/redirect |
| Timestamp/expiry | PENDING | Owner-attributable start/end; consume on attempt or expire |
| Purpose | Map canonical1115 NSE symbols | No implied candles/trading authority |
| Session provenance | AUTHENTICATED_SESSION_NOT_ESTABLISHED | Legitimate existing session under approved owner custody |
| Process identity | PENDING | Approved isolated runtime identity/hash; no launcher of trading app |
| Destination | api.kite.trade:443 proposed | Independently verified TLS/egress/no-proxy and route checks |
| Credential custody | PENDING | No token restoration/login/refresh or logging; authorized owner handoff |
| Attempt consumption | PARTIAL local fixture proof | Independent authority/rollback/failure policy and operational evidence |
| Response handling |32MiB fixture limit; live limits DESIGNED | Wire/decoded32MiB,connect10s/read30s/overall45s proposals need proof |
| Retention/deletion | NOT_AUTHORIZED | Approved destination,ACL,encryption,expiry,backup/deletion policy |
| Logging | Local fixture redaction VERIFIED | External sinks,dumps,collectors and custody PENDING |
| Rollback limits | Counterexample retained | Explicit bounded guarantees, no universal exactly-once claim |
| HALT | Preserved; GateA integration absent | Independent fail-closed check on the actual future path |
| Owner acceptance/rejection | PENDING | New explicit attributable decision; no invented signature |

Stop on any unresolved mandatory term,security/custody,HALT,scope,budget,clock,source,
response or storage condition. Uncertain dispatch consumes the attempt; no automatic
retry,proxy,redirect,token refresh or fallback source. No genuine snapshot persisted.

Minimum next decisions, in dependency order: obtain separately authorized clarification
contact or equivalent attributable contract evidence; settle retention/custody/authority;
review a supported isolated host and runtime/security design; demonstrate missing live
controls with separately scoped tests; then seek exact one-request owner authorization.
None of those actions is authorized automatically by this packet.

Gate B PENDING / NOT_AUTHORIZED independently. No historical candles,full-universe
acquisition,backtests using new genuine data,WebSockets,orders,live trading,HALT arm/
resume or capital-limit change. StageB ZERO_CALL_ABORT,Phase12 BLOCK,H1/H2 FROZEN,
July TEST SEALED,INR10000 ceiling,MIS NOT_READY and operational NO_GO remain unchanged.
