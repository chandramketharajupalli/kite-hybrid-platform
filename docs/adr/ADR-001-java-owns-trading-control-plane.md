# ADR-001: Java owns trading/control plane

Status: Accepted

## Context

Execution and authoritative state need one owner.

## Decision

Java owns authentication, validation, risk, intents, OMS, execution, lifecycle, positions, persistence and reconciliation.

## Consequences

Python cannot make trading decisions effective without Java validation; no broker integration exists yet.
