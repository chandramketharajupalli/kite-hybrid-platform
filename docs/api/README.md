# API foundation

No trading API or signal-ingress endpoint exists in Phase 1.
Actuator exposes /actuator/health, /actuator/health/liveness,
/actuator/health/readiness, /actuator/info and /actuator/prometheus locally.
Database readiness is part of application readiness outside the test profile.
The internal tradingstatus endpoint is not exposed by default and always reports
ready=false because execution is unavailable. It is separate from health.

Future command APIs require authentication, authorization, idempotency, audit
and explicit risk/OMS application services before any broker execution.
