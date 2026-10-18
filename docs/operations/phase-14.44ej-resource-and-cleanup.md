# Resource enforcement and cleanup design

Phase1444EJResourceLimits.v1 is NOT_APPROVED_NOT_ENFORCED. All future executable budgets remain null. Synthetic numeric fixtures are dimensionless test values, not recommendations or production limits.

Candidate Windows Job Object mechanisms requiring local API/version qualification:
- ActiveProcessLimit bounds total active job members; distinct child-only count needs supervisor accounting and no breakaway.
- PerJobUserTimeLimit/PerProcessUserTimeLimit concern CPU user time, not wall duration or necessarily all aggregate kernel time; required CPU semantics must be approved and independently tested.
- ProcessMemoryLimit/JobMemoryLimit constrain committed memory; they must not be represented as total resident working-set guarantees.
- WorkingSet limits, where supported, are per-process behavior and require verification; polling peak working set is observation only.
- KILL_ON_JOB_CLOSE and owned-job termination are cleanup mechanisms, not permission to terminate an arbitrary PID.

External independently verified controls are required for wall deadlines, bounded stdout/stderr transport, writable-storage quotas and path containment, retry count, exact executable/test allowlists and separate sentinel protection. Never infer filesystem/network isolation from a Job Object. Existing sealed supervisors do not enforce this full design. OS feature/capability claims remain UNVERIFIED until separate controlled qualification.

Phase1444EJCleanupAuthority.v1 is NOT_AUTHORIZED. Require exact retained owned-job identity, owner process/creation identity, verified tree membership, explicit timeout termination authority, partial receipt retention, safe disposable-storage scope, post-cleanup empty-job verification and revocation handling. Rollback scope must explicitly exclude repository baseline and every historical custody generation. Storage path/ACL/no-reparse checks and cleanup receipts required; no host-wide cleanup. Unknown ownership means no termination of unrelated processes. No cleanup or rollback performed here.
