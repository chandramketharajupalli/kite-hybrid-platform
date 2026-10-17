# Phase 14.29 dependency decision matrix

Thirty-nine distributions were assessed: 37 existing environment distributions plus
cached Hatchling and trove-classifiers. All 39 have INSTALLED_RECORD_VERIFIED integrity;
38 third-party dependencies have ARCHIVE_MISSING provenance. The local strategy-engine
project has UNVERIFIED archive provenance: observed local build archives have no applicable
trusted registry artifact entry. No archive digest match or mismatch was found for a
required exact artifact. Eighteen retained cache archives were inspected, without changes.
Wrong identities/versions/platforms are NOT_APPLICABLE, not invented hash mismatches.

The cache roots are uv-cache ($LOCALAPPDATA/uv/cache) and pip-wheel-cache
($LOCALAPPDATA/pip/Cache/wheels). This is a bounded audit of known caches, not an exhaustive
private disk search. Public records use aliases. No network registry lookup occurred.
The retained JSON contains exact expected digests, candidates, metadata fingerprints,
missing/unhashed RECORD entries and optional-extra dependency edges.

Frozen uv.lock continuity is verified; independent registry authenticity is unestablished.
Backend RECORDs verify but original Hatchling 1.27.0, packaging 26.3, pathspec 1.1.1,
pluggy 1.6.0 and trove-classifiers 2026.6.1.19 archive evidence is missing. Runtime tzdata
is an explicit Phase 14.28 probe supplement absent from project runtime metadata.

| Dependency | Version | Groups | Relationship | Installed integrity | Archive provenance |
|---|---|---|---|---|---|
| annotated-types | 0.8.0 | runtime | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| arrow | 1.4.0 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| attrs | 26.1.0 | development, test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| colorama | 0.4.6 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| fqdn | 1.5.1 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| hatchling | 1.27.0 | build | DIRECT | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| idna | 3.20 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| iniconfig | 2.3.0 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| isoduration | 20.11.0 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| jsonpointer | 3.1.1 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| jsonschema | 4.26.0 | test | DIRECT | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| jsonschema-specifications | 2025.9.1 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| librt | 0.15.0 | development | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| mypy | 1.20.2 | development | DIRECT | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| mypy-extensions | 1.1.0 | development | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| packaging | 26.3 | build, test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| pathspec | 1.1.1 | build, development | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| pluggy | 1.6.0 | build, test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| pydantic | 2.13.5 | runtime | DIRECT | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| pydantic-core | 2.46.5 | runtime | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| pydantic-settings | 2.15.0 | runtime | DIRECT | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| pygments | 2.21.0 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| pytest | 8.4.2 | test | DIRECT | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| python-dateutil | 2.9.0.post0 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| python-dotenv | 1.2.3 | runtime | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| referencing | 0.37.0 | development, test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| rfc3339-validator | 0.1.4 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| rfc3987 | 1.3.8 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| rpds-py | 2026.6.3 | development, test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| ruff | 0.16.8 | development | DIRECT | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| six | 1.17.0 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| strategy-engine | 0.1.0 | local project | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | UNVERIFIED |
| trove-classifiers | 2026.6.1.19 | build | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| types-jsonschema | 4.26.0.20260518 | development | DIRECT | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| typing-extensions | 4.16.0 | development, runtime, test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| typing-inspection | 0.4.4 | runtime | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| tzdata | 2026.4 | runtime, test | DIRECT | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| uri-template | 1.3.0 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
| webcolors | 25.10.0 | test | TRANSITIVE_OR_UNSELECTED | INSTALLED_RECORD_VERIFIED | ARCHIVE_MISSING |
