# Phase 14.29 frozen provenance assessment plan

Baseline: develop, HEAD and local origin/develop:
a0aa911590c05979592004923c6a68e1b2f9583e. All twenty Phase 14.28 additions are committed.
All 1,384 tracked raw hashes were captured. Preserve these and the separate Phase 14.22
plan at e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31.
Preflight verified Phase 14.28 manifest, receipts, external evidence and resource contract;
historical research/governance evidence, 13 newline reconciliations, 18 frozen source
hashes, 63 research files, K/L, 1,115 symbols and 13,380 combinations.

The working universe, committed blob, sealed resource manifest, both original wheels and
canonical source all match SHA-256
4345207d985672c34e79347eafb13475ed8c7fe5f440914b25ac0999465c3729.
All 1,116 CRLF line endings and 1,115 enabled unique NSE symbols, spelling and order match.
No existing resource, configuration, implementation, manifest, Git config or lockfile edits.

## Scope and trust

Add only a phase1429 audit module, tests, a build/verification tool, six requested documents
and minimal deterministic reports/manifest. Never change trading or expand historical
repository-bound APIs. Phase 14.28 found 37 verified installed RECORDs/version matches,
18 cached archives but no relevant original archive matches; backend archive provenance
was UNVERIFIED. Retain those findings and the narrower standalone API boundary.

Separate runtime, build, tests, development tools, optional extras and transitives by
explicit direct requirements and evaluated dependency edges. Installed presence alone
does not make a package runtime. Audit cached Hatchling 1.27.0 and build transitives
separately. Normalize distribution identity; retain metadata hashes, missing/unhashed
files, locked versions and exact applicable artifact entries.

Evidence hierarchy: independently trusted artifact digest (A); exact archive matched to
frozen lockfile digest (B, lockfile trust separately stated); installed RECORD (C); locked
version match (D); name/version observation (E). Repository preservation establishes the
lockfile's continuity, not independent registry authenticity. No registry attestation is
available. Never elevate C/D/E to archive provenance. Missing originals remain missing.

## Offline methodology

Audit only known uv and pip cache roots already used by Phase 14.28. Represent locations
with aliases in public reports. Do not inspect unrelated private directories. No network,
installation, upgrades, cache cleaning or archive mutation. Bound archive count to 4096,
archive size to 128 MiB and metadata to 1 MiB. Inspect ZIP/tar metadata without extraction;
reject missing/ambiguous identity, incorrect filename/metadata agreement, wrong platform
tags, wrong exact lock artifact digest and duplicate candidates. Do not trust names alone.

Rebuild two independent selected-source wheels and deterministic source ZIPs using the
unchanged Phase 14.28 contract and cached backend, without dependency installation.
Run isolated probes against the new wheel using the already-provisioned Phase 14.28
isolated environment, outside checkout, with -I -B and checkout/socket/credential guards.
No physical OS egress guarantee; physical symlink testing remains unestablished.

## Budgets and gates

One full Python suite initially, focused provenance/resource tests, Ruff --no-cache,
strict mypy with external cache, build/security checks and two deterministic read-only
manifest replays. Python -B, PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain
-p no:cacheprovider, external basetemp/JUnit/cache directories; single test process.
Capture failed attempts and corrective reruns. Check all protected hashes after each
major gate. Keep archive contents and synthetic candle counts bounded. No Java changes.

Freeze this plan hash before implementation. Corrections require additive amendments.
Stop on baseline/resource/preservation violations or a required network/credential path.
Report limitations rather than inventing provenance. Missing original archives require
PHASE_14_29_PARTIAL and OFFLINE_RC_CONDITIONAL. No owner acceptance is inferred.

## Deliverables and preserved decisions

Seal exact inventory, resource reconciliation, dependency decision matrix, original
archive candidates and match/mismatch/missing counts, backend evidence, reproducible
build hashes, fresh receipts, security scans and two replay receipts with an external
certificate and separate command receipt. New text LF and Git-byte equivalent. Preserve
initial failures. Genuine qualification remains zero/BLOCKED, Gate A/B NOT_AUTHORIZED,
Stage B ZERO_CALL_ABORT, Phase 12 BLOCK, H1/H2 FROZEN, July SEALED, operational NO_GO.
No broker calls, genuine data acquisition, operational changes, staging, commit or push.
