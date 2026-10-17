# Phase 14.31 frozen Gate 31-A plan

Baseline: develop, HEAD and local origin/develop
14c3b93425d7efbfe3e9fa82b0c1b76bdd9c06f1. Phase 14.30's exact three modified and
15 added paths are committed and sealed bytes match. The tracked worktree and index
are clean. The separate untracked Phase 14.22 plan retains
e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31.
Capture all 1,412 current tracked raw hashes externally before implementation.

Preflight passed the committed Phase 14.30 replay, 50 focused/1,025 full-suite receipts,
resource/build evidence, historical preservation, 13 newline reconciliations, 18 frozen
source hashes, 63 research files, K/L governance, 1,115 symbols and 13,380 combinations.
Legacy checks use the verified committed pre-change bytes only for the three sealed
Phase 14.30 edits; every other protected read uses current unchanged worktree bytes.
No historical verifier or evidence is edited.

## Scope and authority

Gate 31-A only: read local metadata, lockfile, bounded existing uv/pip caches and prior
sealed artifacts; generate deterministic preparation evidence and synthetic tests.
No network request, dependency download, installation, upgrade, replacement, cache
mutation, environment creation, staging, commit or push. Gates 31-B and 31-C remain
NOT_AUTHORIZED. The acquisition decision starts PENDING with no owner evidence.

The Windows dependency contract remains tzdata==2026.4; sys_platform == 'win32'.
Phase 14.30's clean environment was empty and installation blocked by missing artifacts.
No clean-installation or standalone certification is performed in this preparation phase.

## Exact artifact and trust policy

Recompute the runtime closure independently from the lock graph and installed Requires-Dist
metadata, applying target markers and version constraints. Target CPython 3.12.14,
Windows AMD64, cp312-cp312-win_amd64 plus compatible universal tags. Fail on ambiguity,
missing/duplicate packages, unsupported extras or incompatible Python requirements.
Choose one uniquely compatible locked wheel per runtime distribution; no source builds.
Recheck that the installed and locked closures agree. Record optional/test/development
and cached Hatchling build dependencies separately, outside the acquisition scope.

Candidate sources are exact files.pythonhosted.org artifact URLs already in uv.lock;
do not contact them. No internal repository or mirror is authorized. Frozen lockfile
hashes establish correspondence only; independent registry attestation is unavailable.
Expected digests must not be labeled independently trusted. Preserve the historical
39 RECORD verifications, 38 missing originals and zero applicable original matches.
Require independently reviewed digest evidence before future acquisition acceptance.

The packet binds the acquisition manifest fingerprint, exact identities/URLs/digests,
source hosts, external quarantine destination, exact count and summed locked byte sizes,
single-manifest/single-baseline validity, maximum 24-hour future approval lifetime,
no upgrades and no implicit installation. Owner evidence, issued time and expiry are
absent while PENDING. Pure synthetic validators cannot grant real owner approval.

Future quarantine must be external, absolute, fresh, free of traversal/symlink escapes
and exclusive-created. Keep mismatches isolated with failure receipts; do not execute,
repair, overwrite or retry alternate sources. Acceptance requires approved evidence,
raw SHA-256, exact size, wheel identity/tags and allowlist agreement. Installation is a
separate authorization over accepted wheelhouse hashes, local project wheel and installer.

## Permitted inventory and validation

No tracked edits. Add only phase1431 audit/policy code, focused tests, an evidence
generator and read-only verifier, the seven requested documents, the acquisition
manifest, authorization packet and evidence manifest. Extra report only if necessary.
Preserve the frozen plan SHA-256; scope changes require an additive amendment.

Use one pytest process at a time, bounded synthetic files and up to 4,096 cache candidates.
Limit existing artifact reads to the established 128 MiB per-file cache bound. Use unique
external evidence/temp directories and external caches, Python -B,
PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain -p no:cacheprovider, Ruff --no-cache and
strict mypy. Budget 15 minutes for full tests and five minutes per other gate.
No Java contracts change. Run focused Gate 31-A tests and the full relevant Python suite.
Verify every tracked hash and the preservation exception after each major gate.

Seal exact inventory, plan/artifact/packet fingerprints, closure, digest coverage,
authorization state, fresh receipts, initial failures and corrective reruns. Perform
secret/canary, LF/Git-byte and whitespace checks and two independent read-only replays.
An external integrity certificate and separate command receipt bind final replays.

Stop with no further edits on unexpected repository/protected changes or a safety
violation. Missing independent digest evidence is a PARTIAL preparation result, not
permission to acquire. Acquisition PENDING; installation NOT_AUTHORIZED/NOT_ATTEMPTED;
RC CONDITIONAL; owner acceptance absent. Genuine qualification BLOCKED with zero pairs;
Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase 12 BLOCK; H1/H2 FROZEN;
July SEALED; operational NO_GO. No real Kite requests or credentials.
