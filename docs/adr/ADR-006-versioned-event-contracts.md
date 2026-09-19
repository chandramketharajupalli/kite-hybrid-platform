# ADR-006: Versioned event contracts

Status: Accepted

## Context

Java and Python must agree without sharing implementation types.

## Decision

Use JSON Schema Draft 2020-12 for envelope and SignalEvent.v1, with shared fixtures and both-language validation. Separate domain semantics, serialization and transport.

## Consequences

No runtime transport selected. Binary encoding remains possible for high-volume ticks. Closed v1 schemas require a new version for incompatible extensions; other events remain a roadmap.
