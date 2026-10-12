# Phase 14.7 offline verification runbook

Use the standalone JDK 21 module. Do not start the trading application, load .env,
restore credentials, contact Zerodha or instantiate a real broker transport.

Focused unit command:
`mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Dtest=GateAHaltBoundaryTest test`

Full standalone unit plus disposable process-ledger integration command:
`mvnw.cmd -o -f apps/synthetic-credential-owner/pom.xml -Pintegration -Dit.test=GateAHaltLedgerIT,GateALedgerIT verify`

Tests use synthetic keys and UUID-scoped temporary journals. Child processes are
bounded and forcibly stopped on timeout; fixture cleanup removes only their own
journals. No database, container, broker, credential store or host configuration
is involved. Generated target reports and binaries are excluded from the change set.

Preservation: import scripts/research/verify_phase145.py without bytecode writes;
bind BASELINE in memory to 83e42b4fd3e2ec79f82b6d897cae18da41000b9e and call verify().
Do not edit the verifier. Separately verify the Phase 14.6 canonical manifest
fingerprint and every baseline/artifact raw hash. This preserves original Phase 14.0
hashes using the documented 13-file Phase 14.0A committed-blob newline reconciliation.

For the new envelope, canonicalize manifest with JSON sorted keys and separators
comma/colon, UTF-8, no trailing newline; SHA-256 must equal fingerprint. Rehash every
baseline and artifact file. Replay twice. The envelope itself is excluded from its
own hash table to avoid circular hashing. Verify new text is LF and Git hash-object
with path filtering equals the raw blob object ID. Do not stage files to do this.

Run `uv run --offline python scripts/check-secrets.py`, git diff --check and exact
porcelain inventory. An unexpected tracked change or preservation mismatch stops
the phase. No commit, push, deployment or genuine acquisition follows verification.

Terms remain TERMS_UNRESOLVED based on retained Phase 14.6 assessment; no new vendor
decision exists. Authentication is NOT_ESTABLISHED. Separate terms, custody,
production containment and explicit owner decisions are prerequisites to any future
Gate A request. Gate B is independent and NOT_AUTHORIZED.
