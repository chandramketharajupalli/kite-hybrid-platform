# Phase 14.33 quarantine policy

Do not create the proposed directory before explicit artifact acquisition approval. The directory must be new, dedicated, outside the repository and owned by the authorized operator. On Windows, apply and verify an explicit restrictive DACL for the owner/operator and necessary system administration; do not claim restrictive ACLs until actual creation and inspection receipts exist. No permission changes are made during preparation.

Reject relative/UNC paths, traversal components, checkout descendants and symlink/junction ancestors. Use only exact allowlisted filenames; do not derive output paths from response headers. Require an absent destination, no overwrite and exclusive custody throughout acquisition and verification. No launchers, execution, extraction, imports, environment creation or installation. The offline verifier rejects links, nonregular files, extra files, hardlinks and changed file identity/size/mtime. These checks do not eliminate a hostile concurrent filesystem race; exclusive owner-controlled custody is required.

Future procedure after approval:

1. Reverify current baseline, manifest, original metadata custody and exact approval. Record authorization consumption and a request ledger before the first request. Never retry an interrupted or ambiguous acquisition without a new owner decision.
2. Create the dedicated quarantine with approved ownership/ACLs. Create a uniquely named .part file exclusively (CREATE_NEW semantics) for the exact artifact; never overwrite. Partial files must not be recognized as verified wheels.
3. Stream only the approved HTTPS response, with no redirects or retries. Enforce the exact per-artifact expected size, total 3,029,164-byte budget, eight-request maximum and total 20-second request timeout. Stop on unexpected status, source, encoding, content length or stream overrun.
4. Close and flush the safely staged file, calculate raw SHA-256 and compare size/hash to the independently attested artifact record. Do not open wheel members. Preserve immutable transport and verification receipts outside the final wheel inventory, including exact URL, owner/packet fingerprints, request sequence, start/end times, complete status, counts, sizes and raw digest.
5. Only after the checks pass, atomically rename inside the same filesystem using a no-replace operation (Windows MoveFileW-equivalent semantics). Verify the final filename and raw hash again. Filesystem flushes are not independent proof of physical power-loss durability.
6. On any failure retain diagnostic evidence and the failed .part bytes under failed isolation within the quarantine; stop immediately. Never repair, retry, substitute, execute or silently delete them. The quarantine remains incomplete and cannot pass the final exact-inventory verifier. A later run requires a fresh authorized destination.
7. A successful final inventory contains exactly the eight allowlisted wheel files and no other entries. Reverify all raw bytes against authenticated receipts and budgets. Successful verification still does not authorize installation.

No quarantine permissions or real acquisition behavior have been exercised. Tests use bounded synthetic bytes and a temporary Windows NTFS junction outside the repository; the junction rejection passed. No real dependency archives are imported or extracted.

PREPARATION_ONLY. Acquisition PENDING and NOT_AUTHORIZED; installation NOT_AUTHORIZED; clean installation NOT_ATTEMPTED. Zero artifacts downloaded and zero real raw archive hashes verified. OFFLINE_RC_CONDITIONAL. Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase 12 BLOCK; H1/H2 FROZEN; July SEALED; genuine qualified pairs zero; operational NO_GO.
