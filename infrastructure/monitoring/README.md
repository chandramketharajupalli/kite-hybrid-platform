# Monitoring

Micrometer and Actuator expose foundation metrics. No monitoring server is
installed in Phase 1. A future Prometheus deployment can scrape /actuator/prometheus
after authentication/network access is designed. Do not label metrics with
unbounded event IDs, instrument IDs or broker order IDs.
