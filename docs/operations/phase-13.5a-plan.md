# Phase 13.5A frozen synthetic plan

Registered 2026-10-10 before production edits or new-behavior tests. Clean develop,
HEAD and origin/develop dc614fa78bc4cc0c07c9209d9243826ec1faa7f7.
JDK 21.0.12, Maven wrapper 3.9.11, uv 0.12.10, Docker 29.8.0.
G1 manifest and 18-source verifier passed; 63 protected files inventoried.

## Existing evidence and trust boundary

Preserve Phase13.5 Stage B ZERO_CALL_ABORT, zero requests. Its live session,
HALT, external isolation, DB role/baselines and log sinks were not verified.
Existing wire is exact-route, single-attempt, no-retry/no-redirect; existing
harness compares session/HALT and SELECT-only database fingerprints. None of
these establish external exclusion. Authentication restore can clean durable
tokens and call other APIs: it is forbidden. No authentication lifecycle edits.

Use a package-private, unwired, synthetic-only same-process handoff. Possession
means identity of non-serializable owner and recipient objects, not a UUID string.
Trusted composition must keep those references private. No hostile code/reflection
security within one JVM is claimed. No IPC or real credential transfer is added.
Scope is fixed GET /user/margins/equity on the existing loopback factory only.
Bind session execution identity, HALT object/epoch, owner, recipient, clock,
creation/expiry and isolation lease; atomic one-winner consumption, explicit
revocation, no persistence/restart recovery. Maximum lifetime 30 seconds.
Clock rollback, auth loss/change, owner closure, isolation loss and epoch change
revoke. Failed consume burns capability. No token accessor or session export.

Isolation is a separate expiring same-process lease backed by a supplied,
independently controlled exclusion witness. Default unavailable. Disposable
fixtures use an OS file lock and competing child process plus DB observer checks;
this proves cooperating fixture exclusion only. A witness cannot certify itself
as live host/database isolation. Official wire is forbidden by this synthetic seam.
No global production lock, process killing, service reconfiguration or credential
loading. Host visibility, all potential writers and future reconnect prevention
remain mandatory unimplemented live review prerequisites.

## Allowed files and corrections

Under apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/:
new KiteEquityReadHandoff and KiteEquityReadIsolation; narrow guards in
KiteEquityReadHarness and KiteEquityReadRequestFactory if needed at dispatch;
KiteEquityReadIntegrity privilege/schema checks. No generic transport/auth/risk
approval changes. Matching unit/integration tests, existing
ControlledEquityReadArchitectureTest, four Phase13.5A documents only.
Preserve type-guarded sequence query. Harden observer denial for reachable
user-defined mutating functions/role escalation/schema privileges where verifiable;
reject incomplete evidence rather than infer safety. DB checks remain SELECT-only.

## Threats and acceptance matrix

Replay/theft: wrong owner/recipient/session/endpoint, inspect then consume,
concurrent consume, serialization/logging attempts, restart and owner termination.
Time: expiry boundary, future/rollback, expired lease. HALT: changed identity/epoch,
unknown/disabled state before request and loss during request. Isolation: competing
lock holder/process, crash release, stale witness, lost lock and incomplete host/DB
visibility; detect deterioration before dispatch and discard after dispatch.
DB: no writes/DDL/escalation/sequence/function capability, statistics visibility,
missing table/permission, schema drift, same-count changed rows, external writer,
rollback and read-only transaction violations. Fresh autocommit observations;
no claim of an atomic multi-table snapshot or prevention of external future writes.
Wire: existing loopback error matrix incl response loss, malformed/duplicate/trailing,
oversize, 401/403/429/5xx/timeout/redirect; one attempt, no restoration or fallback.
Logs: synthetic marker tests, redacted capability/result, verbose wire denial.
E2E: fake authenticated session + capability + witness + SELECT-only disposable
observer + exact fake GET + before/after integrity + unchanged epoch + replay denial.
Every missing precondition before dispatch must produce zero fake HTTP requests.
Loss after dispatch must never produce a second request or usable observation.
Architecture guards exclude startup, execution, auth lifecycle, order and token stores.

## Validation and stop criteria

Run focused handoff/isolation/harness/wire/architecture and disposable tests, full
`./mvnw.cmd test` and `./mvnw.cmd -Pintegration verify` (shared observer/harness).
Run `uv run python scripts/verify-project.py`, `uv run python scripts/check-secrets.py`,
`git diff --check`, G1/18-source and protected-file comparisons. No Python changes
planned; Python tests/Ruff/mypy only if changed. Report every failed run and counts.
No criteria relaxation. Baseline mismatch, leaked secret or inability to validate
safety blocks completion; incomplete tests yield PARTIAL, never invented PASS.

Real broker request budget ZERO. Real handoff NOT_ESTABLISHED even if synthetic
tests pass. No real launcher, .env/config/migration/risk/research changes, July TEST
access, HALT resume, operational permits, orders, DB/token writes, commit or push.
Artifacts contain only synthetic provenance and bounded outcomes; no raw payload,
credentials, account identifiers or token fingerprints. Temporary build/test output
stays ignored. Deliver plan, validation, runbook and kite-secure-session-handoff.md.
