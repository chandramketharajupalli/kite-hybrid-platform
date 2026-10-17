# Phase 14.26 critical-alert policy

Scope: SYNTHETIC_FIXTURE. Policy version: Phase1426CriticalAlerts.v1.

The frozen family is the ten critical incidents in Phase 14.24 ground truth fingerprint `11308eecb93adfd1c3c9cd5a3ac393eb8f1111afdd3f7d9aed2e539f664a836d`. The initial Phase 14.26 plan records the exact eight historically missed identities. Incident IDs, categories, severity, source evidence and the original 04:01:00Z deadline are unchanged. Historical metrics remain two timely detections, three false positives, eight missed critical incidents, one duplicate and one late alert.

Acceptance requires ten timely monitored alerts, zero missing mandatory obligations, and all mandatory offline tests passing. The synthetic emitter accepts events at 04:00:05Z; the independent matcher retains the original start, deadline and window. A missing event or missing alert blocks certification. A late alert does not satisfy an obligation. Duplicates and late alerts also contribute to false positives under the inherited Phase 14.24 policy; metrics must not be summed as disjoint categories.

An obligation binds incident identity, run/session, original incident evidence, expected category, deadline and ground-truth fingerprint. Matching additionally requires the accepted event fingerprint. Exact duplicate alert IDs are corrupt input; distinct duplicate emissions are classified separately. Ledger corruption, missing records, changed incident evidence, stale caller heads and changed monitoring policy fail closed. There is no silent repair.

Every unresolved mandatory critical incident requires paper HALT and blocks new entries. Assurance completeness is distinct from incident resolution: complete alert coverage does not clear HALT. Acknowledgment and resolution are not clearance authorities. This layer returns the required safety decision; existing paper-session controls remain the enforcement boundary and are regression-tested. No operational HALT is touched.

Report monitored coverage and evaluator coverage independently. Deliberately deleting one of ten alerts must produce nine timely matches, one missed critical incident, ten evaluated obligations, incomplete evidence and certification denial. Detecting that deliberate loss is a successful assurance check, not successful monitored alerting.
