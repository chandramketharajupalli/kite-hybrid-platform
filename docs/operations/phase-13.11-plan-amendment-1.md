# Phase 13.11 plan amendment 1 — measured fixture corrections

Parent plan SHA-256: b913dba5cfadc1f8d432f3fa356b55fbbee2a17cb463ebcc94030042d1279807.
No acceptance threshold or operational permission changes.

The first sandbox run failed on container-name versus resolved container-ID equality,
and on the image's empty witness directory. Resolve the exact namespace ID before
comparison; require no owner mounts and no witness private key, rather than absence
of an empty image directory. The first corrective run passed all four owner/witness
tests but failed the process-denial diagnostic's expected errno formatting. Preserve
the failed reports. Recognize explicit EPERM wording as well as numeric errno;
unknown errors or a successfully spawned process still fail. Do not accept a timeout.

Add measured cgroup limits and a sanitized uncaught-exception canary path. A separate
manual diagnostic initially failed because PowerShell parsed an unquoted JVM -D
argument; retain that failed launch and the correctly quoted diagnostic separately.

Clarify resource lifetime: each Docker command/owner invocation remains bounded to
30 seconds; the shared witness fixture spans several invocations and gets an explicit
120-second self-termination bound, with a 120-second per-test JUnit bound and finally
cleanup. Its peer must signal readiness before the witness advertises READY. This
does not enlarge the 120-minute phase budget or three-running-container limit.

The new forced-file witness is a disposable test-only fixture, independent of the
owner container's filesystem. It is not the existing PostgreSQL authority, does not
certify physical power loss, and cannot defeat rollback of its own volume/key/markers.
Existing PostgreSQL negative controls and recovery tests remain unchanged and required.
