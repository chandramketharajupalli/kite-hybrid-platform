# Phase 13.5E frozen deployment decision protocol

Registered 2026-10-10 before implementation or test execution. Clean develop;
HEAD and origin/develop both 3b7dafdc32834e963e68e4cdaacd9c8972dd037f.
JDK 21.0.12, Maven wrapper 3.9.11, uv 0.12.10, Docker 29.8.0.
G1 manifest, 18 source hashes and 63 protected files passed initial verification;
acquisition/evaluation BLOCK. July TEST unopened. Stage B ZERO_CALL_ABORT retained.

## Decision to investigate

Compare A existing trading JVM, B minimal separate diagnostic JVM and C separate
synthetic environment with disposable DB. Prefer B with no trading DB access as
the future target; C is the only currently executable proof boundary. Reject A
for shared startup/credential/execution authority. Evaluate whether B can reuse
current contracts without bypassing mandatory integrity and ownership guards.

Inspection shows the existing harness requires KiteEquityReadIntegrity and the
handoff uses private same-process references. A DB-free adapter invocation would
bypass the approved harness checks. A separate launcher cannot inherit an existing
authenticated session through those references. Therefore select the permitted
documentation-only implementation path unless inspection establishes a safe
composition without changing these guards. Do not introduce a parallel broker
client, fake live attestation or a token transfer workaround. No standalone
deployment claim may be based on the existing Maven fixture alone.

## Scope and trust boundaries

Allowed changes: five documents only: this plan, phase-13.5e-validation.md,
phase-13.5e-certification-matrix.md, phase-13.5e-runbook.md under docs/operations,
and docs/architecture/kite-minimal-read-only-deployment.md. No code, packaging,
configuration or policy changes are planned. Record any inability to implement
an executable as a deliberate design boundary, not an implemented control.

Threats include bearer export, owner/recipient confusion, replay, expiry/clock
rollback, hostile same-JVM code, crashes/dumps, Windows services/IDE/tasks,
WSL/Docker mounts/environment/logging, remote clients, new and existing database
writers, privileged roles, triggers/functions and restored-state snapshots.
Distinguish trusted code from independently authenticated owner/deployment facts.

Future ownership must bind independently established interactive-auth origin,
account, execution/session identity, owner and recipient, exact read authority,
single use, expiry, revocation, HALT epoch and isolation lease. No UUID or local
profileValidated flag proves real provenance. No credential export via files,
environment, command line, logs or IPC. No real session acquisition here.

Independent HALT must cover every relevant runtime, authenticated owner, epoch,
issuance/expiry and revocation. A local latch/fallback or disabled controller is
insufficient. Preserve knownHaltedAt and all operational execution semantics.

Evaluate removing DB access by construction for future B, not by skipping current
guards. This changes what can be claimed: no diagnostic-caused DB writes does not
mean no other writer, unchanged trading state or atomic account funding evidence.
Existing one-GET approval requires global isolation/baselines and is not transferable
to this alternative. A new contract/security review is required before any change.

For C, restricted SELECT observer plus statistics and content/count/schema/permission
evidence remain fixture requirements. Neither snapshots nor ACLs exclude others.
Preserve NOLOGIN, scoped-drain and write/restore counterexamples. No real session
termination or role changes. No real trading DB connection.

## Registered validation and acceptance

No duplicate tests solely to increase totals. Re-run the six relevant existing
unit/architecture classes as regression evidence for the proposed reused boundary:
KiteEquityReadHandoffTest,KiteEquityReadIsolationTest,KiteEquityReadHarnessTest,
KiteEquityReadRequestFactoryTest,ControlledEquityReadArchitectureTest,RuntimeTradingHaltTest.
Then full `./mvnw.cmd test` and relevant disposable
`./mvnw.cmd -Pintegration -Dit.test=KiteEquityReadHarnessIntegrationTest verify`.
No shared production changes: full unfiltered integration not required. Python
unchanged: pytest/Ruff/mypy not applicable. Record new counts, not prior totals.

Map each test to actual guarantees: synthetic identity/HALT/revocation; strict
route/budget/failure/no retry/redirect; post-read capture; ACL/statistics/schema
drift and writer counterexamples. Do not assert tests prove process packaging,
network namespaces, sink allowlists or live ownership. Existing unsafe wire DEBUG
checks are not a complete log-appender/file/network sink allowlist.

Run project verifier, secret scanner and diff check. Recheck G1 SHA, existing
18-source verifier, all 63 protected hashes against initial bytes and normalized
HEAD, Phase 12 gate and Git inventory. No generated artifacts in deliverables.

Acceptance: explicit ADR with A/B/C comparison, DB-free target threat model,
gap-to-control and certification matrices, lifecycle/exit-state specification,
safe synthetic runbook, honest executable limitations, fresh passing required
tests and preserved safety. Documentation-only design is acceptable under user
section 7; standalone implementation must remain DESIGNED_NOT_DEPLOYED.
Engineering design validation never implies standalone deployment validation.
Missing required tests => PARTIAL; baseline or safety failure => BLOCKED.

## Logging, storage and stop rules

Design startup and pre-dispatch checks for sink allowlist, files, environment,
process ownership and isolated directories; explicitly mark absent implementations.
No live origin in executable configuration. Request budget zero real calls; no
real token, normal application startup, HALT resume/arm/permit/order or real DB.
No .env, migrations, research, CNC, reserve or INR 10,000 ceiling changes.

Reports contain sanitized states and counts only. Ignored test logs are synthetic
and local, not account evidence. Do not store raw real responses, tokens or private
fingerprints. Stop on real-system dependency or unsafe guard bypass. No commit/push.
Freeze exact plan SHA-256 in validation before tests; do not revise criteria later.
Operational NO_GO while any live prerequisite is absent, stale or conflicting.
