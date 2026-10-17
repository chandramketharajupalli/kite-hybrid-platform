# Phase 14.32 frozen attestation acceptance policy

Version Phase1432AttestationLedger.v1. Mandatory family: the exact eight Phase 14.31 artifacts. The pinned acquisition fingerprint prevents resealed changes to filenames, versions, tags, SHA-256 values, URLs or budgets. No archive substitutions or lockfile normalization.

Classifications:

- INDEPENDENT_METADATA_HASH_MATCH: reviewed exact-endpoint custody, exact identity, digest and size, and explicitly not yanked. Index agreement only.
- INDEPENDENT_METADATA_HASH_MISMATCH: expected SHA-256 or size differs; individual digest_match and size_match fields identify the discrepancy.
- METADATA_UNAVAILABLE: no explicitly supplied local metadata.
- ARTIFACT_NOT_FOUND: no exact filename in the version record, including an incompatible wheel or withdrawn/missing file. No inference that absence proves why a file was removed.
- ARTIFACT_YANKED: exact record marked yanked; remains blocked even if its hash agrees.
- ARTIFACT_IDENTITY_AMBIGUOUS: wrong name/version, duplicate exact files, inconsistent URL/type/tag identity.
- SOURCE_UNTRUSTED: endpoint or independently supplied custody binding fails.

Malformed/corrupt data, missing mandatory fields, unknown evidence version, duplicate evidence identity, duplicate JSON keys, altered raw bytes and bounds failures raise an explicit error and cannot certify a ledger. Missing actual observations use null timestamps and observed digests, not zeros or fabricated values. Maximum eight 2 MiB evidence bodies and 4,096 file records per body. JSON values never execute code.

READY_FOR_DECISION requires eight real independent metadata matches and all evidence gates. Current result is PHASE_14_32_PARTIAL. Zero real independent matches; eight missing records; zero observed hash mismatches (all unavailable, not passing). Eight frozen lockfile digests are available. Original runtime archives remain eight missing; the historical broader Phase 14.29 count remains 38 missing original third-party archives. No new archive evidence exists. No publisher signatures, independently authenticated publisher attestations or independent registry attestations are claimed.

Source labels and fixture hashes alone cannot establish trust. Future custody mapping must be reviewed from separately authorized raw index observations. No synthetic fixture may be inserted into that real mapping. Acquisition still requires an explicit owner decision after attestation; metadata approval cannot satisfy it.

All results concern offline preparation. No network requests, distribution downloads or installations occurred. Acquisition PENDING; installation NOT_AUTHORIZED; clean installation NOT_ATTEMPTED; OFFLINE_RC_CONDITIONAL. Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase 12 BLOCK; H1/H2 FROZEN; July SEALED; genuine qualified pairs zero; operational NO_GO.
