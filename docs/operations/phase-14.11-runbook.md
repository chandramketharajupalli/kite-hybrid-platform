# Phase 14.11 offline runbook

Use installed JDK21 and cached Maven artifacts only. Sequential commands:

```text
mvnw.cmd -o -f apps/gate-a-offline-policy/pom.xml verify
mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration -Dit.test=GateAHaltLedgerIT,GateALedgerIT verify
mvnw.cmd -o -f apps/trading-core/pom.xml -Dtest=Phase141OfflineTest,Phase142ReadinessTest test
```

No full trading application or Spring broker startup. New policy suite uses memory
fixtures; existing process suites create only scoped disposable filesystem journals,
bounded child JVMs and cleanup their own resources. One Maven run at a time. No Docker,
PostgreSQL, image pull, VM, host/network changes, deployment or real credential access.
The parser tests use synthetic bytes and no broker transport. Do not enable Gate A.

The standalone policy's three authority/validator ports have no production adapters.
Do not supply accepting lambdas as production implementations. No session token may
be loaded into these tests. Existing witness and Linux measurements remain historical.

Preservation: import verify_phase145.py with BASELINE rebound only in memory to
8753bdbef2ab699d2b47d2acd6789e8c1ba6150f, disable Python bytecode, check Phase14.6-14.10
envelopes and both supplements, then compare all baseline raw files. Never rewrite
old manifests. Replay the new canonical sorted compact UTF-8 JSON manifest twice,
rehashing every artifact and baseline file. Envelope excluded from its own hash table.
Check all new text LF and raw blob IDs against git hash-object path filtering.

Run scripts/check-secrets.py, scoped canary scan, whitespace and exact Git inventory.
Keep generated target reports/JARs ignored; record their hashes and counts in evidence.
Do not stage, commit or push. Request the missing screenshot privately before any
claim of image inspection, then use the response protocol without contacting Zerodha.
