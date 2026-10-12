# Phase 14.1 offline resolution policy

Canonical input remains config/universes/phase14_unique_universe.csv, unchanged.
Reconcile all 1,140 original rows against 25 physical duplicate lines and the
1,115 first-occurrence enabled NSE rows. No alias rewriting, sorting or truncation.

Every input row retains its ordinal and exact exchange/symbol spelling; matching
uses stripped, case-insensitive keys. Duplicate source rows are retained as
INVALID_SOURCE and cannot create duplicate pilot requests. Malformed/disabled or
non-NSE input is INVALID_SOURCE. Native CSV snapshot decoding errors reject the
whole import with INVALID_MASTER_SOURCE; never continue using partial mappings.

Without a supplied dated master: MASTER_UNAVAILABLE for all valid unique symbols.
A master observed in the future or over 24 hours old is STALE_MASTER. An optional
publisher date older than the preceding IST date is also stale; a future publisher
date is rejected at provenance construction. Observation time is a caller claim;
it must never be labelled a publisher-attested date. Absence of the latter is
recorded explicitly. A content hash does not establish either claim's truth.

Reuse HistoricalUniverseResolver for exact candidates. More than one symbol match,
duplicate token, prior token reuse or changed mapping is AMBIGUOUS. A single
NSE cash match with a valid uint32 instrument_token is RESOLVED unless explicitly
inactive. Indices, other exchanges, derivatives (including expired contracts),
absent/aliased symbols and inactive rows are NOT_FOUND with distinct reasons and
all matching candidates retained. exchange_token is never used as instrument_token.
No current master is interpreted as point-in-time historical membership.

EQ/NSE cash classification is not proof of common-stock subtype, liquidity or
funding eligibility. ETFs/other cash products require further owner evidence
before pilot selection. A prior snapshot must be supplied to assess changes
across snapshots; lack of one does not prove absence of historical token reuse.
Mergers, renames and corporate actions require independent provenance.

The Phase14.0/14.0A chain is verified using exact path-scoped newline equivalence
against committed blobs. Original evidence is not rewritten and no global
normalization exception is granted. Live/paper-order promotion is not supported.
