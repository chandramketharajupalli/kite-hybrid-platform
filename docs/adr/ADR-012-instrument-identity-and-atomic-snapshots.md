# ADR-012: Deterministic instrument identity and atomic snapshots

Status: Accepted

## Context

Kite tokens can be reused, whereas platform identity must be stable across downloads
and independent of broker mappings. Reference-data refresh must not expose partial state.

## Decision

InstrumentId is UUID.nameUUIDFromBytes over a versioned canonical byte tuple:
instrument-id:v1, exchange, trading symbol, semantic segment, instrument type,
ISO expiry or empty, normalized plain decimal strike or empty.
Each UTF-8 field is prefixed by its four-byte big-endian byte length.
Strings are trimmed and uppercased with Locale.ROOT; internal spaces remain;
control characters and invalid lengths are rejected. Strike strips trailing zeros.
This is a namespaced deterministic UUID-v3 use, not a cryptographic identity proof.
Broker name/token, tick size and lot size do not enter the identity.

Adapters translate venue/type/segment names to canonical platform conventions.
CASH means cash-market listings, not a claim that every EQ record is common stock.
Semantic segments are CASH, INDICES, FUTURES, OPTIONS. Exchange routing codes
(NSE, NFO, etc.) are preserved; future adapters must use the same venue vocabulary.

Every candidate is validated into immutable maps by platform ID, scoped broker ID,
and exchange/symbol. Any malformed row, duplicate/conflict or empty candidate
rejects the entire refresh. No rows are silently skipped. Publish all indexes by
one atomic reference swap; version increases only on success.
A single synchronized refresh service serializes retrieval and publication.

## Consequences

Token changes preserve identity; distinct expiries/strikes/types do not collide
under canonicalization. Symbol/venue renames change identity: ISIN/corporate-action
continuity and multi-broker mapping aggregation are future work.
Repeated platform IDs are rejected even if they carry different broker IDs.
No instrument persistence, Redis cache or trading authorization is introduced.
Retained snapshots remain valid; consumers needing several coherent lookups must
capture one snapshot. Registry content alone does not establish freshness/eligibility.
