# Phase 14.25 offline reliability architecture

The additive phase1425 layer composes Phase 14.19 scheduler crash hooks, Phase
14.20 session replay and Phase 14.24 independent monitoring truth. Strategy,
indicators, risk, execution, accounting and historical results remain unchanged.
Faults operate exclusively on synthetic fixtures in external directories.

Recovery is command-journal replay with explicit synthetic authorization. Every
accepted account snapshot reconciles cash, reserved cash, marked positions and
realized/unrealized equity. Completed sessions cannot reopen; read-only replay
must preserve bytes. Missing/corrupt reports and journals are rejected without
repair. Replay failure preserves existing evidence and represents blocked recovery.

Local evidence fingerprints are integrity bindings, not cryptographic signatures
or independent rollback authority. Rolling back the complete local store and its
head to a prior consistent snapshot allows reexecution. The counterexample is
preserved, so operational certification remains NO_GO and engineering PARTIAL.
Process-level injected exceptions do not establish power-loss durability.

Monitoring success means timely matching to independent synthetic incident truth.
Successful detection of a missing alert by this verifier does not mean the source
monitor produced the alert. The inherited imperfect fixture remains incomplete.
No real Kite request, notification, daemon or operational clearance is introduced.
