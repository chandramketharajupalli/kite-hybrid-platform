# Phase 13.18 concrete Linux host assessment

CONCRETE_HOST_SELECTION_INCOMPLETE. No procurement winner, spending authority or
provisioning readiness is established. [Shortlist](../operations/phase-13.18-candidate-shortlist.md)
contains the official source register and bounded retrieval intervals.
[Costs](../operations/phase-13.18-verified-cost-model.md) deliberately distinguish
published components from an India landed or service total.

## Gate-first selection

A2 Slimbook ONE H255 is a conditional priority for obtaining a 32GB/500GB India
landed quote, because its listed eight cores and memory option provide a concrete
configuration to investigate. This is a next-evidence preference, not a cost or
security winner. A1 System76 Meerkat meer10 remains conditional on India delivery
and a configured 32GB quote; the USD999 starting price does not price that system.
B2 Lightsail Mumbai 16GB/4vCPU/320GB is a conditional remote fallback for a future
account/blueprint/custody assessment. B1 DigitalOcean BLR1 Basic Regular
16GiB/8vCPU/320GiB remains an alternative; exact size availability was not readable
from the regional catalog. Neither published cloud bundle establishes quota,
architecture, supported kernel, tax total or independent witness custody.

C0 is NO_VERIFIED_CANDIDATE. Selected CIM observations identify HP Laptop
14s-dq2xxx, board87FD, one8GiB module and a firmware32GiB maximum claim, not the
exact sales SKU or supported upgrade kit. The official manual retrieval failed.
Do not acquire RAM against this observation. Historical Phase13.16 total7.65GiB
and available0.76-0.85GiB fail an8GiB guest as-is. No new headroom measurement
was taken. Retain proposed4vCPU/8GiB/60GiB workload and14GiB available admission
screen; nominal16/32GB capacity is not a measured pass. Licensing, hypervisor,
firmware, storage reserves and concurrent IDE/Docker demand remain open.

Hard blockers for every option: complete price/availability, supported exact OS,
G01-G15 acceptance evidence, independent witness and logging custody, human
approval. No weighted score can compensate for a mandatory unknown. Dell Pro Micro
QCM1255 is an additional India quote lead only: official Linux/configuration
options exist, but Call to Order supplies no reviewed configured price. No quote
request was sent. Hardware setup/maintenance/physical access and replacement costs
remain unknown; cloud administration, IAM/MFA, residency, snapshots and recurring
costs remain unknown. Migration is synthetic artifacts only, never trading data.

## Intended artifacts and provenance boundary

Proposed OS artifact: Canonical Ubuntu Server24.04 LTS,
ubuntu-24.04.5-live-server-amd64.iso, amd64. The official checksum text observed at
https://releases.ubuntu.com/24.04/SHA256SUMS publishes
97f3d7ffb032c3eb3b23d2c8be9cc76e60c2c1f2c0146ba5ba9fe01cafae0fd8.
This is a text metadata observation; no ISO was downloaded or byte-verified and
no checksum signature/trust chain was verified. Exact cloud blueprint/image IDs
are TBD; this ISO digest does not authenticate a cloud image. Vendor lifecycle,
patch baseline, boot/time evidence and signed acquisition must be checked before
separately authorized provisioning. Proposed kernel is the supported vendor
24.04 server kernel at that time; no exact installed kernel is asserted.

Historical Eclipse Adoptium/Docker Official Image subject:
docker.io/library/eclipse-temurin:21.0.12.1_1-jdk-noble, Linux amd64,
JDK21.0.12.1+1-LTS, platform digest
sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf;
index sha256:b468c3fc688b14450571494f588bd939378e7fd542ed5a73f8efc13f17872a87;
config sha256:5b87e2cf305796b0082d40718341f9b75c2754e9cf81d228262b6902cae4e0b1;
historical source commit511f9356dc4d50932a0a5f8cfb0f87ed1aef4f07.
Source: retained Phase13.12 generation1 image record; no fresh pull/image inspection.
OCI_SIGNATURE_NOT_VERIFIED. Linux package GPG guidance is not an OCI signature.
Future acceptance requires publisher-authorized signer/root, exact subject digest,
predicate/build identity, validity/revocation policy, signature verification and
retained output (transparency or pinned offline trust evidence as applicable).
Digest integrity, source linkage and unsigned SBOM are separate facts. Failure
leaves G08 open, never switches to an unsigned alternative automatically.

## Proposed containment and evidence

Ubuntu AppArmor documentation and Docker userns documentation establish mechanisms,
not candidate enforcement. Require enforcing policy/hash attached to the actual
Java owner, positive allowed behavior, attributed kernel file/process/network
denials and persistence after restart. Record host/guest/container UID/GID maps,
subordinate IDs and ownership with a nonidentity ACL probe; non-root is insufficient.
Measure CapEff0, NNP1, seccomp, read-only root, private bounded tmpfs, PID/CPU/memory
limits and no host sockets/secrets. No module restriction substitutes for OS policy.
Cloud security groups alone do not prove default-deny egress or kernel policy.

Propose deny-by-default IPv4/IPv6 ingress/egress, DNS, proxy inheritance, metadata,
host gateway and bridge escape. No public listener. Synthetic numeric loopback
and a specifically isolated owner/witness test link only after separate approval.
Updates and public evidence acquisition require a separately scoped, time-bounded
allowlist outside owner execution; no broker endpoint exception. Measure counters
and controlled denied probes from the actual Java process without contacting Kite.

Inventory sinks before use: heap/native/core/hs_err dumps, JFR/attach, environment,
stdout/stderr, Maven XML/dumpstreams, Docker json-file, journald, cloud agents,
support bundles, host mounts, CI artifacts, backups and external collectors.
Proposed platform/sink custodians must be named by the owner, not invented here.
Require destination/ACL/encryption/retention/deletion/access-audit records and
bounded canary no-export tests. Proposed sanitized evidence retention7days and
100MiB maximum per run are planning limits, not existing policy; raw sensitive
synthetic dumps remain bounded private tmpfs, scanned in place, never exported,
and disposed with the exact owned resource. Unknown external sinks block G10.
No local clean scan certifies collectors or diagnostics on a future host.

## Witness and rollback authority

A1/A2 local administrator and physical/backup operator may restore all local state.
B1/B2 one cloud account/IAM administrator may restore both owner and witness VMs.
C0 one Windows/hypervisor administrator may restore guest and checkpoints. Merely
placing a database in another VM or another region does not establish independence.
For each option require a distinct root custodian, separate restore/key authority,
independently retained checkpoint, actor/restore matrix and explicit availability
and cost model. No such custodian is identified; G12 stays UNKNOWN for all.

Preserve historical success with surviving witness and full-authority rollback
counterexample permitting a second grant with no HTTP. Neither is a fresh test.
Durable claim/retirement precedes dispatch; lost acknowledgment, stale signature,
clock reversal or root inconsistency deny and quarantine, never retry a spent ID.
Future tests must cover restart, dual-store rollback, total-root rollback, concurrent
owners and uncertain dispatch. No automatic quarantine clearing or exactly-once
claim. Quorum, multi-host failover, physical power loss, hardware monotonicity and
privileged all-root rollback resistance remain unproven. Independent custody adds
unknown storage/operations/recovery costs to every candidate.

V2, governance second review and four technical reviews remain PENDING. All host
security tests remain future work; operational NO_GO and real access NOT_AUTHORIZED.
