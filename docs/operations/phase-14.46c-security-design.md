# Operator HTTP security architecture

Decision: DEFER_PROTECTED_ENDPOINT_ACTIVATION. Exact inspected source paths/hashes are retained in external source-inventory.json. No operator HTTP boundary was found. Broker nonce/state checks, terminal confirmation and loopback diagnostics solve different problems; diagnostics may call the broker and cannot supply projections.

| Method | Path | Permission |
|---|---|---|
| GET | /api/trading/status | public static metadata only |
| GET | /api/trading/strategies | trading:strategies:read |
| GET | /api/trading/instruments | trading:instruments:read |
| GET | /api/trading/qualifications | trading:qualifications:read |
| GET | /api/trading/signals | trading:signals:read |
| GET | /api/trading/positions | trading:positions:read |
| GET | /api/trading/orders | trading:orders:read |
| GET | /api/trading/risk/status | trading:risk:read |

Authorities above are proposed, not existing grants. Deny all mutations and unspecified routes. Status retains its existing opt-in static/no-store contract. Feature flags never authenticate.

Future activation requires a Spring Security servlet filter chain and an independently approved OIDC registration. Use framework issuer/audience/state/nonce/signature/expiry verification. Identify operators by issuer+subject; map approved claims through an explicit permission allowlist, not arbitrary provider roles. Unknown roles, missing account scope/permission, invalid/expired identity and authorization-backend outage deny. Never use user-controlled identity headers or Kite tokens.

Use server-side sessions with fixation protection/change-session-ID, Secure/HttpOnly cookies and SameSite=Lax subject to verified redirects. Require HTTPS and a fixed registered redirect; do not trust arbitrary Host/forwarded headers. Idle/absolute timeout and concurrent-session limits remain unapproved. CSRF-protected POST logout must invalidate the session/cookie and use a fixed approved return URI. No GET logout, global CSRF disabling or trading mutation. Define bounded session revalidation and independent revocation before activation.

Unauthenticated API requests receive bounded 401; insufficient permission receives 403. Do not redirect APIs to an unapproved issuer. Explicit browser login is separate. Authenticated callers receive explicit unavailable projection states, never fabricated zero values. Audit bounded route/reason/correlation records with policy-approved pseudonymous principal references; exclude tokens, cookies, raw claims, secrets and sensitive position details.

Reuse InstrumentRegistry without downloads and verified strategy metadata without Python execution. Qualification requires genuine evidence and separate methodology families. Pagination proposal: default 25, maximum 100, stable snapshot-bound cursor and validated input. No broker/order service invocation. Series v1 preserved; Series v2 unadopted.

The exact first-step embedded patch is deny-only, not the completed OIDC integration. A provider activation diff cannot be finalized without issuer, role/session policy and custody evidence. This is an explicit implementation blocker.
