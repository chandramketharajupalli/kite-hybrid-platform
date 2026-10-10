# Phase 13.5 validation

Date: 2026-10-10. Stage A **CONTROLLED_READ_HARNESS_VALIDATED**. Stage B approval checkpoint
AWAITING; REAL_READ_AWAITING_APPROVAL / REAL_READ_NOT_PERFORMED. Real account
evidence NOT_COLLECTED. No real request is authorized.

## Baseline and freeze registration

Before implementation inspection/edits, all six requested Git commands ran.
Status and diff-check were empty; branch develop; HEAD and origin/develop both
`4aaf9f78637736224cfb846ef7004659ac5603e0`. No fetch/pull/reset/restore/stash/clean.
Baseline log:

```text
4aaf9f7 Add read-only Kite equity margin adapter
d61f6b2 Document Zerodha MIS collateral evidence gaps
18608d4 Add broker-verified MIS collateral evidence contract
12117e9 Add Kite MIS funding evidence diagnostics
2942195 Harden Kite Connect runtime integration
ac87a96 Document pending corporate-action evidence closure
1624d34 Document corporate-action completeness review
9193e0b Reconcile historical NSE security identities
```

JDK 21.0.12, Maven wrapper 3.9.11, uv 0.12.10, Docker client/server 29.8.0.
No tool installs or global environment changes. Deployment/JVM overrides were
removed only in child test processes as registered; no .env read. Python helpers
used PYTHONDONTWRITEBYTECODE=1 / -B to avoid tracked bytecode changes.

Plan frozen before production edits and public source requests, SHA-256:
`40A83D54A06EAE035E893478738E9C84E974CEB73BD65658509F26BFF5C25CBF`.
G1 manifest matched `56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`;
existing verifier passed all 18 source hashes. Baseline SHA-256 inventory includes
63 protected tracked research files. Phase12 five-member acquisition/evaluation
allowed false, with five unresolved corporate-action reasons. July TEST unopened.

## Implemented boundary

Three internal classes compose the existing equity adapter/session/transport:
single-use harness, exact-route wire factory and SELECT-only PostgreSQL integrity
guard. No Spring wiring, credential loader, REST/Actuator endpoint, new Kite
service, funding engine or application configuration. HttpClient5 5.5.2 is
Boot-managed and added for explicit per-client no-retry/no-redirect controls;
normal transport constructors and permissions remain unchanged.

Default DISABLED; only literal loopback synthetic origin or the fixed official
equity route. Invalid routes poison the factory. A non-resettable real JVM budget
prevents multiple factories spending more than one attempt. Budget reservation
unit test creates request objects only, never executes or opens a broker socket.
Connection/response timeout and 64 KiB transport parsing limits remain bounded.

Harness requires an already authenticated in-memory session; it never installs,
validates, exchanges, restores or cleans a real token. HALT must be startup-halted
and runtime HALTED with unchanged epoch. Database role requires server read-only
transactions, SELECT-only table access, no elevated/write/sequence/schema-create
privileges, pg_read_all_stats visibility, no other database clients/prepared
transactions or RLS-hidden trading rows. Required tables and exactly one token
row must exist; allowed authorization rows deny conservatively. No role changes
or database writes are performed by production harness code.

Private before/after fingerprints cover every trading table's count and sorted,
length-delimited row content using SHA-256. No raw row, token bytes, balances,
session IDs or fingerprint values enter the result/log/artifacts. Every broker
outcome executes post-verification; mismatch discards the observation. A result's
statePreserved refers to durable tables/HALT, not in-memory authentication after
401/403. Query bounds, redaction and no-write architecture checks are documented
in the [runbook](phase-13.5-runbook.md).

Independent external quiescence and safe authenticated account/session handoff
remain Stage B preconditions. Database sampling cannot prevent another process
from reconnecting after a check. The harness neither claims that guarantee nor
starts/stops other applications to manufacture it. Real mode enum is not user
approval. No real credential-loading launcher is added; unavailable handoff or
isolation means zero-call abort even if a proposal is later approved.

