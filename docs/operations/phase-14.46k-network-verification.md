# Future endpoint verification - NOT EXECUTED
1. Obtain independent bounded endpoint-check approval specifying operator, allowed actions, request/byte/time bounds and evidence custody.
2. Bind exact HTTPS origin https://repo.maven.apache.org without expanding the three URLs.
3. Establish approved DNS/network restrictions; no inherited/unapproved proxies, credentials, redirects or mirror fallback.
4. Verify TLS chain, exact hostname and certificate validity; fail closed without bypass or automatic retry.
5. Verify approved CA trust-store and revocation-evidence policy.
6. Record observed endpoint, certificate-chain fingerprints, negotiated TLS and route evidence without secrets.
7. Verify independently trusted observation time; local system time alone cannot satisfy this gate.
8. Retain immutable raw receipt, hashes, outcome and limitations in separately approved custody.
9. Seek separate metadata-retrieval approval; endpoint-check approval does not permit POM retrieval.
10. Keep JAR acquisition unauthorized.
No network, DNS, TLS or retrieval implementation or execution in K preparation.
