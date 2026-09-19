# Local containers

Root docker-compose.yml supplies PostgreSQL and optional ephemeral Redis only.
Ports bind to localhost. No application container, Kubernetes or broker service
is introduced. Docker is a user-managed prerequisite; never install it implicitly.
