# Phase 13.5F validation

Date: 2026-10-10. Engineering outcome: **PARTIAL** because the standalone full unit
suite errored twice as recorded below. Operational NO_GO. The versioned proposal
is APPROVAL_PENDING; no runtime contract or real-read authorization has changed.
Previous Phase 13.5 Stage B ZERO_CALL_ABORT is preserved.

## Baseline, protocol and scope

Initial tree clean, develop, HEAD and origin/develop both
e7868c479779636e6dc9e749a2e9440a2369e492. All requested preflight Git commands
ran before edits. No pull/fetch, reset, stash, restore, clean, commit or push.
JDK 21.0.12 and repository Maven wrapper used; Docker-owned PostgreSQL only.

Frozen plan SHA-256:
`182BE2CFBB8C3C0B8FAB8B26A35E475B96CEF1799117436C681AB51C7A94802F`.
Registered before tests/experiments; reverified unchanged. Six documents only;
no production, test, POM, configuration or schema changes. Existing synthetic
controls already demonstrate their limited claims, so no duplicate tests added.

Read current and prior source/contracts, including DB captures, private-reference
handoff, one-attempt factory, snapshot provenance, local HALT, token restore/store,
ApplicationRunner and operator gateway paths. POM review confirms the full artifact
contains Boot/JDBC/Redis/Flyway/PostgreSQL and broad application classes; plain-jar
packaging is not a minimal authority boundary. No dependency exclusion is claimed
implemented. No normal trading application or credential diagnostic was started.

## Design result and limitations

DiagnosticAssuranceContract.v1 explicitly proposes own-process DB noninterference,
not global nonmutation. Trading DB observation and all-writer requirements are
CONTRACT_CHANGE_PENDING_APPROVAL, with no edits to existing harness/integrity.
The normative admission, lifecycle, postcondition and event schema are documentation,
not executable types or an approval parser. No direct adapter bypass introduced.

Preferred C retains broker credentials within an independently isolated owner
and delegates a narrow operation rather than a token. B remains its possible JVM
deployment. A is rejected for shared trading authority. Legitimate prior interactive
authentication in that isolated owner does not yet exist as a reviewed mechanism;
it requires separate scope and cannot be obtained under this phase or old approval.
No cross-process credential channel, attestor, signing key, one-use ledger, module
or operational launcher implemented. Minimal packaging remains unresolved.

The external HALT proposal covers issuer/audience/instance scope, monotonic epochs,
freshness, challenge replay, revocation, restart and compromised issuer keys. Existing
local tests cannot validate those proposed external guarantees. Logging policy covers
all sinks/dumps with a bounded public event allowlist; current code only validates
its existing redaction and known unsafe wire loggers. LIVE_LOGGING_NOT_VERIFIED.

## Test evidence interpretation

Fresh commands use the focused six-class selector in the runbook, full
`./mvnw.cmd test`, and relevant disposable
`./mvnw.cmd -Pintegration -Dit.test=KiteEquityReadHarnessIntegrationTest verify`.
Exact completed totals are recorded in the final audit below. Prior totals are
not reused. Repeated unit runs are not summed as distinct coverage and stale
unselected Failsafe reports are excluded.

Relevant synthetic claims: wrong owner/recipient, replay/expiry/revocation/race,
HALT/session/lease changes, unavailable local HALT, exact route/method/attempt and
one execute, response loss/no retry/redirect, serialization/redaction, call-graph
exclusion, restricted observer and post-read checks. Disposable counterexamples
retain NOLOGIN's existing writer, privileged alternate writer, non-cooperative
locks and restored-state fingerprints. They demonstrate limits, not global safety.

No tests of real ownership, external attestations, deployed DB-free artifact,
complete sink allowlist or cross-process budget are claimed. No unfiltered full
integration needed: shared production auth/transport/safety/persistence unchanged.
Python source unchanged; pytest/Ruff/strict mypy not applicable and not run.

## Protected evidence and exact inventory

G1 manifest SHA remains
`56A1BD05BBBEC36F639450F2F52C491C8C5436C16094F86BCF0C2818D4F63656`.
Existing verifier checks all 18 source hashes. All 63 protected research files match
initial byte SHA-256 inventory and path-normalized HEAD blobs. Phase 12 acquisition/
evaluation BLOCK; SBIN July TEST not opened. Plan hash unchanged. No protected
artifact is rewritten. No risk/funding/CNC/reserve/notional or HALT changes.

