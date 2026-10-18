# Route compatibility assessment

This static review binds the controller hashes in external source-inventory.json. No HTTP calls or application execution occurred. Route availability remains conditional on existing application configuration.

| Existing route | Existing purpose/gate | Effect of the embedded global deny-only proposal |
|---|---|---|
| GET /api/trading/status | Existing opt-in static capability status | Permitted by chain; controller's existing feature gate retained |
| GET /api/broker/kite/auth/status | Broker authentication status | Denied; compatibility change requires approval |
| GET /api/broker/kite/auth/login | Starts broker login flow | Denied; do not add a public exception automatically |
| GET /api/broker/kite/auth/callback | Validates broker state/nonce then exchanges credentials | Denied; would interrupt login; never use as operator callback |
| POST /api/broker/kite/auth/reset | Broker token reset | Denied; existing anti-cross-site header is not operator identity |
| GET /api/development/trading-read/orders, /trades, /positions, /holdings, /margins | Development-only flags and loopback checks; providers may call broker | Denied; these are not side-effect-free projection backends |
| Actuator and other unspecified routes | Existing exposure depends on configuration, not assumed public | Denied; exact health/management exceptions require separate review |
| Future protected /api/trading/* projections | Not activated | Denied; future permission matrix is not an active grant |

A trading-only matcher could avoid changing unrelated routes but might leave them outside the new authentication boundary. Do not replace global denial with broad permitAll or adopt matcher changes silently. Freeze explicit chain ordering, management-port behavior, error/forward dispatch handling and all intended exceptions in the future implementation scope. Broker login, operator login and governance approvals require separate identities and callback paths.

Preserve seven read permissions: trading:strategies:read, trading:instruments:read, trading:qualifications:read, trading:signals:read, trading:positions:read, trading:orders:read, trading:risk:read. Account entitlement is additional for sensitive projections. All mutations remain denied. No sensitive anonymous reads.

Future targeted tests must verify each route when enabled/disabled, exact status body/no-store, anonymous/wrong-role/missing-permission denial, CSRF and logout, unknown routes/methods, fallback chain, no generated credentials, no broker/order calls, no secret disclosure and unavailable sources. None are executed here.