## Evidence matrix

| Question | Status | Missing scope/authority |
| --- | --- | --- |
| Eligible adjusted collateral | UNKNOWN | Authenticated NSE MIS category/haircut-adjusted eligible aggregate and restrictions |
| Actually available collateral | UNKNOWN | Free eligible amount after utilisation, reservations and pending commitments |
| Applicable cash-component rule | CONFLICTING public descriptions; runtime UNKNOWN | Dated account/product/category rule and precedence |
| Qualifying cash-field mapping | CONFLICTING public descriptions; runtime UNKNOWN | Exact broker-confirmed API field/computation, exclusions and effective scope |

No current account, real request, broker effective date, validity/expiry or real
observation fingerprint is bound. Received-at is local time, not an atomic broker
snapshot. The exact direct single-equity response remains a synthetic assumption.
The existing V1 equityObservation projection accepts a redacted receipt plus
request/reference context and remains caller-normalized/unverified. Tests carry
synthetic fingerprints through it; no positive cash, high net or utilisation
shape creates PROVEN. Standalone cash-only projection MIS_MARGIN_UNAVAILABLE;
full-context cash arithmetic/CNC unchanged; collateral-assisted MIS NOT_READY.

Official sources, accessed 2026-10-10:

- [Kite funds route/schema](https://kite.trade/docs/connect/v3/user/#funds-and-margins): GET equity segment, version/token headers; combined-body example. No account-specific eligibility assertion or overall page effective date.
- [Apache builder controls](https://hc.apache.org/httpcomponents-client-5.6.x/current/httpclient5/apidocs/org/apache/hc/client5/http/impl/classic/HttpClientBuilder.html): explicit retry/redirect disabling; local BOM 5.5.2 compiled and tested.
- [PostgreSQL predefined roles](https://www.postgresql.org/docs/17/predefined-roles.html): statistics visibility, enforced explicitly to avoid hidden other-user sessions.
- [Prior broker-authority matrix](../architecture/kite-mis-authoritative-evidence.md): unresolved product/cash-field conflicts retained; unsent broker clarification unchanged.

No F&O ratio is imported into NSE MIS; utilised collateral and net are not free
capacity. No executable candidate or quantity selected. No support message sent.

## Tests and correction history

All tests use synthetic credentials, loopback/intercepted HTTP and disposable
PostgreSQL. No production app startup in the new harness integration tests.
Existing operator suites use isolated fake execution/permit/HALT fixtures.

| Run | Passed | Failures | Errors | Skipped | Outcome |
| --- | ---: | ---: | ---: | ---: | --- |
| Initial focused wire/adapter/mapper/evidence/architecture | 231 | 0 | 0 | 0 | PASS |
| Expanded focused harness selection | 266 | 1 | 0 | 0 | New architecture pattern also matched Apache HTTP config; corrected to application config, existing guards unchanged |
| Initial full unit stage | 1446 | 0 | 0 | 0 | PASS |
| Initial focused disposable harness | 6 | 0 | 14 | 0 | Guard denied before HTTP; PostgreSQL evaluated sequence privilege on non-sequence rows |
| Temporary bounded diagnostic build | 0 | 0 | 0 | 0 | COMPILE FAILURE from diagnostic variable scope; no tests executed |
| Corrected diagnostic build, focused disposable | 6 | 0 | 14 | 0 | Same SQLSTATE 42809 established root cause; unit stage intentionally not run |
| Corrected full unit stage | 1447 | 0 | 0 | 0 | PASS |
| Corrected focused disposable harness | 21 | 0 | 0 | 0 | PASS, whole selected build 2m33s |
| Final standalone unit | 1447 | 0 | 0 | 0 | PASS, 1m37s |
| Final full integration verify: unit | 1447 | 0 | 0 | 0 | PASS, 85 XML suites |
| Final full integration verify: disposable | 546 | 0 | 0 | 0 | PASS, 21 XML suites; full verify 16m12s, exit 0 |

Correction: sequence-privilege query uses CASE guarded by relation kind; checks
are not weakened. Added explicit pg_read_all_stats requirement and negative test.
Temporary diagnostics emitted only bounded step/type/SQLSTATE and were removed.
Added process-wide real budget and poisoned invalid routes; final full suite
includes receipt-to-V1 assertions. No acceptance criteria changed.

Commands: registered focused `-Dtest` selection in runbook, full `./mvnw.cmd test`,
focused `./mvnw.cmd -Pintegration '-Dit.test=KiteEquityReadHarnessIntegrationTest' verify`,
and full `./mvnw.cmd -Pintegration verify`. The one temporary diagnostic repeat
used documented `-DskipUnitTests=true`; no skipped tests are represented as PASS.
Local ignored logs `tmp/phase135-*.log` retain build evidence only.

New coverage: disabled/precondition denials; strict route/method/origin and budget;
redirect, dropped response, timeout, 401/403/429/5xx; malformed/duplicate/trailing/
oversized and equity-only variants; signed/zero/contradictory/unknown fields;
session/time/HALT/integrity deterioration; concurrent run calls; unprivileged
role, other connection, missing token, wrong transaction/isolation and hidden
statistics. External-write tests inject faults only into disposable fixtures;
the harness itself writes nothing and reports STATE_CHANGED rather than success.

Python source unchanged: pytest/Ruff/mypy not applicable, not run. Interim project
verifier PASS and secrets PASS (1692 text files, zero candidate locations).
Final XML totals were independently summed. Full verification and final freeze/Git
audit are recorded below.

## Exact final Git inventory

Modified:

```text
apps/trading-core/pom.xml
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteRestTransport.java
```

New/untracked:

```text
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadHarness.java
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadIntegrity.java
apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadRequestFactory.java
apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadHarnessTest.java
apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadRequestFactoryTest.java
apps/trading-core/src/test/java/com/kitehybrid/platform/ControlledEquityReadArchitectureTest.java
apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadHarnessIntegrationTest.java
docs/operations/phase-13.5-plan.md
docs/operations/phase-13.5-runbook.md
docs/operations/phase-13.5-real-read-approval.md
docs/operations/phase-13.5-validation.md
```

No .env, application resources/configuration, migrations, risk/reserve/CNC,
preflight/dispatch, authentication lifecycle or protected research changes.
INR 10,000 full buffered-notional ceiling unchanged. All operational real Kite
GETs/other requests/WS/order mutations, token/development DB writes, HALT
resume/arm/execute: zero. No commit or push. The concrete Stage B proposal was prepared only after Stage A tests passed.
No separate approval or real request has occurred.

## Final audit

Final required Git commands: status --short, diff --check, diff --stat,
diff --name-status and ls-files --others --exclude-standard; production/POM and
resource diffs inspected, new production sources reviewed. Two modified tracked
files (9 insertions), eleven new/untracked deliverables listed above. No staged
changes, commit or push. HEAD and origin/develop remain the baseline on develop.
Generated targets, temporary hash inventories and local synthetic logs are ignored,
not deliverables; no bytecode/raw account payload in the Git inventory.

G1 manifest, all 18 source hashes, all 63 protected artifacts and frozen-plan SHA
rechecked unchanged. Five-member acquisition remains BLOCK. No .env, safety
resources, migrations, risk limits, cash/CNC, preflight/dispatch or sealed data
diffs. Next bounded action is explicit review of the exact one-GET proposal,
subject to authenticated handoff and independent isolation evidence; broker
clarification remains separately unsent. No automatic live promotion.

Final project verifier, secret scanner and `git diff --check` each exited 0.
The final secret scan inspected 1,694 text files and found zero potential secret
locations. Python source is unchanged; pytest, Ruff and mypy were not applicable
and were not run. The Stage B approval question was presented for the exact
Phase 13.5/v1 proposal; no separate approval was received and zero real calls
were executed. Status: REAL_READ_AWAITING_APPROVAL / REAL_READ_NOT_PERFORMED.

## Stage B continuation: explicit approval, ZERO_CALL_ABORT (2026-10-10)

The user subsequently explicitly approved at most one GET to
`https://api.kite.trade/user/margins/equity`, with no retries, redirects,
supplementary requests, login/restoration, state mutations or trading actions.
This supersedes the earlier awaiting-approval status, but does not waive any
precondition. Outcome: **ZERO_CALL_ABORT / REAL_READ_NOT_PERFORMED**.

Read-only inspection confirmed develop at
`4aaf9f78637736224cfb846ef7004659ac5603e0` and the existing Stage A inventory.
The proposal, runbook, harness and request factory were inspected. Source search
found harness composition only in synthetic tests; no reviewed real-session
handoff was available. A process-name-only check returned no local java/javaw
processes. It did not inspect command lines, credentials or process memory and
does not prove external process isolation or absence of differently named JVMs.

| Mandatory precondition | Current evidence / decision |
| --- | --- |
| Explicit bounded approval | PASS: user approved the exact one-GET/no-write scope. |
| Existing authenticated in-memory session | NOT ESTABLISHED: no accessible authenticated session or independently reviewed account/token binding and handoff. First blocking prerequisite. |
| Active runtime HALT and stable epoch | NOT VERIFIED: no accessible live runtime latch. No new latch was fabricated as operational proof. |
| No execution/startup side effects | Static isolated composition confirmed; no application or execution services started in this continuation. Live embedding not available for verification. |
| Independent process isolation | NOT ESTABLISHED: process-name observation cannot guarantee an exclusive window or prevent reconnection. |
| Read-only DB privileges and monitoring visibility | NOT VERIFIED: no existing approved SELECT-only connection supplied; no DB connection attempted. |
| Encrypted token/trading-table integrity baseline | NOT CAPTURED: aborted before DB access. No before/after integrity claim is made. |
| Request logging and response redaction | Static guards/redacted result confirmed; live logging/tracing/dump configuration NOT VERIFIED. |
| One process-wide request budget | Static atomic one-attempt guard confirmed; no real harness process instantiated, so live budget state not asserted. Zero attempts made. |
| No retries/redirects/other endpoints | PASS at source level: fixed official origin/exact GET path, retries and redirect handling disabled. No live client instantiated. |

No HTTP dispatch, profile call, login, token exchange/restoration, DB connection,
application startup or WebSocket occurred. Real Kite requests remain **0**;
token/development DB mutations and HALT resume/arm/execute remain **0**.
No raw account response or sensitive integrity fingerprint was collected.
Operational HALT and table integrity could not be independently measured; this
continuation performed no operation that changes them. No guard was bypassed.

Response shape remains NOT_COLLECTED. Eligible adjusted collateral and actually
available collateral remain UNKNOWN. Applicable NSE MIS cash rule and qualifying
cash-field mapping remain unresolved public-source conflicts / account-specific
UNKNOWN. Collateral-assisted MIS remains NOT_READY; no candidate or quantity was
selected. Broker clarification remains unsent.

Only this validation document and the approval-status annotation were updated in
this continuation. Production code and Stage A acceptance criteria are unchanged.
Stage A test totals above are historical results, not reruns for this continuation;
Java/integration suites were not repeated for documentation-only changes.
Next bounded action: establish a separately reviewed existing authenticated
in-memory handoff and independently verifiable isolation/read-only prerequisites.
Do not start the application, restore tokens, provision roles or weaken guards
under this approval to manufacture those prerequisites. No commit or push.
