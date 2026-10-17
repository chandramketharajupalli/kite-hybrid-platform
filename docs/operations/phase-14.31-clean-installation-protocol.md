# Phase 14.31 future clean-installation protocol

Gate 31-C: NOT_AUTHORIZED. Current phase installation status: NOT_ATTEMPTED.
Phase 14.30's historical clean-installation result remains BLOCKED. No environment or
package installation is created by this preparation phase.

Before any installation, require accepted acquisition receipts and a separate owner
installation decision binding the exact wheelhouse hashes, local application wheel,
approved installer, interpreter build, destination and test protocol. The eight runtime
dependencies alone do not identify a trusted installer. The local ensurepip candidate
has a recorded digest but unverified original provenance and no execution approval.

Future authorized sequence:

1. Verify CPython 3.12 Windows AMD64 and the approved interpreter/installer identities.
   Create a fresh external venv without system-site-packages; record an empty initial
   distribution inventory. Do not inherit PYTHONPATH or checkout paths.
2. Apply independently evidenced network denial. Use the exact accepted local wheelhouse,
   explicit hashes and versions, no index and no network resolution. A reviewed installer
   invocation must use --no-index, --no-deps, --require-hashes and --only-binary=:all:
   with explicit local wheel paths for all nine runtime installation inputs (eight
   third-party wheels plus the sealed Phase 14.30 project wheel).
3. Capture actual installation commands, stdout/stderr, exit codes, input hashes and
   resulting metadata/RECORD verification. Require exact declared closure and versions;
   reject missing or undeclared packages. Approved bootstrap tooling must be inventoried
   separately and excluded from the declared runtime package set only explicitly.
4. Under -I -B from an unrelated working directory, deny checkout and credential reads.
   Verify imports, Phase1428Resources.v1, configuration and software bindings, the exact
   canonical universe bytes, 1,115 symbols and all six frozen strategy evaluations.
5. Force an empty timezone search path and verify tzdata==2026.4 supplies Asia/Kolkata.
   Check UTC/IST round trips, 09:15/15:30 NSE session boundaries, existing calendar
   fingerprints and fixtures, host-timezone independence and explicit failure when the
   package is hidden in a separate negative test process. Do not change calendar rules.
6. Retain socket/credential/checkout negative guards, resource-tampering rejection and
   deterministic report replay. Record what process guards demonstrate separately from
   independent OS-level egress containment. No broker transport or real data is used.
7. Certify only after actual successful receipts exist. Preserve failed attempts and
   corrective reruns. A planned command, synthetic approval or negative test cannot
   substitute for successful clean installation and standalone execution evidence.

Pinned application wheel: strategy_engine-0.1.0-py3-none-any.whl, SHA-256
80c55e74f63ef0406436bb5ac0aef9722ff9f17b80b3d692ebc2f5c82887c50a.
Canonical universe SHA-256:
4345207d985672c34e79347eafb13475ed8c7fe5f440914b25ac0999465c3729.
No fresh wheel rebuild is needed or performed for Gate 31-A.
