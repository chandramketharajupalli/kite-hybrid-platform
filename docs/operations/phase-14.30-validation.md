# Phase 14.30 validation evidence

Classification PHASE_14_30_PARTIAL. Recommendation OFFLINE_RC_CONDITIONAL.
Baseline develop / HEAD / local origin/develop:
91db90a3e8329c2273a3c414d32a1b01dc04e362.

Frozen original plan SHA-256:
16f6d5825affb63cd3eeaab2c7dcfd3247fc4d87543f94c12b04a2a5843b905b.
Additive amendment 1 authorizes only the timezone dependency allowlist correction.
Three tracked files change: pyproject.toml, uv.lock, tests/test_architecture.py under
apps/strategy-engine. All other 1,394 tracked baseline raw hashes remain unchanged.
The exact new and changed inventory and old/new raw hashes are sealed in the manifest.
The separate Phase 14.22 plan remains unchanged at
 e417f0c971b5a4778774785b7e5137db0051667598e64d9c409c250d3cb46f31.

Final focused suite: 50 passed (22 new, 26 resource regressions, two architecture guards).
Final full Python suite: 1,025 passed. Strict mypy: 105 source files, PASS. Ruff: PASS.
No Java source/contracts changed; no fresh Java behavior claim. Receipts retain exact
commands, stdout/stderr hashes, JUnit counts and zero unexpected protected changes.

Windows declaration: tzdata==2026.4; sys_platform == 'win32'. Lock consistency passes
structural exact-edge/version checks; no resolver refresh. Existing timezone semantics
are unchanged. Tests force an empty TZPATH, reject missing tzdata and validate UTC/IST
session boundaries across 2020/2025/2026 independently of two host TZ settings.

Fresh offline installation: BLOCKED. The new external Windows environment has zero
inherited distributions, no pip and no system-site-packages. Eight required runtime
archives are unavailable. No package was installed or copied into it. Missing tzdata
fails explicitly. A passing negative test does not satisfy the installation gate.

Both new wheels: 80c55e74f63ef0406436bb5ac0aef9722ff9f17b80b3d692ebc2f5c82887c50a.
Both new selected-source ZIPs: 568fdd0d1eef52a0fc5276d2779c221a75cda740f6b204aadf52baf913a9322b.
Old wheel: 65deaf04e64d1b82462025a2e24c3d56e93a0721a602b790ca69f7a302b57354.
Old source ZIP: 05a17c23d5b69428f019ed904dfde611b2da9499ee35d160d7c1a58fce46bea4.
The metadata correction legitimately changes both hashes. Every wheel RECORD member,
selected archive member and package-resource hash verifies. These ZIPs are selected
source archives, not full-project sdists. Backend original archive provenance is absent.

Phase1428Resources.v1 raw manifest hash remains
94d40ebdc73f7a4f2da540568e530f5c5cf63e9c5867c049920942d6abdc9e8d.
Canonical universe remains
4345207d985672c34e79347eafb13475ed8c7fe5f440914b25ac0999465c3729,
with 1,116 CRLF endings, 1,115 enabled unique symbols and frozen spelling/order.
Configuration and software resource bindings remain unchanged. The new wheel executes
six strategies with synthetic inputs under the existing isolated interpreter. Its
checkout/credential/socket guards pass; fresh installation success is not claimed.

Historical provenance remains 39 installed RECORD verifications, 38 missing third-party
original archives, zero applicable original matches and no independent registry
attestation. Original archive provenance is UNVERIFIED. Cached installed files and
reproducible builds do not upgrade that classification. No downloads or upgrades occurred.
Static scans cover 44 modules per wheel, no prohibited imports or entry points. Final
secret/canary, LF, Git-byte and whitespace checks are replayed over the final inventory.
OS-level production egress containment remains PRODUCTION_EGRESS_NOT_VERIFIED.

## Retained failures and corrective receipts

The first historical preflight adapter used the old parent baseline and raised
BASELINE_CHANGED; correcting only its in-memory lifecycle assumptions and passing the
actual committed baseline to the preservation checker passed. No evidence changed.
Initial lint-1 failed formatting and mixed root/Python configuration checks. The next
root-directory lint run reported 34 import-order findings from the wrong project-root
interpretation. Correct lint from the engine directory passed without rewriting any
historical source. Initial focused-1: 49 passed, one failed exact dependency allowlist
check. Amendment 1 authorized adding only tzdata; focused-corrected passed 50.
The lock regression was subsequently pinned to this phase's full baseline instead of
moving HEAD, preserving usefulness after commit. The first full-suite receipt is retained;
final focused, full, Ruff and mypy gates validate the final test source. All receipts
remain external and hash-bound, including failures and corrective reruns.

Report canonical fingerprint: 19e97f58e9b54909694451ca3fe088db661191de229ad90a6925ae77dd45982f.
External evidence directory: C:/Users/CHANDR~1/AppData/Local/Temp/phase1430-tuwucpnc.
The manifest binds all 1,397 initial raw hashes, explicit authorized changes, artifacts,
original failures and final gates. Two independent read-only replays, an external integrity
certificate and a separate certificate-check receipt bind the final evidence. The
certificate is synthetic integrity evidence, not an independent signature or approval.

Preflight verified historical evidence, all 13 newline reconciliations, frozen G1,
18 source hashes, 63 protected research files, K/L governance, the 1,115-symbol universe
and 13,380-combination family. The sealed baseline and repeated preservation gates
verify that historical evidence is untouched. No genuine qualified pairs. Gate A/B
NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase 12 BLOCK; H1/H2 FROZEN; July SEALED;
operational NO_GO. Real Kite requests zero. No staging, commit or push.
