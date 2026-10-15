# Phase 14.15D hardening

Frozen plan SHA256: 173ef2cadc35c5b13a22e730075fc2624dd5c7ef7fd862f7ce6e115b8ef04305.
Baseline: 1cad5f0f4d587837678e41cc3c64780a065a4edd. No strategy or execution methodology changed.

A: hardening.py defines17 canonical relative local source dependencies including package
initializers, phase14 engine/indicators/qualification, reporting/universe/import boundary,
backtest dataset/calendar/serialization and costs, and all phase1415 source. Raw bytes are
hashed in path order. Missing, duplicate, out-of-order or unexpected manifests fail closed;
static local imports and phase1415 package membership are checked. This is trusted local
source integrity, not protection against malicious code replacement or dynamic import tricks.
Checkpoint schema Phase1415Checkpoint.v2 pins inventory, software hash, full-family identity,
strategy parameters, settings, costs, dataset and provenance. Old configuration/schema,
changed dependencies or input, inconsistent result identity and corrupted bytes deny replay.

B: one family_size() =1115*6*2=13380. Frozen Policy equals the original declared thresholds,
including100 OOS/30 per fold/70% wins/60% per fold/10%drawdown/alpha0.05. Even subset runs
require an explicit verified FamilyBinding. Content-derived run IDs pin full canonical universe,
all six strategy definitions, both standard IST windows, policy, software, settings and tariff.
Partition-specific configuration additionally pins its own exact symbol/strategy/window keys.
Missing/conflicting binding, noncanonical members or altered parameters/windows are denied.
Export requires matching bound results and exact expected keys. Missing/duplicate/unexpected
results cannot receive a completion marker. A successful subset is labeled PARTITION;
FULL_FAMILY requires all13380 keys. The create-exclusive partition-manifest.json is written
only after complete export. Interrupted output files without that marker are uncertified.
Rich Result JSON now carries family_run_id and hypothesis_family_size. Unavailable report
CSV/JSON bytes are unchanged. Direct evaluate remains a research primitive; unbound results
cannot be certified/exported as a family partition. No live trading authority is introduced.

C: new tests cover exact target/stop/gap/collision/EOD prices and timestamps, final-bar denial,
invalid/missing/duplicate data, costs/slippage/limits, quarantine, unsupported OR15 sessions,
holidays/special sessions,6/12-month IST boundaries, warmup and executor prefix invariance.
Split tests alter holdout observations and compare earlier training/validation summaries
and trades; indicators cannot borrow warmup across sessions. Dated special sessions remain
valid for non-OR15 rules. No arbitrary new session cutoff or strategy definition was invented.

D: required XML receipts must exist, match SHA256, parse, contain unique nonzero complete
cases and matching suite/status totals. Accepted receipts cannot contain failures/errors/skips.
Required commands/exit evidence must exist and bind the XML hash and identifier. Inventory
IDs must match exactly. Historical failed attempts remain marked HISTORICAL_FAILURE_RETAINED,
never counted as accepted current results. Missing evidence means RECEIPT_UNAVAILABLE.
The B verifier explicitly delegates to D only when the additive D manifest exists. D verifies
immutable historical evidence plus original source snapshots and authorized before/after hashes;
it never rewrites the B manifest to pretend the hardened source was the recovered original.

Software fingerprint: d8723c4ff18797d4d9cd785fb677cc468b42aa321832d9da576108ed40bad4cf
Reference full-family run ID: 4a15ec52623c19b583dfc6400c8c3e82ad4f0efeb7c5ed0fae0f009814273d0f
This binding uses the labeled synthetic test tariff; it is not an actual market-data run.
Third-party runtime versions are recorded separately; no independent publisher authenticity,
malicious rollback resistance or physical power-loss durability is claimed.

No dependencies installed, broker calls, credentials, orders, downloads, WebSockets, HALT,
capital or infrastructure changes. GateA/B NOT_AUTHORIZED;StageB ZERO_CALL_ABORT;Phase12 BLOCK;
H1/H2 FROZEN;July SEALED;NO_GO. No staging,commit or push.
