# Phase 14.18A frozen correction plan

Baseline: 0a608de56d969ecd6b6d10a7eda799c70e036ae1; develop = local origin/develop.
Preflight: clean tree/index, 12 Phase14.18 artifacts committed byte-identically,
1233 original baseline hashes unchanged, canonical manifest and software verified,
13 newline reconciliations, G1, 18 frozen sources, 63 research files, K/L and historical
manifests preserved. Full current baseline: 1245 files. External baseline snapshot
SHA-256: 5061228be8221864617c536e7fa730a9ddedb2b1a0080c73a053eb8b71389e2a.

## Reproduced root cause and existing convention

Policy.fingerprint hashes model_dump_json(), retaining sector-map insertion order.
Fixture order SBIN,TCS,INFY yields
a3c22ad01b3bb18df011c29d47736f821a9c3337e50eaeb7a7b0bd7ad2072981.
Canonical report JSON sorts maps recursively; reloaded INFY,SBIN,TCS yields
43ed6d2a4334ac9ebe1442d2a67feee787afbde5a55f896414c0314885cdd675.
Equal policy values and the two distinct hashes were reproduced before edits.
Existing Phase1416 canonical uses sorted keys, compact JSON, allow_nan=False, UTF-8;
Pydantic JSON represents Decimal as strings. A new typed normalization layer will
preserve numeric meaning and reject unsupported values before using that serializer.

## Authorized changes and schema

Modify only phase1418/portfolio.py and the Phase1418 test fixture constructor; add
phase1418/canonical_policy.py, focused correction tests, a separate Phase14.18A
verifier, these three requested documents and additive corrected evidence.
Do not modify any historical report, manifest, plan or verifier. Archive exact before
bytes of the two permitted tracked changes externally; record before/after hashes.

Canonicalization version: Phase1418RiskPolicyCanonical.v2. Require explicit version
on Policy and Control; unknown/missing/legacy versions fail closed. Normalize maps
recursively by string keys, preserve sequence ordering, and type-tag normalized nodes
to distinguish Decimal from strings/maps/bools/integers. Finite Decimal tuples are
normalized without arithmetic or ambient context; trailing zeros and signed zero
normalize by numeric value. Preserve all significant digits; reject floats, nonfinite
Decimals, non-string map keys and unsupported objects. Encode exact canonical payload
as UTF-8 JSON with a schema domain separator and hash with SHA-256.

## Compatibility

New portfolio plan/summary schemas are v2 and carry the fingerprint version. Include
the new source dependency in software inventory. All old checkpoint schemas, absent
or unknown versions reject before any journal or plan upgrade. No migration occurs.
Existing controls/journal hashes remain historical facts, not reinterpreted as v2.
Old serialized policies require explicit caller version selection for a NEW run;
they cannot be loaded as valid v2 policies implicitly. New examples must be generated
under Phase14.18A paths with new run identities and hashes. Old economic mechanics,
limits, strategy definitions, costs, risk approval requirements and accounting remain.

## Tests, evidence and stop conditions

Test mapping/nested ordering, Unicode, ordered lists, serialization round-trip,
Decimal exactness across contexts, numeric rejection, repeated hashing, changed limits,
required/unknown versions, legacy checkpoint rejection without writes, new replay,
all three example bindings and unchanged portfolio economics. Include existing 49
portfolio regressions for accounting, HALT, EOD, recovery and journal corruption.
Run focused and full Python suites, Ruff no-cache, mypy external cache; Python -B,
PYTHONDONTWRITEBYTECODE=1, pytest --assert=plain -p no:cacheprovider, unique external
basetemp/JUnit/diagnostics, 20-minute gate timeout. Protect all baseline files except
the exact two authorized edits after each gate. Preserve failures and corrective reruns.

New evidence records exact inventory, before/after hashes, original/recomputed/corrected
fingerprints, schema, commands/exit codes, receipts, corrected synthetic examples,
preservation and secret/canary checks. Two deterministic manifest replays; final
external certificate and command receipt must have distinct filenames. Capture this
plan SHA-256 before implementation. No original evidence overwrite or claimed recovery.
Stop on unrelated changes/preservation failure or a need to change frozen methodology.
Scope changes require an additive amendment. No Phase14.19 implementation.

Gate A/B NOT_AUTHORIZED; operational NO_GO. Stage B ZERO_CALL_ABORT; Phase12 BLOCK;
H1/H2 FROZEN; July SEALED. No credentials, genuine market data, Kite requests, orders,
infrastructure, operational HALT changes, staging, commit or push.
