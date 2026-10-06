# Phase 11.4 — SBIN historical corpus and walk-forward research

Date: 2026-10-06 Asia/Kolkata. **CORPUS_CERTIFIED** for canonical minute-bar
completeness/replay under the recorded calendar and empirical timestamp contract.
This is not certification of vendor adjustment policy, historical point-in-time
publication, brokerage invoice accuracy, profitability or live trading suitability.

Baseline verified before inspection/editing: clean develop, HEAD and origin/develop
`a9713184bf3a94ee37df05307674059b81bb9720` (`Add intraday strategy research framework`).
Reviewed Phase 11.0–11.3 architecture, evidence, data/ingestion/export contracts,
Python engine/features/strategies/costs/experiments, tests and AGENTS.md.

The [predeclared plan](phase-11.4-acquisition-plan.md) records sources, range,
calendar, budget, parameter grid, costs and sealed holdout before broker acquisition
or strategy evaluation. [Architecture](../architecture/historical-corpus-walk-forward.md)
describes bounds, ledger continuity, resume, fingerprints and research isolation.

## Real acquisition and replay

| Evidence | Observed |
| --- | --- |
| Instrument | NSE:SBIN, CASH, one exact current registry match, lot 1 |
| Platform identity | `050f94dd-e639-364f-97a6-595594de6543` |
| Interval | ONE_MINUTE / canonical MINUTE, interval start, UTC |
| Requested dates | 2026-02-02 through 2026-07-31 inclusive |
| Included sessions / chunks | 122 / 122 |
| Bars | 45750 |
| First / last canonical start | 2026-02-02T03:45:00Z / 2026-07-31T09:59:00Z |
| Gaps / duplicates / conflicts / unexpected | 0 / 0 / 0 / 0 |
| Excluded expected sessions | 0 |
| Actual historical GETs / maximum | 122 / 122 |
| Profile / instrument-master GETs | 1 / 1 |
| Total allowed real broker requests | 124 |
| Replay new rows / broker calls | 0 / 0 |
| Replay count / fingerprint | unchanged / unchanged |
| Acquisition UTC | 2026-10-05T19:12:00.069678900Z–19:14:24.200856400Z |
| Exported v1 artifacts | 122, 6164748 bytes total |

Content fingerprint:
`b8d362e35976f61213e1526c4eb1686e506853f391566456843faa9b8cacea52`.

Calendar fingerprint:
`22c28c857547dbc81d7796a4b083edb84b0239bbf76cc9b4cc9fcfd04eb77f3f`.

Java plan fingerprint:
`2b93e329997a206321ba45bfa0e47d271a69346a38fe749e51c02629f08f0c1f`.

Python composed corpus identity:
`702631ba2f5ef2959cdfa687f87959e9347818c5e772a2dd266b058c30ad280c`.

Reviewable evidence: `research/phase-11.4/calendar.json`, `manifest.json` and
`acquisition-summary.json`. The manifest contains exact per-shard content/artifact
pins, not raw bars. Local canonical exports/chunk plan are in ignored
`data/phase114-sbin/`. Acquisition logs are in ignored
`tmp/phase114-corpus-acquisition.log`; no raw broker response or credential dump.
The original summary's `databaseWrites=0` means DEVELOPMENT database writes;
the separate research database intentionally contains the canonical bars/provenance.

Python independently validated shared JSON schema, artifact/content/calendar
fingerprints, ordering, exact decimals, complete sessions, identity and bounds.
The July 30 canonical content hash also exactly matches the separately recorded
Phase 11.1 acquisition (`4028d2721ce80b118ae358c8c2d39b61349e479204e14812f5d2825dfd676610`).
This is a data-integrity cross-check, not a TEST strategy evaluation.
The calendar counts are derived from its windows; 375 is not assumed for every
future date. August CAS and November Muhurat are outside the selected range.
No unknown/gapped dates were silently dropped or changed after seeing strategy results.

