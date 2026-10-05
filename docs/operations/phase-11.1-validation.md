# Phase 11.1: Kite historical timestamp certification

**PHASE 11.1 = CERTIFIED_AND_CONTROLLED_ACQUISITION_VALIDATED.**
One completed SBIN minute session passed canonical ingestion and repeat-fetch
PostgreSQL replay in a disposable research database. This is a bounded pipeline
proof, not a retained research corpus, full vendor-quality certification or
permission to trade.

## Certification decision recorded before canonical acquisition

Access/review date: 2026-10-05. Baseline: `develop`, HEAD and local
`origin/develop` both `2b3e0d1d8cc89bdc8089a2522cafee3726bcb83b`, initially clean.

**TIMESTAMP_SEMANTICS = INTERVAL_START; CERTIFIED = true.**
Evidence classification: **EMPIRICALLY_VERIFIED**, supported by authoritative
API examples and session/calendar evidence. This is a bounded certification of
Kite NSE cash one-minute timestamps, not a claim that the primary API reference
explicitly specifies the convention or vendor finality.

The isolated raw probe at 2026-10-05T16:13:47Z requested NSE:SBIN for
2026-07-30 09:14 through 15:31 Asia/Kolkata. It returned 375 unique timestamps,
zero duplicate timestamps, first `2026-07-30T09:15:00+0530`, last
`2026-07-30T15:29:00+0530`. No canonical bars or research rows existed at this
checkpoint. The broad request did not censor the possible first/end labels.

The independently established regular session is [09:15,15:30): 22,500 seconds
/ 60 = 375 minutes. A minute-end convention would label 09:16 through 15:30;
the observed 09:15 through 15:29 labels instead match interval starts. This is
an empirical inference, not proof of every tick's boundary assignment. Canonical
bars use [start,start+60 seconds), and research queries exclude unclosed bars.

## Sources and limits

All links accessed 2026-10-05:

