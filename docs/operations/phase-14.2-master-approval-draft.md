# Decision A: instrument-master acquisition — UNAPPROVED

No request is authorized. Current request budget: ZERO. Owner decision A is
independent of historical decision B and grants no order or account-read permission.
An externally supplied, dated immutable snapshot with attributable custody is the
preferred offline alternative; it still requires permitted-use evidence.

## Proposed exact scope, subject to all gates

| Field | Proposed bound / required binding |
|---|---|
| Method and endpoint | GET https://api.kite.trade/instruments/NSE; no query parameters |
| Exchange / selection | NSE snapshot; retain original bytes; resolve only NSE/CASH/EQ candidates; indices and other subtypes require explicit exclusion/review |
| Maximum calls | ONE, one-use approval; any uncertain dispatch consumes the budget |
| Authentication | Treat as authenticated; official documentation shows API key/access-token header. No assumption of unrestricted anonymous access |
| Account and legal eligibility | PENDING owner evidence; no account access performed |
| Allowed start / absolute stop | PENDING exact UTC instants, maximum 60-second authorization window |
| Timeouts / attempts | Connect 10s, read 30s, hard total 45s; concurrency 1; retries 0; redirects 0 |
| Payload limits | Request <=4 KiB, compressed response <=8 MiB, decompressed CSV <=32 MiB; bound streaming before storage; transport/TLS overhead separately reviewed |
| Monetary authority | INR0 incremental purchase/spend; no subscription change; current account entitlement must be independently established |
| Destination | Proposed D:/Yogendra/kite-read-custody/phase14.2/master/<sha256>.csv; outside Git, not created or ACL-verified in this phase |
| Operator / credential owner | PENDING attributable identifiers, custody attestation and independent reviewer |
| Approval ID / signature / expiry | PENDING; bind full packet hash and one-use state before network dispatch |

## Custody, attribution and stop behavior

Require an owner-controlled encrypted volume, restrictive ACL, no cloud sync or
unreviewed backup, exclusive create and atomic finalized content-addressed output.
Record raw SHA-256, exact bytes, source endpoint, supplied schema, actual retrieval
start/end UTC and separately publisher-attested date if provided. Do not infer a
publisher date from filename, machine date or HTTP observation time. Capture safe
status/byte-count/Retry-After metadata only; never persist Authorization, cookies,
access tokens, profile responses or wire dumps. Instrument tokens are reference
identifiers, not session credentials.

The raw-data retention period is PENDING permitted-use review; no retention right
is assumed. Approval must name deletion date, authorized custodian and backup
policy before acquisition. Audit metadata has a separately reviewed legal retention
schedule. Stop and quarantine partial bytes on unexpected status, encoding, gzip,
schema, size, timing, mapping or custody failure. Delete only that approved temporary
output after required audit retention review; never touch repository/frozen evidence.
Do not retry, follow redirects, login, restore tokens, or issue an auxiliary GET.

## Documentation and unresolved terms

Reviewed 2026-10-12: official [instrument API documentation](https://kite.trade/docs/connect/v3/market-quotes/)
lists the exchange-specific route and authenticated example, a daily snapshot and
distinct instrument_token/exchange_token fields. It recommends local snapshot use;
this does not establish unrestricted licensing or this account's eligibility.

The [Kite terms](https://kite.trade/terms/) restrict API access methods and permitted
use, address virtual/mock-trading applications and confidentiality, and restrict
permanent copies/redistribution unless permitted. They also prescribe audit-record
retention. The interaction with the snapshot-storage guidance and this project's
private research/simulation use needs attributable broker/legal clarification.
These are unresolved gates, not an assertion of legal permission.

Before approval: prove current entitlement, applicable terms/retention permission,
credential-owner control, host egress to only the exact GET route, audit redaction,
HALT independence and an order-incapable isolated process. Existing broad transport
has order methods and must not be treated as this narrow process. No such process
is enabled here. Even a completed packet requires a separate go/no-go review.
