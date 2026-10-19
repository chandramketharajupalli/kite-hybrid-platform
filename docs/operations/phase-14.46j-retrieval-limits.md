# Proposed versus approved limits

Phase1446JMetadataLimits.v1 is embedded in metadata-approval.json.

| Control | Proposed value |
|---|---:|
| Maximum requests | 3 |
| Maximum response bytes per POM | 262144 |
| Maximum total response bytes | 786432 |
| Connection timeout seconds | 5 |
| Read timeout seconds | 10 |
| Retries | 0 |
| Redirects | 0 |

HTTP method GET; credentials NONE. Approved limits remain null. Equality at a proposed bound is accepted by the synthetic fixture; any excess, negative/invalid count, retry, fourth or duplicate request is rejected. Total cap must match accounted response bytes; failed/partial responses must count in a future runner. No automatic increase or fallback.

The in-memory model checks reported measurements only; it is not a network enforcement implementation. Future runner approval must define streaming byte accounting, DNS/TLS/connect/read timeout coverage, cancellation and cleanup, and reject decompression/parser expansion. No real timer, network budget or execution lease has started.
