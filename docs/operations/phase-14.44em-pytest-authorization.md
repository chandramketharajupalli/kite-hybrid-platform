# Pending pytest recovery authorization

Phase1444EMPytestRecoveryApproval.v1 is NOT_AUTHORIZED. Its exact interpreter pin, argument arrays, source-reconciled46 node IDs, source hashes, environment-name allowlist and proposed limits are in pytest-approval.json. The future output directory is only a proposal and is not created. No validity period starts.

Owner identity/authentication, trusted approval time, expiry, revocation, durable replay control, native runner identity, cleanup authority and independent filesystem/network/credential evidence remain null. Approval must bind all source/command/test identities and resource budgets. Real runner activation must reject missing dependencies; synthetic facts cannot confer owner authority.

Proposed budgets are 4 processes, 3 children, 300s wall, 120s CPU, 512MiB memory, 8MiB output, 64MiB writes, zero retries. These are recommendations, not effective limits. Trusted owner approval and independently qualified controls are required before any command executes. No exclusions, PYTHONPATH changes, pytest config changes or dependency upgrades.

On any unexpected failure retain complete binary stdout/stderr and exit status, terminate only the verified owned job, retain partial receipts and stop without retries. Cleanup authority never extends to sealed history or unrelated processes. Rollback is restricted to separately approved disposable storage.
