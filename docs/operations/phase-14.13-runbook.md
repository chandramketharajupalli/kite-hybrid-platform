# Phase 14.13 offline recovery runbook

No acquisition/deployment launcher exists. Use only owned synthetic fixtures with
an existing JDK21; do not access credentials, operational stores or broker endpoints.

```powershell
.\mvnw.cmd -o -f apps/gate-a-offline-adapters/pom.xml -Pintegration verify
.\mvnw.cmd -o -f apps/gate-a-offline-policy/pom.xml verify
```

Run one build at a time. Parent test JVM256MiB; maximum two child JVMs128MiB each,
30-second timeouts. JUnit owns temporary directories; cleanup never targets repository
or operational ledgers. No PostgreSQL/container/image/VM needed or authorized.

Initialize only a new fixture. On restart reopen both histories and the attempt store
with exactly the same authorization binding. Missing/corrupt/mismatched history is
RECOVERY_PENDING: stop; do not truncate, recreate, choose the lower sequence or use
availability recovery to clear denial. CONSUMED cannot become active on restart.
Revocation/quarantine require retained trusted records; source authenticity is a
separate prerequisite. New epoch must fence old permits, not issue new approval.

For uncertainty after witness write, deny and preserve both sides. After both writes,
lost ACK remains spent. Never retry to discover whether an observation happened.
Complete-authority rollback is intentionally exercised only in a disposable negative
control; its second grant demonstrates the limit, not a recovery procedure.

Before any future production decision require the host/authority matrix's independent
custody, authentic issuer/session, network/logging containment and terms evidence.
Do not operationally initialize these synthetic records or translate fixture keys into
real HALT approval. Gate A/B remain NOT_AUTHORIZED; NO_GO.

Preservation: replay canonical manifest fingerprint and raw baseline/artifact hashes
twice; verify existing newline reconciliation against Git blobs with the retained
verifier; inspect exact additions, LF/Git byte equivalence and secret/canary patterns.
No staging/commit/push. Existing compliance generation-2 record remains user-reported,
with eleven unresolved topics and no original screenshot/body hash or broker reply.
