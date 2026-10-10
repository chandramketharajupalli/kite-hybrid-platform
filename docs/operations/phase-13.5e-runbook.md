# Phase 13.5E synthetic-only review runbook

There is no new standalone launcher. The selected B architecture is a design;
the executable C proof uses existing Maven tests, loopback peers and disposable
PostgreSQL. Do not interpret these commands as startup of a minimal live runtime.
Stage B remains ZERO_CALL_ABORT; no previous real-read approval is exercised.

## Preconditions and startup

Use JDK 21, repository Maven wrapper, uv and Docker. Review the fixture source:
KiteEquityReadHarnessIntegrationTest obtains its JDBC target from its owned
PostgreSQLContainer; synthetic sessions are created locally; peers bind literal
127.0.0.1. Never substitute a development database or account credential. Do not
source .env, launch Spring Boot, OperatorConsoleApplication or KiteRestDiagnostic.
The normal ApplicationRunner can restore tokens and is outside this procedure.

From the repository root:

```powershell
.\mvnw.cmd '-Dtest=KiteEquityReadHandoffTest,KiteEquityReadIsolationTest,KiteEquityReadHarnessTest,KiteEquityReadRequestFactoryTest,ControlledEquityReadArchitectureTest,RuntimeTradingHaltTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration '-Dit.test=KiteEquityReadHarnessIntegrationTest' verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

These are regression commands for unchanged code. No additional tests are added
solely to repeat prior coverage. Maven exit 0 means selected tests passed; it is
not a live-read exit code or operational GO. Proposed standalone exit codes in
the ADR are not implemented and must not be used by automation.

## Expected behavior and abort

Unknown/stale HALT, wrong owner/session, expiry/replay, missing lease, excessive
observer rights or insufficient monitoring deny before HTTP. Response loss and
401/403/429/5xx do not retry or restore authentication. Post-read integrity is
attempted after session/HALT/isolation loss; unsafe results are discarded.
Counters refer to fake peers and synthetic fixtures only.

Disposable tests intentionally demonstrate an existing writer surviving NOLOGIN,
privileged alternative connections, transient write/restore and permission drift.
Their expected findings must not be relabeled as global writer exclusion. Fixture
admin mutates/drains only owned container state; never run those operations against
an existing database. A readonly observer cannot prevent other clients writing.

Stop immediately if a real endpoint, credential source, unowned database or normal
application startup is required. Do not remove a guard, install a constant integrity
witness, call the adapter directly or provision a real role to make a test pass.
Report failure without secret values. Existing fixture scopes close connections,
loopback servers, capabilities and containers; stop only owned test resources.
Do not kill unrelated processes or delete user work. No automatic second request.

## Evidence handling and shutdown

Retain sanitized counts, outcomes and source/plan fingerprints in the validation
document. Local tmp/phase135e-*.log and Maven reports contain synthetic execution
evidence and remain ignored. No raw account data, token/capability or private
integrity fingerprints belong in committed artifacts. Follow approved local
retention rather than assuming test logging proves real sink safety.

No host/container sink allowlist or immutable environment was deployed here.
Current tests prove marker redaction and known unsafe wire logging rejection;
arbitrary file/network appenders and actual sink ACLs remain unverified. Do not
claim a secure standalone process from a passing test JVM.

## Next review boundary

Review the ADR's no-DB assurance change and ownership problem first. Determine
whether the diagnostic needs global trading-state invariance or only proof of no
own mutation path. The old approval requires the former and cannot be reused for
the latter. Keep original guards until a separately authorized redesign exists.

Any future implementation scope must cover a minimal dependency artifact, legitimate
non-exporting session ownership, independently attested HALT, process/egress/log-sink
containment, and an incident/retention owner. No token restoration command, live
launcher or credential workaround is provided. Operational NO_GO, no automatic
real GET, no collateral-readiness or live-trading promotion.
