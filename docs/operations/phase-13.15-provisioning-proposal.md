# Phase 13.15 provisioning proposal — DRAFT / NOT APPROVED

This packet requests no automatic action and contains no auto-provisioner or credentials.
Preparing it does not authorize acquisition, host changes, VM creation or deployment.
See [testbed design](../architecture/kite-controlled-linux-vm-testbed.md),
[comparison](phase-13.15-platform-comparison.md) and [gates](phase-13.15-security-acceptance-matrix.md).

## Proposed allocation and exclusions

One disposable local Ubuntu Server24.04 LTS amd64 VM, GA6.8 family with current
security patches; exact image/kernel build/hypervisor UNSELECTED. Estimated ceiling
4vCPU,8GiB RAM,60GiB virtual disk including <=5GiB bounded evidence spool. Capacity
is NOT_MEASURED; require host reserve before approval. These are proposed limits,
not promised performance or available hardware. No cloud resource or purchase.

Maximum three workload containers across the coordinated test run; one exact pinned
cleanup helper separately counted (four total when present). PostgreSQL and witness
containers count as workloads. Do not fit a four-workload topology by renaming one
a helper: split tests or obtain a new scoped design. External witness capacity and
authority are outside this single VM estimate and not supplied. If an independent
root is unavailable, its security gate remains blocked despite local fixture tests.

Per-owner proposal: <=1CPU/256MiB/64PIDs/8MiB scratch. Diagnostic fixture <=24MiB heap,
48MiB scratch,256MiB memory. Bound workload JVM lifetime <=120s, individual diagnostic
<=60s, suite <=15min, future session <=90min. No auto-retry, unbounded dump/disk or
host OOM. Review actual PostgreSQL/helper limits and total headroom before provisioning.
No host folders, socket, credentials, production DB, bridged LAN, real broker route,
shared clipboard or live trading classes. No operational service launcher.

## Acquisition and OCI trust gate

Historical subject only: docker.io/library/eclipse-temurin Linux amd64
sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf.
No new verification or pull here. OCI_SIGNATURE_NOT_VERIFIED remains the verdict.
Before accepting any runtime image, independently establish publisher-authorized
signer identity and verification procedure; pin trust roots and exact platform versus
index subject; validate signature/attestation predicate, build/source binding,
certificate time/validity and revocation policy. If transparency is part of policy,
verify inclusion/checkpoint and identity; offline roots require independently
provisioned trust, freshness and revocation evidence. Never invent a signer/key.

Retain tool/version, policy hash, subject/signature/attestation digests, sanitized
verification output, source URLs and UTC receipt/verification time. Bind SBOM to the
same image; assess package support separately. Reject mismatched digest, untrusted or
expired claims and unsigned metadata. JDK archive GPG, website linkage and SBOM do
not authenticate OCI. If no verifiable chain exists, stop the security-certification
branch; another image requires separate selection/approval, not a weakened gate.

## Separate authorization checkpoints

1. Authorize narrow read-only host feasibility/licensing inventory; no feature changes.
2. Review exact hypervisor/version, OS image/signature sources/digests, package/update
   intake budget, image trust policy, isolated topology, resource limits and named
   custodians. Artifact downloads require explicit scope and caps before execution.
3. Explicitly authorize VM creation and scoped guest policy only after the proposal
   becomes concrete. Current host security/WSL/Docker changes remain excluded unless
   separately named and approved. Acquisition and provisioning are not test certification.
4. Authorize bounded synthetic tests only after effective isolation/cleanup gates,
   retaining all mandatory failure evidence. No permission for real credentials.

Decision record (all blank/pending): owner identity __; authority evidence __;
proposal version/hash __; exact permitted actions __; recipient host/hypervisor __;
budget/cost acceptance __; network/image sources __; custodians __; conditions __;
decision/time/provenance __. No identity, signature, date or approval is filled by AI.

## Resource ownership and emergency stop design

Label future VM/disks/network/container/volumes with phase/run UUID and purpose;
record immutable hypervisor/Docker IDs before use. Pre-create admission is atomic for
cooperating coordinators, with current unrelated workloads included in budget; their
resources are never cleanup targets. Scope cleanup in finally plus an independent
watchdog for killed coordinator, verify exact IDs/labels before removal, capture
sanitized failures and verify zero-owned leftovers. A lock cannot control unrelated
administrators; reconcile inventory before proceeding.

On leak, uncertain ownership, canary exposure, failed mandatory guard or budget breach:
deny new grants, stop further creation/tests, isolate only the owned test network,
preserve evidence, and perform only authorized exact-ID cleanup. Never broad prune,
delete prior evidence, clear witness quarantine or resume trading HALT. Guest disks
and scratch disposal do not prove physical erasure; snapshot/backup custody must be
reviewed. This phase creates zero resources and performs none of these future actions.
