# Local containers

Root docker-compose.yml supplies PostgreSQL and optional ephemeral Redis only.
Ports bind to localhost. No application container, Kubernetes or broker service
is introduced. Docker is a user-managed prerequisite; never install it implicitly.

See the [PowerShell development runbook](../../docs/runbooks/local-development-infrastructure.md)
for setup, authenticated connectivity, Flyway, Testcontainers and data lifecycle.
Use `docker compose config --quiet`; resolved configuration contains local passwords.
The two services are independent, so neither needs a `depends_on` relationship.
