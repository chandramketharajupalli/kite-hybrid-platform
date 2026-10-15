# Offline multi-session architecture

`phase1420.multi` composes the Phase 14.19 Engine and its existing portfolio implementation. No historical source is changed. Calendar sessions and clock commands are explicit synthetic inputs; no daemon, network service or external notification transport exists.

The run identity binds all day inputs, calendar, canonical risk policy/control, synthetic cumulative-risk limits, scheduler and transitive software hashes. Each session additionally binds sequence, previous completion-record hash and starting portfolio-state hash. Missing or incompatible bindings reject recovery.

At a validated new-session boundary, the coordinator starts its account with prior closing cash. The original risk-policy capital remains a configuration ceiling, not a fresh deposit. Existing Decimal accounting computes daily realized/unrealized P&L and fees. The outer ledger retains cumulative realized net P&L, cumulative fees, equity, high-water mark, maximum absolute drawdown and HALT. For an unresolved position, unrealized P&L remains explicit in the daily account; cumulative realized net is not presented as total marked equity return.

Daily counters reset only at an admitted session boundary with explicit synthetic reset evidence. Daily-loss reference is carried opening equity. No reset changes cumulative losses or risk limits. A Portfolio subclass adds the cumulative-drawdown guard; existing sizing, execution, fees, exposure and daily-loss logic are reused.

Persistence has two steps: immutable outcome including all scheduler reports, then immutable chained completion record referencing the raw outcome hash. A crash between them can complete the record from the stored outcome without executing the session again. A crash before outcome publication can rerun that uncommitted synthetic session. Replay checks outcomes, records, bindings, report bytes and the caller-supplied accepted head. An abandoned lock or pending publication rejects automatic recovery.

Complete local-state rollback is detectable only against a separately retained trusted head. A caller that loses or replaces both local state and its trusted head cannot prove freshness. Local hash chains alone do not establish independent rollback resistance. These are bounded single-writer guarantees, not distributed transactions.

The full declared calendar determines expected sessions; no weekday or holiday inference is performed. Missing expected sessions stop advancement with an alert. Duplicate or out-of-order requested dates reject admission. A halted or unresolved prior session cannot advance. Acknowledgments are separate immutable events and never authorize trading resumption.
