# Authorization enforcement boundary

Pure fixture model only. No runtime connection to I/H/F/E modules. I real_authorization and H evaluate_authorization fail closed. F validates its frozen command allowlist, pins images and creates an owned job before suspended-root resume; the historical conhost identity was rejected. E native API initialization occurs on import, so this model never imports it. Exact source fingerprints are in authorization-assessment.json. Historical default rejection and cleanup remain unchanged.

authorize_real returns False for every input. Frozen synthetic Lease.execution_authorized is always False. Verification inputs are TEST_ONLY values; a boolean signature fixture is not a signature verifier or owner identity. No HMAC, real keys, leases or native launch are created.

Independent enforcement needs an enrolled trusted public key and role, payload binding, verified trusted time, atomic durable ledger and external checkpoint, separately verified resource controls and cleanup authority. All remain unavailable. No interpreter-local boolean or JSON status can substitute. No process/network/credential APIs belong in enforcement.py. Preparation replay only reads evidence. Guest isolation remains DEFER_PENDING_APPROVED_ISOLATION_HOST.
