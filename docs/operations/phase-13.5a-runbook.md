# Phase 13.5A synthetic handoff runbook

This phase permits ZERO real Kite calls. It does not execute the earlier approved
Phase 13.5 GET. That Stage B remains ZERO_CALL_ABORT and no live response exists.
No real-session acquisition, token restore or trading startup is a prerequisite
workaround. REAL_HANDOFF_NOT_ESTABLISHED.

## Synthetic Stage A

Use the repository Maven wrapper with JDK21 and available Docker. All sessions,
marker tokens, HTTP peers and database roles/rows are disposable fixtures. The
new handoff rejects the official-origin factory. No .env is loaded or modified.

```powershell
.\mvnw.cmd '-Dtest=KiteEquityReadHandoffTest,KiteEquityReadIsolationTest,KiteEquityReadHarnessTest,KiteEquityReadRequestFactoryTest,ControlledEquityReadArchitectureTest' test
.\mvnw.cmd test
.\mvnw.cmd -Pintegration verify
$env:PYTHONDONTWRITEBYTECODE='1'
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

The focused disposable selector is
`./mvnw.cmd -Pintegration '-Dit.test=KiteEquityReadHarnessIntegrationTest' verify`.
Full integration is required for shared observer/wire changes. The child lock peer
uses only a temporary lock path, no credentials or account identity. Fixtures
release their own resources; never kill unrelated processes or clean a live DB.

Issue a capability only after fake authentication, known fixture HALT and an
independent fixture witness. Keep owner/recipient/capability references private.
Consume once for the exact GET path. Inspect does not grant future dispatch.
Owner termination/close, expiry, auth/session/HALT change or witness loss revokes.
Do not create replacement owners/capabilities to retry a failed request.

A dedicated disposable observer has SELECT, schema USAGE and pg_read_all_stats,
server read-only default, no DDL/TEMP/write/escalation/function rights. The fixture
admin provisions this role before the observer window; the harness never provisions
roles or changes transaction settings. No production role provisioning instructions
are supplied. Before/after fingerprints remain in memory and are not printed.

## Failure handling

- Missing/replayed/wrong owner, recipient, method, path or expired capability:
  deny before fake HTTP; no retries or restoration.
- Lost/stale/throwing isolation witness, HALT or session: revoke; before dispatch
  zero HTTP, after dispatch discard observation with zero further dispatch.
- Unavailable statistics, another DB session, schema/privilege drift or incomplete
  fingerprints: abort. Never lower privileges checks or clear audit rows to pass.
- 401/403, 429, 5xx, timeout, malformed body, redirect or response loss: one attempt
  maximum, no profile/login/reset/fallback. Auth rejection may invalidate memory;
  durable token rows stay untouched by the harness.
- Wire/header DEBUG: abort. Do not print credentials to diagnose the rejection.
- Integrity mismatch: discard observation; never repair database state automatically.

Close only owned synthetic resources. No HALT resume, permit issuance or consumption,
arming, order dispatch, risk-policy change or live shutdown operation is authorized.

## Future live precondition review (not implemented here)

No real launcher command is provided. The current handoff cannot run official
requests. A separately reviewed design must establish a legitimate pre-existing
authenticated session and safe same-process composition without token export,
restoration or main-app startup; intended account/token binding; known live HALT
and execution exclusion; all writers/credential holders across hosts/services/
WSL/containers/tasks; enforceable exclusion for the entire window; a verified
SELECT-only observer with complete visibility; fresh private integrity baselines;
and actual logging/tracing/dump/sink ACL and retention controls.

A process-name scan, cooperating file lock, UUID, test witness or read-only JDBC
flag alone establishes none of that. Unknown evidence means zero-call abort.
Any eventual real operation needs a fresh scoped go/no-go review against the exact
approval and request budget. This phase grants no automatic request or live promotion.
No support message is sent; the existing broker clarification draft remains unsent.
