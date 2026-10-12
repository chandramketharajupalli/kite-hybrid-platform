# Snapshot and resolution contract

No genuine snapshot supplied. Manual supply alone does not prove lawful acquisition.
Require source URL, provenance category, retriever/custodian reference, timezone-aware
retrieval and receipt, raw SHA256, size, permitted use, retention decision and immutable
evidence location. Observation time is distinct from publisher-attested as-of date;
missing attestation stays unknown. Record parser/version Git SHA, schema
kite-instruments-csv-v3, encoding, row count and malformed/duplicate counts.
Keep original bytes separate from derived mappings; never record credential material.

Existing importer rejects hash/length/encoding/schema failures. Resolve NSE cash EQ,
segment NSE using existing case/trim rules without aliases. Preserve instrument_token;
exchange_token is never substituted. Duplicate candidates, token collisions, stale
master and token changes retain reasons. KITE comparison projection does not mutate
original ZERODHA records. Inactive listing is ineligible; unspecified is not proof of
activity. Ticker changes, corporate actions and delistings require external evidence.

Reuse Phase14.2 vocabulary: RESOLVED, AMBIGUOUS, MISSING, INELIGIBLE,
MASTER_UNAVAILABLE, with underlying reasons/candidates. MISSING is no match;
INELIGIBLE includes invalid source/segment/inactive; stale maps to unavailable with
its reason. Unavailable does not mean absent from the exchange. One row per original
ordinal; pin the complete prior JSON/CSV ledger instead of duplicating it.
All 1115 actual entries remain MASTER_UNAVAILABLE. Current membership does not prove
historical point-in-time membership or six/twelve-month availability.
