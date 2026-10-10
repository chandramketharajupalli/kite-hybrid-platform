# Phase 13.5F synthetic-only review runbook

No real launcher, token loader, authenticated custody service or v1 runtime exists.
The six documents are proposals and evidence records. Do not invoke normal Spring
startup, OperatorConsoleApplication, KiteRestDiagnostic or Maven exec: those are
not this synthetic workflow. Do not load .env or point tests at an existing DB.

## Reproduce existing control evidence

Use JDK 21, repository wrapper, uv and Docker. Inspect fixture targets: literal
loopback HTTP and Testcontainers-owned PostgreSQL only, synthetic session values.
Run from repository root:

```powershell
.\mvnw.cmd '-Dtest=KiteEquityReadHandoffTest,KiteEquityReadIsolationTest,KiteEquityReadHarnessTest,KiteEquityReadRequestFactoryTest,ControlledEquityReadArchitectureTest,RuntimeTradingHaltTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration '-Dit.test=KiteEquityReadHarnessIntegrationTest' verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

These tests exercise the old contracts, not an implemented v1. They verify owner/
recipient/replay/expiry/epoch/lease denial, strict method/path and one execute,
response-loss/no retry, redaction/serialization and dependency call exclusions.
Integration verifies restricted observer, drift, post-read checks and other-writer
counterexamples. It is intentionally possible for another fixture writer to act;
matching restored snapshots do not prove global nonmutation.

No external attestation signature, cross-process custody, deployment-wide budget,
minimal classpath or complete sink allowlist is newly tested or implemented.
Do not report these as PASS. Official budget reservation tests never execute a
real request, and synthetic handoff still rejects official-origin clients.

## Failure handling and retention

Missing or wrong owner/session/HALT/lease, unsafe logging, observer visibility or
excess rights means zero-call denial. Response loss/auth/rate/server error spends
the attempt with no retry/restoration. Post-read failure discards observation;
never repair by writing state. Test scopes close only owned peers/connections/
containers. Do not kill unrelated processes, revoke real roles or delete user work.
Stop if a real credential, existing DB, official dispatch or guard bypass is needed.

Retain only sanitized counts/outcomes/source references. Ignored tmp/phase135f-*.log
and Maven reports are synthetic local evidence, not live certification. Do not
commit raw balances, token/session/capability material or private fingerprints.
Actual live sink ACLs and retention are unverified; future v1 must deny without
an approved sink/retention policy. No credential-handling workaround is supplied.

## Approval boundary and shutdown

End the engineering run after reports and owned test-resource cleanup. No automatic
GET. Request independent review of DiagnosticAssuranceContract.v1's narrower
postcondition and removed DB observations. Keep production guards unchanged.
Even approval of the contract would not authorize real authentication, deployment,
token loading or a broker request. Each requires its own reviewed scope and the
eventual real read needs fresh explicit authorization. Operational NO_GO; collateral
MIS NOT_READY; no HALT resume, arm, permit or execution.
