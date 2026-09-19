# ADR-008: Risk required before execution

Status: Accepted

## Context

Signals are proposals and cannot authorize broker calls.

## Decision

Compose RiskRule checks in Java; emergency stop and positive reference price prove the foundation. Rule errors reject; an empty rule list is invalid.

## Consequences

These rules are not sufficient production risk coverage. Future OMS must require current risk approval and recheck halt immediately before broker dispatch.
