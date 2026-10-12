# Gate B owner decision — PENDING

Independent of Gate A. Authorized requests ZERO. No dispatch under Phase14.3.
Proposed ceiling: two verified NSE cash-equity symbols, one actual verified NSE
session, at most two GETs, 5minute interval. Symbols, tokens, session and exact IST/UTC
boundaries are UNSET; no arbitrary selection is made without a genuine master.

Route: https://api.kite.trade/instruments/historical/{instrument_token}/5minute
with approved from/to, continuous=0 and oi=0. Owner/operator/credential owner,
start/stop, entitlement, permitted use, spend cap and custody remain PENDING.
Require master/token lineage, real calendar/holiday/early-close and corporate-action
evidence. One request/second, concurrency one, zero retries/redirects,
connect10/read30 seconds, deadline45 seconds/request, body limit1 MiB/request.

Separate one-use approval/security review must verify exact egress, no order
capability, independent HALT, redacted audit and output custody. Abort on uncertainty,
non-success or exhausted budget; no refresh. Validate completed aligned ascending
unique OHLCV, volume, gaps/partial sessions, fingerprints and replay. Quarantine
inconsistent sessions without invented prices. No orders, WebSockets, trading DB
mutation or promotion. Actual performance remains null.
