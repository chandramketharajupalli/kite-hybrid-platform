# Production isolation feasibility

Decision: adaptation is conceptually feasible but NOT_ESTABLISHED for production.
Do not move the fixture into production unchanged. No runtime, host, image, dependency
or security configuration was changed. Current-host enforcement was not freshly probed.

## Java boundary review

Source root: apps/synthetic-credential-owner/src/test/java/com/kitehybrid/synthetic/.
VERIFIED below means fresh offline fixture evidence, not production certification.

| Requirement | Existing evidence | State and minimum remaining control |
|---|---|---|
| Exact GET /instruments/NSE | GateABoundary.java raw string equality; GateABoundaryTest | VERIFIED fixture; production transport must preserve exact scheme/host/port/path/query policy |
| No proxy/redirect/retry | Non-null proxy denied; closed response fixture denies redirect/errors without refund | VERIFIED fixture; actual TLS/proxy/DNS/redirect implementation NOT_ESTABLISHED |
| Consume before dispatch | AttemptJournal.retire CREATE_NEW/write/force precedes fixture counter | VERIFIED intact-journal scope; trusted issuer/store custody remains required |
| Restart/replay/concurrency | GateALedgerIT new JVMs share journal; one winner | VERIFIED local process scope, no multi-host consensus |
| Synthetic HALT | GateABoundary does NOT call SyntheticHalt | NOT_ESTABLISHED in GateA; approval.gate=A is not HALT. Prior SyntheticOwner HALT tests are a different path |
| Authorization | Synthetic owner string, expiry, gate and retention checks | PARTIAL; no genuine authentication, signed owner approval or independent issuer |
| Response limits |32MiB bounded fixture clones, UTF8/type checks and immutable hashes | PARTIAL; live streaming/decompression/overall deadline not implemented |
| No orders/WebSockets/candles/DB writes/token restore | Boundary has no network/trading/SQL/session imports | VERIFIED absence in fixture; audit complete future transitive runtime and permissions |
| No trading launcher/general HTTP | Boundary is excluded from runtime JAR; closed response only | VERIFIED current absence; production one-route transport is DESIGNED only |
| Logging | Safe fixed errors, redacted renderings, no output in fixture | VERIFIED local tests; host/EDR/collector/dump custody NOT_ESTABLISHED |

PackagingTest verifies unchanged runtime has no trading/Spring/JDBC or broker-host
dependency. That JAR also lacks GateA functionality; it is not an acquisition product.
Reusing it as-is would not satisfy the mission. Existing KiteRestTransport has order
methods and a root /instruments route; expanding/reusing it is not isolated production
dispatch. CSV parsing/resolution should remain in the existing offline importer after
authorized receipt; never add trading DB authority to the acquisition process.

## Retained OS evidence, not fresh measurements

| Control | Exact retained source | Scope / present conclusion |
|---|---|---|
| Linux Java execution/seccomp/resources | docs/operations/phase-13.11-validation.md, Measured controls | Historical UID/GID65534,CapEff0,NNP1,Seccomp2,PID64/memory256MiB/cpu quota; process/root-write denial |
| Diagnostic failures/recovery | docs/operations/phase-13.12-validation.md and phase-13.12-recovery-validation.md | Original BLOCKED resource/teardown incident retained; later bounded recovery is separate evidence |
| Corrected fixture guards | docs/operations/phase-13.13-validation.md | Historical resource/cleanup and bounded diagnostic tests; not blanket dump safety |
| LSM enforcement | docs/operations/phase-13.14-platform-security-matrix.md | AppArmor/SELinux NOT_ESTABLISHED; no current enforcing process label |
| User namespaces | Same Phase13.14 matrix | Identity maps historically, NOT_REMAPPED; no current translated identity proof |
| Egress | Phase13.11 validation; Phase13.16 security-gate matrix G09 | Historical network-none external denial is not HTTPS domain/path allowlisting |
| External logging | docs/operations/phase-13.14-logging-and-dump-boundaries.md | Desktop/EDR/forwarders/backups NOT_VERIFIED; local zero-hit scans insufficient |
| Credential/filesystem | Phase13.11 validation; Phase13.16 G10-G12 | Historical absence of mounts/socket/witness private key; genuine custody NOT_ESTABLISHED |
| Independent witness | docs/operations/phase-13.9-validation.md; Phase13.16 G12 | Surviving fixture coordinator is not independent administrative authority |

## Host and separate Linux option

Retained docs/operations/phase-13.16-host-measurements.md measured Windows11 Home,
about7.65GiB physical RAM and0.76-0.85GiB available on2026-10-11. Its proposed8GiB
guest failed the declared capacity screen. phase-13.16-hypervisor-feasibility.md did
not select a supported installed hypervisor. These are dated observations, not current
capacity measurements or proof that all Linux options are impossible.

A separately provisioned, adequately sized Linux host/VM is a DESIGNED option only.
Require attributable image/JDK authenticity, enforcing AppArmor/SELinux on actual
Java, nonidentity user namespaces, privilege/cgroup/read-only filesystem controls,
separate credential access, controlled dumps/logs and independently administered
witness/checkpoint retention. No host selected, accessed, purchased or provisioned.
Common-admin VMs do not establish independent custody.

Minimum future network design must combine default-deny IPv4/IPv6/DNS/proxy/metadata
and gateway policy with exact application route enforcement and verified TLS hostname
validation. IP/domain allowlisting alone cannot enforce an HTTPS path. Broker addressing
and DNS changes need bounded policy evidence; do not hard-code invented addresses.
Use a one-use scope-bound admission, consume before uncertain send, deadlines and
bounded wire/decoded reads; no retry/redirect/token refresh. Independent runtime HALT
and logging checks must deny before dispatch without importing a trading launcher.
These are requirements to review later, not deployed controls or acquisition approval.
