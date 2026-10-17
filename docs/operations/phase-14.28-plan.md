# Phase 14.28 frozen implementation plan

Baseline: develop; HEAD and local origin/develop are
9d4199524f29ebf4b77094e81ca3ccf574e8078a. Phase 14.27 is committed as
13 additions. Preflight verified its sealed report, software/dependency inventory,
18/951 test receipts, external artifacts and certificate/replays; all 1,364 tracked
files; 13 newline reconciliations, 18 frozen source hashes, 63 research files,
K/L and historical manifests; 1,140 source rows minus 25 duplicates = 1,115 unique
symbols in frozen order; six strategies and 13,380 research combinations.

Preserve all existing tracked bytes and the separate untracked Phase 14.22 plan
(e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31).
No staging, commit, push, network, credentials, broker access or operational changes.

## Existing contract and failure

Phase 14.27 cached Hatchling 1.27.0 builds two identical selected-source wheels
and fixed-timestamp source ZIPs. Its isolated probe imports six strategies, but
hardening.UNIVERSE and SOURCE derive from the source checkout. Original dependency
archives were not available; installed RECORD verification does not close provenance.
Retain that failed resource-binding evidence. Reproduce the historical path failure
outside the repository before adding the new resource entry point.

## Resource ownership and scope

Add phase1428 only: a versioned importlib.resources loader, explicit two-resource
allowlist, unchanged canonical CSV bytes, and canonical nonsecret offline configuration.
Pin resource and manifest digests, symbol ordering, counts, schema and software inventory.
Resources are immutable inputs: no CWD/home fallback, network lookup or .env access.
Reject missing, modified, incompatible, unexpected, traversal and symlink resources.
The original source universe remains authoritative; package an exact byte copy only.
Configuration binds existing frozen policy/strategy fingerprints and offline-only scope.
Existing trading/indicator/risk/accounting algorithms and historical modules stay intact.
The supported standalone entry point will validate resources, initialize the registry
and run bounded synthetic assessment through existing components. Historical research
replay tools that explicitly require historical evidence remain repository tools;
state the exact standalone runtime surface and any unresolved bindings honestly.

## Implementation and build boundaries

Allowed additions: phase1428 source/resources, focused tests, an offline build/evidence
verifier, five requested documents and minimal evidence manifest/report. No old source
or pyproject/lockfile edits: use an explicit staged project with existing pyproject and
a selected import closure plus the new package. No launchers, broker/config credential
modules, research datasets or historical evidence in the wheel. Build with cached
backend only. Two independent staged wheels and source ZIPs must be byte-identical.
Install/extract verified wheels externally with cached runtime dependencies; run Python
isolated from unrelated CWD, deny checkout reads and sockets/subprocesses via audit hook.
Read-only resource behavior is tested. This is not OS/native egress containment.

## Provenance and validation

Inventory installed metadata, RECORD hashes, uv.lock versions/archive hashes and
bounded known local uv/pip archive caches. Match only exact original local archive bytes
to lockfile digests. Classify ARCHIVE_HASH_VERIFIED, INSTALLED_RECORD_VERIFIED,
LOCK_VERSION_ONLY or UNVERIFIED independently. Missing original archive evidence keeps
provenance UNVERIFIED and OFFLINE_RC_CONDITIONAL. Do not download, replace or fabricate.

Use PYTHONDONTWRITEBYTECODE=1, Python -B, pytest --assert=plain -p no:cacheprovider,
external temp/cache paths, Ruff --no-cache and strict mypy. One full-suite run initially;
rerun affected gates only for corrections. Bound resources to 4 MiB each, source closure
128 files, tests single process and archive scans known caches only. Run focused resource,
tampering, isolated runtime, reproducibility and provenance tests, full Python suite,
lint/type checks, security scan and two deterministic manifest verifications. No Java
changes or tests. Capture all failed attempts and corrective reruns externally. Verify
baseline and exception hashes after each major gate. Freeze this plan hash before code.

## Evidence and acceptance

Record baseline/current inventories, plan/resource/software/dependency fingerprints,
archive hashes and contents, probe receipts, exact fresh tests, failures and corrections,
external certificate separate from command receipt, two manifest replays and limitations.
LF-stable new text and Git-byte equivalence are required; frozen CSV bytes take precedence.
Stop for baseline/preservation failures, unsafe package content, network/credential
requirements or resource-byte changes. Missing archive provenance yields PARTIAL, never
VALIDATED. Genuine qualification stays zero/BLOCKED, Gate A/B NOT_AUTHORIZED,
Stage B ZERO_CALL_ABORT, Phase 12 BLOCK, H1/H2 FROZEN, July SEALED, operational NO_GO.
Owner approval, independent custody, power-loss/multi-host safety and production egress
are not established. Scope changes require additive amendments retaining initial failures.
