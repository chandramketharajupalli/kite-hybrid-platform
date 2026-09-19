# Redis

Optional ephemeral cache for market snapshots and health. Local persistence is
disabled. No orders, positions, trades or authoritative deduplication live here.
The application does not depend on Redis for readiness.

The official `redis:7.4.5` image requires a locally supplied `REDIS_PASSWORD`.
Protected mode remains enabled. The healthcheck uses `REDISCLI_AUTH` and requires
an actual PONG response. Host access is published only on 127.0.0.1:6379.
The `/data` tmpfs overrides the image's volume; no durable Redis volume is created.
Stopping/restarting Redis loses its data, as intended for an ephemeral cache.
There is no production cache behavior to integration-test yet; use authenticated
PING from the development runbook to check infrastructure connectivity.
