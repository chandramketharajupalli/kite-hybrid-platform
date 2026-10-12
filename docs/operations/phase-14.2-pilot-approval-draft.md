# Decision B: first historical read — UNAPPROVED

Decision A does not imply B. Current request budget ZERO. No real symbols, tokens,
session or signed approval have been verified, so this packet is intentionally
not dispatchable. Do not substitute synthetic values for missing approval fields.

## Binding request table

| Field | Request 1 | Optional request 2 |
|---|---|---|
| Explicit NSE common-equity symbol | PENDING | PENDING |
| Verified instrument_token / stable platform ID | PENDING | PENDING |
| Raw dated master SHA-256 and matching row evidence | PENDING | PENDING |
| Prior token-lineage / corporate-action evidence | PENDING | PENDING |
| Same actual NSE session date | PENDING | PENDING |
| Exact session from/to in Asia/Kolkata and UTC | PENDING | PENDING |
| Exact GET route and serialized query hash | PENDING | PENDING |

Maximum TWO distinct verified symbols, ONE completed trading session, at most TWO
GETs to https://api.kite.trade/instruments/historical/<instrument_token>/5minute.
Use from/to bound to the verified session; continuous=0 and oi=0. No quote, profile,
margin or instrument refresh may be added implicitly. One-second start spacing,
concurrency 1, zero retries/redirects; connect 10s/read 30s and absolute 120s run
ceiling. <=4 KiB request each, <=1 MiB response each and <=2 MiB total response;
unexpected compression stops unless separately bounded and approved. A missing or
uncertain response consumes its attempted call budget. No automatic restart.

Required owner bindings: approval ID/signature/packet hash, credential owner,
operator and independent reviewer; exact UTC allowed start/stop; verified valid
session custody without restoration/login; account historical-data entitlement;
legal permitted-use/retention evidence from decision A's review; maximum spend
(currently INR0); private output location and ACL attestation; one-use approval
consumption, egress/method/path/query allowlist, audit sink and redaction proof.
Actual NSE holiday/special-session calendar and corporate-action evidence must be
pinned independently. No dates, liquid-stock selection or entitlement are guessed.

Proposed output location: D:/Yogendra/kite-read-custody/phase14.2/historical/<plan-hash>/,
outside Git; no directory or permissions created here. Raw observations immutable
and separately hashed from derived/exported series. Retention/deletion schedule
must be approved before use; sanitize audit metadata, not original candle values.
Quarantine the entire session on gaps, duplicates, wrong offsets, incomplete bars,
OHLCV errors or provenance conflict. No filling missing candles or reporting returns
from a rejected window. Confirm content replay before any research handoff.

Stop immediately for 401/403/429/5xx, redirects, timeout, budget overrun, changed
mapping/calendar, ambiguous outcome, logging/custody uncertainty or any security
exception. No retry/token refresh, WebSocket, order path, trading-table write or
HALT resume/arm. Independent HALT proof must not require mutating operational state.
No general trading application startup. Approval B is not execution permission.

## Current documentary basis and limitations

The official [historical API](https://kite.trade/docs/connect/v3/historical/)
documents the token-based endpoint, 5minute interval and from/to parameters.
It does not certify this stock's history, adjustment semantics or our account's
range/retention entitlement. Our one-session limit is a conservative proposal.
The [rate-limit documentation](https://kite.trade/docs/connect/v3/exceptions/)
lists historical requests at 3/second; this draft stays at 1/second, or tighter if
account-specific evidence requires it. Neither rate implies availability.

The [terms review](phase-14.2-master-approval-draft.md) remains unresolved.
No genuine data verification is claimed. A supplied master, completed binding
table and separate explicit owner decision plus independent go/no-go review must
precede any future narrow read-process implementation or dispatch.
