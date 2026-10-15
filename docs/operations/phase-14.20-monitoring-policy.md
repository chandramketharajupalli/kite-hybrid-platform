# Synthetic monitoring policy

All health events and alerts are offline. Severity levels are INFO, WARNING, ERROR and CRITICAL. Alert identity hashes run ID, session date, explicit timestamp, source, reason, evidence fingerprint and initial acknowledgment status. Accepted session alerts are bundled atomically with the completion record; replay compares the same bytes, suppressing duplicates and detecting missing alerts.

SESSION_STARTED and SESSION_COMPLETED are INFO events. SESSION_MISSING and PAPER_HALT_TRIGGERED are CRITICAL. Invalid ordering, corrupt journals, missing reports, policy/data/software mismatches and resource or ownership violations return a deterministic CRITICAL `Rejected.alert` diagnostic without executing another session. Rejected-call diagnostics are returned to the offline caller, not claimed as durable accepted-session alerts.

The risk/health report identifies completion, reconciliation and persistent HALT. Detailed scheduler outcomes retain missing-data, policy, EOD and portfolio reasons. Success is emitted only for reconciled completion. Calendar gaps stop advancement; there is no implicit skip policy. Unknown cumulative drawdown limits or daily-reset authorization fail closed. New limits are labeled synthetic and are not approved production capital limits.

`acknowledge` writes one immutable acknowledgment per alert; repeating identical acknowledgment evidence is idempotent. `alert_view` overlays acknowledgment state without rewriting original alerts. `acknowledgments` can verify a separately retained acknowledgment-chain head to detect lost tail history. Acknowledgment does not clear HALT or reopen a terminal run.

Risk policy remains Phase1418RiskPolicyCanonical.v2, fingerprint 3dd7a1cf99042ce027011f6a99ca1f298ab62c6254bc85e584171250a925c7d1. Causal execution remains Phase1418BCausalExecution.v1, fingerprint 2078a10ecad7df0faef638e3e6e37eb4808c4a8c33af5fb81883b427d0ed5925. Synthetic cumulative limits use Phase1420SyntheticLimits.v1 and are separately bound to the run.

No email, SMS, Slack, monitoring service or broker calls occur. Genuine mapping, authorized data, approved risk settings and genuine qualification remain unavailable. Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase12 BLOCK; H1/H2 FROZEN; July SEALED; operational NO_GO.
