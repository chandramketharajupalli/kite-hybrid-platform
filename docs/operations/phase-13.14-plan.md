# Phase 13.14 frozen assessment plan v1

Baseline: clean develop; HEAD and local origin/develop both
`19c91847a37bdc85020642e037e349caa7c4dd20`. No fetch; remote freshness unknown.
Before-work inventory: 877 tracked raw-byte hashes in ignored tmp/phase1314/before.json.
Phase13.13 manifest and eight parents, 14 registered parent plans, G1, 18 registered
normalized sources, 63 protected research files, 25 K/L files and 11 K envelopes
verified. Three registered CRLF source differences are retained without rewriting.

## Scope, evidence and immutable acceptance

Assessment only. Add this plan, phase-13.14 platform-security-matrix,
oci-provenance-assessment, logging-and-dump-boundaries, runbook and validation under
docs/operations; docs/architecture/kite-sandbox-alternative-linux-host-assessment.md;
research/phase-13.14/generation-1/evidence-manifest.json. Ignored tmp/phase1314 may
hold sanitized observations, progress and checks. No executable or historical edits.
Record plan raw SHA externally before inspections; amendments must be additive.

Use source-defined raw, universal-newline text and canonical-envelope conventions.
Inspect committed Phase13.11-13.13 plans/reports/runbooks/architecture, including
13.12 failure/recovery; K/L and V2 approval records; prior synthetic manifests and
Phase12 gate. Historical tests remain historical, even when their records rehash.
Compare Desktop, controlled Linux VM and dedicated Linux host without assuming
availability, cost, host privilege, hardware monotonicity or policy enforcement.

Per-control states: VERIFIED, PARTIAL, UNAVAILABLE_ON_TESTED_BACKEND, NOT_VERIFIED,
NOT_TESTED, FAILED. Evidence must identify fresh observation versus retained test.
Overall PLATFORM_ASSESSMENT_COMPLETE_GAPS_OPEN unless every mandatory control is
independently demonstrated; BLOCKED on safety/integrity failure. Never CERTIFIED.
OCI signature, effective LSM, genuine UID/GID remapping and complete sink ownership
remain mandatory evidence gaps unless independently established; no retrospective waiver.

## Exact bounded observation and research allowance

Read-only commands, <=45 seconds each, no retries except a documented parser correction:

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber,OSArchitecture
(Get-Item 'C:\Program Files\Docker\Docker\Docker Desktop.exe').VersionInfo | Select-Object ProductVersion,FileVersion
docker version --format '{{json .}}'
docker info --format '{{json .}}'
docker image inspect docker.io/library/eclipse-temurin@sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf
docker ps -a --no-trunc --format '{{json .}}'
docker volume ls --format '{{json .}}'
Get-Command cosign,notation,gpg -ErrorAction SilentlyContinue | Select-Object Name
```

Project only safe selected Docker fields: versions, kernel, OS/architecture, security
options, cgroup version/driver, logging driver, image IDs/digests/rootfs. Do not retain
raw daemon environment, proxy values, hostname, private addresses or credentials.
Inspect resource IDs/labels only for ownership; never inspect unrelated environments.
No containers created, started, exec'ed, removed, images pulled/built or volumes modified.
No direct VM/host securityfs or privileged host access. Current per-process enforcement
is NOT_TESTED; retained Java /proc and denial evidence provides historical scope only.

At most eight official public documentation opens, no search expansion/retries or
artifact acquisition: Adoptium installation/containers and installation/gpg-verification;
Docker engine/security/apparmor, engine/security/userns-remap,
engine/security/rootless, engine/storage/tmpfs, engine/logging/configure and
engine/security/seccomp. Record returned source and access outcome. No registry
authentication, signature-tool installation, alternative image or unrelated network.
Reuse hash-verified cached official metadata; unsigned statements are supporting only.
No vulnerability-clearance or exhaustive-signature-absence claim.

## Budgets, validation and stop rules

Assessment <=90 minutes; zero new workloads (permanent ceiling remains three before
creation for any separately authorized future fixture). No new canary/fault test or
Java/Python suite: no code changes. Current container/volume before/after inventory
must identify owned leftovers; ambiguous ownership stops further work, never broad
cleanup. Do not delete unrelated resources or prior failures/dumpstreams.

Run uv run --offline python scripts/verify-project.py and scripts/check-secrets.py;
git diff --check, status --short --untracked-files=all, diff --name-status and
diff --cached --name-status. Scan new artifacts for known synthetic canaries without
printing them. Rehash all 877 initial files and protected inputs. Canonical JSON uses
sorted keys, compact separators, ensure_ascii=True, allow_nan=False, UTF-8/no newline,
payload only; manifest envelope excludes self-digest. Replay from disk twice and
verify linked input hashes. Keep exact commands, failures, results and raw hashes.

Stop on baseline/protected mismatch, unexpected resource activity or ownership
uncertainty, canary leak, forbidden acquisition or need to change host/daemon policy.
No host provisioning/purchase/migration, Windows/WSL feature change or security install.
No real Kite/token/account/WebSocket/order/trading DB/HALT/trading/application startup,
deployment, .env/risk/config/migration changes, staging, commit or push.
V2, governance second review and four technical reviews stay PENDING; real access
NOT_AUTHORIZED, NO_GO, MIS NOT_READY. Preserve Phase12 BLOCK, H1/H2 FROZEN,
July TEST SEALED, INR10,000 ceiling and ZERO_CALL_ABORT. Stop after assessment;
alternative-host implementation requires separate explicit authorization.
