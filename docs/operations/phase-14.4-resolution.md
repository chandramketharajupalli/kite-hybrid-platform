# Actual instrument resolution

No genuine master acquired or supplied. All results are MASTER_UNAVAILABLE, not
claims that securities are missing from NSE. RESOLVED=0; AMBIGUOUS=0; NOT_FOUND=0;
INVALID_INSTRUMENT=0; MASTER_UNAVAILABLE=1115; NOT_EVALUATED=0. Total1115.
Actual tokens, snapshot hash, observation timestamp and performance remain absent.

Canonical universe reconciles 1140 source rows, 25 duplicate rows, 1115 unique enabled
NSE symbols. First-occurrence ordering and exact spelling retained. Legacy universe
not used. Reuse the complete Phase14.2 ledger without duplicating or rewriting it:
research/phase-14.2/generation-1/resolution-ledger.json
SHA256: 5ad51059a9bb7a49bb8d42f3d17d3ff713cacc877ff20d54229915dc8553fed9

Every row matches the canonical symbols and fresh Java test-emitted missing-master
ledger. Phase14.3 ledger reference also pins the CSV. No status translation is needed
for current unavailable rows. Future reporting may map existing MISSING to NOT_FOUND
and INELIGIBLE to INVALID_INSTRUMENT while preserving underlying reason/candidates;
no production implementation of that translation is claimed here.

Future genuine import must preserve immutable permitted raw bytes separately from
fixtures, validate all rows, quarantine malformed/duplicate identities and never
force resolution. Use exchange+tradingsymbol lookup; token values are not permanent
identities. Preserve ZERODHA/KITE comparison behavior. Renames, suspensions and
corporate actions require dated evidence. Idempotent offline import is not a grant
of persistence rights. Gate B proposal selection remains deferred until genuine
verified symbols, session and separate approval exist.
