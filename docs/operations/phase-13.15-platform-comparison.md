# Phase 13.15 candidate comparison

All candidate capabilities below are proposals or historical evidence. No fresh
host inspection, hypervisor discovery, capacity measurement or pricing occurred.

| Criterion | A: Windows + Docker Desktop | B: isolated local Linux VM | C: dedicated Linux test host |
|---|---|---|---|
| Feasibility | Existing historical synthetic runs; current state NOT_MEASURED | Hypervisor, guest support, CPU/RAM/disk NOT_MEASURED | Hardware/access/availability NOT_MEASURED |
| LSM | Historical tested backend unavailable | Proposed Ubuntu/AppArmor; require guest enforcement/audit/restart | Same acceptance, host kernel directly observable only with authorization |
| Rootless/userns | Historical identity mapping | Rootless preferred conditionally; real guest-host UID mapping required | Same mapping proof; rootful daemon fallback separately reviewed |
| Hypervisor boundary | Desktop VM managed by host; not independent of admin | Explicit guest isolation; hypervisor admin can restore/inspect guest | No owner-VM hypervisor required, but host/physical admins remain privileged |
| Network | Historical bounded network-none tests | Private switch/no NAT; guest and host denial evidence required | Host/container policy and controlled witness network required |
| Logging/dumps | Local synthetic evidence; external sinks open | Add guest/hypervisor/backup collectors to sink inventory | Add host/physical storage/backup collectors |
| Disk/snapshots | Common host restore authority | Immutable base, bounded disposable disk; deny hidden snapshots/exports | Explicit disk/backup authority; dedicated hardware does not eliminate restore |
| Witness independence | Software fixture only | Witness/root outside VM restore/admin domain; currently unavailable evidence | Separate root custody still required; same host witness is insufficient |
| Overhead | Existing tools, unresolved platform gates | Guest patching, virtual switch, policy/audit/backup ownership | Hardware lifecycle, physical security, recovery and patching ownership |
| Licensing/cost | Entitlements/current costs NOT_MEASURED | Hypervisor/support/package terms and cost NOT_MEASURED | Hardware, power, support and licensing NOT_MEASURED |
| Certification | No complete platform certification | No system exists; DESIGNED only | No system selected; UNVERIFIED |

Conditional preference B permits a scoped guest-policy experiment without assuming
new dedicated hardware. Disqualify it if host capacity/support cannot be evidenced,
actual-owner LSM/audit or userns cannot be enforced, collectors remain unknown, or
root independence cannot satisfy the intended claim. C is a separately authorized
alternative, not an automatic fallback purchase. A remains usable only for its
existing bounded synthetic scope, not mandatory-gap closure.

Future read-only capacity gate (not executed): authorized OS edition/build and CPU
virtualization features; installed hypervisor/version and vendor-supported guest;
available RAM/disk plus measured idle reserve; existing VM inventory/virtual switches;
Secure Boot/vTPM support; admin/snapshot permissions; support/license terms. On Windows,
propose selected Get-CimInstance processor/computer-system/OS fields, Get-Volume free
space and the identified hypervisor's read-only inventory. Do not enable features,
install a hypervisor or discover unrelated user data. No host-root authority inferred
from running an IDE. Approval names and budget remain blank until supplied.

See [provisioning proposal](phase-13.15-provisioning-proposal.md) and
[acceptance matrix](phase-13.15-security-acceptance-matrix.md). No rating, price,
host availability or security certification is fabricated.
