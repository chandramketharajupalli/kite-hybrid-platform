# Endpoint trust requirements

Conventional candidate:https://repo.maven.apache.org. Endpoint identity UNVERIFIED; repository NOT_APPROVED. No DNS lookup, TLS handshake or HTTP request was performed. I's conventional proposal and cache labels do not authenticate ownership, transport or publisher.

Future requirements:exact hostname and response URL; certificate chain, validity and hostname verification using an independently approved trust store; approved DNS resolution/network boundary; reject alternate hosts, redirects, unapproved/inherited proxies and mirror fallback; no credentials. Trust-store identity and independent endpoint-verification evidence remain null.

HTTPS alone does not prove publisher provenance. Require separate source/metadata provenance and checksum/signature policy. Treat XML as data, reject DTD/external entities and executable content, enforce parser/decompression limits. A future network runner must independently demonstrate enforcement; this phase only models requirements.
