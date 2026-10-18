# Owner authority

Phase1444EJOwnerAuthority.v1 is TRUST_NOT_ESTABLISHED. All identity/key/algorithm/evidence/time/expiry/revocation fields are null. Enrollment requires independent identity and role verification, approved trust root, public-key fingerprint, authorized asymmetric algorithm, authentic signed decision, exact payload/scope and source fingerprints, separately verified timestamp, revocation and replay ID. Chat approval and synthetic fixtures confer no authority.

Decision signatures must cover version/domain, baseline, source hashes, helper/catalog evidence, exact commands and18test IDs, resources, cleanup, decision ID/nonce and time bounds. Canonicalization: sorted compact ASCII-escaped JSON, no NaN, UTF8. Unknown fields/ambiguous serialization must reject at a future strict verifier. Key generation, enrollment and real signing are excluded. Native activation, qualification, adoption and Seriesv2 remain NOT_AUTHORIZED.

The controlled decision is embedded in authorization-assessment.json; it cannot authorize itself. Historical current catalog verification does not prove historical signature/console association or fresh revocation.
