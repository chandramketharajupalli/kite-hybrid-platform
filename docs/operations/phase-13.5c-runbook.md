# Phase 13.5C synthetic prerequisite verification

ZERO real Kite requests, real token loads and development DB connections. No
normal trading-app startup, login/restore/callback, operational HALT change,
arming, permit, order, real-read launcher or automatic continuation is authorized.
Previous Phase 13.5 Stage B remains ZERO_CALL_ABORT, zero calls.

## Safe synthetic commands

From the repository root, use JDK 21, the Maven wrapper and Docker for disposable
PostgreSQL only. Do not load .env or pass credentials through command arguments.

```powershell
.\mvnw.cmd '-Dtest=KiteEquityReadHandoffTest,KiteEquityReadIsolationTest,KiteEquityReadHarnessTest,KiteEquityReadRequestFactoryTest,ControlledEquityReadArchitectureTest,RuntimeTradingHaltTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration '-Dit.test=KiteEquityReadHarnessIntegrationTest' verify
$env:PYTHONDONTWRITEBYTECODE='1'
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

The selected integration build also reruns all unit/architecture tests. This phase
changes only tests and documents, so the registered relevant disposable suite is
sufficient. If shared HALT/auth/transport/persistence/execution production code
changes, run full `./mvnw.cmd -Pintegration verify`; do not substitute the selector.
Historical full-suite counts in Phase 13.5B are not new Phase 13.5C results.

Test setup creates synthetic sessions/roles and encrypted fixture rows in container
databases. Deliberate writes, role changes and synthetic HALT transitions are fault
injection only; they are not operational activity. The child JVM writes only its
owned temporary fixture file. Cleanup is limited to owned fixtures, never unrelated
processes, databases, containers or user files. No real-origin execute is called.

## Evidence capture and triage

Keep owner/recipient/session references private. Existing authenticated local state
is required by the fixture but is classified synthetic. Bind epoch/expiry/lease;
consume once. Missing/wrong/replayed/expired authority denies before HTTP. Unknown
HALT, session replacement or lost isolation also denies; no restoration or retry.

The observer checks current effective permissions and full statistics visibility,
then privately compares token/trading row counts, content and column schema. Keep
fingerprints and rows in memory, not reports. Missing table/ACL/statistics evidence,
unexpected clients, inherited UPDATE/TRIGGER or other write privileges means abort.
Do not clear audit rows, elevate the observer or write a repair to make it pass.

After 401/403, 429, 5xx, timeout, malformed/oversized response, response loss or
midflight revocation, attempt post-read integrity, discard invalid observations
and never retry. Late detected wire/header DEBUG poisons the attempt. Close only
owned diagnostic resources. A receipt is historical provenance/time, not continuing
authority or independently verified funding eligibility.

Expected counterexamples are important results: a nonparticipant acts while a
cooperative lease stays valid; a pre-existing writer remains able to write after
NOLOGIN blocks new connections; committed write/restore can compare equal. These
prevent a false isolation PASS. They are not grounds to weaken the guards.

## Future operational review, not a runnable procedure

Use the [five-row matrix](phase-13.5c-prerequisite-matrix.md) and
[provenance/topology review](../architecture/kite-operational-session-provenance.md).
Independent live provenance, HALT, all-writer exclusion, observer/baselines and
logging proof remain NOT_ESTABLISHED. No live attestation timestamps are available.
The synthetic handoff still rejects official-origin clients.

Do not execute the older approved GET. A future step needs separate design and
operational review, precise authorization and a fresh go/no-go. Neither this
runbook nor passing tests authorizes token loading, login, live service/DB changes,
account access or trading. Keep collateral-assisted MIS NOT_READY, Phase 12 BLOCK,
H1/H2 frozen, July TEST sealed and INR 10,000 ceiling/reserve/CNC unchanged.
