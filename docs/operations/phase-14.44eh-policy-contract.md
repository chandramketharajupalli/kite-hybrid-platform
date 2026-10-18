# Versioned console-host authorization contract

Phase1444EHConsoleHostAuthorization.v1 starts NOT_AUTHORIZED. The exact envelope is in authorization-assessment.json. It binds baseline913a1014a0ffd5fa14fc9d228ffb3070b1d33d0f, sealed E-F supervisor SHA256a70a068cf71b8da035d5d15c764ef8721b73ce265fd3eaa4b05ffa973de76597, C:/Windows/System32/conhost.exe hashc86146188b78e484f32cc29e2331689659943b47689a85570342044f499938ac, Microsoft Windows catalog signer, exact catalog receipt/hash, CONSOLE_HOST_FOR_SYNTHETIC_EXECUTION role and exact normal synthetic command identity.

Creation policy HANDLE_PID_CREATION_MATCH; parent policy ROOT_PID_CREATION_LIFETIME; job policy RETAINED_OWNED_JOB; console policy EXPLICIT_ASSOCIATION_REQUIRED. Job membership does not establish console association. A signature does not establish ownership. Unknown relationship rejects. Existing owned-job cleanup and termination authority are unchanged.

Required fields are exactly the policy FIELDS set: contract,status,git_baseline,supervisor_fingerprint,executable_path,executable_sha256,catalog_signer,catalog_evidence_reference,catalog_evidence_sha256,required_role,command_identity,creation_policy,parent_policy,job_policy,console_policy,owner_identity,authorization_identity,approval_evidence,approval_timestamp,expiry,revocation_status,replay_identifier,payload_fingerprint. Missing/unknown fields or non-string/non-null values reject. Known fields are populated; real owner/approval/time/expiry/replay fields remain null; revocation status UNKNOWN.

Canonical payload fingerprint: SHA256 of UTF8 sorted compact JSON, ensure_ascii=True,allow_nan=False, excluding only payload_fingerprint. This is document integrity, not a signature. Current pending envelope fingerprint0f19b960467de96fab03507808cd730cc8ff31785dd698db7aec02e310842216.

Synthetic fixtures use explicit TEST_ONLY owner/evidence/status, injected UTC time, approval interval no longer than168hours, and revocation observation no more than1hour old and not future-dated. Real current timestamps are never substituted. Replayed decision IDs/nonces and conflicting decisions reject against injected snapshots. These checks neither consume approvals nor establish durable replay protection.

Adapter raw SHA256c417ba7802c2d08362c0e3d83aad57d109c4a07dbc99e030d9748adf4adbc043. It cannot authenticate an owner or activate a supervisor. Real authorization remains NOT_AUTHORIZED regardless of synthetic test outcomes.
