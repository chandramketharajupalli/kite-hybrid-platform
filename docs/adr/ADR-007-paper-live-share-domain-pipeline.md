# ADR-007: Paper/live share domain pipeline

Status: Accepted

## Context

Separate simulation business logic would diverge from live controls.

## Decision

BACKTEST, REPLAY, PAPER and LIVE share Java signal validation, risk, intents and OMS; execution adapters and clocks differ.

## Consequences

No production matching or execution is implemented now. Python simulation cannot bypass the control plane.
