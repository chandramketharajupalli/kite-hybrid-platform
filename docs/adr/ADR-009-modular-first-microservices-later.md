# ADR-009: Modular first, microservices later

Status: Accepted

## Context

Operational complexity would obscure correctness at this stage.

## Decision

One Java application and one lightweight Python package with explicit domain and adapter boundaries.

## Consequences

No Kafka, runtime messaging stack, Kubernetes or frontend. Extract services only after measured operational needs and transaction boundaries justify it.
