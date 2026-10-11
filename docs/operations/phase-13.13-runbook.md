# Phase 13.13 bounded reproduction

Verify develop, the recorded baseline and preserved evidence before work. Do not
repair a mismatch. Use the cached digest-pinned image; no pull, host install, daemon
change, new profile or real credential source. The frozen plan is controlling.

Inspect existing containers/volumes first. Unrelated resources consume active workload
budget but are never cleanup targets. Maximum three workloads plus pinned Ryuk.
The fixture's cooperative lock is not protection against an unrelated Docker operator.
Inspect exact labels/IDs after each run; any leak, ambiguity, budget violation or
mandatory focused failure stops broader testing. Preserve logs and perform only
verified exact-ID cleanup. Do not delete a resource by a broad name prefix.

With JDK21 and the repository Maven wrapper, execute sequentially:

```powershell
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=SandboxDiagnosticsIT' verify
# Require passing XML and verified cleanup/budget before continuing.
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=JavaSandboxIT,FenceLedgerIT,IsolationLedgerIT,FenceOwnerIT,RootRecoveryIT' verify
.\mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration verify
.\mvnw.cmd -o test
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
git status --short --untracked-files=all
git diff --name-status
git diff --cached --name-status
```

UTC test JVMs and disposable PostgreSQL are configured by the existing module.
Archive selected XML before the next run; stale reports are not fresh evidence.
Resource logs use unique target/phase1313 directories. Actual-owner records use
unique target/phase1313-owner directories; diagnostics retain unique recovery paths.
Inspect effective Java/Docker controls and configured logging destinations, not just
flags. Never export a heap/core/private key. Scan canaries without printing their
values. Retain known dumpstreams and classify only the exact benign diagnostic.

Rehash all baseline/protected inputs, retaining registered newline conventions.
Replay the manifest twice using sorted compact ASCII JSON, allow_nan=False,
UTF-8/no newline and excluding the envelope fingerprint. Intentional test corrections
have exact before-source snapshots; historical evidence remains immutable.

No passing suite can fill OCI-signature, LSM, remapping or external-sink evidence gaps.
Do not call partial results certified. No staging, commit/push, real broker call,
token access, application startup, HALT action, deployment or operational promotion.
