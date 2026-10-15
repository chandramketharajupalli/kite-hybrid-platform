# Offline portfolio coordinator

Phase1418 is an isolated Python research simulator. It has no broker transport,
operational database, authentication, network subscription or live order path.
All 1233 baseline files, including Phase14.17, remain unchanged.

Request reuses the existing qualification Row, Dataset, Definition, Settings,
IntradayCostSchedule and explicit paper Authorization. Only LONG is supported;
unsupported directions reject at the contract boundary. Each pair first passes
Phase14.17's admission and frozen execution. Portfolio orchestration consumes those
validated candidate events and recalculates fees for the portfolio's accepted quantity.
It never creates a new indicator, signal rule or stop/target execution algorithm.

Ordering version: timestamp-exits-marks-canonical-symbol-strategy-signal-v1.
At a shared boundary, completed exits are processed before completed-candle marks,
then proposals and entries. Ties use canonical universe order, strategy identifier,
signal fingerprint and deterministic duplicate occurrence. Input tuple permutation
does not affect output. Future proceeds or marks cannot fund earlier entries.
All 1115 enabled symbols and S01-S06 remain eligible inputs; the 13380 hypothesis
family and independent qualification semantics remain frozen.

This is a conservative candidate-stream model. A rejected portfolio candidate does
not cause the frozen single-pair engine to generate replacement signals while its
reference position was occupied. Entry prices, gap/stop/target handling, collision
priority and EOD timestamps remain those of the frozen engine. Quantities may shrink
but cannot exceed its reference fill. Partial fills explicitly reject. Per-candle
volume participation retains the frozen research fill assumption, not a live fill
guarantee or an executable exchange queue model.

The single-writer Coordinator owns only simulated cash, reservations and positions.
There is one position per symbol across strategies. Every transition records run,
order, strategy, signal, qualification and policy identities, cash/reservation deltas,
position-count delta, fees, full account state and hash-chain predecessor. Position
snapshots include integer quantity, entry basis/fees/time, stop/target and ownership.
MARK and SESSION_END are portfolio journal events alongside the order lifecycle.

Each immutable event is published atomically to an external numbered journal file.
Resume recomputes the deterministic model and validates every accepted event in a
contiguous prefix, including its reconciled state, before appending any new event.
A prefix ending before reservation, after reservation/fill or during exit is valid;
missing interior events, edits, reordered events or pending partial files fail closed.
A completed journal may not be truncated and silently repaired. Report bytes and the
terminal anchor are bound in run-summary.json, published only after all reports.
Local custody does not establish independent rollback resistance. A corrupt run
raises a PAPER_HALTED recovery error and is not automatically repaired or resumed.

Execution is single-threaded: at most 6690 requests, 100000 aggregate input bars,
existing 30000-bar per-dataset cap, 200000 journal events and 256 MiB output budget.
Caller-supplied batches exceeding these bounds reject. This is not an assertion that
all 1115 full twelve-month histories fit in memory or a production throughput claim.
External candidate transcripts are content addressed and reused on identical replay.

Software fingerprints cover the previous 23 dependencies plus both Phase1418 modules.
Run identity binds universe, registry, policy, explicit authorization, delivery control
and all request fingerprints (qualification, data/calendar/corporate-action provenance,
settings, costs and strategy version). No operational HALT or capital limit is changed.
