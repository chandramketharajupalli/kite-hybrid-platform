# Phase 14.31 owner acquisition decision packet

Decision: PENDING. Gate 31-B: NOT_AUTHORIZED. Gate 31-C: NOT_AUTHORIZED.
No owner decision evidence, signature, issued timestamp or expiry has been supplied.
Formal offline RC acceptance is a third, separate owner decision.

The machine-readable acquisition-authorization.json binds the exact acquisition
manifest fingerprint, full baseline, artifact allowlist, exact source host, proposed
external destination, count and byte limits. The artifact manifest binds full URLs,
filenames, versions, expected digests, Python constraints and compatible wheel tags.

Requested scope, not granted authority:

- Eight exact third-party runtime wheels; maximum total raw bytes 3,029,164.
- Candidate host files.pythonhosted.org only, exact manifest URLs only.
- Proposed destination beneath D:/Yogendra/offline-artifact-quarantine/phase-14.31,
  with the complete acquisition fingerprint as its final directory name.
- No package upgrades, version/variant substitutions, source builds or installation.
- No redirects, alternate-source retries, cache mutation or artifact execution.
- Single baseline, manifest and acquisition; a future approval may last at most 24 hours.

The destination has only been validated as a proposed path. It has not been created.
Expected hashes are present in the frozen lockfile, but independently trusted digest
coverage is zero. The packet is therefore PARTIAL, not ready for unconditional execution.
The owner must supply separately reviewed digest evidence and an explicit acquisition
decision bound to this exact packet. PENDING, REJECTED and DEFERRED permit no acquisition.
An APPROVED label without trusted decision evidence also permits no acquisition.

Future Gate 31-B procedure, requiring separate approval:

1. Verify the decision's provenance or signature with the approved owner evidence store;
   reject absent, stale, wrong-manifest, wrong-baseline or over-broad approval.
   Verify it has not been consumed and durably claim the single acquisition before
   transfer. The Gate 31-A pure validator does not implement this future receipt store.
2. Recheck exact URLs, source hosts, artifact IDs, digests, destination and byte budgets.
   Independently reviewed digest evidence is mandatory; lock correspondence alone is
   insufficient under this packet's policy.
3. Exclusively create the fresh quarantine outside checkout, with restricted access.
   Revalidate path/handle identity to address symlink and time-of-check races.
4. Acquire only allowlisted raw files, enforce per-file and aggregate byte limits during
   transfer, and never execute or import them. Record complete receipts and failures.
5. Verify raw SHA-256 and exact size, then inspect wheel identity, compatible tags,
   metadata dependencies, RECORD coverage and member paths without extraction/execution.
6. Reject mismatches or ambiguity into retained quarantine failure evidence. Do not
   overwrite, repair, substitute, retry alternate sources or silently discard failures.
7. Accept verified artifacts only through a separate receipt bound to the approval and
   manifest. Acquisition approval does not authorize installation or release acceptance.

No part of this procedure has been executed in Phase 14.31.
