# Phase 13.16 hypervisor feasibility

No hypervisor is selected for provisioning. The measured host has insufficient RAM
for the frozen8GiB guest and unknown alternative tooling. This assessment neither
installs software nor recommends bypassing Windows edition restrictions.

| Option | Measured availability | Feasibility for proposed testbed |
|---|---|---|
| Hyper-V standalone VM | Windows11 Home Single Language; Get-VM absent; feature query failed | NOT_FEASIBLE on measured edition under documented support requirements; actual feature state UNKNOWN |
| Other local VM tooling | VBoxManage,vmrun,QEMU command names absent; no matching process returned | UNKNOWN; not proof uninstalled; exact supported version/license/config/coexistence/admin rights unverified |
| WSL2/Docker Desktop | WSL and Docker metadata accessible; prior sandbox tests historical | Existing development capability, not equivalent to separately administered Linux VM; current target memory screen fails |
| Separately authorized Linux host | No candidate measured or accessed | UNKNOWN; possible future assessment only, no purchase/provisioning authorization |

Microsoft lists Windows11 Professional/Enterprise for Hyper-V and requires hardware
virtualization/SLAT and enough RAM for host plus guests. Its guidance also notes
that requirement reporting differs when a hypervisor is already present:
[host requirements](https://learn.microsoft.com/en-us/windows-server/virtualization/hyper-v/host-hardware-requirements).
The installation documentation is supporting eligibility guidance, not an executed
procedure: [Hyper-V installation](https://learn.microsoft.com/en-us/virtualization/hyper-v-on-windows/quick-start/enable-hyper-v).
No edition upgrade/license purchase is assumed or authorized.

CIM HypervisorPresent=true with false firmware/SLAT/VMMonitor fields cannot establish
BIOS settings, absent silicon capability or nested virtualization support. No firmware
or bcdedit changes, no elevated inspection. A future supported VM product must be
verified against its exact version, current Windows build, active Windows hypervisor
and Docker/WSL coexistence policy. Command discovery alone cannot do this.

WSL is intended for integrated Windows/Linux development and can expose Windows files;
its presence is not the separate administrator/snapshot boundary proposed here.
[Microsoft WSL FAQ](https://learn.microsoft.com/en-us/windows/wsl/faq) supplies context,
not proof of the measured distro's mount or network policy. No .wslconfig or distro
contents were read. Nested virtualization is unnecessary for ordinary containers
in a full Linux VM, but any nested-hypervisor-dependent topology needs separate proof.

Preserve Phase13.15's candidate Ubuntu24.04/AppArmor design without claiming installed
LSM, userns, boot/time attestation or default-deny network. Docker SecurityOptions
only advertises daemon features; every future guest enforcement control is NOT_VERIFIED.
OCI exact-subject publisher signature remains OCI_SIGNATURE_NOT_VERIFIED.

Next bounded prerequisite: choose whether to assess a higher-memory, supported local
host configuration or a separate Linux host. Selection, entitlement, capacity, kernel
audit access, collector ownership and independent witness authority require new evidence.
No CPU/RAM reserve reduction, hidden paging reliance or WSL substitution closes the gap.
