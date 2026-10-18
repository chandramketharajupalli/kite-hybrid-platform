# Phase1444BEnrollmentPolicy.v1 — proposed

Initial TRUST_NOT_ESTABLISHED. Real enrollment requires separate explicit approval; this package enrolls nobody and creates no real key. OwnerTrust null fields are genuine gaps, not placeholders that an executor may default.

An independent authorized reviewer must establish the owner's stable identity reference and evidence-adoption role from approved original identity/authority records. Retain sensitive originals outside Git under approved custody/retention; repository references must be minimized hashes and safe opaque IDs. Verify role delegation, conflicts, permitted decision types, authority effective/expiry bounds, approval identity, verification-policy fingerprint and independent reviewer authority.

Only after a cryptographic dependency and key provider are approved may a separate ceremony generate an owner signing key. Establish the trust-root authority independently before accepting a public key. Verify key provenance, algorithm, public-key bytes/fingerprint, allowed use and proof of possession against a fresh reviewer challenge. Proof of possession alone does not establish identity or authority. Bind the challenge, identity evidence, public-key fingerprint, root, exact policies, baseline and enrollment decision into a reviewed immutable record. Do not bootstrap trust by accepting a root supplied by the decision being verified.

Proposed Ed25519 public-key fingerprint is SHA256 of the exact 32-byte public-key encoding, subject to provider/library interoperability review. This phase has no Ed25519 key and does not claim that fingerprint exists. Synthetic fixture key identifiers are opaque test IDs and are not real public-key fingerprints.

Before any real decision, independently certify signature verification, current authority, trusted time, key/owner/decision revocation and durable atomic replay controls. Missing evidence must deny. Every restart must recheck authority and revocation freshness; a previously passing fixture is not approval. Independent certification requires real review evidence, not a locally generated hash certificate.

Future adoption decisions bind source Git SHA, amendment raw SHA256 78f508939a1a050b7b2e90005e11f81e8262147a207144b88514fc467ab9f6fd, canonical fingerprint 36a79d7a4e43f0bd9ac4f93a9d0258918eb7a22692efce41f4d465fd59ce47c4 and certificate raw SHA256 ec3a30573abe7068d540523de1bc570fc0a6b8553119f60755bb934b3a9a9db1. Such decisions can only permit current-validation evidentiary treatment after authentication; never historical recovery or implementation.
