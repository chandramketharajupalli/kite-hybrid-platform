# Alternative Linux test host: conditional selection

Result: ALTERNATIVE_HOST_SELECTION_INCOMPLETE / SELECTION_PENDING_EVIDENCE.
Prefer **A, dedicated Linux hardware**, conditionally; **B, remote Linux VM**, is
the fallback. C, upgraded Windows plus isolated Linux VM, remains contingent on
hardware compatibility, supported hypervisor and sufficient measured headroom.
This is an engineering recommendation, not purchase or provisioning approval.
See [comparison and source register](../operations/phase-13.17-option-comparison.md),
[all 15 gates](../operations/phase-13.17-security-gate-matrix.md) and
[cost/risk model](../operations/phase-13.17-cost-and-risk-model.md).

## Why this preference is conditional

A can put kernel policy, process labels, audit collection and physical storage under
explicit test-host administration without the current Windows memory and edition
constraints. That is a design advantage, not proof that hardware is available or
an administrator is independent. Require exact machine specification, vendor Ubuntu
compatibility, firmware/boot evidence, measured sustained load, disk health/capacity,
physical-access custodian, supported patch coverage and a written quote first.
Propose Ubuntu Server24.04 LTS amd64 with supported patched kernel, AppArmor enforcing,
and rootless containers if their LSM/cgroup compatibility passes; otherwise assess
userns-remap separately. No engine mode or kernel ABI is selected as verified.

B avoids consuming the measured Windows RAM but introduces provider, account,
network-management, billing and snapshot authorities. Illustrative candidate only:
AWS EC2 m7i.xlarge in ap-south-1, vendor-listed4vCPU/16GiB, EBS-only. This is a
larger candidate than the8GiB guest target, not a claim that16GiB is required or
available to this account. Exact Ubuntu AMI, tenancy, quota/AZ capacity, storage,
price, access policy and guest enforcement are UNKNOWN. Do not assume nested
virtualization or bare-metal control. Ordinary guest containers need no nested VM;
any later nested design requires separate verified support. Provider documentation
does not replace actual Java-process/audit evidence.

C retains Windows/hypervisor administration and snapshot coupling. Phase13.16's
7.650913GiB physical RAM and0.762306-0.849621GiB available cannot support the frozen
8GiB guest.16GiB is only a minimum upgrade candidate to investigate; with the
14GiB available screen it may still fail under IDE/Docker load.32GiB is a planning
preference, not verified fit or an entitlement to relax reserves. Motherboard model,
slots/soldering, module type/max RAM, firmware, supported hypervisor and license
must be verified before any quote or change. A guest does not fix Desktop's own
unavailable LSM/remapping evidence; it introduces a different boundary to test.

## Trust and containment specification

Assets: fake credentials, owner request/capability, journal, disposable PG ledger,
witness/checkpoint/key, image identity, HALT epoch, diagnostic evidence and audit.
Actors: caller, JVM, guest/root/container daemon, physical/hypervisor/provider admin,
image publisher, network custodian, backup operator, sink custodian and observer.
An administrator able to replace code/policy or inspect memory remains privileged.
No proposal defeats a hostile administrator controlling every evidence authority.

Future permitted test topology: owner and fake peer on numeric loopback; isolated
fixture traffic only. Default deny ingress/egress IPv4/IPv6/DNS, metadata, host
gateway, proxies and bridge escape. No broker routes or broker connectivity probes.
Management/bootstrap and test mode must be separate, expressly authorized stages.
Remote management needs a separately approved restricted path with MFA/IAM/key
custody and no general outbound escape. A security group alone is insufficient;
require guest/container policy, route/counter evidence and explicit Java denials.

Every candidate needs actual nonidentity uid_map/gid_map, namespace ownership and
guest-host ACL tests; non-root alone is insufficient. C also needs Windows-host
sharing/ACL evidence, which is not a Linux UID translation. Require enforcing
AppArmor profile/hash/Java label/audit/restart, CapEff0, NNP1, seccomp, read-only
root, no host/secret/socket mounts, bounded private tmpfs and cgroups/PIDs. Use
positive authorized behavior and narrowly attributed denial tests. No control is
passed by a flag, a distribution name or java.base packaging alone.

Sink inventory must include JVM heap/native/hs_err/JFR/attach, stdout/stderr,
exceptions, Maven reports/dumpstreams, Docker json-file, journald, host diagnostics,
cloud agents/control-plane audit, CI, backups/snapshots and external forwarding.
For each record exact destination, owner, ACL, encryption/key custody, retention,
deletion and access audit. Proposed sanitized logs <=7days/100MiB per run; raw
canary-bearing dumps confined to bounded private tmpfs, scanned in place, never
exported. These limits are proposals, not installed policy. Test positive canary
detection in private scratch and absence at every unauthorized sink; missing sink
ownership blocks acceptance. A local scan cannot certify unknown external systems.

## Image and runtime trust

Historical identity only: docker.io/library/eclipse-temurin Linux amd64
sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf.
OCI_SIGNATURE_NOT_VERIFIED. No pull, registry authentication or new image inspection.
Require publisher-authorized verification procedure, independently established
signer/issuer/root and exact platform/index subject, signed predicate/build binding,
validity and revocation policy. Verify transparency inclusion/checkpoint where
required, or independently provisioned offline roots with freshness evidence.
Keep signature/attestation/tool/policy digests and redacted output offline; reject
wrong subject, untrusted/expired/revoked evidence or unsigned metadata. Archive GPG,
HTTPS, SBOM and source linkage do not authenticate an OCI signature. An unavailable
chain keeps this mandatory gate open for A/B/C alike. Verify OS signed checksum
and exact bytes separately; preserve minimal JDK21 java.base runtime/no launcher,
test adapters excluded, no real credential loader or trading application.

## Witness authority and recovery

Local journal/PG and owner can roll back. A witness/coordinator/key/checkpoint must
survive outside that restore and administrative domain. For A: a distinct custodian
and storage beyond the machine and its backups. For B: separate effective restore
authority, not merely a second VM or account recoverable by the same organization
administrator. For C: outside the Windows administrator's VM/disk snapshots. None
is supplied or authenticated. Append-only naming, signatures and object retention
are not monotonicity if the adversary can restore every trusted root.

Require signed compare-and-advance bound to attempt/owner/scope/HALT epoch before
dispatch. Missing or conflicting key/root/time, lost ACK or uncertain state denies
dispatch and quarantines; restart/availability toggles cannot clear spent IDs.
Retain surviving-witness local-rollback denial and all-authority restore allowing
two grants in a NO-HTTP counterexample. Future tests need races, kill/replacement,
restart, stale epoch, witness failure, lost ACK and snapshot recovery. No universal
rollback, hardware monotonicity, power-loss or multi-host guarantee is claimed.

Disqualifiers for every option: insufficient capacity/authority, unverified mandatory
image/LSM/userns/sink/root evidence, uncontrolled egress, unaffordable unapproved
cost or inability to enforce cleanup. Selection and all security certification stay
pending. No operational transition follows this document.
