# Phase 13.14 platform evidence matrix

Fresh read-only observation: 2026-10-11T15:51:01.5295337Z, local machine clock;
not a signed timestamp, owner decision or independent authority attestation.
Host identity redacted. Windows11 Home Single Language, 10.0.26200/build26200,64-bit.
Docker client/server29.8.0, Linux amd64; backend Docker Desktop,
kernel6.18.33.2-microsoft-standard-WSL2, cgroupfs/v2, json-file logging.
The fixed Desktop executable path in the plan was absent; application version is
NOT_VERIFIED. No filesystem search, installation or host configuration followed.

| Control | State | Evidence and qualification |
|---|---|---|
| Cached image identity | VERIFIED | Fresh inspect ID/digest/Linux amd64 and five rootfs layers agree with rehashed cached config |
| OCI signature | NOT_VERIFIED | OCI_SIGNATURE_NOT_VERIFIED; no trusted signer/root/exact subject verification |
| AppArmor/SELinux | UNAVAILABLE_ON_TESTED_BACKEND | Retained Phase13.13 Java AppArmor=N, SELinux/LSM list unavailable, label kernel/OTHER_OR_UNCONFINED; not freshly executed |
| User namespace remapping | NOT_VERIFIED | Retained Java uid_map/gid_map identity 0->0,4294967295; current daemon advertises seccomp/cgroupns only |
| Rootless availability | NOT_VERIFIED | No rootless option advertised; installation/kernel feasibility not tested and no daemon reconfiguration |
| Seccomp/cgroup capabilities | PARTIAL | Fresh daemon advertises builtin seccomp and cgroupns/cgroupv2; this alone does not prove per-process enforcement |
| Java seccomp/NNP/caps/non-root | VERIFIED, historical fixture scope | Phase13.13 measured Seccomp2,NNP1,CapEff0,UID/GID65534; Phase13.14 execution NOT_TESTED |
| Filesystem/process | VERIFIED, historical fixture scope | Root write denied, scratch succeeds, absent host/socket/private key, ProcessBuilder EPERM; no new probe |
| Network namespace/egress | VERIFIED, historical fixture scope | Isolated loopback/network-none, no external route, ENETUNREACH and invalid DNS denial; no external network test now |
| Actual Java owner | VERIFIED, historical fixture scope | Six-case owner suite previously passed twice; NOT_TESTED now |
| Heap/native/attach containment | PARTIAL | Local bounded diagnostic evidence retained; canary-bearing heaps confined, not called clean; arbitrary crashes and physical erase unverified |
| External sinks/retention | NOT_VERIFIED | Fresh daemon json-file only; external collectors/Desktop diagnostics/backups not audited |
| Resource budget/cleanup | VERIFIED for current inventory | Zero active/owned workloads observed; no creation/removal; historical fourth-workload denial retained |
| Witness local-rollback denial | VERIFIED, historical fixture scope | Surviving witness keeps peer receipt count1; no new rollback run |
| Complete-root rollback | VERIFIED negative, historical | Two grants possible/no HTTP; universal rollback prevention remains unresolved |
| Dedicated VM/host enforcement | NOT_TESTED | Acceptance design only; no host selected or provisioned |

State vocabulary is from the frozen plan. Historical VERIFIED entries certify only
the provenance of retained fixture results, not current process execution or live
security. No running owner exists to inspect in this assessment. Current host, Docker
VM and prior container observations are deliberately separate.

Source authority: Phase13.13 generation1 manifest (raw/canonical reverified), its
measured_controls/controls/cleanup/recovery and retained raw evidence; Phase13.12
generation1 failure and generation2 recovery; Phase13.11 acquisition/owner evidence.
Current selected observations and command provenance are embedded in Phase13.14's
manifest. No new process measurement is inferred from Docker SecurityOptions.

Overall: PLATFORM_ASSESSMENT_COMPLETE_GAPS_OPEN. Mandatory OCI authenticity, effective
LSM, actual UID translation and external-sink ownership remain open. Proposed host
acceptance is in the architecture assessment; it does not authorize deployment.
