# Pre-dispatch checklist — no dispatch

Every mandatory row must be verified for the exact future request. Pending is denial.

| Gate | Current result | Required evidence |
|---|---|---|
| Git/preservation/universe | PASS offline | Exact baseline and inherited hashes; 1140/25/1115 |
| Source/method | Documented | One GET https://api.kite.trade/instruments/NSE |
| Owner scope/start/expiry | PENDING | Attributable approval matching request and storage |
| Terms/retention/entitlement | UNRESOLVED | Account-specific permitted use and expiry/deletion policy |
| Session custody | NOT_ESTABLISHED | Legitimate existing authenticated owner session; no restore |
| Gate B exclusion | NOT_AUTHORIZED | No candle routes, WebSockets or orders |
| HALT / ZERO_CALL_ABORT | Preserved | Independent review; no override or resume |
| TLS/destination | Source configuration reviewed only | Certificate/hostname verification, exact route and egress containment |
| Durable budget | NOT_ESTABLISHED_FOR_GATE_A | Atomically consume once before uncertain dispatch; restart-safe deny |
| Other components | NOT_ESTABLISHED_FOR_GATE_A | No shared capability, no order-capable launcher |
| Response bounds | Existing generic primitives | Exact NSE wire/decoded limits and deadline verified in narrow process |
| Logging | Existing synthetic tests | Approved narrow-process redaction and verbose-log denial |
| Storage/expiry | NOT_AUTHORIZED | ACL/custody, retention and deletion evidence before persistence |

Current outcome: zero real requests. Do not turn successful synthetic tests into
production attestations. Do not test live TLS/session availability with a probe: that
would be an additional network request. No Gate A execution decision is made here.
If any future requirement fails, stop before dispatch; uncertain dispatch consumes
the one-use budget permanently and requires new review, not a retry.
