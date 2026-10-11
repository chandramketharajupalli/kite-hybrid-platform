# Phase 13.16 read-only host measurements

Result: HOST_FEASIBILITY_INCOMPLETE. The proposed 4vCPU/8GiB/60GiB VM is
NOT_FEASIBLE under measured current memory conditions. Missing hypervisor, firmware,
encryption and restore-policy evidence also prevents a complete authorization packet.
No guest security is certified. See [hypervisor assessment](phase-13.16-hypervisor-feasibility.md)
and [draft packet](phase-13.16-provisioning-authorization-request.md).

Measurements ran 2026-10-11T17:00:57.9213528Z through 17:01:55.251944+00:00.
These are local-machine UTC observations, not authenticated remote receipts or owner
decision timestamps. Host identity is redacted. Sanitized command-level observations
and exact bytes/units are embedded in the generation1 manifest.

## OS, CPU and memory

| Measurement | Observation | Source / limit |
|---|---|---|
| OS | Windows11 Home Single Language,64-bit,10.0.26200/build26200 | Win32_OperatingSystem; WSL reports revision26200.9550 |
| Boot/uptime | 2026-09-29T16:47:50.3284940Z;1037588 seconds (~12 days) | Local OS clock, not verified boot attestation |
| CPU | 4 physical cores,8 logical processors | Win32_Processor; topology only |
| Hypervisor present | true | Win32_ComputerSystem; does not identify a usable standalone Hyper-V VM service |
| Firmware/SLAT/VMMonitor fields | all false as exposed by CIM | Contradicts a simple no-hypervisor inference; firmware capability NOT_VERIFIED, not diagnosed disabled |
| Physical memory | 8215105536 bytes =7.650913GiB | Win32_ComputerSystem |
| OS visible memory | 8022564KiB =7.650913GiB | Win32_OperatingSystem; KiB=1024bytes |

Three planned snapshots (no load generated):

| Start UTC | Available KiB / GiB | Used fraction of visible memory | CPU load |
|---|---:|---:|---:|
| 17:01:00.6270847Z | 799336 /0.762306 | 90.04% | 51% |
| 17:01:07.0093828Z | 888136 /0.846992 | 88.93% | 35% |
| 17:01:13.3214652Z | 890892 /0.849621 | 88.90% | 37% |

Used fraction is an arithmetic indicator, not a paging/commit/latency measurement.
The earlier OS snapshot had708096KiB free; the three planned snapshots are the
predeclared capacity sample. They are not sustained IDE/build peak measurements.

RAM screen requires14GiB available:8GiB VM+2GiB estimated overhead+4GiB residual
host margin. Even total observed physical RAM is below the8GiB guest target. Paging,
overcommit or shrinking reserves cannot turn this into adequate physical capacity.
No application was stopped to improve results. CPU topology meets the count screen,
but51% breaches the predeclared <50% snapshot-load screen; performance remains unknown.

## Disk and reserves

Both volumes report fixed NTFS, Healthy. Get-Volume and Get-PSDrive agree at sampling.
Proposed D:\Yogendra\synthetic-vm-testbed is only a draft path; it was not created,
searched or reserved and its ACL/backup inclusion is unverified.

| Volume | Total bytes / GiB | Free bytes / GiB | Proposed screen |
|---|---:|---:|---|
| D |314451161088 /292.855465 |276895260672 /257.878807 |60guest+60rollback+20staging+max(40,10%total)=180GiB;77.878807GiB surplus |
| C |196591218688 /183.089840 |67049639936 /62.444843 |20GiB host reserve;42.444843GiB surplus |

Disk capacity is CONDITIONALLY_FEASIBLE at this instant, not allocated space or an
I/O guarantee. Thin-disk growth, other workloads, backups and snapshots can consume
headroom. The rollback allowance never authorizes restoring spent witness/root state.
Disk encryption status and complete backup/snapshot ownership are UNKNOWN.

## Existing workloads and virtualization observations

Selected process working sets at17:01:21Z: idea64 two processes503.90625MiB;
Docker Desktop five99.09375MiB; com.docker.backend two105.976563MiB. Working sets
can share pages and omit VM/commit allocations; do not sum them as complete host
demand or subtract them again from free RAM. Other queried Java/vmmem/vendor VM
names were not returned; their absence is not proof of zero unattributed demand.

Running Desktop process version4.91.0.239619 was readable. Docker client/server29.8.0,
Linux amd64 backend6.18.33.2-microsoft-standard-WSL2, cgroupfs/v2,json-file, advertised
seccomp builtin/cgroupns. Docker reports8 CPUs and3919179776bytes (~3.65GiB) memory
visible to its engine; this is not additional host RAM or a measurement of usage.
Three unrelated containers are exited; zero running/paused. docker stats was NOT_RUN
because none was running. No container was created, started, removed or exec'ed.

WSL2.7.14.0/kernel6.18.33.2-2, default version2. One listed docker-desktop distribution
reported Stopped at17:01:45Z, while Docker server queries succeeded immediately after.
Preserve both observations; they do not prove endpoint-to-distribution identity or a
controlled backend state transition. No distro start/shutdown command was issued.
No unrelated distro names, hostnames, usernames, addresses or process arguments retained.

## Inaccessible observations

Get-VM,VBoxManage,vmrun,qemu-system-x86_64 were not discoverable as commands. This is
not an exhaustive software installation search. Get-VM was NOT_RUN; no feature enabled.
Four Get-WindowsOptionalFeature queries returned COMException/NotSpecified for
Microsoft-Hyper-V-All,VirtualMachinePlatform,Microsoft-Windows-Subsystem-Linux and
HypervisorPlatform. Actual feature states UNKNOWN; no elevated retry.
Get-BitLockerVolume failed with PermissionDenied/0x80041003; no key material requested.
Win32_ShadowCopy count failed with0x80041014; complete backup policy UNKNOWN.
These six failed inspections are retained. Existing command availability was not
mistaken for capability or administrative authorization.

Overall capacity: NOT_FEASIBLE for the frozen target now. Hypervisor selection UNKNOWN;
firmware/nesting, installed alternate tooling, licensing, encryption, backup ownership
and sustained load remain unresolved. Do not offer provisioning approval on this evidence.
