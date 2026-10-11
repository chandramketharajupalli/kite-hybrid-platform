# Phase 13.10 plan amendment 1: exact test-runtime diagnostic allowlist

Parent plan raw SHA-256:
eb7ff542afd3a80284a5473301f631957d09122fadc68a3ab1811f0177f4e82d.
Original plan remains immutable. Add this numbered artifact to permitted scope.

Focused run: 20 unit checks passed; RootRecoveryIT ran 9 cases, 4 failures, no
errors/skips. The four killed children that initialized test dependencies emitted
the same three SLF4J no-provider/NOP diagnostic lines. An empty-stderr assumption
incorrectly classified this known tooling output as a containment failure. Failed
log/XML retained under tmp/phase1310; no evidence erased or hidden.

Before rerun, replace the new fixture's empty-stderr check with an exact allowlist:
empty, or precisely the observed three fixed SLF4J diagnostic lines after CRLF-to-LF
comparison. Reject any prefix/suffix/extra output, and explicitly reject the generated
witness authentication canary. Assertion failure messages must not echo captured
content. No stderr redirection/discard, logging-provider suppression, runtime change,
weakened secret prohibition, timeout increase or recovery-state assertion change.

This is test-sink classification, not external log containment certification. All
kill, retirement, replacement, peer-count, quarantine and negative controls remain.
Sandbox execution still NOT_EXECUTED; no operational permission is changed.
