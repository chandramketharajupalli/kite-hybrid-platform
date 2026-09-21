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
