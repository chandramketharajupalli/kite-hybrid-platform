# Local order-command verification

Phase 5B.1 does not enable real trading. Keep the default
`KITE_ORDER_EXECUTION_ENABLED=false`; do not set it while running the local
verification command.

Run the application tests, which use an in-memory fake gateway and an
in-process `MockRestServiceServer` for the Kite adapter:

```powershell
.\mvnw.cmd -pl apps/trading-core '-Dtest=OrderApplicationServiceTest,KiteOrderAdapterTest' test
```

Run PostgreSQL idempotency and restart tests with the integration profile when
Docker is available:

```powershell
.\mvnw.cmd -pl apps/trading-core -Pintegration -DskipUnitTests verify
```

These commands do not call Kite and do not create a real order. There is no
development endpoint for order execution in this phase.

## Phase 5B.2 local execution checkpoint

The integration profile also runs the complete risk-approved execution path
against a loopback-only fake Kite HTTP server. The fake server captures the
form-encoded place, modify and cancel requests in memory and never forwards
traffic. The test harness rejects production or other non-loopback order hosts.

`SUBMITTING` is durable before the place request is sent. Three crash windows
remain intentionally unresolved until Phase 8 reconciliation: a crash before
the request leaves the process, a request accepted while its response is lost,
and a response received before the submitted state is persisted. Each remains
ambiguous and is never automatically retried; reconciliation must use
broker-side evidence before taking recovery action.
