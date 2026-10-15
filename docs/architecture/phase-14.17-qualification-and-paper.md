# Qualification and isolated paper simulation

The additive phase1417 package consumes Phase1416 Job and Dataset contracts, frozen
Phase1415 Result, registry, execution and costs. No baseline source is modified.
Qualification reuses existing chronological evaluation and publishes a full-family
view with explicit primary states and all original reasons. It never pools stocks,
changes strategy definitions or optimizes parameters.

Every qualification run binds the entire Phase1416 job, the explicit transitive local
software inventory and paper-risk fingerprint. Result rows bind dataset, calendar,
corporate-action policy, strategy, frozen qualification policy and split windows.
The underlying symbol checkpoint is verified before projection. A completion summary
is published only after all 13380 ordered keys have been emitted. Byte/hash mismatch
or incomplete partitions reject replay. Pending publication is not completion.

Paper is an offline, separate simulation of one bound pair and one isolated simulated
account. It consumes validated qualification evidence and a separate forward Dataset;
the qualification window must end before that paper dataset starts. There is no
Kite adapter, broker session, operational database or operational HALT reference.
Multiple paper runs are separate experiments, not a shared portfolio or authority to
reuse the same capital concurrently. One position at a time is supported; simultaneous
cross-strategy allocation is not inferred. Unknown correlation review blocks readiness.

Frozen execute() supplies the candidate entry/exit prices and timestamps, including
next-bar entry, slippage, stop-first collisions and EOD. Risk sizing uses entry-time
price, cash and volume. Costs are quoted for the actual simulated size. A denied
candidate does not become a fill. The frozen engine's candidate stream is conservative:
this layer does not manufacture replacement signals while the reference executor
would hold a position. Partial fills are explicitly unsupported and rejected.
Insufficient exit liquidity produces a paper halt with the outstanding simulated
position recorded, never an invented liquidation.

Each event records qualification/risk/signal/run identities, order identity, time,
state, cash, position, fill quantities/prices/fees and a previous/current hash chain.
Repeated or invalid transitions reject. Complete simulation replay must reproduce the
stored bytes. The chain detects changes under trusted local custody; it does not
provide independently anchored rollback resistance.

Synthetic numerical qualification proofs are usable only under explicit
SYNTHETIC_FIXTURE authorization with fixture-only risk limits and synthetic forward
data. They remain INSUFFICIENT_EVIDENCE for genuine historical qualification, and every
event retains the synthetic scope. Genuine-mode paper requires a genuine QUALIFIED
row and complete risk review. No authorization mode enables real trading.
