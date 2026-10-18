# Route-scoped design

Phase1446ETradingRouteSecurity.v1 proposes a single @Order(1) chain with exact matcher /api/trading/**. GET /api/trading/status is the sole public static exception, retaining the existing opt-in controller gate and no-store response. All other trading requests deny in the current no-provider proposal. No HTTP Basic, form fallback, mutation or automatic activation.

Future protected reads require an approved operator principal, account scope where relevant and exact permissions:
- /strategies: trading:strategies:read
- /instruments: trading:instruments:read
- /qualifications: trading:qualifications:read
- /signals: trading:signals:read
- /positions: trading:positions:read
- /orders: trading:orders:read
- /risk/status: trading:risk:read

Paths above are under /api/trading. Unknown routes and every trading mutation deny, even with another permission. No identity from user headers, broker tokens or feature flags. The deny-only patch does not implement the future authenticated projection grant.

## Explicit no-match behavior

No catch-all chain is proposed. Outside /api/trading/**, this chain does not authenticate, authorize or provide CSRF protection. Existing controller, broker-use-case and deployment controls remain responsible. This is not permitAll and not a claim of protection. Never describe no-match routes as operator-authenticated. Unknown unmatched routes continue through the existing dispatcher and controls; do not assume 404 if another mapping exists.

A custom chain can affect Boot's default/management auto-configuration and Security firewall behavior outside matching requests. Those effects are not certified offline. Require dedicated management/exposure/firewall/dispatch regression tests before applying the dependency/configuration change. Do not add global denial or broad exceptions as a shortcut.

Framework matcher normalization, trailing slash, servlet context, encoded separators, semicolons, path traversal and forward/error dispatch need tests against exact runtime versions. The in-memory model checks canonical paths only and is not a Spring matcher implementation.

## Future identity integration

OIDC issuer/client remain null. A future separately approved login/callback chain must match its own explicit operator paths; placing oauth2Login on a trading-only chain does not automatically make its default authorization/callback endpoints reachable. Do not reuse Kite callback or nonce. Freeze chain orders, callback paths, fixed redirects and principal adapter in a new exact scope.

Use framework issuer/audience/signature/state/nonce/expiry checks, stable issuer+subject and approved allowlisted claims mapped to permissions. Unknown role/issuer or backend outage denies. Server-side session fixation protection, Secure/HttpOnly/reviewed SameSite cookies, idle/absolute expiry, revocation and CSRF-protected POST logout are required before activation. No trading mutation. Unknown session policy remains unresolved, not executable configuration.
