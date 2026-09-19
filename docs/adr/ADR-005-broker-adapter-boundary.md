# ADR-005: Broker adapter boundary

Status: Accepted

## Context

Domain lifecycle must survive broker and simulation changes.

## Decision

Java execution uses a BrokerAdapter port; domain models never import Kite SDK types.

## Consequences

No adapter is executable in Phase 1. Paper and Kite adapters come later. Ambiguous broker outcomes require reconciliation, not automatic resubmission.
