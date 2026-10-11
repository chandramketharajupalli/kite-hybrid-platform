# Phase 13.7 plan amendment 1 — dynamic cryptographic provider boundary

Parent frozen plan raw SHA-256:
D79F7251482A1A8529C47CFD9B877DA607AEFDDC163BE3C82179E541FE274E81.
Baseline unchanged: develop / 34cc3e5b242547774a5225d30aa460699db2b2d9.
The original plan remains byte-for-byte unchanged. Acceptance criteria are not relaxed.

After initial passing suites, direct local JDK21 provider inspection showed
`Signature.getInstance("Ed25519").getProvider().getClass().getModule().getName()`
returns jdk.crypto.ec. Static jdeps reported java.base but cannot discover dynamic
provider loading. Thus initial static packaging evidence alone was insufficient for
the requested java.base-only execution boundary. Retain this finding and initial runs.

Change only SyntheticHalt's synthetic signing profile and existing test key factory:
RSA-3072 with SHA256withRSA, provided by java.base on the installed JDK21. This keeps
approximately 128-bit classical strength, with generated fixture keys only. No live
algorithm, approved contract, authentication path or historical evidence is changed.
Version the signed payload domain as SYNTHETIC_HALT_V2_RSA3072 so old synthetic
assertions cannot silently cross profiles. Reject wrong algorithms/undersized keys.

Add a unit guard checking the actual key-generation and signature provider modules
and a wrong/undersized-key negative case. Also execute synthetic signature verification
with Java --limit-modules java.base in a test-only subprocess. No runtime launcher,
new third-party dependency, or public network access. New test/helper code stays in
already allowed RecoverySecurityTest/SyntheticSecurityTest. This amendment is the
only extra documentation path beyond the parent plan.

Rerun focused unit -> disposable ledger -> loopback -> full standalone -> existing
Java unit/architecture in order, offline and UTC. Key generation may make those runs
slower but per-process/socket/query budgets remain unchanged. Record fresh counts and
the pre-amendment packaging limitation. The final manifest binds both plan hashes.
No provider mismatch may be waived by renaming a static scan as runtime certification.
