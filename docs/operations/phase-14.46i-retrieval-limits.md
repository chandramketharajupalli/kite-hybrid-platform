# Retrieval limits

Phase1446IMetadataRetrievalLimits.v1 is embedded in retrieval-authorization.json. Approved origin and approved exact URL allowlist remain null; proposed URLs are recorded separately. Maximum request count, per-response bytes, total transfer cap, connection/read timeouts, retries, destination directory and cleanup authority remain null. Null means not executable, never unlimited.

Future approval must establish finite enforceable request/response/transfer/time/retry budgets, including TLS/DNS and failed-response behavior; deny oversize/decompression expansion before resource exhaustion. Count retries toward the exact request budget. No numeric limits are invented here.

Required policy:deny all redirects; verify certificate chain and hostname; no credentials or inherited proxy configuration; deny unapproved endpoints and separately approve DNS/network boundaries. No broker/identity-provider endpoints. Isolated writable destination must be source-safe, non-reparse and distinct from sealed custody/application caches. Cleanup needs separate bounded authority.
