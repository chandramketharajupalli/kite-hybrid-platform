# Unsent retention clarification

Status: DRAFT_NOT_SENT. Owner review and explicit send authorization are separate. Recipient and ticket reference must be confirmed from approved custody before sending; no ticket identifier is invented.

Subject: Clarification of Kite Connect data storage and retention for private backtesting

Dear Zerodha Support,

I am preparing a private, personal-use research system covering 1,115 NSE symbols. Before acquiring data, I would appreciate written clarification of the applicable Kite Connect terms, particularly Clause 4(b). Please distinguish instrument-master mappings from historical candles and specify any conditions or retention limits for each item:

1. May the NSE master CSV be stored temporarily for parsing, and for how long?
2. May an exchange/trading-symbol-to-token mapping be retained between refreshes, and for how long?
3. May daily refreshed masters be retained as historical copies, or must each prior copy be deleted?
4. May five-minute historical candles be processed temporarily for private backtests over six- and twelve-month windows?
5. What maximum candle-cache duration is permitted, if any?
6. Is a permanent local historical OHLCV database prohibited under Clause 4(b)?
7. May aggregate backtest statistics and simulated trade records be retained, and what content and duration limits apply?
8. May source hashes, retrieval timestamps and audit records be retained, and for how long?
9. Are backup or recovery copies permitted, including provider-managed backups, and how must expiry be enforced?
10. When must each category be deleted, and what deletion evidence may be retained?
11. Does local encrypted storage change any applicable storage or retention condition?
12. May permitted data be reused across multiple backtest runs within an authorized retention period?

This request concerns private research only, without redistribution or public display. I am not assuming permission for live-data paper trading or API order placement. Please identify the applicable terms/version and any account, licensing or deletion conditions. If an activity is not permitted, please state that explicitly.

Thank you.

Before owner-approved sending, add the original ticket reference only if independently available. This draft contains no credentials, tokens, private headers or assumed retention periods. Nothing has been sent.

