# ADR-002: Python owns strategy/quant plane

Status: Accepted

## Context

Research and strategies benefit from Python tooling.

## Decision

Python owns indicators, scanners, strategies, optimization, replay and backtest orchestration, emitting signals only.

## Consequences

Simulation also routes signals through Java risk and execution semantics. There is no Python order or position ledger.