- [Kite historical API](https://kite.trade/docs/connect/v3/historical/):
  documented GET route, minute interval, offset timestamps and OHLCV shape.
  Its example returns both exact `from` and `to` labels. Primary prose does not
  explicitly define start/end timestamps or guarantee range inclusivity.
- [Kite rate limits](https://kite.trade/docs/connect/v3/exceptions/#api-rate-limit):
  historical limit is three requests/second; this review uses no retry.
- [Zerodha 2026 trading holiday calendar](https://zerodha.com/marketintel/holiday-calendar/):
  no July trading holiday. July 30 is a Thursday; the decision uses the published
  calendar, not weekday alone. Settlement holidays are distinct.
- [NSE market timings](https://www.nseindia.com/resources/exchange-communication-holidays):
  regular cash hours 09:15–15:30, subject to calendar/session exceptions.
  The dynamic holiday table did not render in the fetched page. The discovered
  CMTR71775 PDF could not be retrieved and is not treated as read evidence.
- [Zerodha closing-auction explanation](https://zerodha.com/z-connect/general/everything-you-need-to-know-about-closing-auction-session-cas):
  the change for eligible equities starts August 3, 2026. July 30 deliberately
  precedes it. Do not apply this session's 375-bar expectation to later CAS dates.
- [October 3 forum clarification](https://kite.trade/forum/discussion/16272/clarification-required-historical-5-minute-candle-semantics-at-nse-15-15-cas-boundary):
  `salim_chisty` explicitly describes start labels and subsequent-interval ownership
  at the boundary. However, the [public profile](https://kite.trade/forum/profile/salim_chisty)
  exposes only Member, so staff attribution was not independently established.
  This is corroborating forum evidence, **not** the authoritative basis of certification.
- [Unfinished-candle discussion](https://kite.trade/forum/discussion/14764/timestamps-on-1-minute-candles-are-wrong):
  Matti recommends excluding the unfinished candle; the user's start-label
  explanation is not promoted to an API guarantee.

## Read budget and isolation

Initial budget announced before broker access: profile GET <=1, instrument-master
GET <=1, minute historical GET <=3 (raw certification, acquisition, replay).
The namespace defect below ended the first harness. After its deterministic
reproduction/fix and passing synthetic/disposable checks, the budget was explicitly
revised **before further calls** to profile <=2, instruments <=2, historical <=3:
**seven total**. The new harness needed fresh profile/reference observations;
the raw certification probe was not repeated. This was a separate controlled
acquisition after a code correction, not automatic provider retry or repeated
experimentation. No other broker routes, polling, retries, auth exchange or
WebSocket. A local checkpoint separated raw observation from normalization;
it is not a production setting.

The explicit Java harness reuses production session/profile/instrument/transport
adapters without Spring startup. HTTPS origin/method/path/query and call counts
are allowlisted before dispatch. Its local HALT starts and stays HALTED, with
emergency stop true and execution/operator/live-test/live/streaming flags false.
Token save/clear and authentication exchange throw. Development DB connections
enforce server-side default_transaction_read_only plus JDBC read-only transactions;
no Flyway runs there. Token fingerprint is retained only in memory for equality.

Initial token metadata: one row, issued 2026-10-05T10:08:26.720571Z,
expires 2026-10-06T00:30:00Z, locally usable. Profile validation then established
authenticated/tokenAvailable/initializationReady and session identity available.
SBIN resolved once: platform ID `050f94dd-e639-364f-97a6-595594de6543`, NSE/CASH,
enabled universe membership once, lot 1, tick 0.05. Broker token is not recorded.

## Acquisition outcome and reproduced defect

After the certification checkpoint, an isolated Testcontainers PostgreSQL 17.6
database migrated and validated through research V11. The first canonical
ingestion then failed before sending another broker request. Source inspection
found that `KiteHistoricalAdapter` checked mapping namespace `KITE`, whereas the
real CSV mapper uses `KiteBrokerIdentity.BROKER_ID`, whose value is `ZERODHA`.
The original synthetic adapter fixture used KITE and masked this integration gap.

A new test using the actual production CSV mapper reproduced
`REFERENCE_UNAVAILABLE` with zero historical HTTP requests. The fix uses the
shared constant; another test rejects the KITE authentication label as a mapping
namespace. No alias acceptance, remapping, reference-freshness relaxation or
instrument identity changes were introduced.

The first harness ended after that failure. Its coarse local catch emitted
REVIEW_UNAVAILABLE; the subsequent synthetic reproduction establishes the
specific failure. No automatic retry occurred. A second, independently bounded
acquisition harness used the corrected adapter after the budget revision above.
It refreshed authentication/reference, required the same SBIN platform identity,
and issued only the two planned canonical acquisition/replay GETs.

Actual broker requests: **7 total**: profile 2, instruments 2, raw minute
historical 1, canonical minute historical 2. Order routes/mutations/submissions:
**0**. WebSockets: **0**. Raw responses stayed in memory. Only bounded evidence
was reported; canonical rows were written exclusively to disposable research PG.

## Real canonical dataset and replay

Session: 2026-07-30, [09:15,15:30) Asia/Kolkata, UTC
`[2026-07-30T03:45:00Z,2026-07-30T10:00:00Z)`. Provider requests:
`from=2026-07-30 09:15:00`, `to=2026-07-30 15:30:00`, interval minute,
continuous=0, oi=0. Instrument/source: the same platform ID above, NSE:SBIN,
KITE, `v3-minute-start-20261005-no-local-adjustments`.

| Evidence | Actual result |
| --- | --- |
| Expected, derived from confirmed session duration | 375 |
| First acquisition received / accepted / inserted | 375 / 375 / 375 |
| Duplicate timestamps / conflicts / missing / unexpected | 0 / 0 / 0 / 0 |
| Canonical first / last start | 03:45:00Z / 09:59:00Z |
| First bar OHLC, volume | 1013.5 / 1014.9 / 1007.6 / 1009; 108421 |
| Last bar OHLC, volume | 1024.2 / 1025.4 / 1023.1 / 1024; 41910 |
| First ingestion timestamp | 2026-10-05T16:23:47.377353Z |
| Immediate real re-fetch replay inserts / existing | 0 / 375 |
| Original complete bar rows and first provenance | Unchanged by exact row comparison |
| Canonical ingestion hash vs ordered query vs re-fetch | Equal |
| Query at 09:17 IST | Exactly 09:15 and 09:16; 09:17 excluded |
| Research trading/auth rows | 0 |

Content SHA-256:
`4028d2721ce80b118ae358c8c2d39b61349e479204e14812f5d2825dfd676610`.
Calendar SHA-256:
`3b5e1ec84994bb1fdd824a047ed2064aeef54be78be3d9638b6610d5dd34e1ac`.
Reference SHA-256:
`43a1466500ee1d0d398f5aa565e174f7779ff8c020d2ba119c1c68aa9c9ed673`.

The calendar input is retained exactly here: version `phase111-20260730-v1`;
source string
`https://zerodha.com/marketintel/holiday-calendar/;https://www.nseindia.com/resources/exchange-communication-holidays;phase-11.1-validation`;
one map entry `2026-07-30 -> EXPECTED_SESSION, sessions=[09:15,15:30)`.
All other dates are UNKNOWN. No permanent holiday calendar was installed.

The repository manifest and provenance were verified in the running isolated
database. The dataset cutoff was captured immediately after first ingestion;
the decision cutoff was session end. `replay(manifest)` returned the identical
dataset and original provenance after re-fetch. The exact dataset-cutoff instant
was not emitted outside the harness. Consequently this report's hashes/samples
are proof evidence, not an exported runnable dataset manifest. The disposable
database and full canonical dataset were destroyed after validation. A future
retained research dataset must export the complete manifest/calendar and pin
the dataset, rather than treating this report as a backtest input.

## Preservation evidence

Before/after both real harnesses (16:13:43Z–16:15:05Z and
16:23:33Z–16:23:50Z), nine development-table counts
were unchanged: orders, order_idempotency, risk_decisions, reconciliation_decisions,
reconciliation_trades, strategy_evaluations, execution_authorizations and
kite_login_attempts remained zero; kite_access_tokens remained one. The token-row
fingerprint was unchanged. Issued/expiry metadata remained as recorded above.
Both evidence reads verified `transaction_read_only=on`. No development migration,
write, order creation, token save/clear or auth exchange occurred. `.env` SHA-256
was unchanged (digest not emitted). Temporary settings were child-process-only.

The isolated local HALT was HALTED at every broker call and on exit; no real
application server/execution service was started or modified. Production-runtime
HALT was not resumed or queried: this harness uses its independent fail-closed
HALT guard. Research PostgreSQL schema writes are distinct from real development
DB mutations, which were **0**. No arm, execution, permit or authorization.

## Range, cutoff and reproducibility contract

The official example characterizes exact from/to labels as inclusive. The broad
real probe does not independently certify inclusivity because its endpoints lie
outside continuous trading. The adapter explicitly requests through `to`, keeps
the exact `from` bar and discards only a bar starting exactly at `to`; any other
out-of-window bar fails. Canonical windows remain [from,to). Exact endpoint
filtering is tested with a fake response, not claimed as a second real probe.

Canonical timestamps remain UTC, with Asia/Kolkata for provider/session conversion.
Strict calendar/offset/shape/decimal/trailing-JSON parsing remains intact. The
completed-bar rule is end <= decision cutoff: 09:17 admits 09:15 and 09:16, not
09:17. It does not certify provider publication latency or revision finality.

The real review harness checked full quality and duplicate timestamp evidence
before its intended persistence. The generic Phase 11.0 ingestion service still
returns quality evidence and can store incomplete/unknown-session data; callers
must not promote such datasets to backtest-ready status. No silent repair.

Identical replay keeps original bar rows and first-observation provenance.
Changed values produce CONFLICT and roll back the chunk; no overwrite/retry.
Content fingerprints exclude request/ingestion timing and DB IDs. Research
consumers must retain both cutoffs, the exact calendar map/source/version, the
source normalization version and content fingerprint, then verify `replay`.
The existing API supports those requests but is not an immutable dataset-version
catalog or proof that corrected historical prices were known at decision time.

No corporate-action-adjustment or historical symbol-continuity claim is made.
Current identity does not prove historical mapping across renames, delistings or
token reuse. The platform makes no local price/volume adjustments; future long
ranges need explicit vendor adjustment/availability and historical-identity policy.
No strategy, indicator, backtest, optimization, paper engine or execution added.

## Changes and validation

Production changes are confined to `KiteHistoricalAdapter`: shared canonical
broker mapping constant, certified minute-start production factory, and a
source version identifying this normalization convention. Domain, repository,
shared transport/session, migrations, config, risk, valuation, quantity sizing,
HALT, execution, reconciliation and token-store implementations are unchanged.
Normal trading Flyway still uses only `db/migration` through V10; V11 is opt-in
under `db/historical` for an isolated research datasource.

New authentication regressions cover 200/400 TokenException and 401/403:
historical failures preserve token/session identity and generation, leave HALT
unchanged and issue one GET without retry; ordinary profile failures still
invalidate the session. Tests use synthetic credentials and literal loopback
origins. Architecture rules continue to forbid historical execution dependencies.

`KiteHistoricalPipelineTest` combines the production CSV mapper, fake GET-only
transport, historical adapter/service, and disposable PostgreSQL. Its synthetic
full-session response includes the exact endpoint overlap. It checks quality
before persistence, exact decimals, ascending rows, immutable replay/hash,
provenance, decision cutoff, zero trading/auth rows and unchanged HALT/session.
Existing repository tests retain conflict rollback, concurrency, restart,
manifest mismatch and default-V10/research-V11 isolation coverage.

Checks ran in child test processes with deployment/JVM environment variables
cleared; no dotenv helper or real credentials were loaded by automated tests.

| Check | Result |
| --- | --- |
| Namespace reproduction before fix | Failed as expected: REFERENCE_UNAVAILABLE, zero HTTP |
| Focused historical unit/architecture | 40 passed, zero failures/errors/skips |
| Focused `-Pintegration verify` | 8 passed, zero failures/errors/skips; 7 repository + 1 pipeline |
| Full unit/architecture | 1195 passed, zero failures/errors/skips |
| Project verifier | PASS |
| Secret scan | Final PASS: 695 text files, zero findings |
| Diff whitespace check | PASS |

Commands:

```powershell
.\mvnw.cmd '-Dtest=HistoricalDataTest,HistoricalArchitectureTest,KiteHistoricalAdapterTest' test
.\mvnw.cmd -Pintegration '-DskipUnitTests=true' '-Dit.test=PostgresHistoricalBarRepositoryTest,KiteHistoricalPipelineTest' verify
.\mvnw.cmd test
uv run python scripts/verify-project.py
uv run python scripts/check-secrets.py
git diff --check
```

The full 500+ execution/database integration suite was not repeated in this
phase. The full unit/architecture suite and focused PostgreSQL/transport pipeline
checks cover these changes; Phase 10 execution implementations were not edited.

Local ignored evidence: `tmp/phase111-token-review.log`,
`tmp/phase111-historical-review.log`, `tmp/phase111-namespace-reproduction.log`,
`tmp/Phase111HistoricalReadOnlyReview.java`, `tmp/Phase111CertifiedAcquisition.java`,
`tmp/phase111-certified-acquisition.log` and the phase111 test logs. The local
checkpoint file was removed after the harness exited. These local artifacts
are not a scheduled process or a second production acquisition surface.

## Final Git inventory

Branch remains develop, HEAD and origin/develop remain the baseline above.
No staged changes, commit or push. Five tracked files modified and two new
files; only one production Java file changed. `git diff --check` is clean.
Tracked-only `git diff --stat`: 96 insertions, 19 deletions (new files excluded).

```text
 M README.md
 M apps/trading-core/src/main/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteHistoricalAdapter.java
 M apps/trading-core/src/test/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteHistoricalAdapterTest.java
 M docs/architecture/historical-market-data.md
 M docs/operations/README.md
?? apps/trading-core/src/integrationTest/java/com/kitehybrid/platform/broker/infrastructure/kite/KiteHistoricalPipelineTest.java
?? docs/operations/phase-11.1-validation.md
```

Remaining research limits: minute-only scope, no guaranteed vendor finality or
historical publication-time evidence, session/calendar exceptions requiring
explicit date/instrument evidence, no historical identity/adjustment certification,
and no retained dataset/version catalog. These are distinct from the successful
bounded timestamp, ingestion, quality, persistence and replay checks here.
