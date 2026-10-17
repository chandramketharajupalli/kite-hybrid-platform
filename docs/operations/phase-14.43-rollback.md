# Phase1443MigrationRollbackDecision.v1

Status PENDING_OWNER_DECISION; rollback authority null. Policy prepared, real migration/rollback NOT_TESTED. No claim of validated recovery is made.

Keep Series v1 and historical reports immutable. Candidate data,results and qualification must use separate versioned namespaces and fingerprints. Activation and rollback require separately authenticated authority, exact affected versions,scope,expiry and evidence. No automatic fallback,v2-to-v1 rewriting,deletion of prior receipts or restoration of incompatible qualifications.

At each restart verify decision/evidence/signature,baseline,policy/scope/content hashes,expiry,revocation/conflict state and predecessor receipt before considering a stage. Passing a predecessor is necessary evidence but never approval for the successor. Interrupted work must preserve partial receipts and require explicit recovery; no automatic repair or replay of expired authority.

A future rehearsal must demonstrate interrupted activation,rollback,namespace isolation,immutable legacy hashes and complete recovery receipts under approved scope. Any failed rollback must preserve diagnostics and leave activation blocked. These are proposed requirements only; current synthetic decision serialization/reverification tests are not real rollback tests.
