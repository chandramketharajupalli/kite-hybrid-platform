# Public documentation assessment

Reviewed 2026-10-12. TERMS_UNRESOLVED. Public documentation was consulted;
no account entitlement or broker API was accessed.

[Instrument documentation](https://kite.trade/docs/connect/v3/market-quotes/)
documents daily CSV, GET /instruments/:exchange, authenticated examples and distinct
instrument/exchange tokens. Suggested daily caching does not independently establish
permission for permanent research storage.

[Terms](https://kite.trade/terms/) sections 2(a), 4(b) and 5 address documented
access, mock-trading application restrictions, copying/databases/redistribution,
credential confidentiality and audit retention. Section 2(e) separately concerns
automated trading approvals. Applicability to private backtests/simulation and
snapshot retention needs an attributable owner/broker assessment. No permission is
inferred from public accessibility. Audit retention and data custody need separate
resolution; this is an engineering stop decision, not a legal determination.

[Historical documentation](https://kite.trade/docs/connect/v3/historical/) documents
5minute and token-specific routes. [Limits](https://kite.trade/docs/connect/v3/exceptions/)
do not establish account eligibility or availability for any particular window.
Proposal pacing remains one request/second, concurrency one, zero retries.
Required evidence: account entitlement, entity/territory applicability, permitted
research use, retention/redistribution rights and credential custody. Both gates stay closed.
