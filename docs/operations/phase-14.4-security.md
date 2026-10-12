# Existing controls and request-budget assessment

All Java names are under apps/trading-core/src/main/java/com/kitehybrid/platform/.

| Reused component | Observed behavior | Gate A implication |
|---|---|---|
| broker/infrastructure/kite/KiteOfflineInstrumentMaster.java | Supplied bytes only, 32MiB/hash/schema/UTF8 checks | No network, custody or retention authority |
| instrument/application/DatedInstrumentMaster.java | Observation/publisher provenance separated | Caller assertion is not genuine-source proof |
| instrument/application/OfflineUniverseResolution.java and HistoricalUniverseResolver.java | Exact matching, ambiguity/token-reuse/staleness; namespace projection | Preserve original ZERODHA values; no heuristic mappings |
| historical/application/OfflineDataVerification.java | Full ledger and quarantine reports | Reuse, no new report engine |
| historical/application/HistoricalDataIngestionService.java and FiveMinuteAcquisitionPlan.java | Existing five-minute ingestion/planning | No Gate B execution |
| broker/infrastructure/kite/KiteHistoricalAdapter.java | Production five-minute flag false | Remains disabled |
| broker/infrastructure/kite/KiteRestTransport.java | HTTPS fixed origin, redirect disabled, 10s/30s, bounded wire/decompression, no automatic retry | Generic instrument route is /instruments, NOT exact /instruments/NSE; also order-capable. Not accepted for Gate A |
| Phase14.3 acquisition-preview.json | Static pending preview, zero authorized budget | No dispatch or durable consumption implementation |

TLS uses standard JVM HTTPS trust/hostname behavior; no live handshake performed.
Exact NSE egress containment and whole-attempt45s deadline are proposed, not verified
operational controls. Generic timeouts are not proof of an overall deadline.

Existing equity-read harness/handoff tests exercise synthetic one-use budgets,
approval expiry, failed-check consumption, concurrency and logging. Their scope is
equity-margin reads, not a transferable NSE-master permission or production readiness.
No harness repurposing, endpoint expansion or launcher is added in this phase.

Future required protocol: atomically record authorization ID + exact request hash as
CONSUMED before network dispatch, deny on persistence uncertainty, prohibit replay
after restart, and never credit back uncertain/failed attempts. Verification must
also exclude every other consumer. Gate A implementation/independent attestation for
that protocol is absent. Terms/custody must be resolved before considering such work.

Storage restrictions are documented fail-closed decisions, not a newly implemented
retention scheduler. No raw file or database was created. No genuine snapshot means
there is nothing to expire/delete. Existing raw evidence is never cleanup material.
NO_GO, HALT, Stage B ZERO_CALL_ABORT, Phase12 BLOCK, H1/H2 FROZEN, July TEST SEALED,
INR10000 buffered-notional ceiling and V2/governance/technical review pending remain.
