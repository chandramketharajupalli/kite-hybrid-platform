# Phase 14.41 identity continuity

Phase1441InstrumentIdentityTimeline.v1 is a synthetic compatibility ledger. Stable security identity is an explicitly evidenced claim, not something inferred from a current token or matching symbol. Periods are ordered half-open UTC intervals with generation/source hashes, observation, availability, expiry, transition reason and evidence reference.

Reject gaps, overlaps, cross-security mixing, expired mappings, missing transition evidence, unchanged generations on transitions, unsupported symbol/token changes and token reuse across different securities in the supplied registry. Registry completeness remains a required future source obligation: a successful local check cannot prove absence of a conflicting record outside its supplied scope. Token reuse is conservatively rejected even across nonoverlapping periods until an independently justified policy exists.

POINT_IN_TIME requires evidence available before period start. RETROSPECTIVELY_NORMALIZED permits evidence known by the assessment time but blocks legacy conversion; it does not authorize historical strategy knowledge. Delisting/suspension and restructuring evidence are assessed as action-bearing scenarios with no automatic synthesis of candles or securities. Multi-token timelines stay separate from single-token v1 research.
