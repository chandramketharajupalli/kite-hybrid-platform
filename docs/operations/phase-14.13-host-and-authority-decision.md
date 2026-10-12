# Phase 14.13 conditional host and authority decision

Decision: production use DEFERRED. No deployment/procurement approval. Gate A and Gate B
NOT_AUTHORIZED. INDEPENDENT_ROLLBACK_AUTHORITY_NOT_ESTABLISHED. Current machine capacity
and third-party authority availability were not freshly measured or assumed.

## Authority options

| Option | Administrator / rollback / atomicity | Recovery and custody | Burden / acceptance evidence |
|---|---|---|---|
| A local filesystem | Same operator can restore every file; tested file-lock CAS only | Revocation survives retained valid files; missing/corrupt deny; no independent key/backup custodian | Low fixture burden; production requires proved filesystem/ACL/flush behavior plus an external non-rollbackable checkpoint |
| B separate PostgreSQL witness | Separate DB is not a separate administrator; transaction CAS and retained Root shown only in historical Phase13.9-13.10 tests | Replay denial depends on surviving Root, witness checkpoint and separate restore rights; same admin can roll back both | Moderate DB/backup/recovery operations; require immutable minimum checkpoint, explicit roles/restore audit and fault tests against real chosen storage |
| C independent remote monotonic authority | Conditional design: independently controlled compare-and-advance service, outside owner restore scope | Require authenticated freshness, deny on partition/uncertainty, sticky revocation, protected signing keys and independently controlled backups | Ongoing service/security/availability burden; price and service availability unknown; require attributable administrator agreement and tested restore/failover/custody |
| D hardware/external monotonic counter | No supported counter established on this host; do not infer from TPM presence or product labels | Counter/key reset, replacement, rollback and recovery semantics must be proved; deny when counter unavailable | Hardware/integration/support burden unknown; require exact capability/firmware evidence, anti-reset trust model and independently reviewed lifecycle tests |

No option currently satisfies production acceptance. B physical separation alone is
insufficient. C is the conditional architecture to evaluate if genuine independent
custody can be established; it is not a selected vendor or an instruction to subscribe.
No price, account entitlement, hardware capability or signed custodian agreement is invented.

## Host options

| Candidate | Conditional disposition | Required evidence before selection |
|---|---|---|
| Current Windows/Docker Desktop host | Not accepted for production on retained evidence | Fresh sustained capacity, actual JVM containment, enforcing LSM/UID mapping, DNS/egress/sink ownership and independent authority |
| Upgraded Windows with isolated Linux VM | DESIGNED only | Verified post-upgrade headroom, supported hypervisor/image provenance, measured guest/host isolation and separate authority custody |
| Dedicated Linux host | DESIGNED only | Resource budget, non-root/caps/seccomp/LSM/userns/filesystem enforcement on actual JVM, patch/backup/admin ownership, independent authority |
| Remote Linux VM plus independent authority | Conditional candidate for further review | All Linux controls plus documented cloud/guest/log/backup owners and genuinely separate authority administration; no owner ability to restore the authoritative minimum checkpoint |

Phase13.16 host measurements are historical:7.650913GiB physical RAM,0.762306-0.849621GiB
available during three samples, inadequate for its proposed8GiB guest and14GiB available
screen. Those figures are not current capacity. No RAM upgrade, hypervisor activation,
VM/image download or host probe is performed here. Phase13.17-13.19 retained hardware,
image, custody and budget gaps are not promoted by the new fixture tests.

## Measurable production acceptance

| Control | Classification now | Evidence required |
|---|---|---|
| Offline route/signed checks/checkpoint CAS | VERIFIED within fresh fixture scope | Preserve executable negative tests and exact packaged dependency allowlist |
| Non-root, caps, seccomp, read-only root, scratch/resource limits | PARTIAL historical Phase13.11 Java measurements | Repeat on selected production-intended JVM: UID/GID,CapEff0,NNP1,Seccomp2,write denial,owned bounded scratch,CPU/memory/PID limit and kill/cleanup evidence |
| AppArmor/SELinux and user namespaces | NOT_ESTABLISHED | Enforcing profile attached to actual process, denied forbidden accesses and nonidentity UID/GID mappings |
| Selective destination egress, DNS/proxy escapes | NOT_ESTABLISHED | Default-deny IPv4/IPv6/host/metadata routes; only separately approved exact destination; measured proxy/DNS/redirect bypass denial plus application exact path enforcement |
| External logs/dumps/backups | NOT_ESTABLISHED | Named owners,all sinks,ACLs,encryption,retention,deletion and canary/dump containment tests |
| Owner-held session / credentials | NOT_ESTABLISHED | Attributable valid session and entitlement; short-lived non-CLI/non-env injection, no restoration/login automation, secret-free diagnostics and incident revocation |
| Independent witness/key/restore custody | NOT_ESTABLISHED | Separate administrator and keys, unforgeable monotonic minimum checkpoint, explicit backup/restore rights and owner-rollback challenge tests |
| Physical persistence / replicated recovery | NOT_ESTABLISHED | Chosen storage guarantees and independently reviewed nondestructive test plan; no inference from process kills |

Resource acceptance must have predeclared sustained-load limits and margins for the actual
selected host, not inherited stale snapshots. Historical Phase13.12 teardown/resource
failure remains BLOCKED evidence. Phase13.14 LSM/namespace gaps and Phase13.16 G03-G14
remain unresolved. Fresh checks here do not exercise OS enforcement or external sinks.
PRODUCTION_EGRESS_NOT_VERIFIED; PHYSICAL_POWER_LOSS_NOT_VERIFIED.

Sole-developer/operator role remains user-declared. No independent reviewer/custodian is
invented. Self-review and local backups are not independent approval or root custody.
AUTHENTICATED_SESSION_NOT_ESTABLISHED. Future incident procedure must revoke the session,
deny at issuer and monotonic authority, preserve audit evidence, and prohibit automatic
resume. Logging/retention controls require attributable owners and terms clearance.

Compliance: original Phase14.11 user-reported sent email to compliance@zerodha.com and
all eleven topics remain unchanged/UNRESOLVED. No reply,ticket,delivery proof,screenshot
bytes or sent-body hash supplied. ZERODHA_TERMS_UNRESOLVED. No vendor contact performed.
Operational NO_GO,ZERO_CALL_ABORT,Phase12BLOCK,H1/H2FROZEN,JulySEALED,INR10000 ceiling
and V2/governance/four technical reviews pending remain unchanged.