Modified tracked files: none. Exact new/untracked inventory:

- docs/operations/phase-13.5f-plan.md
- docs/architecture/kite-db-free-assurance-contract.md
- docs/architecture/kite-authenticated-session-ownership.md
- docs/operations/phase-13.5f-decision-matrix.md
- docs/operations/phase-13.5f-validation.md
- docs/operations/phase-13.5f-runbook.md

Tracked diff/stat/name-status and production/resource/POM diffs are empty; untracked
documents are enumerated separately. No .env, credentials, migrations, safety config,
research, strategy, raw account responses or generated bytecode changes in Git.
Ignored tmp/phase135f logs/inventory and Maven reports are local synthetic evidence.
No staged files, commit or push.

## Operational decision

Real ownership and independent HALT NOT_ESTABLISHED; global DB nonmutation NOT_PROVEN;
minimal packaging/egress/logging LIVE NOT_VERIFIED. Four broker funding terms remain
UNKNOWN/CONFLICTING; collateral MIS NOT_READY. Contract APPROVAL_PENDING; revised
real account read NOT_AUTHORIZED. Operational NO_GO.

Real Kite GETs/HTTP/WebSockets/orders: 0. Real token loads/restoration/exchange and
development trading DB access/mutations: 0. Operational HALT resume/arm/permit/execute:
0. Synthetic fixtures are not operational authorizations. INR 10,000 buffered
notional unchanged; Phase 12 BLOCK, H1/H2 FROZEN, July TEST SEALED.

Next: independent review and explicit approval or rejection of the revised assurance
contract, then separately scoped ownership/packaging engineering. No automatic
authentication acquisition, real GET or live promotion. Old one-GET approval cannot
be reused for this material assurance change.

## Fresh runs and observed regression limitation

Focused selector: 136 passed, 0 failures/errors/skips. Selected integration verify:
unit phase 1508 passed, 0 failures/errors/skips; disposable suite 55 passed,
0 failures/errors/skips, BUILD SUCCESS at 2026-10-10T23:25:23+05:30.

First standalone full unit run: 1507 passed, 0 assertion failures, 1 error,
0 skipped, BUILD FAILURE at 23:27:14+05:30. Existing
JdkKiteWebSocketTransportTest.actualTransportGatewayDecoderStoreAndHealthRecoverTogetherAfterLoopbackReconnect
raised NoSuchElementException at line 84: the Awaitility callback sees CONNECTED
then calls store.latest(...).orElseThrow() before the asynchronous tick is present.
This is consistent with a timing-sensitive test condition, not proof of a production
defect or a fixed issue. The same test passed in the preceding fresh integration
unit phase. No code, timeout, exception policy or acceptance criterion was changed.
The full suite was rerun once; final result is recorded below. The failed run is
retained in tmp/phase135f-unit.log; rerun in tmp/phase135f-unit-rerun.log.

This pre-existing test reliability issue remains a separate bounded follow-up;
it is not silently repaired or represented as a passing initial run. No official
WebSocket was used: the test operates a local RFC6455 peer with synthetic sessions.

## Final results and audit

The one full rerun also failed at the same test/line with NoSuchElementException,
completed 2026-10-10T23:28:53+05:30: 1507 passed, 0 assertion failures, 1 error,
0 skipped. No further rerun was used to seek a green result. Source inspection
identifies an unsafe optional read during asynchronous polling; no fix or proof
of production correctness is claimed. Required standalone validation remains
unsatisfied, so engineering status PARTIAL despite complete design deliverables.

| Fresh run | Passed | Assertion failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Focused | 136 | 0 | 0 | 0 |
| Unit phase of selected integration verify | 1508 | 0 | 0 | 0 |
| Disposable integration | 55 | 0 | 0 | 0 |
| Standalone full unit, first run | 1507 | 0 | 1 | 0 |
| Standalone full unit, one rerun | 1507 | 0 | 1 | 0 |

Project verifier, secret scan and diff check PASS; final scan found zero potential
secret locations. Final G1/18/63/plan/gate checks pass. Git inventory remains exactly
six new documents, no tracked/staged changes; source/resource/POM diffs empty.
No production or test edit was made to force acceptance. No commit/push.
Next engineering follow-up: narrowly review/fix the existing loopback test's
optional-value polling assertion and rerun full unit validation under a separate
scope, while independently reviewing the proposed assurance contract. Neither
follow-up authorizes any real broker activity.
