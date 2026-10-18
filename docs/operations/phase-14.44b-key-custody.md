# Phase1444BKeyCustodyPolicy.v1 — proposed

No real private key, public-key enrollment, owner identity document or trust root is generated in this phase. Synthetic HMAC keys use disposable random bytes held only in test process memory; no key is written to a manifest, file, command argument, log or test receipt. Do not retain secrets on test failure. Python memory erasure is not guaranteed; no secure-zeroization claim is made.

Future real private keys must stay outside Git and ordinary evidence storage, in a separately approved protected provider. Require owner-only signing access plus documented recovery/security administration, least privilege, authentication/user presence policy, key usage restrictions and audit logging without key material. Independently test export denial, process access boundaries and provider enforcement. Restricted directory ACLs do not make software keys non-exportable. No OS-backed non-exportability is certified here.

Enroll only public-key fingerprints and safe evidence references. No private-key material in manifests; no plaintext secret logs. Approve retention and backup before key creation. Prefer provider-supported protected recovery; plaintext export/backups are prohibited unless a separate reviewed policy expressly permits them. Never infer a backup policy from filesystem access.

Rotation requires independent owner/authority approval, proof of possession of the new key, bound old/new public fingerprints and activation/revocation times, trust-policy version, replay namespace continuity and independent audit. Deny caller-requested rotation without that evidence. Lost/suspected keys require immediate revocation and suspension; recovery uses independently verified authority and a new enrollment ceremony, never silent fallback. Preserve historical decisions and revocation evidence. Historical certified results may not be deleted by enrollment, rotation or recovery.

Actual provider identity, key handles, signer, recovery custodian, enrollment approver, retention schedule and rotation authority remain unassigned. This policy is review material, not real authority certification.
