# Phase 14.12 offline runbook

No deployment or acquisition entry point exists. Do not obtain credentials, contact
Zerodha, initialize a genuine acquisition store or wire a network transport.

From repository root with an already installed JDK21, run sequentially:

```powershell
.\mvnw.cmd -o -f apps/gate-a-offline-adapters/pom.xml -Pintegration verify
.\mvnw.cmd -o -f apps/gate-a-offline-policy/pom.xml verify
```

Only generated synthetic signing keys, fixed synthetic times/master rows and disposable
filesystem stores are used. Process probes are test-only, maximum two concurrently,
128MiB heap each, 30-second timeout. No DB/container/VM/network fixture needed. JUnit
owns temporary directories; never delete arbitrary paths or restore a live ledger.
Do not infer safety from a successful bare compilation: inspect the source allowlist,
runtime classes and exact dependency graph. The selected-source module must never be
co-loaded with the trading-core artifact because it contains overlapping parser classes.

Expected: valid fixture yields synthetic=true receipt and one observed response. Every
attempt remains consumed on failure after marker creation. Missing/partial/corrupt store
denies; never reinitialize for recovery. Lost ACK must not prompt a retry. A crash before
reservation has consumed nothing and emitted nothing; a separate explicit fixture run
can consume later. Persistent authority rollback is not solved by changing request IDs.

Preservation replay: validate canonical JSON envelope fingerprint (sorted keys, compact
separators, UTF8/ASCII escapes), raw artifact and baseline maps; compare all thirteen
Phase14.0A newline-only exceptions against committed blobs using the existing verifier.
Run two identical replays and inspect Git inventory, LF/clean-filter byte equivalence,
repository secret patterns/canaries. Never rewrite original manifests to make a check pass.

Compliance: keep Phase14.11 generation-2 USER_REPORTED_SENT_WITH_SCREENSHOT and eleven
UNRESOLVED topics. Original image/exact sent text and any genuine response require
private authorized custody, original hash/authenticity review and additive sanitized
evidence. Do not invent a reply or retroactively alter prior NOT_SENT proposals.

Before any future production decision: establish legitimate current HALT issuer and
owner approval, durable epoch/revocation/root custody, supported non-rollbackable authority,
credential/session provenance, exact endpoint egress/TLS/proxy policy, logging/dumps,
retention/deletion rights and scoped owner authorization. These tests grant none of them.
Gate A and Gate B remain NOT_AUTHORIZED independently. Preserve NO_GO, ZERO_CALL_ABORT,
Phase12 BLOCK, H1/H2 FROZEN, July SEALED, INR10000 ceiling and pending governance reviews.
