# Proposed read permission matrix

These contracts do not activate routes. Status preserves existing opt-in static metadata behavior.

| GET path | Required permission |
|---|---|
| /api/trading/status | Public static capabilities only; no sensitive state |
| /api/trading/strategies | trading:strategies:read |
| /api/trading/instruments | trading:instruments:read |
| /api/trading/qualifications | trading:qualifications:read |
| /api/trading/signals | trading:signals:read |
| /api/trading/positions | trading:positions:read |
| /api/trading/orders | trading:orders:read |
| /api/trading/risk/status | trading:risk:read |

Protected routes require verified operator identity, known server-mapped role, exact permission and authorized account scope before any data access. No wildcard permission inference, broker token reuse, caller-controlled identity header or feature-flag authority. Roles require explicit approved mapping; no production role registry is invented.

Future responses:401 for missing/invalid/expired identity;403 for authenticated permission/account denial;503 for unavailable authentication dependency without projection access; disabled routes absent or fail closed. Do not leak account existence or authentication backend details. API requests should not silently redirect to broker login. A feature flag does not supply identity.

No mutating methods, paper-order or live-order permissions are authorized. Reject unrecognized methods/routes; explicitly review Spring HEAD/OPTIONS behavior before activation. GETs must not refresh instruments, acquire tokens, download prices, evaluate strategies, run backtests or issue order/risk commands.

Pagination proposal: default25, limit1..100, strict integer parsing, reject duplicates/malformed identifiers/cursors; snapshot-bound deterministic ordering/cursors. No negative offsets or unlimited list operations. This is proposed policy, not a runtime-validated limit.
