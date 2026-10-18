# Compatibility boundary

| Family | Trading matcher | Preserved behavior and remaining tests |
|---|---|---|
| Static trading status | Matches | Existing opt-in static response/no-store; no broker login |
| Other trading routes | Matches | Denied until approved identity and implementation; future seven read permissions |
| Kite auth status/login/callback/reset | Does not match | Existing state/nonce/cookie/consumption and token-exchange boundaries retained; no operator credentials inferred |
| Development trading-read | Does not match | Profiles, flags, loopback/Host/origin/fetch-site/proxy and explicit method rejection retained |
| Development universe | Does not match | Development flag and loopback/local-host/proxy checks retained; no new operator authentication asserted |
| Development market-data | Does not match | Existing conditional local-only checks retained; start/subscription/stop can affect broker streaming and are not invoked |
| Management health/info/prometheus | Does not match | Existing configuration retained; actual Boot management security behavior must be qualified |
| Other/unmatched routes and error dispatch | Does not match | Existing controls only; no new fallback chain and no assumed exposure |

The earlier global deny-all proposal would have blocked Kite login/callback. The new proposal confines its denial rules to trading paths. This resolves the design-level matcher conflict, not runtime compatibility. No route behavior changes are applied.

Mandatory future compatibility tests: canonical Kite login redirect and nonce attributes; missing/duplicate/mismatched callback state/nonce rejection; consumed-attempt replay rejection before exchange; reset header behavior; diagnostic profiles/flags/loopback/proxy/Host/origin/fetch-site/method restrictions; management exposure and health details; order/risk controls unchanged; error codes/redaction; matcher edge cases and servlet dispatch. Use synthetic mocks and assert no actual token exchange, WebSocket, database production writes or broker/order call. Existing controllers are never repurposed for projections.
