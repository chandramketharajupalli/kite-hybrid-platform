# Phase 13.16 authorization packet — DRAFT / NOT APPROVED

Disposition: **do not provision the frozen target on the measured host**. This packet
is reviewable but cannot support immediate provisioning approval: RAM fails and key
hypervisor/security facts remain unknown. No authorization is requested during this run.
See [measurements](phase-13.16-host-measurements.md), [feasibility](phase-13.16-hypervisor-feasibility.md)
and [all fifteen gates](phase-13.16-security-gate-matrix.md).

| Proposed field | Actual readiness |
|---|---|
| Hypervisor/version | UNKNOWN / UNSELECTED; native Hyper-V edition eligibility fails; alternate tooling not verified |
| OS | Proposed Ubuntu Server24.04 LTS amd64/GA6.8 supported patched family; exact point image, URL, digest and kernel ABI TBD |
| OS trust | Future vendor signed checksum verification with independently established signing key, then exact-byte image hash; not executed |
| OCI/JDK21 | Historical Temurin subject sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf; no fresh verification; authenticated signer/root/subject chain required |
| Allocation | Frozen estimate4vCPU/8GiB/60GiB; NOT_FEASIBLE with7.65GiB total and0.76-0.85GiB available RAM |
| Storage | Proposed D:\Yogendra\synthetic-vm-testbed, not created/reserved; path ACL/backup/encryption UNKNOWN |
| Reserves | D requires180GiB including guest60,rollback60,staging20,reserve40; measured257.88GiB free. C requires20GiB,measured62.44GiB free |
| Network | Proposed private isolated switch/no NAT/public route/DNS/host service access; numeric loopback only; implementation TBD |
| Administration | Named host/guest/network/log/witness custodians TBD; no privilege inferred from IDE access |
| Time/resource budget | Future session<=90min,suite<=15min,workload<=120s,diagnostic<=60s; max3 workloads plus1 identified helper; no allocation now |

## Conditions before any future authorization

Resolve host memory and supported virtualization platform first through separately
authorized assessment; no upgrade, purchase, process termination or feature enablement
is assumed. Verify sustained concurrent IDE/Docker demand, entitlement/coexistence,
firmware/nested requirements, encryption and backup ownership. Freeze exact image
subjects, signing roots, allowed sources/download caps, networking and security policy
before acquisition. If OCI signature cannot be authenticated, retain
OCI_SIGNATURE_NOT_VERIFIED and deny security certification; do not substitute hashes.

Require effective guest AppArmor, actual userns mapping, non-root/CapEff0/NNP/seccomp,
read-only root/bounded tmpfs/cgroups, explicit egress and sink ownership. Windows-host
isolation is distinct from guest UID translation. No real credentials, broker data,
trading classes, database mounts, Docker socket, host home or operational launcher.
Boot/time attestation, logging/diagnostic containment and witness root independence
remain NOT_VERIFIED, not inferred from a Linux distribution choice.

## Future actions requiring separate explicit authorization

These names identify approval scope, not executable instructions or an auto-provisioner:

- An elevated read-only Get-WindowsOptionalFeature/Get-BitLockerVolume or a qualified
  administrator-supplied attestation, only if the user later authorizes that route.
- Any OS edition/hypervisor installation, Enable-WindowsOptionalFeature, host reboot,
  BIOS/firmware action or Docker/WSL change; excluded from this phase and not selected.
- Exact OS/OCI downloads, signature tooling and trust-root intake; URLs/digests/byte
  budgets must be filled before authorization. No floating tags or hidden network build.
- Once a supported platform is chosen: its exact creation/configuration commands
  (for example New-VM/New-VHD/Set-VM/Set-VMProcessor/New-VMSwitch/Start-VM only on an
  authorized eligible Hyper-V host). Parameters and target IDs remain TBD; none ran.
- Guest installation, AppArmor/rootless policy and firewall/log configuration, then
  bounded synthetic tests. No host-wide security weakening or implicit live access.

Potential disruptions: RAM/paging/CPU pressure, disk expansion, IDE/Docker contention,
hypervisor coexistence changes, reboot and network/diagnostic policy effects. Current
RAM already fails the margin, so these cannot be dismissed as negligible. Prices,
licenses and hardware upgrade feasibility are UNKNOWN; no spending authorized.

## Rollback and decommission proposal

Keep immutable approved base and per-run disks; no shared host bind or secret volumes.
Record exact run labels and immutable VM/disk/network/container IDs before use. Enforce
pre-create budget and unconditional cleanup plus killed-coordinator recovery. On
failure stop new grants/creation, retain sanitized evidence, isolate only owned test
resources and remove only verified owned IDs under explicit future authorization.
Never broad-prune, remove unrelated resources or erase failure records.

Software rollback cannot restore consumed attempts to usable state. Witness key/state
and coordinator checkpoints require distinct restore authority outside owner snapshots;
unknown or inconsistent recovery stays quarantined. All-authority rollback remains
the permanent negative control. Decommission needs snapshot/backup/copy inventory and
verified disposal; deleting a VM or tmpfs does not prove physical erasure.

Authorization fields deliberately blank: reviewer identity __; authority __; exact
packet hash __; selected platform/image/topology __; authorized actions __; cost and
resource acceptance __; custodians __; conditions __; decision/time/provenance __.
No signature or consent inferred. V2/second review/four technical reviews PENDING;
implementation under V2/real access NOT_AUTHORIZED; operational NO_GO/MIS NOT_READY.
