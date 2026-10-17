# Phase 14.29 provenance boundary

This phase adds a read-only dependency audit and rebuilds the unchanged selected offline
Phase 14.28 library. It does not alter dependency versions, caches, lockfiles, resources,
trading methodology, historical evidence or the historical repository-bound API surface.

Evidence levels are distinct: A independently trusted original digest/archive; B archive
matching the exact frozen lock entry (lock trust separately assessed); C installed RECORD;
D installed version matching lock; E name/version observation. There is no independent
registry attestation. Git/history preservation establishes lockfile continuity only.
Successful RECORD verification and byte-identical rebuilding cannot establish A or B.

Archive inspection hashes raw bytes and reads bounded wheel METADATA or source PKG-INFO
without extraction. Filename identity must agree with unique metadata identity. Wheels
must have applicable interpreter/platform tags. Hash matching is against the exact
artifact URL basename, package version and digest, not another platform/version's entry.
Duplicates cannot elevate evidence. Unrelated archives are not reported as hash failures.
Cache locations use uv-cache and pip-wheel-cache aliases, not personal directory names.

Dependency scopes come from declared roots and evaluated requirement/extra edges.
Runtime additionally includes tzdata because the frozen Windows standalone probe supplies
it for ZoneInfo; project runtime metadata does not declare it. This gap is explicit and
unresolved. Optional format-extra edges remain visible. Test/dev/build overlap is allowed;
installed presence alone is not a runtime requirement. The local editable project is
separate from third-party archive provenance and has no trusted registry artifact entry.

Only the unchanged Phase1428Resources.v1 synthetic entry point is supported standalone.
The fresh wheel is loaded with precedence over the pre-existing isolated installation,
using -I -B outside checkout and audit guards. No new environment or dependency installation
is performed. Python audit guards are not independent OS/native-code egress containment.
