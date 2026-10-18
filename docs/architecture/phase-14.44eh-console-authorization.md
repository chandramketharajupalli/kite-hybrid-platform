# Inactive console authorization boundary

Phase1444EHConsoleHostPolicy.v1 is a pure preparation adapter. It imports no native supervisor and has no process, termination, credential, network, database or broker API. No existing supervisor calls it. Sealed E-E/E-F sources and default helper rejection remain unchanged. No executable allowlist is expanded.

evaluate_authorization always rejects real authorization because owner authentication is NOT_ESTABLISHED. Envelope schema/integrity validation cannot override that result. evaluate_synthetic operates only on TEST_ONLY fixture envelopes and observations; its Verdict may report test_accepted, but execution_authorized is a read-only property that always returns false. No output grants process launch or cleanup authority. These interfaces are not an authentication mechanism or production execution hook.

Synthetic bindings require exact executable path/raw hash in both envelope and observed fixture; catalog signer/reference/hash and verified-catalog fixture; source baseline and supervisor hash; exact command identity; handle PID/creation and approved parent lifetime; retained owned-job identity/membership; explicit console-association token. No filename/PID/catalog-only acceptance. Missing or mismatched evidence rejects. Exit lifecycle is distinguished from identity verification; Win5/31 never promotes unknown identity.

Replay and conflict checks use injected immutable in-memory sets, with no consumption or persistence claim. UTC-aware synthetic time and fresh synthetic revocation evidence are mandatory. Actual trusted time, real revocation and owner authority remain unavailable. No real validity period starts.

Current conhost bytes and offline catalog-member evidence are preserved. Historical console association and signature remain unknown; current metadata is not retroactive evidence. The policy requires console association, so those historical observations cannot produce a helper authorization. Guest isolation remains DEFER_PENDING_APPROVED_ISOLATION_HOST.
