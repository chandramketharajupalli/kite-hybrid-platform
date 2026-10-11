# Phase 13.15 frozen design plan v1

Baseline: clean develop, HEAD and local origin/develop
ca308f86527e2adb380a72ea1fe78cba82f37c1b. No fetch; remote freshness unknown.
885 tracked raw hashes captured in ignored tmp/phase1315/before.json. Phase13.14
canonical fingerprint 782f718b4135679be7bcd85d8f02cf035c6e00eb7ac1eafb5eb82ab0b68c745b
and its registered inputs match: 18 normalized sources, 63 research files, 25 K/L
files, 11 governance envelopes, nine prior manifests and 15 prior plan registrations.
Raw bytes protect documents; registered universal-newline UTF-8 protects only the
18 frozen sources. Preserve the three recorded CRLF differences without rewriting.

## Scope and sources

Design only; zero VM/container/cloud resources, downloads, installs, runtime tests,
host inspection or policy changes. Read repository instructions and committed
Phase13.14 documents/manifest, linked governance, freeze and synthetic evidence.
Historical observations are not fresh host measurements. No source/test/config edits.
Permitted additions: this plan; docs/architecture/kite-controlled-linux-vm-testbed.md;
docs/operations/phase-13.15-platform-comparison.md, phase-13.15-security-acceptance-matrix.md,
phase-13.15-provisioning-proposal.md, phase-13.15-runbook.md, phase-13.15-validation.md;
research/phase-13.15/generation-1/evidence-manifest.json. Ignored tmp/phase1315 holds
before hashes and validation receipts only. Record this plan's raw SHA externally.
No retrospective edits; material changes require numbered additive hashed amendments.

Permit <=10 official public documentation opens, no retries/artifact retrieval, for
Ubuntu release lifecycle, Ubuntu Server AppArmor/Secure Boot/time/security updates
and signed-image verification, Docker rootless/userns and Oracle Java21 diagnostics.
Use only ubuntu.com, documentation.ubuntu.com, docs.docker.com, docs.oracle.com.
Documentation informs proposals, never current enforcement. No image registry pull,
OS image download, acquisition, installer, cloud/API request or Git fetch.

## Proposed design and threats

Compare current Desktop, controlled local Linux VM and dedicated Linux host.
Tentative guest Ubuntu Server24.04 LTS/GA6.8 family with supported current security
patches, subject to vendor documentation and future exact image/kernel selection.
No installed-system claim. Prefer AppArmor and evaluate rootless versus userns-remap
without changing either. Budgets are estimates: one VM, 4vCPU/8GiB RAM/60GiB disk
maximum proposed allocation pending host measurement, <=3 workload containers plus
one explicitly identified cleanup helper. This phase itself consumes zero such slots.

Model synthetic credentials, actual Java owner, guest, hypervisor/host admin, registry,
daemon, collectors, journal/disposable PostgreSQL, witness/coordinator, observer and
snapshot operator. Include egress/exfiltration, escape, forged HALT, replay, dumps,
image substitution, forwarding, administrative tampering and all-authority rollback.
Do not claim hardware monotonicity or protection against a privileged common administrator.

Mandatory future gates: exact OS image authenticity/support, OCI exact-subject
signature authentication, enforcing AppArmor with Java denial/audit/restart proof,
real UID/GID mapping and guest/physical-host boundary, resource limits, default-deny
network, actual-owner one-use probes, all sinks owned and bounded, witness independent
restore authority, recovery negatives and unconditional verified cleanup. Unsupported
controls remain open. No design document is enforcement evidence or provisioning consent.

## Acceptance, validation and termination

States DESIGNED, VERIFIED, UNVERIFIED, UNAVAILABLE, FAILED. Design ready means every
mandatory gate has evidence requirements, proposed accountable role and next action,
not that enforcement passes. Provisioning remains PROVISIONING_AUTHORIZATION_PENDING;
TESTBED_SECURITY_CERTIFIED unavailable in this phase; OPERATIONAL_NO_GO unchanged.
Overall LINUX_VM_TESTBED_DESIGN_READY_PROVISIONING_PENDING only after document/reference,
project/secrets/diff/protection/replay checks pass; DESIGN_PARTIAL for incomplete design;
BLOCKED/BASELINE_OR_EVIDENCE_BLOCKED on baseline, integrity or safety failure.

Budget <=90 minutes, no new executable validator, no Java/Python suite/Ruff/mypy runs.
Run uv run --offline python scripts/verify-project.py and scripts/check-secrets.py,
git diff --check, status --short --untracked-files=all, diff --name-status and
diff --cached --name-status. Offline one-off structural probes verify local Markdown
links, exact inventory, known synthetic-canary absence, hashes and canonical replay.
Canonical envelope: sorted compact ASCII JSON, UTF-8/no newline, allow_nan=False,
manifest payload only, self-fingerprint excluded. Replay twice from disk with inputs.
Record actual failures and corrections. Rehash all 885 baseline files and registered
protected inventories; no prior report or raw failure evidence is changed/deleted.

Stop on fingerprint mismatch, unrelated change, secret exposure, need for provisioning,
host policy modification or unauthorized acquisition. No real Kite/account/token/
WebSocket/order/trading DB/HALT/arm/execute/live or paper operations; no trading startup,
.env/migration/risk changes, stage/commit/push. Preserve V2/governance second review/
four reviews PENDING, real access/implementation under V2 NOT_AUTHORIZED, NO_GO,
MIS NOT_READY, Phase12 BLOCK, H1/H2 FROZEN, July SEALED, INR10,000 and ZERO_CALL_ABORT.
Stop after the reviewable DRAFT / NOT APPROVED proposal; no automatic next action.
