# Phase 13.5E controls and evidence matrix

2026-10-10; baseline 3b7dafdc32834e963e68e4cdaacd9c8972dd037f.
Operational decision **NO_GO**. Previous Stage B **ZERO_CALL_ABORT** preserved.
New evidence means fresh fixture execution or this phase's explicit design analysis,
not newly collected live facts. Exact test totals are in validation.

| Prerequisite | Existing evidence | New synthetic proof | Independent live evidence required | Status | Failure/stop condition |
|---|---|---|---|---|---|
| Session provenance | Private-reference handoff, local synthetic auth | Fresh handoff regression; no real composition | Authentication authority: legitimate pre-existing interactive-auth origin, expiry, owner identity | VALIDATED_SYNTHETIC; LIVE_NOT_VERIFIED | Caller assertion, missing owner or expired evidence |
| Account binding | Session execution identity and snapshot owner | Synthetic replacement/wrong-owner denial | Authentication/security owner: intended account bound to existing session without disclosure | LIVE_NOT_VERIFIED | UUID/token-shaped string substituted for proof |
| Independent HALT | knownHaltedAt; startup fallback distinction | Fresh epoch/unknown/revocation tests | Runtime controller: all-instance authenticated epoch/config and freshness | VALIDATED_SYNTHETIC; LIVE_NOT_VERIFIED | Unknown, conflicting, stale or changing runtime |
| Writer exclusion | NOLOGIN/lock/drain/snapshot counterexamples | Fresh disposable counterexamples; DB-free assurance distinction documented | Deployment/DB authority: enforce all-writer exclusion if original contract retained | LIVE_NOT_VERIFIED | Any uncovered writer; no global claim from own-write prevention |
| Observer privileges | SELECT, stats, inherited/trigger/function/sequence denial | Fresh restricted-role integration | DB security owner: actual effective rights and full monitoring coverage | VALIDATED_SYNTHETIC; LIVE_NOT_VERIFIED | Missing visibility or excess privileges |
| Integrity baseline | Private row/count/column and permission evidence | Fresh drift/rollback/restoration cases | Independent observer: approved real baseline if original contract retained | VALIDATED_SYNTHETIC; LIVE_NOT_VERIFIED | Missing/drifting evidence; equal hashes never prove exclusivity |
| Logging sinks | Marker redaction, no serialization, known wire DEBUG checks | Existing behavior rerun; no sink-allowlist implementation | Host/log owner: all sink ACLs/configuration/retention/dumps and late change control | DESIGNED_NOT_DEPLOYED; LIVE_NOT_VERIFIED | Unknown/file/network sink or unsafe instrumentation |
| HTTP limits | Exact path, no retries/redirects, one execute | Fresh loopback failure matrix; no official dispatch | Runtime/network reviewer: deployed artifact, process budget and egress proof | VALIDATED_SYNTHETIC; LIVE_NOT_VERIFIED | Wrong route, reused budget, changed configuration |
| Deployment ownership | Call-graph architecture rules | A rejected; B no-DB target analyzed; C fixture rerun | Packaging/host owner: minimal artifact, nonprivileged process, environment/filesystem proof | DESIGNED_NOT_DEPLOYED | Full trading app/classpath represented as minimal deployment |
| Incident response | Fail-closed outcomes and owned-resource cleanup | Fresh failure-path fixture evidence | Operations owner: approved abort/report/containment procedures | VALIDATED_SYNTHETIC; LIVE_NOT_VERIFIED | Retry, token restoration, DB repair or automatic resume |
| Data retention | Redacted receipts and ignored synthetic reports | No real payload collected; documentation-only outputs | Data/log owner: actual sinks, access controls and bounded retention | DESIGNED_NOT_DEPLOYED; LIVE_NOT_VERIFIED | Raw account/token/capability in any artifact |
| Funding authority | V1 observed != PROVEN; four missing terms | No policy change or account evidence | Broker evidence owner: dated eligible/free collateral, cash rule/field | LIVE_NOT_VERIFIED; public cash descriptions remain CONFLICTING | Any promotion from net/utilised/synthetic balances |

All live evidence has no collected timestamp or validity interval in this phase.
Fixture execution date is not broker effective date. Future evidence must identify
source, accountable owner, account/product/runtime scope, observation/effective time,
expiry and revocation. Unknown/stale/conflicting evidence yields NO_GO.

The selected future no-DB design would change the assurance claim and require a
new review; it does not mark observer/global-isolation rows satisfied or remove
them from the existing Stage B contract. No activation flag or operational GO
mechanism is added. Collateral-assisted readiness remains NOT_READY.
