# ADR-010: Live trading fail closed

Status: Accepted

## Context

A configuration error must not enable real orders.

## Decision

Default PAPER, ENABLE_LIVE_TRADING=false, EMERGENCY_STOP=true. Reject LIVE mode or enabled live flag at startup; provide no executable broker adapter.

## Consequences

Application health is independent of trading readiness. Even clearing emergency stop cannot enable execution in Phase 1.
