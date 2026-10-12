# Terms assessment and clarification — DRAFT / NOT_SENT

Public pages observed starting 2026-10-12T10:02:02Z; subsequent support/termination
reference checks during this same session. These are observation times, not publisher
revision dates, identity verification or consent. No authenticated site was opened.

Sources: [Kite terms](https://kite.trade/terms/),
[instrument documentation](https://kite.trade/docs/connect/v3/market-quotes/#instruments),
[published support portal](https://support.zerodha.com/).

Terms assessment: TERMS_UNRESOLVED. Section2 contemplates personal interfaces;
2(a) restricts mock-trading uses and public market-data display. Section4(b) restricts
copying, databases and derived content absent permission. Section5(a) requires audit
retention; that does not itself authorize market-data retention. Section9(b) addresses
cache deletion on termination. Section13 addresses precedence of agreements/policies.
Account-specific licensing, private research applicability, permitted retention periods
and subscription lapse versus termination remain unverified.

Instrument documentation describes an exchange CSV endpoint, daily generation and
database caching; it recommends exchange+tradingsymbol rather than permanent token
identity. The brief excerpt "store in a database at your end" is technical guidance;
its scope relative to contractual restrictions still needs attributable clarification.
No assumption that public documentation or endpoint availability grants storage rights.

Recipient channel: Zerodha Support Portal, https://support.zerodha.com/ (published
ticket channel). Request routing to the Kite Connect terms/data-licensing team.
No unverified email address is supplied. Sender identity and account verification
remain PENDING, to be supplied by the owner outside this credential-free draft.
Draft provenance: prepared for Phase14.5 at baseline2c3632a from the cited sources.
Neither recipient approval nor permission to send has been given.

Subject: Clarification of private NSE instrument-master use and retention

Hello Zerodha Support,

Please clarify whether one authenticated GET /instruments/NSE may be used privately
to map 1,115 supplied NSE symbols, with no public display or redistribution. May the
raw CSV be held temporarily, and for how long? May exchange/symbol/token mapping
records, content hashes, audit metadata and research-database entries be retained?
Please distinguish these categories and specify expiry/deletion duties, including
subscription lapse and agreement termination, backups and legally required audit logs.

How does the documentation's local-storage guidance apply alongside terms sections
2(a),4(b),5 and9(b)? Does private offline backtesting fall within the mock-trading
restriction? What broker/exchange licensing or written permission is required?
Historical-candle access would be a separate future decision; please identify the
applicable historical-data permitted-use and retention terms without treating this
request as approval for acquisition. No credentials or account data are included.

Thank you.

Status: DRAFT / NOT_SENT. Owner must approve exact recipient/channel and text before
any contact. Nothing has been submitted, signed or represented as a broker answer.
