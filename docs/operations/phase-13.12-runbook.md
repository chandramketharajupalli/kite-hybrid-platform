# Phase 13.12 bounded synthetic reproduction

**Execution is stopped.** The initial diagnostic teardown failed and exceeded its
concurrency budget. Commands below describe the planned sequence, not permission to
rerun the failing fixture unchanged. Review phase-13.12-validation.md and correct
cleanup under a separately authorized bounded continuation before execution.

Start only from the verified baseline and frozen plan. Do not repair a dirty tree,
fetch, pull an alternate image or change host security policy. All prior evidence
must match its registered raw/canonical/text-normalized fingerprints first.

The base image must already be cached at the exact Phase 13.11 digest. Image identity
does not satisfy the mandatory signature criterion. Missing LSM, remapping or
signature verification keeps the result PARTIAL even when every JUnit test passes.

Use JDK21, the repository wrapper and offline dependency resolution. Test JVM UTC is
set by the unchanged module POM. Run in this order:

```powershell
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=SandboxDiagnosticsIT,JavaSandboxIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=FenceLedgerIT,IsolationLedgerIT,FenceOwnerIT,RootRecoveryIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration verify
.\mvnw.cmd -o test
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
git status --short --untracked-files=all
git diff --name-status
git diff --cached --name-status
```

The test builds COPY-only local images and runs unprivileged, network-none containers
with read-only roots and scoped tmpfs. Actual-owner positive tests share only their
disposable witness's isolated loopback namespace. No public network probe or real
credential is needed. PostgreSQL is disposable and test-only.

Do not copy a heap/core/error dump out of a container. The diagnostic positive heap
contains a fake canary by design; scan it in place and export only digest/size/presence.
Retain sanitized commands, container inspect records and failed test XML under ignored
target/phase1312 or tmp/phase1312. Owned container removal disposes tmpfs; do not
delete historical logs to hide a failure. Inspect cleanup and disk artifacts explicitly.

Reconcile Maven exit status with XML counts; stale XML from a different selected suite
is not evidence for a fresh focused run. Preserve each run's selected XML before later
runs overwrite it. Scan canaries, dumpstreams and changed/untracked text; exclude no
unexpected diagnostic generically. Rehash every baseline file and registered envelope.
Replay the new canonical payload from disk twice with sorted compact ASCII JSON,
UTF-8 without newline and allow_nan=False, excluding the envelope/self digest.

No root broad integration or Python suite is required without corresponding source
changes. No stage/commit/push, operational startup, real broker request, token access,
HALT change or automatic next phase follows this reproduction.
