# Phase 14.30 clean installation assessment

Clean offline installation: BLOCKED. A new external Windows venv was created with
with_pip=False and system_site_packages=False. Its isolated -I -B probe found zero
installed distributions, confirmed the venv configuration and explicitly rejected
Asia/Kolkata with an empty timezone path and no tzdata. No packages were installed.

Eight declared active runtime dependencies lack applicable retained archives matched
to the existing lock digests:

- annotated-types
- pydantic
- pydantic-core
- pydantic-settings
- python-dotenv
- typing-extensions
- typing-inspection
- tzdata

Their installed versions match the current lockfile and constraints, but those installed
distributions are not approved archive substitutes. Neither copying site-packages nor
repacking installed files was used. No dependency installation, upgrade or download
occurred. The empty-environment negative probe is not successful installation evidence.

Separately, the newly built wheel executes all six frozen strategies on bounded synthetic
candles using the existing Phase 14.28 isolated environment. Checkout reads, credential
reads and socket operations are denied by that probe. It loads the new wheel with
precedence and verifies the original resource/configuration contract. This does not
establish installation completeness for a fresh environment.

The deterministic assessment records all 39 installed/build distributions by runtime,
Windows-only runtime, build, test, development, optional-extra and transitive scope.
tzdata is shared with the development graph but is a Windows-marked direct runtime edge.
Historical provenance remains 39 installed RECORD verifications, 38 missing original
third-party archives and zero applicable original archive matches. Independent registry
attestation is unavailable. Original archive provenance remains UNVERIFIED.

The external provenance-report.json preserves the existing Phase 14.29 auditor's raw
output. Its legacy runtime_supplement flag is hardcoded to the historical undeclared
state; it is not used as the current declaration decision. The Phase 14.30 report's
dependency_declaration, active runtime_graph, marked lock checks and wheel METADATA
are the authoritative current declaration evidence. No historical auditor was rewritten.

Next prerequisite: supply independently reviewed, applicable local artifacts and their
trusted expected digests through a separately authorized process. Then rerun clean
installation without system inheritance, network resolution or undeclared packages.
This report grants no download, package substitution or operational authorization.
