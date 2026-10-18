# Static HTTP route inventory

54 explicit method/path combinations in five trading-core main Java controllers, including 35 explicit diagnostic method rejections. Exact source paths/hashes and per-route existing controls, side effects, broker/operational sensitivity and unknown deployment exposure are in route-assessment.json. All main Java mapping annotations were inspected; no implemented operator filter chain or functional RouterFunction was found. Static inventory does not certify runtime exposure.

| Method | Route | Controller | Effects |
|---|---|---|---|
| GET | /api/trading/status | TradingFoundationController | Static capability response; no-store |
| GET | /api/broker/kite/auth/status | KiteAuthenticationController | Reads broker authentication status |
| GET | /api/broker/kite/auth/login | KiteAuthenticationController | May restore broker state; creates login attempt and redirects with nonce cookie |
| GET | /api/broker/kite/auth/callback | KiteAuthenticationController | Consumes validated state/nonce before request-token exchange; broker/storage effects |
| POST | /api/broker/kite/auth/reset | KiteAuthenticationController | Requires X-Kite-Auth-Reset true; cancels attempt and resets broker state |
| GET | /api/development/universe/validate | UniverseValidationDiagnosticController | Reads local validation/registry data |
| GET | /api/development/universe/unresolved | UniverseValidationDiagnosticController | Reads local validation/registry data |
| GET | /api/development/universe/lookup | UniverseValidationDiagnosticController | Reads local validation/registry data |
| GET | /api/development/trading-read/orders | KiteTradingReadDiagnosticController | Calls broker read provider; no-store; no orders submitted |
| POST | /api/development/trading-read/orders | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| PUT | /api/development/trading-read/orders | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| PATCH | /api/development/trading-read/orders | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| DELETE | /api/development/trading-read/orders | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| HEAD | /api/development/trading-read/orders | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| OPTIONS | /api/development/trading-read/orders | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| TRACE | /api/development/trading-read/orders | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| GET | /api/development/trading-read/trades | KiteTradingReadDiagnosticController | Calls broker read provider; no-store; no orders submitted |
| POST | /api/development/trading-read/trades | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| PUT | /api/development/trading-read/trades | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| PATCH | /api/development/trading-read/trades | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| DELETE | /api/development/trading-read/trades | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| HEAD | /api/development/trading-read/trades | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| OPTIONS | /api/development/trading-read/trades | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| TRACE | /api/development/trading-read/trades | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| GET | /api/development/trading-read/positions | KiteTradingReadDiagnosticController | Calls broker read provider; no-store; no orders submitted |
| POST | /api/development/trading-read/positions | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| PUT | /api/development/trading-read/positions | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| PATCH | /api/development/trading-read/positions | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| DELETE | /api/development/trading-read/positions | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| HEAD | /api/development/trading-read/positions | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| OPTIONS | /api/development/trading-read/positions | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| TRACE | /api/development/trading-read/positions | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| GET | /api/development/trading-read/holdings | KiteTradingReadDiagnosticController | Calls broker read provider; no-store; no orders submitted |
| POST | /api/development/trading-read/holdings | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| PUT | /api/development/trading-read/holdings | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| PATCH | /api/development/trading-read/holdings | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| DELETE | /api/development/trading-read/holdings | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| HEAD | /api/development/trading-read/holdings | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| OPTIONS | /api/development/trading-read/holdings | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| TRACE | /api/development/trading-read/holdings | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| GET | /api/development/trading-read/margins | KiteTradingReadDiagnosticController | Calls broker read provider; no-store; no orders submitted |
| POST | /api/development/trading-read/margins | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| PUT | /api/development/trading-read/margins | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| PATCH | /api/development/trading-read/margins | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| DELETE | /api/development/trading-read/margins | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| HEAD | /api/development/trading-read/margins | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| OPTIONS | /api/development/trading-read/margins | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| TRACE | /api/development/trading-read/margins | KiteTradingReadDiagnosticController | 403 or 405 rejection; no provider read |
| POST | /api/development/market-data/start | KiteMarketDataDiagnosticController | Subscribes and starts gateway; can initiate broker streaming |
| POST | /api/development/market-data/subscriptions | KiteMarketDataDiagnosticController | Changes gateway subscriptions |
| DELETE | /api/development/market-data/subscriptions/{id} | KiteMarketDataDiagnosticController | Removes subscription |
| POST | /api/development/market-data/stop | KiteMarketDataDiagnosticController | Stops gateway |
| GET | /api/development/market-data/status | KiteMarketDataDiagnosticController | Reads gateway health |
| GET | /api/development/market-data/latest | KiteMarketDataDiagnosticController | Reads registry/latest-tick store |

Framework implicit HEAD/OPTIONS behavior outside explicit rejection handlers, /error dispatch, servlet registrations, management base-path/port overrides and deployment proxy policy remain runtime-unverified. application.yml exposes management health/info/prometheus, disables health details, enables probes and defines readinessState/db readiness. Do not infer anonymous access or no side effects: health can query infrastructure. Framework routes are not counted as explicit Java mappings.

Broker login may restore broker state, generate state/nonce and redirect. Callback validates single state parameter and browser nonce, rejects duplicates, consumes the attempt before token exchange and clears the nonce cookie. Reset header protection is not identity. Existing source behavior is retained, not exercised. Diagnostics are conditional and use controller-local checks; absence of a framework dependency does not make them public or authenticated.
