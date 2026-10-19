# Independent retrieval decision
Phase1446KMetadataRetrievalDecision.v1 is NOT_AUTHORIZED and cannot approve itself.
Proposed limits: maximum_requests=3; maximum_response_bytes=262144; maximum_total_bytes=786432; connect_timeout_seconds=5; read_timeout_seconds=10; retries=0; redirects=0; method=GET; credentials=NONE.
Approved limits, endpoint identity evidence, operator/owner identity, owner approval, trusted timestamp, expiry, revocation, durable replay ledger and restricted output custody remain null.
Future independent approval must bind actual baseline, frozen plan, exact origin/URL fingerprint, endpoint evidence, limits, authenticated operator, finite expiry and approved custody. Local clock observations are not trusted timestamps. Fail closed on unknown time or revocation status.
Require unique approval ID and durable atomic consume-before-use ledger; duplicate, concurrent and post-crash reuse rejected. Ledger unavailability denies action. Revocation and bounded cleanup require independently authorized procedures.
Retrieval custody is separate from preparation custody: isolated approved destination, owner/ACL, no reparse/overwrite, bounded raw POM XML, raw SHA256/receipts, no DTD/external entities/executable archives or sealed-source mounts. No retrieval destination is created.
JAR, Java, runtime and Gate A/B remain NOT_AUTHORIZED; NO_GO. Synthetic passing fixtures never grant real authority.
