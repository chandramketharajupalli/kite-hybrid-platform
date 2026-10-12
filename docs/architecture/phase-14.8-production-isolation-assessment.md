# Phase 14.8 production isolation assessment

Production adaptation remains incomplete. PRODUCTION_EGRESS_NOT_VERIFIED.
No deployment, runtime modification, host probe, container or credential access.
VERIFIED below describes only the stated source or historical test scope. PARTIAL
means bounded evidence with operational gaps; DESIGNED is unimplemented policy;
NOT_ESTABLISHED means no sufficient evidence. NOT_APPLICABLE never waives a future
production requirement.

Source root for class references: apps/synthetic-credential-owner/src/test/java/
com/kitehybrid/synthetic/. SyntheticHalt is under the same package in src/main/java.
Historical test counts are in Phase 14.7 validation and its fingerprinted manifest.

| Control | Classification and evidence | Required before production |
|---|---|---|
| Exact GET https://api.kite.trade/instruments/NSE | VERIFIED fixture: GateAHaltBoundary.acquire and forbiddenRoutesMethodsAndProxies | Preserve byte-exact method/route in an isolated transport; no general dispatch API |
| No orders, WebSockets or candles | VERIFIED fixture: closed response only; PackagingTest.tradingAndSpringClassesAreAbsent | Audit complete final runtime and process/egress permissions |
| No trading launcher or DB write authority | VERIFIED fixture: test-scope boundary; PackagingTest standalone dependency checks | Least-privilege runtime identity, absent DB credentials/network routes, no application startup |
| No token restoration | VERIFIED absence in reviewed boundary | Separate legitimate session provenance and custody; no restoration path |
| No redirects/retries/proxy input | VERIFIED fixture: exact match, non-null proxy denied, unknownDeliveryAndErrorsRemainSpent | Prove actual HTTP, JVM/environment proxy, DNS and redirect controls |
| Response limit | PARTIAL: GateABoundary.Response caps supplied array at 32MiB | Bound wire and decoded/decompressed bytes before allocation and during reads |
| Timeouts | DESIGNED: fixture TIMEOUT outcome only | Explicit connect/read/overall deadlines; cancellation consumes attempt |
| HALT and expiry | VERIFIED synthetic: admit/recheck; signed scope and <=30s assertion | Independent authenticated issuer/current state, time uncertainty and revocation channel |
| Durable one-use | PARTIAL: forced marker plus shared local journal tests | Trusted durable store and rollback authority; directory/power-loss evidence |
| Destination/egress/TLS | NOT_ESTABLISHED: no production HTTP exists | Default-deny network, verified TLS, destination/DNS/proxy restrictions |
| Logging | PARTIAL: exceptionsAndOutputDoNotExposeAuthorization | Verify all actual sinks, dumps, diagnostics, permissions and retention |

GateAHaltBoundary validates identity and owner evidence, admits a HALT lease, retires
the attempt in memory, rechecks HALT, invokes AttemptJournal.retire, and rechecks in
the AFTER_JOURNAL_FORCE callback immediately before the old closed fixture can send.
It then rechecks before releasing the result. The Control monitor orders state
mutations relative to fixture dispatch. Historical revocation tests show zero sends
before admission and one send with discarded result after admission. The modeled
delivery window is not a real network in-flight test. Revocation cannot undo sent bytes.

The prior GateABoundary remains a callable historical test fixture; no claim is made
that every unrelated caller is migrated. The new entry point offers no unguarded
fallback. Neither fixture is in the production runtime JAR. Moving the test class
unchanged would not create a safe acquisition product. Existing general Kite transport
is outside this boundary and must not be imported to obtain convenient dispatch.

Historical OS evidence, not fresh host attestation:

| Control | Classification | Evidence and remaining limit |
|---|---|---|
| Seccomp | PARTIAL | Phase 13.11 validation measured actual Java Seccomp=2 and process-creation EPERM; no production process measured |
| Non-root/capabilities | PARTIAL | Same report UID/GID65534, CapEff0, NoNewPrivs1; no production identity |
| Resource limits | PARTIAL | Same report PIDs64, memory256MiB and CPU quota; no live resource budget |
| Filesystem | PARTIAL | Same report root-write denial, private scratch, no host/socket/key mounts; genuine response custody unverified |
| AppArmor/SELinux | NOT_ESTABLISHED | Phase 13.14 platform-security matrix: unavailable/unconfined on tested backend |
| User namespaces | NOT_ESTABLISHED | Same matrix: retained identity maps, no measured remapping |
| Network destinations | NOT_ESTABLISHED | Network-none denial in Phase 13.11 does not prove selective broker HTTPS access |
| DNS/proxies | NOT_ESTABLISHED | Historical invalid-DNS/no-route cases cannot certify controlled production resolution/proxy bypass denial |
| External logging | NOT_ESTABLISHED | Phase 13.14 logging-and-dump-boundaries: EDR, Desktop, collectors, backups unknown |
| Credential/witness isolation | NOT_ESTABLISHED | Phase 13.16 security-gate-matrix G10-G13: no separate production custodians/root |

Retained Phase 13.16 host-measurements found insufficient RAM for its proposed VM,
and hypervisor-feasibility did not establish the target. These are dated findings,
not current measurements or proof that every Linux option is impossible. Separately
provisioned Linux is DESIGNED only: require signed image/JDK provenance, enforcing
LSM on actual Java, UID translation, privilege/cgroup/filesystem controls and independent
network/sink measurements. No infrastructure has been selected or provisioned.

Future network policy must cover IPv4/IPv6, DNS, proxy environment/system properties,
metadata and host gateways, redirects and exact TLS host validation. Network IP/host
allowlisting cannot enforce an encrypted URL path; both transport and egress controls
are needed. No invented broker IP list, test signature or Docker configuration is proof.

The request-budget and custody limitations are detailed in the readiness matrix.
No new implementation is justified until terms and production authority are resolved.
