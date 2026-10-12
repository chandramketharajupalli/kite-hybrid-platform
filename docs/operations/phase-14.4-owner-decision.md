# Gate A owner decision packet

Decision: PENDING. Real authorization: NOT_AUTHORIZED. Authorized request budget: 0.
Owner identity, operator, approval timestamp, authorization ID, start and expiry: UNSET.
These fields must be attributable and explicitly approved; elapsed time is not consent.

Proposed method/source: GET https://api.kite.trade/instruments/NSE, exactly one attempt.
Purpose: dated NSE cash-equity mapping of the canonical 1115 symbols. Permitted-use basis
is UNRESOLVED; see terms assessment. This document is not a signed authorization.
Headers, only inside approved custody: X-Kite-Version: 3; Authorization using documented
token api_key:access_token syntax; Accept-Encoding: gzip. No header values in logs.
Use only a separately authorized existing legitimate authenticated session, never
restore tokens, read .env, refresh, log in or start the trading application.
AUTHENTICATED_SESSION_NOT_ESTABLISHED. No credential access performed.

Proposed limits: concurrency1; one initial HTTP GET; retries0; redirects0;
connect10/read30 seconds; whole-attempt deadline45 seconds; wire and decoded body
each at most32 MiB. No auxiliary calls, historical requests, portfolio/order routes
or WebSockets. Exact authorization expires on the first consumed attempt or approved
expiry, whichever occurs first. Expiry is UNSET, therefore unusable now.

Storage authorization: DENIED_PENDING_TERMS_AND_CUSTODY. Raw retention: UNSET.
Permanent mappings/research retention: UNSET. Approved destination: NONE.
Candidate only: D:/Yogendra/phase14-private-custody/instruments-NSE.csv; not created.
Before approval specify owner-only ACL, custodian, encryption, backup exclusion,
finite deletion deadline, termination-trigger handling and auditable expiry/deletion.
No snapshot or response persistence until these rights and controls are verified.
Audit metadata retention requires its own permitted scope; do not equate it to raw
content retention. Never overwrite existing evidence or publish the raw CSV in Git.

Logging proposal: allowlisted event IDs, budget state, redacted result category,
source/hash/size/timestamps only where permitted; never headers, cookies, raw response
diagnostics or secret-bearing exceptions. Disable verbose wire/header logging.
Abort on any unmet checklist item, uncertain session ownership, non-success, timeout,
size/encoding/schema issue, TLS failure, exhausted budget or storage uncertainty.
No automatic retry, fallback or token refresh; a new decision is required after failure.

Gate B: PENDING / NOT_AUTHORIZED. A cannot authorize candles, trading, HALT changes,
order activity or reuse by another component. Existing safety restrictions prevail
even after owner approval. No request is scheduled by this packet.
