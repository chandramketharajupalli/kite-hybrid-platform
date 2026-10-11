# Phase 13.14 logging and diagnostic boundaries

Fresh evidence establishes daemon logging driver json-file only. No new dump or
canary workload ran. Phase13.13's 5521-file zero-hit scan, private canary-bearing heap
dumps and bounded native/attach tests remain historical, hash-preserved evidence.
Positive heap dumps intentionally contained the synthetic canary; they were scanned
inside private tmpfs, never exported. Container disposal is not physical secure erase.
Docker notes tmpfs can be swapped: [tmpfs documentation](https://docs.docker.com/engine/storage/tmpfs/).

| Potential sink | Observed scope | Required future owner/evidence; currently unapproved |
|---|---|---|
| Java stdout/stderr, redacted errors | Historical owner/diagnostic captures | Packaging/logging custodian: fixed field allowlist, exception-chain negatives, no bodies/headers/signatures; drift denies startup/dispatch |
| Heap/core/native hs_err | Historical bounded private tmpfs, forbidden-path denials | Runtime custodian: private bounded scratch, no persistent exports, ACL and encryption/swap policy, exact disposal and authorized inspection proof |
| Attach/JFR/jcmd/JVM diagnostic agents | Historical specific attach-path denial; JFR absent in restricted module layer | Runtime custodian: supported probe/agent inventory and denial; arbitrary native diagnostics remain unverified |
| Maven XML/text/dumpstreams/IDE console | Historical local synthetic scan; old diagnostics retained | Test custodian: access-controlled evidence storage, redacted summaries, raw failure retention and canary scan before export |
| Docker json-file/container runtime logs | Current driver metadata; historical fixture log captures | Platform custodian: per-container destination/options, host ACL, encryption, rotation/retention and audit evidence |
| Docker Desktop diagnostics/VM storage | NOT_VERIFIED | Desktop/host custodian: diagnostic collection/export inventory, explicit consent and recipient policy, access and deletion controls |
| Host collectors/Windows Event Log/EDR/crash collection | NOT_VERIFIED; no broad host scan | Host security custodian: scope, capture fields, destinations, service identities, access logs and synthetic non-secret negative tests |
| Forwarded logs/traces/metrics/proxies | No fixture forwarder historically configured; external environment NOT_VERIFIED | Named destination owner: allowlist, field filtering, transport encryption, authentication and independently verified collection path |
| Backups/snapshots/retention/deletion | NOT_VERIFIED | Storage custodian: copies/regions/access/keys, retention periods and deletion verification including backups; do not infer deletion from file absence |

Custodian names, actual destinations, retention durations and approvals are absent;
these role assignments are proposed responsibilities, not fabricated ownership.
Every authorized sink needs an exact destination, purpose, least-privilege ACL,
encryption at rest/in transit where applicable, bounded retention approved by its
owner, deletion method, backup treatment and access audit. Unknown sinks block full
containment certification. Raw canaries, credentials, account data and private keys
must not enter evidence. Keep sanitized failure provenance without exporting dumps.

Docker's default driver can be overridden per container; driver metadata does not
enumerate every collector. [Logging configuration](https://docs.docker.com/engine/logging/configure/)
is design guidance, not evidence of this host's retention or collector ownership.
Require startup and pre-dispatch sink configuration checks plus negative drift tests
on any future authorized host. No external collector connection is permitted here.
Local redaction and no-hit scans cannot certify privileged host memory access, swap,
arbitrary crash contents or unknown external sinks. Overall containment remains PARTIAL.
