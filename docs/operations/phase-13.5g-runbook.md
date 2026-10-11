# Phase 13.5G synthetic review runbook

Version 1, 2026-10-11. Documentation and existing synthetic/disposable tests only.
This is not an operational launcher. Stage B ZERO_CALL_ABORT is preserved.
Do not run normal application startup, Maven exec, auth diagnostics or a live owner.
Do not load .env, tokens, existing databases or real account data.

## Reproduction

Require the registered clean baseline before starting a new review; the completed
review itself leaves six new untracked documents. Never reset/stash/clean/restore
to manufacture the baseline. Verify G1 exact SHA, 18 registered text hashes and
63 protected normalized HEAD blobs; retain raw byte hashes for final comparison.
Use JDK 21 and repository wrapper, uv and Docker already installed. Inspect test
targets first: literal loopback peers, synthetic sessions, fixed clocks and the
Testcontainers-owned PostgreSQL only. Full unit tests include synthetic loopback
WebSocket peers, never real Kite streaming.

```powershell
.\mvnw.cmd '-Dtest=KiteEquityReadHandoffTest,KiteEquityReadIsolationTest,KiteEquityReadHarnessTest,KiteEquityReadRequestFactoryTest,ControlledEquityReadArchitectureTest,RuntimeTradingHaltTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration '-Dit.test=KiteEquityReadHarnessIntegrationTest' verify
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
git status --short
git diff --stat
git diff --name-status
```

Run builds sequentially so Maven reports cannot overwrite a concurrent run. Record
actual totals and timestamps per command, not stale Failsafe XML or historical
phase counts. Integration verify repeats the full unit phase; do not sum overlapping
executions as unique tests. Full unfiltered integration is unnecessary for six
documentation additions; it becomes required if shared production transport,
security/authentication/runtime/persistence changes under another scope. Python
source unchanged: pytest/Ruff/strict mypy are not applicable here.

Official budget tests reserve request objects without executing them. The synthetic
handoff rejects official-origin clients. Do not weaken those controls or substitute
a constant integrity witness. Existing tests do not execute a v2 service or certify
cross-process one-use, live HALT, independent session provenance or minimal packaging.
See the decision matrix for exact U/I/D scope.

## Failure and artifact handling

Unknown identity, time, HALT, scope, logging, isolation or observer rights means
deny. After a synthetic attempt, error/timeout/response loss spends the attempt;
postcheck failure discards result. Never retry or repair state to obtain acceptance.
Keep failed runs and reruns distinct. Stop if any check would require a real
credential, real broker endpoint, existing DB connection or guard bypass.

Only test-owned processes, loopback peers and disposable containers may be closed
by their fixture lifecycle. Do not terminate unrelated services or revoke real
roles. Inspect generated artifacts and Git untracked paths. Ignored tmp/phase135g/
holds sanitized synthetic build logs and protected-file hash inventory; target/
contains Maven reports. No real payloads or secrets belong there or in deliverables.
No generated artifacts are staged; no commit or push.

Fingerprint procedure: SHA-256 exact UTF-8 file bytes of the frozen review plan
and finalized v2 proposal, recorded in validation; include companion owner-boundary
hash. Do not embed a file's own digest into itself. Recheck plan and proposal after
validation. No criterion edits after test results; version and explain any future
revision instead of silently replacing this proposal.

## Future gates, not authorized actions

First obtain independent explicit accept/reject decisions from security/authentication,
runtime/DB, packaging/logging and user/operator on the exact v2 digest and removal
of global invariance. Then separately scope implementation and synthetic validation
of owner-originated auth, durable one-use ledger, independent HALT and minimal
package/sinks. Any actual authentication or deployment needs separate authorization
and independent evidence; neither follows from contract approval.

Only after separate live certification could an explicit new authorization name
one GET https://api.kite.trade/user/margins/equity under the revised contract.
The previous approval cannot be reused. This runbook provides no command for that
operation. No retries/redirects/supplementary endpoints, automatic renewal or
trading promotion would follow even from an authorized observation.

Stop after reporting. Operational NO_GO, real-read authorization NONE, MIS NOT_READY,
HALT/execution fencing unchanged, INR 10,000 full buffered-notional ceiling unchanged,
Phase 12 BLOCK, H1/H2 FROZEN and SBIN July TEST SEALED.
