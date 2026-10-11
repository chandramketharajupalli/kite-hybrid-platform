# Phase 13.16 frozen read-only feasibility plan v1

Baseline: clean develop, HEAD/local origin/develop
0228ac39ffedbb9bee3ac0bb101631a96050558e. No fetch; remote freshness unknown.
893 tracked raw hashes captured before work. Phase13.15 canonical fingerprint
acd96cd2c3048c1e8fc3363fc043ff59bcda3b0b8d003bed5a73b53f6d7ec58f verified, with
registered inputs, G1/18 normalized sources/63 raw research,25 K/L,11 governance
envelopes and ten earlier manifests. No prior evidence is rewritten.

## Scope and permitted artifacts

Read-only Windows host feasibility; no elevation, feature/configuration changes,
provisioning or workloads. Add only docs/operations/phase-13.16-{plan,
host-measurements,hypervisor-feasibility,security-gate-matrix,
provisioning-authorization-request,runbook,validation}.md and
research/phase-13.16/generation-1/evidence-manifest.json. Ignored tmp/phase1316
holds selected sanitized observations/check receipts. Freeze raw plan SHA before
measurement; material changes need a separately hashed numbered amendment.

## Exact measurement allowance

Use available commands without elevation, <=45 seconds per command, no automatic
retry/install. Record start/end UTC from local machine clock, command, selected
output, success/error; timestamps are not independent identity/governance attestation.
Never retain computer/user names, serials, MAC/IP addresses, command lines, keys,
full environment or raw Docker configuration. Suppress potentially identifying
error text; retain error type/category/code and sanitized explanation.

1. Get-CimInstance Win32_OperatingSystem: Caption,Version,BuildNumber,OSArchitecture,
   LastBootUpTime,TotalVisibleMemorySize,FreePhysicalMemory (KiB).
   Get-CimInstance Win32_ComputerSystem: TotalPhysicalMemory,NumberOfLogicalProcessors,
   HypervisorPresent (no identity fields).
   Get-CimInstance Win32_Processor: NumberOfCores,NumberOfLogicalProcessors,
   VirtualizationFirmwareEnabled,SecondLevelAddressTranslationExtensions,
   VMMonitorModeExtensions,LoadPercentage. Three memory/CPU snapshots >=5s apart,
   not a peak-load benchmark or proof of future performance.
2. Get-Volume -DriveLetter D,C: DriveLetter,FileSystemType,DriveType,HealthStatus,
   Size,SizeRemaining. Get-PSDrive -Name D,C: Name,Used,Free fallback; no user paths.
   Proposed storage D:\Yogendra\synthetic-vm-testbed (NOT created). Free capacity
   is not reservation; thin disks and backups may consume it later.
3. Get-Command for Get-VM,Get-WindowsOptionalFeature,Get-BitLockerVolume,VBoxManage,
   vmrun,qemu-system-x86_64,wsl,docker: availability only. If present, query
   Get-VM with only State,ProcessorCount,MemoryAssigned,MemoryDemand,Uptime (no names);
   Get-WindowsOptionalFeature -Online -FeatureName for Microsoft-Hyper-V-All,
   VirtualMachinePlatform,Microsoft-Windows-Subsystem-Linux,HypervisorPlatform;
   failures are UNKNOWN, never a reason to enable or elevate.
4. wsl --status, wsl --version, wsl --list --verbose. Capture versions/states;
   replace distribution names with ordinal IDs, retain docker-desktop only as known
   component if present. No distro start/shutdown/exec or .wslconfig inspection.
5. docker version --format '{{json .}}'; docker info --format '{{json .}}': select
   versions,OS/kernel/architecture,security options,cgroup,logging,NCPU,MemTotal,
   container counts only. docker ps -a --no-trunc --format '{{json .}}': select
   immutable ID/state and owned/unrelated classification, no environment or ports.
   docker stats --no-stream --format '{{json .}}' only if running containers exist,
   retaining CPU/memory limits/usage by ordinal workload. No start/build/pull/exec.
