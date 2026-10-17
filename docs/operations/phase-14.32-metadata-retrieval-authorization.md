# Phase 14.32 metadata-only retrieval request

Owner decision: PENDING. Network authorization: NOT_AUTHORIZED. Request fingerprint: 585905f4b586e0c566aabed68289c72cd31c10e8ed718c8f4299c6e9b00f9996.

Bind to acquisition manifest 74b40b6723fd6bfc5401a48e76fd8e94f2cd68919b5c532fa4b271c7f59db65c and research/phase-14.32/generation-1/metadata-retrieval-request.json. Exact host pypi.org; HTTPS GET; eight exact-version JSON endpoints below. Maximum eight requests, one per endpoint; maximum 2,097,152 response bytes each and 16,777,216 aggregate bytes; 20-second timeout per request. No redirects (including same-host redirects), retries, alternate endpoints, credentials, archive requests, broker calls or installations. Reject non-success HTTP status, oversized response or non-JSON payload and preserve the failure. No silent substitution of another version.

Validity: a future owner decision must bind this request fingerprint, scope, identity, issued time and expiry no later than 24 hours after issuance. No approval currently exists. Before any retrieval, verify that decision and its separately approved custody record. Successful retrieval does not grant acquisition permission.

Proposed raw custody: a new external evidence directory under the existing Phase 14.32 temporary evidence root, created only after approval. Preserve exact response bytes, raw SHA-256, endpoint, request/response times, status and reviewed source evidence. Never place unrelated headers or private information in public evidence. Treat a future observation as an additive amendment; retain the current METADATA_UNAVAILABLE ledger and reruns. Do not derive an observation timestamp from a file modification time.

- https://pypi.org/pypi/annotated-types/0.8.0/json
- https://pypi.org/pypi/pydantic/2.13.5/json
- https://pypi.org/pypi/pydantic-core/2.46.5/json
- https://pypi.org/pypi/pydantic-settings/2.15.0/json
- https://pypi.org/pypi/python-dotenv/1.2.3/json
- https://pypi.org/pypi/typing-extensions/4.16.0/json
- https://pypi.org/pypi/typing-inspection/0.4.4/json
- https://pypi.org/pypi/tzdata/2026.4/json

All results concern offline preparation. No network requests, distribution downloads or installations occurred. Acquisition PENDING; installation NOT_AUTHORIZED; clean installation NOT_ATTEMPTED; OFFLINE_RC_CONDITIONAL. Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase 12 BLOCK; H1/H2 FROZEN; July SEALED; genuine qualified pairs zero; operational NO_GO.
