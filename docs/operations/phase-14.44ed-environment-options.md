# Environment alternatives

| Option | Evidence and feasibility | Remaining requirements |
| --- | --- | --- |
| Current Windows 11 Home workstation | Build 26200, 64-bit; HypervisorPresent true; Docker command present, Hyper-V and Sandbox commands absent. Feature queries previously required elevation; backend/provider qualification unverified. | Independently verify supported host/guest/provider compatibility, license, image and actual containment. No host feature or edition change authorized. |
| Dedicated Windows VM host | Candidate architecture only; no approved host/provider/version identified. | Owner-approved dedicated host, privileges, supported hypervisor, licensed image and full runtime provenance; qualify all containment and export controls. |
| Remote Windows VM host | Candidate architecture only; no host identity or custody established. No remote connection made. | Approve remote operator, transport, custody, source transfer, license, image/runtime closure and independent containment receipts. |
| Deferral | Available now without provisioning or new acquisition. | Preserve pending selection and original test scope until an approved host is independently established. |

Machine-readable alternatives include provider identity, host/guest compatibility, licensing, privileges, image/runtime evidence, guest namespace, sealed-host exclusions, network/credential/process containment, snapshot/rollback, evidence export, operational complexity and approvals. Unknown versions, image hashes, pricing and privileges are not invented. A VM's existence alone proves no containment. Snapshot restore must not silently reset governance or replay state. Evidence export must be allowlisted and separately authorized, never a writable host sealed-custody mount.