6. Get-Process for idea64,java,javaw,vmmem,vmmemWSL,Docker Desktop,com.docker.backend,
   vmware-vmx,VirtualBoxVM,qemu-system-x86_64: aggregate count/WorkingSet64 by name;
   exclude argv/environment/user/path. For a running Docker Desktop process only,
   attempt MainModule.FileVersionInfo product/file version; unavailable stays UNKNOWN.
   Other VM tools: Get-Command result and existing executable VersionInfo only;
   no vendor tool invocation that might initialize state or require services.
7. If Get-BitLockerVolume exists, read D/C MountPoint,VolumeStatus,ProtectionStatus,
   EncryptionPercentage only (no KeyProtector). Get-CimInstance Win32_ShadowCopy
   count only; do not infer complete backup policy from an empty count. Access
   denial remains UNKNOWN. No filesystem traversal or backup/content access.

Permit <=6 official documentation opens at learn.microsoft.com, docs.docker.com,
virtualbox.org or broadcom.com for edition prerequisites/coexistence if needed;
no artifact acquisition, licenses accepted, installs or unrelated network. Source
guidance is distinct from installed/configured host evidence. No Git fetch.

## Predeclared capacity and safety decision

Assess proposed 4vCPU/8GiB/60GiB VM without assuming capacity. Proposed conservative
admission screen: >=8 logical processors and >=4 physical cores; observed CPU load
<50% across snapshots, but sustained IDE/build peaks remain unknown. RAM minimum
available >=14GiB: 8GiB guest +2GiB estimated hypervisor overhead +4GiB residual
host margin, with existing processes already consuming measured RAM. No double-count
of their working sets; attribution is supporting only. Disk on D: require 60GiB
guest +60GiB rollback workspace +20GiB intake/staging + reserve max(40GiB,10% total).
Check C free reserve >=20GiB separately. These are proposed safety estimates, not
vendor minimums or permission to snapshot witness state. No disk allocation performed.

Passing snapshots yields at most CONDITIONALLY_FEASIBLE pending sustained workload,
hypervisor support/privileges, storage policy and user approval. Insufficient measured
headroom => NOT_FEASIBLE for the target under current conditions; missing facts =>
UNKNOWN. Do not reduce reserves after seeing results. Separate hardware screen,
installed hypervisor feasibility, guest security and overall authorization.

Map every original G01-G15 row with owner/pass evidence/next action. All untested
guest controls NOT_VERIFIED; software witness is not hardware monotonicity and common
admin/snapshot control defeats claimed independence. OCI_SIGNATURE_NOT_VERIFIED stays
open. Draft packet may say hypervisor UNKNOWN and OS exact artifact TBD honestly.

## Validation and stop

Total <=90 minutes; zero workload resources (inherited future max3 plus one identified
cleanup helper). No Java/Python/Ruff/mypy suites; no executable code changes.
Run offline project verifier/secrets, local links/15-row/schema/whitespace/canary
probes, git diff --check/status/name-status/cached inventory and before/after hashes.
Canonical replay twice uses sorted compact ASCII JSON manifest payload, UTF-8/no
newline, ensure_ascii=True,allow_nan=False; excludes envelope fingerprint.

Baseline/protected mismatch => HOST_FEASIBILITY_BLOCKED and stop. Expected read-only
command denial => record UNKNOWN and continue independent inspections without
elevation. Missing essential capacity/hypervisor evidence => HOST_FEASIBILITY_INCOMPLETE.
Complete measured decision even if NOT_FEASIBLE => HOST_FEASIBILITY_ASSESSED_PROVISIONING_PENDING.
No VM/image download/feature/BIOS/firewall/Docker/WSL/logging change, real broker/token/
account/WebSocket/order/trading DB/HALT/arm/execute, live/paper trading, startup,
deployment, stage/commit/push. Preserve V2/second review/four reviews PENDING, NO_GO,
MIS NOT_READY, Phase12 BLOCK,H1/H2 FROZEN,July SEALED,INR10,000,ZERO_CALL_ABORT.
Stop after evidence and DRAFT / NOT APPROVED packet; no request to act during this run.
