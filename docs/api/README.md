# API foundation

No trading API or signal-ingress endpoint exists in Phase 1.
Actuator exposes /actuator/health, /actuator/health/liveness,
/actuator/health/readiness, /actuator/info and /actuator/prometheus locally.
Database readiness is part of application readiness outside the test profile.
The internal tradingstatus endpoint is not exposed by default and always reports
ready=false because execution is unavailable. It is separate from health.

Future command APIs require authentication, authorization, idempotency, audit
and explicit risk/OMS application services before any broker execution.

Kite authentication is available on the existing loopback port 8080:

| Method and path | Behavior |
| --- | --- |
| `GET /api/broker/kite/auth/status` | Safe authenticated/tokenAvailable/broker/code/loginUrl/initializationReady fields; no token values |
| `GET /api/broker/kite/auth/login` | Safe status when authenticated; otherwise browser redirect to the official Kite login page |
| `GET /api/broker/kite/auth/callback` | Validates browser state and callback parameters, exchanges request_token, validates/stores session, continues initialization |
| `POST /api/broker/kite/auth/reset` | Requires `X-Kite-Auth-Reset: true`; clears the local session and stored token only |

Start login through this application in the same browser used for the callback.
Use `http://localhost:8080/api/broker/kite/auth/callback` as the exact developer
console redirect URL. Configure credentials and encryption once as described in
[Kite Authentication](../../README.md#kite-authentication). The callback accepts
`request_token`, `status`, `action`, `type`, and the generated `state`; no response
contains API secrets, request tokens or access tokens. Invalid/missing callback
input returns a safe client error, and broker/storage failures use sanitized errors.
The callback requires the returned `state` and the matching `KITE_LOGIN_NONCE`
cookie plus an unexpired PostgreSQL attempt. It never uses HttpSession or
JSESSIONID. Atomic consumption occurs before token exchange; missing state,
missing/mismatched browser proof, expired attempts and replays return HTTP 403
with exactly `{"broker":"KITE","code":"KITE_CALLBACK_STATE_INVALID"}`.
Database unavailability returns safe HTTP 503 without exchanging the request
token. Cookie-only fallback is intentionally disabled; see the
[callback runbook](../runbooks/kite-auth-callback.md).
The endpoints are local control APIs, not trading/order execution APIs.

The optional standalone Kite diagnostic invoked through Maven `exec:exec` remains
explicit and read-only. The internal kitestatus
Actuator endpoint reports passive session/registry state, remains unexposed by
default and does not make application health depend on Kite.
