# Phase 13.6 synthetic test runbook

This runbook runs fixtures only. There is no application/service start command,
real credential source, broker route or operational launcher. Keep V2, four reviews
and governance second review PENDING. Do not supply .env, account data or broker
credentials. Existing trading services/databases are outside this procedure.

1. Inspect develop, HEAD, origin/develop, status including untracked and staged files,
   and diff check. Preserve known K/L work byte-for-byte. Unexpected ownership or
   protected-source mismatch requires stopping, never repairing the checkout.
2. Verify G1, the 18 registered normalized sources, 63 raw protected research files,
   V2/amendment exact hashes and prior K/L generation fingerprints. Compare with the
   new evidence manifest's before-work inventory. Keep old evidence untouched.
3. Use installed JDK 21, repository Maven wrapper and Docker for owned disposable
   Testcontainers only. The test POM sets UTC; it does not change host timezone.
   Never substitute a local trading DB URL. All PostgreSQL fixtures use durability
   settings ON, unique fixture markers and dynamically allocated loopback ports.
4. Run the durable ledger gate before any owner HTTP integration:

   ```powershell
   .\mvnw.cmd -f apps/synthetic-credential-owner/pom.xml -Pintegration '-Dit.test=LedgerIT' verify
   ```

   Require all tests to pass. Failed ledger durability means LEDGER_NOT_VALIDATED;
   no simulated dispatch integration until corrected and rerun. The crash test
   kills only its own disposable container, inspects its new port, and verifies the
   old consumed row after crash recovery. Never point it at other infrastructure.
5. After that gate, run all isolated synthetic unit and disposable integration:

   ```powershell
   .\mvnw.cmd -f apps/synthetic-credential-owner/pom.xml -Pintegration verify
   ```

   OwnerIT creates numeric-loopback fake peers and generated synthetic keys/entropy.
   No HTTP route is accepted from environment/system properties. Fault scenarios
   leave attempts permanently spent. Fixture schemas exist only in owned containers;
   Testcontainers cleans them up. No application startup is part of these commands.
6. For this additive module, run the existing unit/architecture regression once:

   ```powershell
   .\mvnw.cmd test
   ```

   Root reactor deliberately does not discover the standalone prototype. Do not
   claim that root success covers it. Existing broad trading integration is not
   necessary when no shared runtime/transport/authentication/persistence code changes.
7. Inspect the new runtime JAR: no Main-Class, service launcher, test classes,
   third-party runtime dependencies or trading imports. `jdeps --print-module-deps`
   must return only java.base. Inspect Maven runtime/test dependency trees separately.
   Failure means MINIMAL_ARTIFACT_NOT_VALIDATED, not a waived exclusion.
8. Run `uv run python scripts/verify-project.py`,
   `uv run python scripts/check-secrets.py`, `git diff --check`,
   `git diff --cached --name-status`, and a full untracked inventory. Recheck all
   protected hashes and K/L snapshots. No Python source change means Python tests,
   Ruff and mypy are NOT_APPLICABLE; no fabricated historical test counts.

Record actual XML counts, failure history and reruns. The manifest hashes redacted
summaries and artifacts rather than publishing raw JVM properties/container logs.
Its deterministic replay checks canonical body fingerprint, inventory hashes and
retained statuses twice. Replaying evidence is not rerunning tests or certifying
real systems. New results belong in a new evidence generation; do not rewrite old
generations to conceal a failure.

Stop after reporting. Do not run the JAR, expose a service, inject a real session,
install network policy, adopt governance, resume HALT or interpret any success as a
broker read authorization. Missing independent auth, durable operational issuance/
fencing, external HALT, minimal deployment and complete logging controls still need
separate engineering/review authorization. INR10,000, cash/CNC, Phase12 BLOCK,
H1/H2 FROZEN and July TEST SEALED remain unchanged.
