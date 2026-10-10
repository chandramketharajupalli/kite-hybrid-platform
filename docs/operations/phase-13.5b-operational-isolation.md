# Phase 13.5B operational isolation decision

Decision date: 2026-10-10. **NO_GO**. Synthetic-only certification.
No live account, credential store, trading database, process inventory or log sink
was accessed to manufacture operational proof. Prior Stage B: ZERO_CALL_ABORT.

The deterministic decision rule is conjunction: every live prerequisite below
must have independent, current, scope-bound evidence. UNKNOWN, stale, conflicting,
caller-asserted or synthetic-only evidence means NO_GO. Successful regression
tests do not change any live column. No executable GO switch is introduced.

| Prerequisite | Code / synthetic evidence | Live decision and missing proof |
| --- | --- | --- |
| Authenticated-session provenance | Private owner/recipient possession; identity/expiry/replay checks; official wire rejected | REAL_AUTH_SESSION_NOT_ESTABLISHED: legitimate existing authentication and intended account/token binding, reviewed safe composition |
| HALT / execution exclusion | Known startup HALT and epoch before/after; unknown denies; architecture excludes startup/operator/order/permit services | LIVE_HALT_NOT_ESTABLISHED: independently attested relevant runtime epoch and absence of execution capabilities |
| Enforceable all-writer exclusion | Cooperative child-process lock, expiring witness, connection checks and revocation tests | LIVE_WRITER_EXCLUSION_NOT_ESTABLISHED: prevention across all processes, hosts and credential holders for the full window |
| Observer privileges / visibility | Disposable restricted role, denied DML/DDL/sequence/escalation/functions, required statistics visibility | LIVE_OBSERVER_NOT_ESTABLISHED: actual role, ACLs, membership, catalog coverage and monitoring permissions |
| Token / trading integrity baselines | Canonical private content/count/schema comparisons; same-count/schema drift detected | LIVE_BASELINES_NOT_ESTABLISHED: authorized SELECT-only connection and independently captured private baseline; snapshots alone insufficient |
| Logging / sink safety | Marker redaction, JSON/native serialization checks; admission and wire DEBUG denial | LIVE_LOGGING_NOT_ESTABLISHED: actual sinks, ACLs, agents/tracing/dumps, retention and change control |
| Exact endpoint / budget | Fixed route; per-JVM official budget tested without execute; loopback one-execute/no-retry/no-redirect matrix | SYNTHETIC_CONTROL_VALIDATED only: no reviewed live composition or actual process budget established |
| Response redaction / authority | Receipt contains time/provenance; strict parsing and no raw values; synthetic never attestation | SYNTHETIC_CONTROL_VALIDATED only: no live payload/sink proof or account-specific terms |

## Exclusion threat inventory

| Potential actor / race | What the existing control detects | What it cannot certify |
| --- | --- | --- |
| Java/javaw, non-Java clients, IntelliJ run configurations | Cooperative lock conflict or visible connection at sampling time | Process names do not identify every credential holder; a new launch after sampling |
| Windows services / scheduled tasks | A visible active connection if present during checks | Dormant tasks, privileged launchers, service restart or later reconnection |
| Docker / WSL / remote hosts | Connections visible to a sufficiently privileged PostgreSQL observer | Host/container coverage or preventing a later remote connection |
| Privileged DB writer / administrator | Visible concurrent session or persistent content/schema change | Non-cooperative writes, privilege changes after checks, transient write-and-restore |
| Lock owner crash / lease expiry | Child crash releases fixture lock; expired/lost witness revokes capability | A released lock does not keep all other actors excluded after the crash |
| HTTP in flight / TOCTOU | Loss detected afterward discards observation and spends attempt | Revocation cannot undo a request already received by the peer |
| Snapshot and statistics gaps | Fresh autocommit checks at both ends; missing visibility denies | Atomic coverage of all tables, unseen short-lived connections, rollback/restored state |

The committed-write/restoration counterexample deliberately passes before/after
equality. That is evidence against treating equality as exclusivity, not a defect
to hide or a criterion to weaken. Local file locks apply only to cooperating
participants; they are not a database firewall or broker credential fence.

## Safe operation of the synthetic certification

Use JDK 21, repository wrapper and disposable Docker PostgreSQL. Do not start
the normal application or load .env/real tokens. The test suite creates fake
sessions and fixture roles; SQL mutations in negative tests affect only containers.

```powershell
.\mvnw.cmd '-Dtest=KiteEquityReadHandoffTest,KiteEquityReadIsolationTest,KiteEquityReadHarnessTest,KiteEquityReadRequestFactoryTest,ControlledEquityReadArchitectureTest,RuntimeTradingHaltTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

Focused disposable selection: `./mvnw.cmd -Pintegration
'-Dit.test=KiteEquityReadHarnessIntegrationTest' verify` (one command).
On failed preconditions, abort before HTTP. After any attempt, compare integrity,
check epoch/session, discard invalid evidence and do not retry. Close only owned
fixture resources. Do not repair a discrepancy by writing token/trading state.

No real-read launcher command is provided. A future separately scoped design
review must close the live evidence gaps and establish enforceable exclusion,
then obtain a fresh go/no-go against explicit authorization. Previous one-GET
approval is not authorization to acquire a session, provision a role, alter
services or run a new handoff experiment. This phase ends without a broker call.

Broker-authoritative gaps remain the eligible haircut-adjusted NSE MIS amount,
free portion after commitments, exact applicable cash rule and qualifying funds
field/computation. The Phase 13.3 clarification draft remains unsent; no general
intraday-collateral question or real quantity is introduced.
