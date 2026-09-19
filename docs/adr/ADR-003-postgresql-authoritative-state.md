# ADR-003: PostgreSQL authoritative state

Status: Accepted

## Context

Recovery requires durable, transactional trading state.

## Decision

PostgreSQL will own orders, order events, fills, trades, positions and reconciliation state. Redis is ephemeral.

## Consequences

Phase 1 creates a namespace only. Future state changes and idempotency markers must commit atomically; Redis loss must not lose trading history.
