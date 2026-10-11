# Phase 13.14 alternative Linux host assessment

Recommendation: retain Docker Desktop for the already bounded synthetic regressions;
separately authorize an acceptance assessment of a controlled dedicated Linux VM
before any migration. Use a dedicated Linux test host if the proposed VM cannot
provide attributable kernel audit, enforceable policy, real namespace translation
or separation from witness/root snapshot administration. No candidate is provisioned,
available, approved or certified by this recommendation. Prices and capacity are unknown.

The current assessment is PLATFORM_ASSESSMENT_COMPLETE_GAPS_OPEN. Fresh observations
are limited to read-only Windows/Docker/image/resource metadata. Actual Java owner,
dump, concurrency and rollback results are retained Phase13.13 evidence, not reruns.
See the operations matrix and generation-1 manifest for identity and exact scope.

## Alternatives and acceptance work

| Requirement | A: current Docker Desktop | B: controlled dedicated Linux VM | C: dedicated Linux test host |
|---|---|---|---|
| OCI authenticity | NOT_VERIFIED; changing host cannot sign the image | Same publisher trust gate; no automatic improvement | Same publisher trust gate |
| Actual Java confinement | Historical bounded seccomp/NNP/caps/filesystem/process/network denials | NOT_TESTED; rerun actual owner with guest kernel/audit binding | NOT_TESTED; same owner probes against host kernel |
| AppArmor or SELinux | UNAVAILABLE_ON_TESTED_BACKEND in retained Java evidence | Candidate only; prove loaded enforcing policy and denial attribution | Candidate only; prove identical acceptance profile |
| Host/container UID translation | Historical identity mapping; daemon advertises no userns/rootless | Choose and test rootless or userns-remap; guest-to-container and hypervisor boundary separate | Choose and test rootless or userns-remap with host-side process observation |
| Default-deny egress | Historical network-none and loopback fixture | Prove container and guest routes/firewall/namespace topology; no broker probe | Prove container/host topology; no public probe |
| Dumps and logs | Local synthetic sinks historically tested; Desktop/host/external sinks unknown | Inventory guest, hypervisor, backup and administrator sinks | Inventory host, collectors and backup sinks; physical access remains a threat |
| Resource/cleanup budget | Historical three-workload denial and finally cleanup | Rerun fourth-container denial and failure cleanup; no relaxed limits | Same required tests |
| Witness/root separation | Single-host software fixture, not trusted hardware | Keep root outside guest snapshot/restore domain; a second VM on the same snapshot controller is insufficient evidence | Separate witness/root administration and storage; dedicated owner hardware alone is insufficient |
| Evidence collection | Current daemon metadata plus retained Java evidence | Obtain process label, mappings, policy hash, kernel audit and reboot evidence | Same, plus documented hardware/storage/admin inventory |
| Operations burden | Existing tooling; security gaps remain | Additional guest patching, policy, audit and hypervisor/backup ownership required | Additional host lifecycle, physical access, patching, recovery and capacity ownership required |
| Rollback/power loss | All-authority restore still grants twice; power-loss unproven | Snapshots add an explicit rollback actor; no universal guarantee | Privileged restore still possible; no universal guarantee |

These are design obligations, not ratings or promises that an uninspected platform
supports every feature. Host selection must not reclassify mandatory gaps as optional.

## Proposed Linux acceptance profile (not executed)

1. Bind authorized host identity, kernel/build, runtime, image digest and actual owner
   PID/container ID to timezone-bearing observations. Independently verify publisher
   signature policy and exact OCI subject before claiming authenticated provenance.
2. Choose AppArmor OR SELinux. Record policy source/hash/version, loaded/enforcing
   state, actual Java process label and absence of an unconfined/permissive override.
   Provoke an otherwise permitted synthetic Java operation forbidden by this policy;
   require explicit denial and matching kernel audit PID/profile/rule/time. A denial
   already caused by read-only storage or seccomp does not prove an LSM decision.
   After a separately authorized restart, repeat state, label and denial/audit checks.
   Docker documents profile-based AppArmor confinement and audit inspection:
   [AppArmor guidance](https://docs.docker.com/engine/security/apparmor/).
3. Record container /proc/self/uid_map and gid_map, daemon mode, subordinate ranges,
   and host /proc/<owner-pid> mapping/status from an authorized observer. Require
   nonidentity translation and a disposable host-file ACL negative with a permitted
   control file. Running UID65534 alone is insufficient. Never mount real host secrets.
4. Choose rootless where its prerequisites and required resource/network/LSM controls
   can be demonstrated together. Rootless places daemon and containers in a user
   namespace; userns-remap retains a rootful daemon. Neither choice is verified here.
   Record supported cgroup limits, subordinate IDs, socket access and storage ownership;
   do not assume a mode flag proves the policy. See [rootless](https://docs.docker.com/engine/security/rootless/)
   and [userns-remap](https://docs.docker.com/engine/security/userns-remap/).
5. Preserve non-root, CapEff0, NoNewPrivs1, enforced seccomp, read-only root, bounded
   private scratch, private PID/IPC/network namespaces, no host socket/secrets mounts,
   one CPU, memory/PID limits and max three workloads. Require actual Java positives
   and explicit filesystem/process/egress negatives plus cleanup under failure.
6. Inventory every diagnostic/log/backup sink and custodian before any certification.
   Rerun synthetic leak/containment probes under approved bounds, never export heaps.
   Require independent evidence for external sinks, not only redaction unit tests.

## Monotonic-root acceptance boundary

The owner JVM, local journal/ledger, witness storage/key and coordinator checkpoint
form an authority graph. The tested witness survives local rollback only because
the fixture retains it and its coordinator. It is software, not hardware monotonicity.
Restoring every store and root checkpoint can grant twice; the retained negative
control performs no HTTP. At-most-once attempt consumption is not exactly-once delivery.

A future assessment needs an actor-to-store restore matrix, separately retained
checkpoint custody, authenticated advancement, backup/restore ACL/audit, failure
domains and quarantine recovery evidence. Test local and witness restart, lost ACK,
key/epoch conflict, coordinator loss, concurrent replacement and full-root restore.
Independent durable-root claims additionally require storage/flush guarantees and
bounded power-loss evidence; multi-host claims require actual partition/quorum and
failover tests. A signature or append-only filename cannot detect coordinated restore
of every trusted authority. Missing root evidence must deny authorization, not reset IDs.

No provisioning, security-policy loading, daemon change, purchase, migration or
deployment is authorized. V2, governance second review and four reviews remain PENDING;
real access NOT_AUTHORIZED, operational NO_GO and MIS NOT_READY. Owner acceptance
and a recorded first checkpoint do not supply the missing second decision/time proof.
