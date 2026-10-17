# Phase 14.25 synthetic recovery policy

Only explicit synthetic EVALUATE_REPLAY requests with SYNTHETIC_REPLAY scope are
accepted. The request binds ID, run, incident, evidence hash, synthetic authority
identity, UTC authorization time, expiry and approval fingerprint. Its fingerprint
must match a separately supplied trusted approval map. Invalid, expired, future,
wrong-scope, changed-evidence or untrusted approvals fail closed.

This hash-based test contract is not a real signature/custody system. No live
recovery authority is created. Tests explicitly supply synthetic approvals; the
library never creates approval automatically. Valid approval permits evaluation
only and cannot clear HALT. No clearance procedure exists in this phase.

Paper HALT blocks subsequent session advancement and new entries; unresolved
positions remain represented. Alert acknowledgment cannot clear it. Missing,
corrupt or incompatible evidence cannot authorize recovery or silent repair.
Local hashes do not resist complete authority rollback; independent durable
rollback authority remains a prerequisite for operational readiness.
