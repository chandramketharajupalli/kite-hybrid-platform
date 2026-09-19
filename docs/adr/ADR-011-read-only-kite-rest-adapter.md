# ADR-011: Narrow read-only Kite REST adapter

Status: Accepted

## Context

Phase 2 needs only authenticated profile and instrument-master GET requests.
Zerodha maintains an official Java SDK (v4.0.1 inspected). Its instrument DTO uses
double for tick_size/last_price; its dependency bundle also covers trading and
WebSockets. Phase 1 forbids SDK dependencies and prices represented as floating point.

## Decision

Use existing Spring RestClient and the JDK HTTP connection implementation behind
Kite infrastructure adapters. Fix the HTTPS origin to api.kite.trade and restrict
the implementation to GET /user/profile and GET /instruments with API version 3.
Disable redirects, set connect/read timeouts, bound response bodies and sanitize
errors. No SDK or general-purpose custom HTTP framework is added.

Add only jackson-dataformat-csv, aligned to Spring Boot's existing Jackson BOM
(2.21.4). It handles CSV quoting/headers; decimal text maps directly to BigDecimal.
Transport/JSON/CSV types remain inside infrastructure. A configured external
access token is required; no login, token exchange, refresh or logout is implemented.

## Consequences

The adapter owns a small, tested two-endpoint HTTP contract. It does not inherit
broker mutation methods or WebSocket dependencies. Official SDK support is not
absent; direct HTTP is a deliberate fit for this phase. Reconsider only through
an explicit decision that preserves decimal correctness, ports and read-only scope.
Mock tests establish mapping/HTTP behavior, not successful real Kite connectivity.

Sources inspected:

- [Official Java SDK release](https://github.com/zerodha/javakiteconnect/releases/tag/v4.0.1)
- [Instrument DTO](https://github.com/zerodha/javakiteconnect/blob/v4.0.1/kiteconnect/src/com/zerodhatech/models/Instrument.java)
- [SDK dependencies](https://github.com/zerodha/javakiteconnect/blob/v4.0.1/pom.xml)
- [Authentication and profile](https://kite.trade/docs/connect/v3/user/)
- [Instrument CSV](https://kite.trade/docs/connect/v3/market-data-and-instruments/)
- [Error categories](https://kite.trade/docs/connect/v3/exceptions/)
- [Jackson CSV module](https://github.com/FasterXML/jackson-dataformats-text/tree/2.21/csv)
