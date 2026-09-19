# ADR-004: Strategies cannot access brokers

Status: Accepted

## Context

A strategy bypass would invalidate centralized controls.

## Decision

Python must not authenticate for trading, place, modify or cancel broker orders, own authoritative positions/orders, or bypass Java risk.

## Consequences

Dependency tests guard accidental coupling. Future credential isolation and network policy must enforce the runtime boundary; tests are not a sandbox.
