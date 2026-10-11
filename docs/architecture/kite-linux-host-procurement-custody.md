# Phase 13.19 procurement and security-custody boundary

PROCUREMENT_EVIDENCE_INCOMPLETE. No selected purchase, price cap or provisioning
approval. [Quote ledger](../operations/phase-13.19-quote-ledger.md),
[costs](../operations/phase-13.19-cost-model.md),
[custody](../operations/phase-13.19-custody-risk-register.md),
[gates](../operations/phase-13.19-security-gate-matrix.md).

Exactly inherited candidates retained: A1 Meerkat meer10 Core3 100U/32GB/500GB;
A2 ONE H255/32GB/500GB; B1 Basic Regular16GiB/8vCPU/320GiB proposedBLR1;
B2 standard Lightsail Linux/Unix publicIPv4 GeneralPurpose16GB/4vCPU/320GB
proposedMumbai; C0 HP14s-dq2xxx/87FD unqualified upgrade. Vendor units are retained,
not silently converted to GiB. No substitutions or refreshed host measurements.

New bounded evidence supports A2 India shipping coverage and its adapter's input
range, but not exact landed price, plug safety, stock or delivery. A2's warranty
freight terms conflict across public pages. A1 India shipment remains unestablished.
Cloud public bundles are re-observed, not account quotes; B1 size availability cells
are unreadable and B2 quota/blueprint/architecture unverified. C0 still lacks exact
SKU/kit proof; the historical firmware maximum is not compatibility certification.
No candidate clears hard blockers for exact configuration, total cost, supply/account
eligibility, contractual terms, custody, image trust, logging and witness authority.

A2 remains only a priority for a future authorized terms/quote inquiry; B2 remains
only a remote evidence fallback. These are not winners or permissions. Bare-metal
ownership adds physical protection, patching, electricity, repairs and disposal;
cloud removes hardware maintenance but adds IAM/provider/snapshot/recurring-billing
exposure. C0 adds compatibility, license and Windows/hypervisor contention. No
unmeasured labor, reliability or cost rating is assigned.

## Restore/control threat matrix

All actors below are modeled capabilities to investigate, not verified assignments.
No candidate has independently demonstrated rights or provider-access exclusions.

| Candidate | Owner store/journal/ledger | Witness and root key | Logs | Backup/snapshot | Required surviving authority |
|---|---|---|---|---|---|
| A1 | Local root/physical admin could copy/restore disks | Same admin if local witness/key; placement TBD | Local admin can alter local logs | Disk/image operator could restore whole machine | Distinct custodian and restore/key boundary outside owner image |
| A2 | Same bare-metal restore threat | Same admin if local witness/key; placement TBD | Local admin/collector control unknown | Physical backup/firmware admin threat | Independently retained authenticated checkpoint outside shared restore authority |
| B1 | Cloud account/project restore and guest root; provider boundary unverified | Same account/recovery owner may control both VMs | Guest/project/collector rights unknown | Snapshots and residual backups under account/provider | Separate tested admin/key/restore authority, not another Droplet alone |
| B2 | Account/IAM root plus guest and provider layers | Same account/root recovery can defeat apparent separation | Guest/cloud/collector restore rights unknown | Retained snapshots/disks may survive instance deletion | Distinct root custodian, explicit provider and account recovery assumptions |
| C0 | Windows/hypervisor/guest administrator | Second VM under same host still jointly restorable | Windows/guest/collector rights unknown | Host disk/VM snapshot operator | Root checkpoint/key outside Windows administrator's rollback domain |

Independence NOT_ESTABLISHED for all five. Document actual grants, delegation,
backup recovery credentials, snapshot rights and collusion assumptions before tests.
Separating paths, databases, regions or account labels is insufficient if one actor
can restore everything. Protect the root key and checkpoint as well as the witness
store. Logs need independent retention to evidence tampering; they do not themselves
supply a monotonic authority. No external custodian is nominated without consent.

Preserve historical surviving-witness replay denial and all-authority rollback
permitting a second grant, without HTTP in the negative control. No new recovery
execution here. Before dispatch require durable claim and fresh authenticated root;
missing/ambiguous acknowledgment, stale key/epoch/time or checkpoint regression
quarantines and denies, never retries spent authorization. Restart cannot erase
quarantine. Required future tests: independent JVM races, kill/restart, each/both
local-store restore, root loss, all-root restore and uncertain response. Software
witness is not hardware monotonicity; no universal at-most-once, exactly-once,
physical power-loss or multi-host consensus assurance is claimed.

## Security, trust and data boundary

Retain exact15 gates; whole candidate controls remain unverified. Proposed Ubuntu
Server24.04 LTS and historical Temurin21 linux/amd64 subject retain Phase13.18
identity evidence only. Historical Temurin platform digest:
sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf.
OCI_SIGNATURE_NOT_VERIFIED: require authorized signer/root, exact subject/predicate,
validity/revocation and retained verification output. No archive-GPG, unsigned
metadata or digest-only substitution; no image acquisition here.

Actual Java AppArmor enforcing policy/audit/restart, nonidentity UID/GID maps,
seccomp/NNP/CapEff0/non-root/read-only root/private tmpfs and cgroup/PID limits
remain future measurements. Keep3-workload pre-create limit and separately counted
helper, exact labels/IDs and finally cleanup. No new resource or cleanup performed.

Default-deny IPv4/IPv6 ingress/egress including DNS/proxies/metadata/host gateways;
no public listener or Kite route. Only explicitly authorized numeric loopback and
isolated synthetic witness traffic. Provider ingress security groups are not proof
of egress denial. Observe host/guest/container counters and controlled negative
probes, never broker connectivity. Updates require a distinct approved network scope.

Enumerate heap/core/native/hs_err/JFR/attach, stdout/stderr, Docker json-file,
journald, Maven/CI reports/dumpstreams, provider agents, support bundles, host mounts,
Windows diagnostics, backups and external collectors. Each needs owner/destination,
ACL/encryption, bounded retention/deletion and access audit. Synthetic canaries
stay in bounded private scratch; prove no unauthorized export. No clean local scan
certifies external sinks. Provider deletion documentation is not physical media
purge attestation; retained backups and billing resources require separate closure.

V2/second review/four technical reviews PENDING; implementation under V2 and real
access NOT_AUTHORIZED. Assessment cannot certify a host or authorize procurement.
