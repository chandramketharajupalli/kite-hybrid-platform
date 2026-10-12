# Phase 14.0 universe and instrument audit

All three user-supplied input files are preserved byte-for-byte under input/phase14.
The exact canonical copy is config/universes/phase14_unique_universe.csv. The
pre-existing repository universe.csv is untouched and is not used for Phase 14.

| Resource | SHA-256 |
|---|---|
| universe(1).csv | bec552ae3c23c0f22b8980cb0c933b300de53d475efab07c4ddd22de3c9a02ca |
| phase14_unique_universe.csv | 4345207d985672c34e79347eafb13475ed8c7fe5f440914b25ac0999465c3729 |
| phase14_removed_duplicates.csv | ae6d0c8a363959b6e6b9a5f62e1487b1caf77321dd2be981ba08136c40488300 |

1,140 data records, 25 exact duplicate exchange/symbol entries, 1,115 unique
members. All are NSE with enabled=TRUE. Comparison trims and case-folds exchange
and symbol, but output retains exact source spellings, special characters such as
&, and first-occurrence ordering. No guessed symbol identity, ticker replacement,
sorting or truncation occurs. Canonical rows match the deterministic source
deduplication exactly. Duplicate source_row values are physical CSV line numbers
including the header as line 1, not one-based data-record indices. A first probe
using data-record indices differed by one; the physical-line reconciliation passes
for all 25 records. Reimporting the canonical file removes zero additional rows.

Machine-readable audit and the full removed-row reconciliation:
[universe-audit.json](../../research/phase-14.0/generation-1/universe-audit.json).

No dated complete instrument master is among the supplied resources. Old frozen
instrument-certification artifacts do not establish current mappings for this
universe and were not repurposed as current evidence. Counts are therefore
RESOLVED=0, UNRESOLVED=1,115, AMBIGUOUS=0, INACTIVE=0, NON_EQUITY=0. These are
missing-evidence statuses, not assertions that the symbols do not exist at Kite.
The [per-symbol report](../../research/phase-14.0/generation-1/instrument-resolution.csv)
preserves all members with empty tokens/snapshot dates/hashes and explicit reasons.
No actual resolution calls were made. Synthetic resolver tests do not contribute
to real resolution counts.

The [stock-strategy report](../../research/phase-14.0/generation-1/stock-strategy-results.csv)
contains 2,230 rows: every symbol independently for the reference strategy at six
and twelve months. All actual performance fields are empty/null because no sourced
history or certified mapping is available. The separate short synthetic example
is explicitly SIMULATED and never attributed to any supplied stock.
