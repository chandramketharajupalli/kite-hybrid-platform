# Phase 13.5C frozen prerequisite plan

Registered 2026-10-10 before implementation or new-behavior tests. Clean develop;
HEAD and origin/develop both 95ef537ede15ee5b3f32f814e5d8ad6f3e0c6805.
JDK 21.0.12, Maven wrapper 3.9.11, uv 0.12.10, Docker 29.8.0.
G1 manifest SHA and 18 frozen sources pass; 63 protected files match Phase 13.5B.
Five-member acquisition/evaluation BLOCK; July TEST unopened. Prior Stage B
ZERO_CALL_ABORT and zero requests remain historical facts, never rewritten.

## Scope and five prerequisites

1. Session provenance: existing synthetic KiteSession, exact private owner/recipient
   references, fixed Kite broker boundary, session identity, expiry, HALT epoch,
   lease and atomic consume. Independently authenticated real provenance remains
   absent; local profileValidated(), a UUID or token-shaped value is not proof.
2. Independent HALT: preserve knownHaltedAt and existing execution semantics;
   unknown/false/stale/replaced evidence denies. A local object does not attest a
   different live runtime. No live HALT methods are invoked.
3. Writer exclusion: distinguish cooperative locks from non-cooperative processes,
   existing sessions from future connections and snapshot equality from prevention.
   Require independent shutdown/credential/network controls for any future topology.
4. Observer/integrity: SELECT-only ACLs, inherited membership, TRIGGER, sequence,
   schema/function privileges, catalog/statistics visibility, private row/count/schema
   fingerprints and post-read capture despite auth/HALT/lease loss.
5. Logging/HTTP: exact loopback GET, one execute, no retry/redirect, safe late logging
   checks, strict bounded response parsing and no metrics/serialization secret export.

Real requests, real token loads, development DB connections, normal app startup,
operational HALT resume/arming/permits/orders: ZERO. No real-origin launcher or
credential loader. Keep synthetic handoff's official-origin rejection unchanged.
No funding policy, reserve, CNC, INR 10,000 ceiling, config, migrations or research edits.

## Evidence vocabulary and decision

SYNTHETICALLY_VALIDATED means newly executed fixture evidence only.
LIVE_NOT_ESTABLISHED means independent current operational evidence is absent.
STALE means expired or mismatched epoch/session/scope; CONFLICTING means incompatible
sources; BLOCKED means a required check cannot run or denies a precondition.
Each prerequisite matrix row records source, observation date, scope, limitation
and denial reason. Operational GO requires all independent live terms current and
proven; synthetic status cannot be promoted. Expected operational result NO_GO.

## Threat model and trust boundaries

Replay/theft, wrong reference/session/recipient, clock rollback, concurrent consume,
revocation, process death and midflight state changes remain fenced by existing
code. Same-process capabilities assume trusted code; reflection, hostile agents,
heap dumps and OS administrators are not excluded. No portable session artifact.
Kite broker identity is fixed by the concrete session/adapter and exact route, not
a caller-selectable broker label; this identifies intended scope, not authentication.

Consider non-Java/Java processes, IDE launches, services/tasks, Docker/WSL, remote
hosts, alternate credentials and administrators. File/advisory locks are cooperative.
NOLOGIN does not by itself terminate an already-connected writer. Matching snapshots
cannot detect committed write-and-restore; preserve and rerun that counterexample.
Future topology review must cover both already-connected and newly connecting writers.
Restricted observer and independent isolation controller are distinct trust domains.

## Demonstrated coverage gaps and exact allowed changes

Production code remains unchanged unless review demonstrates a new defect; such a
defect requires a documented bounded plan extension before production edits, with
unchanged acceptance criteria and full integration if shared code changes.
Current work is test/document-only, reusing existing classes and fixtures:

- apps/trading-core/src/test/java/com/kitehybrid/platform/ControlledEquityReadArchitectureTest.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadHandoffTest.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadIsolationTest.java
- apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteEquityReadHarnessIntegrationTest.java
- The five requested Phase 13.5C documents only.

New coverage: synthetic authenticated session still yields UNKNOWN funding terms;
diagnostic dependency guard excludes metrics/tracing export; concurrent fixture
resume discards observation and still checks integrity; independent non-cooperating
child can act despite held file lock; inherited write/TRIGGER privileges deny before
HTTP; fixture NOLOGIN blocks new writer but does not stop an existing connection.
No duplicate client, new authority engine or public activation switch.

## Acceptance and validation

Run focused handoff/isolation/harness/wire/architecture/HALT tests; full
./mvnw.cmd test; disposable ./mvnw.cmd -Pintegration
'-Dit.test=KiteEquityReadHarnessIntegrationTest' verify. The latter also reruns all
unit tests. Full unfiltered -Pintegration verify is mandatory if shared production
HALT/auth/transport/persistence/execution code changes; otherwise focused disposable
coverage is sufficient for these test-only additions. Do not reuse previous totals.
All final Java edits precede final test runs; documentation may record results later.

Preserve/rerun existing replay/expiry/session/epoch/lease/serialization/logging cases,
HTTP 301/302/307/308/401/403/429/5xx/timeout/lost response/malformed/oversize/wrong route,
SELECT-only/statistics/DDL/sequence/function/escalation, changed rows/empty schema,
rollback/transient restore/concurrent connection. Failed prerequisites: zero HTTP;
post-dispatch loss: no retry, post-integrity attempted, observation discarded.
Non-exclusion counterexamples must succeed as demonstrations of limitations, not
be reported as global exclusion PASS. Existing official budget tests never execute
request objects or contact Kite; synthetic budgets remain per fixture.

Run project verifier, secret scan, diff --check, final G1/18/63 checks and Git audit.
Python unchanged: pytest/Ruff/mypy not applicable unless source changes. Record
actual failures/errors/skips/corrections. Baseline or unsafe evidence blocks;
incomplete required validation is PARTIAL. Never relax criteria to claim GO.

Plan fingerprint: SHA-256 of exact file bytes, recorded in validation after creation
and compared at completion, avoiding self-reference. No raw account values, token
fingerprints, session identifiers or credentials in documentation. Temporary fixture
logs and public research hash inventories stay ignored. No commit/push. Stop after
the report; no automatic real request or live promotion.
