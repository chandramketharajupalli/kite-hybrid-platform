# Phase 14.26 synthetic recovery authority

Scope: SYNTHETIC_FIXTURE. Contract version: Phase1426Recovery.v1; witness version: Phase1426Witness.v1.

The approval contains request ID, run ID, incident ID, synthetic authority identity, action, scope, issue and expiry times, evidence digest, current trusted-head digest, approval digest and one-use consumption identity. The only action is RESUME_SYNTHETIC_REPLAY and the only scope is SYNTHETIC_RECOVERY_ONLY. The allowlist must arrive from the caller; production code does not grant or mint approvals. Fingerprints provide integrity and allowlist binding, not cryptographic signatures or genuine identity authentication.

Reject absent, corrupt, unknown-version, untrusted, future or expired approvals and mismatched run, incident, action, scope, evidence or head. Reject spent identities and requests, missing consumption records and ambiguous outcomes. The comparison time and caller head are explicit trusted synthetic inputs; they are not independently authenticated clocks or remote witnesses.

Consumption order is exclusive witness ownership, complete verification, local durable consumption publication, CONSUMED witness publication, callback, validated monotonic result, and COMPLETED witness publication. No automatic retry follows a crash after local consumption, after witness consumption, during callback or after an ambiguous acknowledgment. A crash before consumption leaves no grant used; a new explicit invocation can retry. A stale owner lock is a denial, never permission to erase it.

The witness binds run, session sequence, highest accepted event sequence, journal digest, authority epoch, sticky paper HALT and last accepted action. Rollback of journal state, local consumption/approval or HALT while the current witness and expected head survive is rejected. Moving the witness outside the tested local subtree provides experimental separation only. Complete rollback of all authority, witness and caller state permits reuse; no universal rollback-prevention claim is made.

Acknowledgment, resolution, recovery approval, recovery execution and HALT clearance are separate concepts. No clearance procedure is implemented. A returned state that clears HALT is rejected; its approval remains consumed. Recovery adapters must obey the unchanged scheduler accounting/replay contracts and must not introduce new trading decisions. Operational recovery, real credentials and operational HALT remain outside scope.