Post-acquisition source cross-check: [Zerodha market-session documentation](https://support.zerodha.com/category/trading-and-markets/trading-faqs/market-sessions/articles/what-are-the-market-timings)
explicitly confirms the ordinary Monday–Friday rule, trading-holiday exclusions,
previous 09:15–15:30 equity session and August 3 CAS change. It corroborates the
recorded calendar; no calendar dates/windows or strategy parameters were changed.

## Safety and storage lifecycle

Real order mutations, account trading reads, WebSocket connections, execution,
arming, resume, token mutations and development DB mutations: **zero**.
The isolated guard reported HALTED throughout. Profile initialization succeeded;
no auth exchange or token save/clear path was enabled. The token remained locally
valid; internal token-row fingerprint and nine development-table counts were equal
before/after. Eight trading/login tables stayed zero; token rows stayed one.
Both development evidence transactions verified read-only mode. `.env` hash unchanged.

Dedicated local container `kite-phase114-research`, volume of the same name,
database `phase114_research`, PostgreSQL 17.6, UTC, bound only to
127.0.0.1:55414. Separate from development port 5432 and its volume. Role `research`
uses trust authentication only in this explicitly local research container; do not
expose/share it. No credentials are copied into research PostgreSQL. Explicit
research migrations reach V11; ordinary trading Flyway remains V10. No new migration.
The container may be stopped/restarted without losing its named volume. Do not
delete the volume or pinned exports to restart ingestion. Back up both for retention.
The reviewed research container was stopped after acquisition/replay; its volume
and exported dataset remain intact. Development PostgreSQL was not stopped.

`scripts/research/Acquire-Phase114Corpus.ps1 -Acquire` is an explicit opt-in local
tool requiring an existing tested Java build, JDK 21, dedicated research container
and valid reviewed token. It never starts the application, resumes HALT or creates
execution services. It uses only process-local environment and UTC. Reruns verify
pinned files and reuse complete DB chunks, without historical re-fetches; a new
profile/reference read still requires its own reviewed request budget.

Offline verification:
`uv run --project apps/strategy-engine python scripts/research/run_phase114.py data/phase114-sbin`.
Development evaluation adds `--development`; there is no TEST CLI option.
To replay the exact recorded specification, use
`--replay-spec data/phase114-sbin/walkforward-spec.json`. This preserves the reviewed
implementation identity rather than recalculating it from differently formatted
source files. The caller must use the reviewed implementation; output pin conflicts
fail rather than overwrite the previous report. Creating a changed generation with
`--development` deliberately refuses to overwrite an existing different spec.
Set process-local `PYTHONDONTWRITEBYTECODE=1` during validation to preserve tracked
legacy bytecode files. No normal test loads `.env` or invokes the real harness.

## Research result and remaining limits

Three expanding folds, four predeclared baselines, cold/flat partitions and fixed
cost/slippage scenario. Final July TEST remains sealed; no TEST metric, trade,
selection or frozen final evaluation is produced. No strategy is selected for live use.
Completed 24 evaluations (12 TRAIN, 12 VALIDATION). All twelve validation results
were negative under the predeclared cost/slippage scenario. No parameters were
changed in response and no winner was selected. This establishes research mechanics,
not profitability or live suitability. Display values below are rounded HALF_EVEN
to two decimals; the pinned JSON retains exact Decimal values.

| Baseline | Validation month | Trades | Net P&L INR | Costs INR | Max drawdown INR |
| --- | --- | ---: | ---: | ---: | ---: |
| EMA_CROSS | 04/2026 | 139 | -275.63 | 157.93 | 289.22 |
| EMA_CROSS | 05/2026 | 138 | -264.66 | 145.38 | 267.44 |
| EMA_CROSS | 06/2026 | 135 | -236.07 | 145.18 | 240.75 |
| OPENING_RANGE | 04/2026 | 15 | -17.44 | 16.95 | 58.19 |
| OPENING_RANGE | 05/2026 | 10 | -43.91 | 10.62 | 44.51 |
| OPENING_RANGE | 06/2026 | 15 | -33.89 | 15.92 | 47.53 |
| RSI_RECOVERY | 04/2026 | 34 | -79.73 | 38.61 | 81.96 |
| RSI_RECOVERY | 05/2026 | 39 | -107.35 | 41.45 | 107.65 |
| RSI_RECOVERY | 06/2026 | 43 | -76.84 | 45.90 | 79.44 |
| VWAP_CROSS | 04/2026 | 146 | -272.52 | 166.14 | 290.14 |
| VWAP_CROSS | 05/2026 | 137 | -294.72 | 144.74 | 303.63 |
| VWAP_CROSS | 06/2026 | 179 | -341.92 | 192.39 | 358.82 |

Specification fingerprint:
`613ae9b2d06879206b1dfdae2c7e14444408efc49e0002d81e50ab7559f2ace0`.
Report fingerprint:
`ae497b05d9fed7475c3d30f0f2bdc04c975c8c3c7a9057ad4c924175417de110`.
Both artifacts are retained under `research/phase-11.4/`. Report readback verifies
24 development-only records and unchanged identity. Repeated synthetic runs verify
result determinism; the full real-corpus evaluation was run once, not twice.
Database replay and offline corpus verification were repeated without broker calls.

Evaluation took 1245.24 seconds while integration tests ran concurrently. Observed
Python peak working set was at least 282,775,552 bytes; this is a sampled operational
measurement, not a formal memory ceiling. Daily exports total 6,164,748 bytes.
Independent offline corpus verification took 18.17 seconds. Dataset.v1 shard bounds
remain intact; no unbounded full-market export was introduced.

## Validation and code changes

- Java unit/architecture: 1,202 passed, zero failures/errors/skips.
- Full disposable PostgreSQL integration suite: 503 passed, zero failures/errors/skips.
- Focused historical PostgreSQL integration: 10 passed.
- Python: 123 passed; Ruff and strict mypy (20 source files) passed.
- Project verifier, secret scan and `git diff --check`: passed.

The final unit check adds rejection of missing/changed persisted provenance.
The final Python round-trip regression fixes a narrow replay parsing issue:
serialized `HH:MM:00` configuration times are accepted without rounding; nonzero
seconds remain rejected. It does not change simulation arithmetic or the recorded
report identity. The full integration run preceded the final missing-provenance
unit guard; existing valid-provenance paths are unchanged.

Production changes are confined to research: Java deterministic corpus planning
and restartable acquisition; Python bounded corpus composition, session-bounded
backtest contexts and expanding walk-forward specifications/evaluation. Explicit
local acquisition/offline tools, evidence, documentation and tests are added.
No migration, normal Flyway setting, live execution/risk/funding behavior or default
configuration changed. No commit or push was performed.

Known dividend: issuer's May 2026 filing records INR 17.35/share. No dividend
adjustment/overnight credit is invented. Public corporate-actions extraction did
not establish exhaustive absence of splits/bonuses; this remains a limitation.
Current reference plus dated SBIN issuer naming supports this recent bounded
identity review, not proof of arbitrary historical token continuity. No provider
adjustment/finality claim. These limitations must accompany later strategy claims.
