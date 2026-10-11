# Phase 13.17 option comparison and source provenance

Status: SELECTION_PENDING_EVIDENCE. A/B/C below are this phase's option labels;
they differ from Phase13.15 labels. No new host, VM, account or service was contacted.
Public vendor documentation was read without login or acquisition.

Evidence classes: E16 = historical measured Phase13.16; D15/D16 = committed design;
P01-P11 = current official public guidance, not enforcement; A = explicit engineering
assumption; U = unavailable data. Access date2026-10-11 UTC, not an independent
identity/approval timestamp. See [historical measurements](phase-13.16-host-measurements.md),
[original requirements](phase-13.15-security-acceptance-matrix.md) and
[Phase13.16 mapping](phase-13.16-security-gate-matrix.md).

| Criterion | A dedicated Linux machine | B remote Linux VM | C upgraded Windows + isolated Linux VM |
|---|---|---|---|
| Candidate | Ubuntu24.04 LTS/AppArmor; model/kernel ABI U (P01/P02,D15) | Illustrative AWS m7i.xlarge/ap-south-1,4vCPU/16GiB/EBS-only (P05/P06); image/AZ/account U | Exact upgraded machine/hypervisor U; same proposed Linux guest (D15,E16) |
| Resource feasibility | Hardware not supplied; CPU/RAM/disk/headroom U | Catalogue fit only;16GiB is candidate above8GiB target, not new minimum; quotas/performance U | E16 current7.65GiB total/0.76-0.85GiB available fails; upgraded16/32GiB compatibility and headroom U |
| LSM visibility | Proposed direct guest/host audit control; actual Java enforce/audit test required | Proposed guest audit only; provider kernel/tenancy restrictions U | Proposed guest audit; Windows hypervisor and Desktop remain separate |
| Userns | Rootless or remap with measured maps/ACLs (P03/P09) | Guest-host maps plus provider boundary; availability U | Guest-host Linux maps do not establish Windows SID mapping |
| Privileged authority | Physical/root/backup custodian U | Provider plus account/IAM/snapshot admins U; no dedicated tenancy claim | Windows/hypervisor/guest admin and physical user U |
| Egress | Proposed host/container deny policy and isolated fixture network | P08 groups plus guest/container controls required; DNS/metadata exceptions reviewed, no public route assumed | Proposed private switch + guest controls; no existing Desktop policy change |
| Logs/dumps | Host/journald/daemon/backup custody U | Additional cloud agents,control-plane and snapshot sinks U | Additional Windows/Desktop/EDR/hypervisor sinks U |
| Encryption/backups | Storage/key/restore policy U | P11 EBS capability only; keys,snapshot permissions,retention U | E16 BitLocker/backup queries failed; remains U |
| Witness | Separate storage/admin/checkpoint beyond host required | Separate effective restore authority beyond same account/admin required | Root beyond Windows VM snapshots/admin required |
| Cost | Hardware,power,repair/support TBD | Compute,storage,snapshots,egress,logs,support,witness/tax TBD | RAM,labor,edition/hypervisor/support/power TBD |
| Effort/exposure | A: physical lifecycle; site/residency U; sanitized rebuild only | A: IAM/MFA,remote management,billing/region/exports; residency not certified | A: upgrade risk,Windows/guest maintenance and contention; sanitized rebuild only |
| Decision | Conditional preference; no purchasable target established | Conditional fallback; no account/provisioning authorized | Deferred pending compatibility/license/resource evidence |

Historical E16 snapshot2026-10-11T17:00:57.9213528Z..17:01:55.251944+00:00:
4cores/8logical; D257.878807GiB and C62.444843GiB free. Disk reserves do not establish
memory feasibility. Current 8GiB guest NOT_FEASIBLE; do not reinterpret this as a
measurement of a hypothetical upgraded host. Hardware slot/max-RAM/firmware evidence,
license and sustained concurrent IDE/Docker samples remain necessary. P04 documents
supported Hyper-V editions; present command/hypervisor indicator alone proves neither
configuration nor authority. No fresh CIM/Docker/WSL or hardware probes ran here.

