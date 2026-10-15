# Phase1424Monitoring.v1

Ground truth is a versioned independently authored synthetic incident schedule. The fixture
declares ten categories: missing data, duplicate session, risk breach, paper HALT, corrupted
journal, missing EOD exit, policy mismatch, missing report, clock rollback and reconciliation
failure. It contains incident ID, run, date, severity, start/end, expected alert, deadline,
matching-window end and synthetic source identity. Unknown versions and hash mismatches fail.

Incidents sort by start and ID; candidate alerts sort by timestamp and ID. Matching requires
the same run, session date and category, with start <= emitted time <= deadline. Each alert
and incident can participate in at most one timely match. This predeclared greedy ordering
is deterministic; it is not an optimization for the highest possible score.

True positives are timely matches. False negatives are incidents without a timely match.
All unmatched emitted alerts are false positives, including late and duplicate alerts.
An unmatched alert in a timely incident window is labeled duplicate; otherwise one within
deadline < time <= window-end is labeled late. Alerts outside all windows remain unmatched.
Thus precision = TP/(TP+FP), recall = TP/(TP+FN), F1 = 2TP/(2TP+FP+FN). Undefined denominators
are null / NOT_COMPUTABLE; absent independent truth is NOT_EVALUATED.

Detection delays are emitted timestamp minus incident start for timely matches. Maximum
delay is null when none match. Acknowledgment delay uses the existing verified acknowledgment
chain. Resolution delay requires a separate sealed resolution record. Acknowledgment is not
resolution; resolution is not HALT clearance. Unresolved incidents and missed critical
incidents are explicit outputs. No notification is sent and no HALT state is modified.

Fixture effectiveness is EVALUATED_SYNTHETIC. Production effectiveness remains NOT_EVALUATED.
Independent fixture authorship is documented provenance, not independent production ground
truth or proof that a real detector will generate these alerts.
