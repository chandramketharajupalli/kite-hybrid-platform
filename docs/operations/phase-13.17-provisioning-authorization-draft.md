# Phase 13.17 authorization packet

**DRAFT_NOT_APPROVED / NOT_SENT / PROVISIONING_NOT_AUTHORIZED**

Conditional preference A dedicated Linux machine; fallback B remote Linux VM.
Exact hardware/account/host owner, quote, image, authority and spending limit remain
TBD. This packet is not yet executable or approval-complete. C requires verified
upgrade compatibility and supported virtualization first. Read the
[selection](../architecture/kite-alternative-linux-test-host-selection.md),
[45-cell gate matrix](phase-13.17-security-gate-matrix.md) and
[cost/risk model](phase-13.17-cost-and-risk-model.md).

## Fields that must be resolved before authorization

| Field | Draft only |
|---|---|
| Exact target / owner / authority | TBD; no identity, delegation, account or hardware ownership inferred |
| Platform | A vendor/model/firmware TBD; B illustrative AWS m7i.xlarge/ap-south-1, exact AZ/tenancy/quota TBD; C unselected |
| OS / image verification | Proposed Ubuntu Server24.04 LTS amd64, patched supported kernel; exact ISO/AMI/publisher ID/digest/signed checksum/key provenance TBD |
| OCI runtime | Historical digest only; exact authenticated signer/subject verification must close G08 before certification |
| Resources | Minimum frozen workload target4vCPU/8GiB/60GiB, measured host reserves; B candidate4vCPU/16GiB plus separately priced disk; independent witness budget separate |
| Storage / network | Exact path/volume/backup/encryption and isolated topology TBD; no public broker route, default deny with approved test-only traffic |
| Permissions | Host/guest/network/log custodian and separate witness restore authority TBD; least privilege, MFA for remote administration, no real secrets |
| Money | All amounts/currency/taxes TBD; spending authorization NONE, no purchase/commitment allowed |
| Time | Proposed future session<=90min, suite<=15min, workload<=120s, diagnostic<=60s; review against selected capacity before adoption |
| Resource count | Max3 workload containers; PG/witness count; one explicitly identified helper separate, never relabel a workload |

## Future actions requiring separate exact authorization

First obtain a hardware specification/quote or accountable remote candidate proposal
without provisioning. Supply supported image subjects, trust procedures, custody,
residency, costs and mandatory-control feasibility. An assessment decision does not
authorize a purchase or account creation. A later packet must name exact allowed
acquisition/configuration actions, byte/time/spend caps and responsible administrator.
No executable auto-provisioner or ready-to-run creation commands are supplied here.

Only a separately approved phase could acquire signed OS/JDK artifacts, purchase or
allocate exact resources, install/configure guest policy, restricted management,
firewall, remapping and sink controls. Preserve current host settings unless explicit
actions name them. Bootstrap network must close before isolated test mode. No real
Kite token/account/DB access, trading startup, broker route or operational launcher.

After effective controls and cleanup guards are measured, separately authorize
bounded synthetic actual-owner, HALT, canary and surviving-root recovery tests.
Retain full-root two-grant counterexample without HTTP. Unknown/failed mandatory
gate stops progression; security unavailable is not permission to bypass it.

## Rollback, failure and decommission

Record phase/run labels and immutable machine/disk/network/container IDs before any
creation; pre-create admission and finally/watchdog cleanup; never delete unrelated
resources. Failure => stop grants/creation, preserve redacted evidence, isolate owned
resources and clean only authorized verified IDs. Require final zero-owned inventory.
Do not clear root quarantine or restore spent IDs when reverting software.

Keep witness/checkpoint/key outside owner snapshot/admin authority. Decommission
must inventory disks, snapshots, backups, exported evidence and remote collectors;
apply approved retention and cryptographic disposal policy with custodian evidence.
Deleting a VM/tmpfs is not proof of physical erasure. Proposed sanitized logs<=7days,
100MiB/run; no raw dumps/canaries exported; sanitized failure evidence retained until
explicit disposition. Unknown external sink retention blocks certification.

Blank decision record: identity __; verified authority __; packet hash __; exact
target/actions __; budget/currency/tax/spend cap __; custodians __; conditions __;
decision __; UTC timestamp/provenance __. No signature, acceptance or reviewer
decision is filled by Codex. Governance adoption, contract V2 approval, four technical
reviews, provisioning, implementation, real read and trading are separate decisions.
All existing pending states remain pending; operational NO_GO / MIS NOT_READY.
