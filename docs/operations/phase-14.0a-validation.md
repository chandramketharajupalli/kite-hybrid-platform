# Phase 14.0A committed evidence hash reconciliation

Classification: PHASE_14_0A_COMMITTED_HASH_RECONCILED. Evidence-only additive attestation.

Baseline: develop; HEAD and local origin/develop `2c94ad5808e35f750573ba1d3b5966bca52ac1d4`. Initial worktree/index clean; no fetch.

Exactly 13 Phase14 paths differ between original raw observations and committed blobs. All 13 differences consist only of CRLF-to-LF replacement. Original manifest, CSVs, source, frozen research, governance, .gitattributes and Git configuration were not edited.

Normalization is byte replacement `0D 0A -> 0A` only, followed by SHA-256 over file bytes (no Git object header). No decoding, trimming, BOM removal or newline insertion. Bare CR is rejected for these paths. Working-tree raw hashes equal original manifest hashes. Committed, normalized and isolated-checkout hashes are equal.

## Exact hash ledger

| Path | Original manifest = working-tree raw SHA-256 | Committed blob = normalized content = clean-checkout SHA-256 | CRLF pairs |
|---|---|---|---|
| `apps/strategy-engine/src/strategy_engine/phase14/engine.py` | `673cba69e9270a458774c7a12176d75f9babb227a7e5ab768ce9697e357fbb98` | `3f9f95d8dd66132fdd940aee6817f030a658f5fda976fc43daec5ba349a5bc27` | 481 |
| `apps/strategy-engine/src/strategy_engine/phase14/reporting.py` | `d3e6f0f21f403f52c44800a8f44dcdc0ede52e3bbcd9911e3e6aef745e8e8505` | `3372321a511573cafe03ea0a167125dbf55e1e89179692103a3177a431150964` | 198 |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/historical/application/HistoricalBarRepository.java` | `eb086bfb10a1472752ab9578f023b33d8dc237d36936e63c012a2e2bf9677439` | `5b32e7d79b2644d2beb6060f05c522dda2fe56d735e4fe0a12e215ee14f082e7` | 77 |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/historical/application/HistoricalDataIngestionService.java` | `0eeb3799de5cc6931938e1f226f2f287a51286283ee34e0b11fcd72580103223` | `7d3041f87addd0f5c19c3caad4abb53ae32011af92b2a7cc35a9921683baa4c9` | 75 |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/historical/infrastructure/HistoricalResearchExporter.java` | `2c888f21151195bc272ac06248653af26324f0fa0fb07440d99ceeaffe28cd3e` | `74bf6c0f846bb315e86a164ddd088dfc5a56df6ad959faa73b13e83a07668a7b` | 90 |
| `apps/trading-core/src/main/java/com/kitehybrid/platform/historical/infrastructure/PostgresHistoricalBarRepository.java` | `204aa6d0077e0c41882d9ded62b9ddb59010627458e3867d084d96840059f004` | `dd4e5993b9422cbe75baab3d23c3165099fd5ed5c88e65caef12cd22a995da1f` | 93 |
| `apps/trading-core/src/main/resources/db/historical/V12__five_minute_research.sql` | `951737e90df4f95de4dd82a3acf6595c79e0c327d9170f9bfe1089c0a2716f4a` | `23effde631582959bb000b16b671dfa2cf416cb20e7f4e07da8b799d6d22af1d` | 13 |
| `config/universes/phase14_unique_universe.csv` | `4345207d985672c34e79347eafb13475ed8c7fe5f440914b25ac0999465c3729` | `1f5846c7caa4a0d7c265d054b90e292b16f2d1e0159ee87e27281878b2c2e16d` | 1116 |
| `input/phase14/phase14_removed_duplicates.csv` | `ae6d0c8a363959b6e6b9a5f62e1487b1caf77321dd2be981ba08136c40488300` | `0852914e724fd0018ffc046c9b24eba7af037b7bdaf9dee637c2493ee8e8586b` | 26 |
| `input/phase14/phase14_unique_universe.csv` | `4345207d985672c34e79347eafb13475ed8c7fe5f440914b25ac0999465c3729` | `1f5846c7caa4a0d7c265d054b90e292b16f2d1e0159ee87e27281878b2c2e16d` | 1116 |
| `input/phase14/universe(1).csv` | `bec552ae3c23c0f22b8980cb0c933b300de53d475efab07c4ddd22de3c9a02ca` | `dc6cd8f4b0228dff3bd0483cd664d6e905d17aa8cb32fe24ed6e798e8bd111b4` | 1141 |
| `research/phase-14.0/generation-1/instrument-resolution.csv` | `eb02d1527dd5d414e6e29cb7037ff538349b0d07616c88d956139f0c64d73612` | `8d6aace2cb75acd8d3db8ce10e026dc053d5827d6a2990620ffa8039056087b2` | 1116 |
| `research/phase-14.0/generation-1/stock-strategy-results.csv` | `cf7eb89173d8d84d9f6f76ae6c4c7b6567f1fe55f382c70121c6ad87fc60c11e` | `76a146cc7610ce4810caeb2aced844db0b8e38a7ee6bbc5d8575bdb5439e1d0a` | 2231 |

## Validation

- Original manifest envelope and all 978 working-tree hash entries: PASS.
- All 979 pre-existing tracked files unchanged: PASS.
- Protected checks: 63 research, 25 K/L, 17 plans, 8 Phase13.20 documents, 18 frozen normalized sources, 15 parent envelopes, 11 governance envelopes: PASS.
- Isolated clean checkout: all 54 Phase14 paths equal committed blob bytes; exact 13-file normalization check PASS. Two independent re-read passes: PASS.
- Working tree, committed blobs and isolated checkout each reconcile 1,140 source rows, 25 exact duplicates, 1,115 unique enabled NSE symbols; original order and all six ampersand symbols preserved.
- Java/Python/integration suites: NOT_RUN, evidence-only changes. No historical test totals reused.

The original preservation function was executed in memory with only its baseline variable bound to the verified current commit; source and hash constants were unchanged.

Fixture retained outside the repository: `C:\Users\CHANDR~1\AppData\Local\Temp\kite-phase140a-93tj8415\checkout`. A separate temporary Git index was used; no hooks or fixture code ran.

## Remaining limitations

Original raw hashes describe original CRLF bytes. They are not replaced by normalized hashes. Future verification must explicitly validate this additive record for these 13 paths. The unchanged historical standalone verifier still pins its old baseline and performs raw-only checks; this reconciliation does not make that script pass on a fresh checkout.

No genuine instrument master, historical candles or trading authorization is established. Operational NO_GO, Stage B ZERO_CALL_ABORT, Phase12 BLOCK, H1/H2 FROZEN, July SEALED, buffered-notional ceiling and pending reviews remain unchanged. Zero real Kite requests, credential access, orders, staging, commits or pushes.

Machine-readable evidence: [reconciliation.json](../../research/phase-14.0a/generation-1/reconciliation.json). File SHA-256: `383b4d0ce55b15b6733e49a3f75d1c00f3356b7cfa90b08b5169c1b498c7889a`.