For B, proposed least-privilege federated/temporary administration, MFA, audited
management-only ingress, no root/password SSH or agent forwarding, no general test
internet route. Review exact KMS/key administrators, encrypted volume/snapshot
settings, control-plane audit retention, tenancy/residency terms, remote console
access and ability to attach enforcing AppArmor. None is configured or inspected.
A cloud firewall cannot replace guest namespace/LSM evidence or cover provider
exceptions by assertion. No provider security certification is inherited.

## Current public source register

Eleven unique official pages opened, one Ubuntu redirect retained, no retries or
artifact retrieval. In-page searches located catalogue/region/tax/security facts.
Each statement is limited to the reviewed documentation; no quoted prices or
performance guarantees. No OCI signature research/pull/authentication performed.

| ID | Publisher / version | URL / access date UTC | Region/currency | Supported claim and limit |
|---|---|---|---|---|
| P01 | Canonical; Ubuntu24.04 LTS | [official source](https://ubuntu.com/about/release-cycle); 2026-10-11 | Global; currency not applicable | 24.04 standard maintenance listed through May2029; exact package coverage and acquired image remain unverified. |
| P02 | Canonical; Ubuntu Server AppArmor | [official source](https://documentation.ubuntu.com/server/how-to/security/apparmor/); 2026-10-11 | Global; currency not applicable | Enforce/complain and profile/audit guidance supports proposed protocol; no host policy measured. |
| P03 | Docker; Docker Engine current documentation | [official source](https://docs.docker.com/engine/security/userns-remap/); 2026-10-11 | Global; currency not applicable | Remapping translates IDs; rootful daemon boundary differs from rootless; compatibility requires tests. |
| P04 | Microsoft; Windows11 Hyper-V requirements | [official source](https://learn.microsoft.com/en-us/windows-server/virtualization/hyper-v/host-hardware-requirements); 2026-10-11 | Global; license price/currency not established | Professional/Enterprise and hardware/host-plus-guest memory requirements; current Home edition is not sufficient for this supported Hyper-V route. |
| P05 | AWS; EC2 m7i.xlarge | [official source](https://aws.amazon.com/ec2/instance-types/m7i/); 2026-10-11 | Region-independent specification; currency not applicable | Vendor specification:4vCPU/16GiB,EBS-only; not measured capacity, performance or dedicated tenancy. |
| P06 | AWS; EC2 regional instance catalogue | [official source](https://docs.aws.amazon.com/ec2/latest/instancetypes/ec2-instance-regions.html); 2026-10-11 | ap-south-1 Mumbai; currency not applicable | M7i family listed in Mumbai; specific AZ/account quota and launch capacity not established. |
| P07 | AWS; EC2 On-Demand pricing guidance | [official source](https://aws.amazon.com/ec2/pricing/on-demand/); 2026-10-11 | Mumbai candidate; exact currency/rate TBD; applicable taxes excluded by published guidance | Exact m7i.xlarge Linux regional rate not established from reviewed content; compute,storage,outbound and tax inputs require a quote. |
| P08 | AWS; VPC security groups | [official source](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html); 2026-10-11 | AWS; currency not applicable to technical claim | Stateful inbound/outbound allow rules have filtering exceptions including Amazon DNS; do not infer complete deny-all isolation from group rules. |
| P09 | Docker; Docker Engine rootless | [official source](https://docs.docker.com/engine/security/rootless/); 2026-10-11 | Global; currency not applicable | Rootless places daemon and containers in a user namespace; actual kernel/LSM/cgroup compatibility not measured. |
| P10 | AWS; IAM security guidance | [official source](https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html); 2026-10-11 | AWS; currency not applicable | MFA,least privilege and temporary credentials are proposed access requirements; no account policy observed. |
| P11 | AWS; EBS encryption | [official source](https://docs.aws.amazon.com/ebs/latest/userguide/ebs-encryption.html); 2026-10-11 | AWS; Mumbai candidate; key/storage charges TBD | KMS-backed EBS encryption guidance supports design; selected volume,key policy,snapshots and audit configuration unverified. |

P02 redirected to https://ubuntu.com/server/docs/how-to/security/apparmor/ .
No other source redirect was reported. Source URLs identify consulted guidance,
not an offline authenticated vendor attestation. No private account/identity data.
