# Trusted time, expiry, revocation and replay

TRUSTED_TIME_UNVERIFIED. No independently trusted approval timestamp, validity period or expiry exists. Do not begin a countdown using the workstation clock. Future authenticated time evidence must bind source trust identity, UTC, uncertainty, not-before/expiry and maximum age. Reject when the uncertainty interval crosses validity bounds, evidence is stale, clock continuity fails or restart/reboot continuity is unverified.

Revocation is UNVERIFIED. Require independently verified owner/key/role/decision revocation authority and fresh evidence. Unavailable or ambiguous revocation fails closed; absence of a revocation record is not proof of validity.

DURABLE_REPLAY_UNVERIFIED. Decision identifier and nonce remain null. Require unique decision and nonce, durable atomic reservation/consumption with revocation fencing, restart-safe rejection, retained tombstones and independent rollback-resistant checkpoint. An in-memory fixture or internal SQLite hash chain does not provide independent rollback protection. A restored consumed decision must not regain authority. No ledger is created or consumed in this assessment.

Local receipt durations, canonical hashes and source hashes are integrity/measurement evidence only. They establish neither trusted approval time nor durable replay authority.
