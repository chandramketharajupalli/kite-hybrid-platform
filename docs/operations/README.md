# Operations foundation

Start in PAPER with live disabled and emergency stop enabled.
See [controlled live readiness](controlled-live-readiness.md) for the service-only
operator boundary, V10 read-only preflight and mandatory backup/restore verification.
No real order is sent by this implementation phase.
See [Phase 10.8A MIS validation](phase-10.8a-validation.md) for product-specific
funding, the full-notional ceiling, synthetic tests and unresolved real collateral eligibility.
See [Phase 10.8B collateral review](phase-10.8b-validation.md) for bounded real
read-only evidence and the NOT_READY collateral/cash-component conclusion.
See [Phase 10.8C composition review](phase-10.8c-validation.md) for refreshed
pledge metadata, token/DB preservation and the remaining authoritative eligibility gaps.
See [Phase 10.8D intraday-first validation](phase-10.8d-validation.md) for the
accepted MIS funding contract, full regressions and historical-strategy handoff.
Application liveness does not depend on a broker or Redis. Readiness includes
database availability outside isolated tests; trading readiness remains false.
Flyway fails startup on migration errors. Phase 5B.1 adds durable order and
idempotency tables, while execution remains disabled by default. Flyway V2 stores encrypted Kite tokens. Retain KITE_TOKEN_ENCRYPTION_KEY separately
from the database across restarts; losing it prevents token decryption. Missing
interactive authentication reports KITE_AUTH_REQUIRED without stopping startup.
See [Kite Authentication](../../README.md#kite-authentication) for the browser
workflow, safe status endpoint and local-only reset.

Boot emits structured JSON console logs with application identity. Never log
credentials or full broker responses without redaction. Actuator/Micrometer
supply basic JVM/process/HTTP metrics. Prometheus is exposed only on loopback.

Future measurements: gateway status, subscriptions, ticks/sec, last tick age,
queue depth, dropped ticks, signals, risk approvals/rejections, orders, broker
errors, reconnect count and reconciliation status. Implement these alongside
their owning runtime behavior; do not invent inactive metric registries.

Docker Compose is for local development only; choose production credentials,
TLS, backups, retention and access control before deployment.

Use the [local development runbook](../runbooks/local-development-infrastructure.md)
for the Maven/Docker workflow and the [Maven migration report](maven-migration-report.md)
for current build and infrastructure validation. The Phase 1, Phase 2 and earlier
development-infrastructure reports retain historical commands and results.
