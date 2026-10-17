# Phase 14.28 dependency provenance

The audit compares Python distribution metadata, installed RECORD files, uv.lock
versions and available lock archive hashes. Thirty-seven installed distributions,
including the editable local project, have verified installed records and matching
versions. This does not prove the provenance of the original downloaded archives.

Known uv cache and pip wheel-cache roots contained eighteen archive files. None matched
the relevant trusted uv.lock archive digests. This was a bounded cache audit, not an
exhaustive disk search. Per-package names, versions, locations, expected digests, actual
candidate archive hashes and classifications are recorded in the synthetic assessment
and external provenance.json. Source-installed local strategy-engine has no registry
archive digest: its source/build identity is verified separately, not invented.

Classifications: 0 ARCHIVE_HASH_VERIFIED; 37 INSTALLED_RECORD_VERIFIED;
0 LOCK_VERSION_ONLY; 0 UNVERIFIED installed records. Overall ORIGINAL ARCHIVE PROVENANCE
remains UNVERIFIED. Cached Hatchling 1.27.0 and trove-classifiers RECORDs verify, but their
original archive provenance is also UNVERIFIED. No dependencies were downloaded,
updated or replaced in the repository environment.

Missing trusted archive matches for the 36 third-party distributions:
annotated-types, arrow, attrs, colorama, fqdn, idna, iniconfig, isoduration,
jsonpointer, jsonschema, jsonschema-specifications, librt, mypy, mypy-extensions,
packaging, pathspec, pluggy, pydantic, pydantic-core, pydantic-settings, pygments,
pytest, python-dateutil, python-dotenv, referencing, rfc3339-validator, rfc3987,
rpds-py, ruff, six, types-jsonschema, typing-extensions, typing-inspection, tzdata,
uri-template, webcolors. Backend archive evidence is missing separately.

A locally rebuilt archive is not a substitute for a retained original archive matched
to a trusted expected digest. OFFLINE_RC_CONDITIONAL is retained. The dependency audit
makes no claim of registry authenticity beyond the frozen lockfile's expected hashes,
complete supply-chain attestation or production egress containment.
