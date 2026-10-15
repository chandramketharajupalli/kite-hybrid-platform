# Portfolio risk and accounting policy

No approved genuine portfolio limits have been supplied. Policy defaults preserve
missing values; simulation admission fails closed. Fixture limits are labeled synthetic
and cannot authorize genuine-mode execution. Historical qualification remains governed
by the unchanged 70%/sample/confidence/drawdown policy and 13380 hypotheses.

Admission requires existing reviewed Risk settings plus explicit strategy and sector
exposure, attributable sector mapping evidence, and a conservative correlated-position
cap. Missing sector mapping or correlation review blocks. This cap is a declared
concentration control, not an estimated covariance model. No sector classifications or
capital limits are inferred from symbol names. Genuine operation remains NO_GO.

Available cash = cash minus reservations. Equity = cash plus marked position value.
Equity must equal opening cash plus realized net P&L plus unrealized net P&L.
Unrealized net P&L deducts accrued entry fees; future exit fees are not invented.
Cost basis records purchase notional separately from fees. FILLED consumes precisely
one reservation and debits purchase notional plus fee. CLOSED credits proceeds minus
fee and recognizes both entry and exit fees. CANCELLED releases the reservation once.
Repeated transitions reject. Cash and reservations must remain nonnegative and
reservations cannot exceed cash. All arithmetic uses existing Decimal PRECISION.

Sizing uses the frozen sizing helper with remaining portfolio, strategy and sector
headroom, symbol cap, cash, integer quantity, stop distance, volume participation,
price buffer and predeclared costs/slippage. Concurrent positions are bounded by the
smaller of the approved simultaneous-position and correlated-position limits. The
one-symbol-owner rule rejects competing strategies rather than silently netting them.
Exposures use the latest delivered completed-candle mark. Price moves can exceed
pre-trade exposure limits; limits do not promise gap-loss protection.

Daily realized and marked equity loss can trigger persistent paper HALT. HALT denies
new entries but known valid exits may reduce existing simulated positions. No automatic
resume occurs, including on the next day. Isolated paper authorization/HALT is never
connected to operational controls. Corrupt recovery data, policy/replay mismatch and
unbalanced accounting raise fail-closed paper-HALT errors without editing evidence.

At each configured session end, pending entries cancel and valid reservations release.
Known frozen EOD exits apply costs. Missing mandatory delivery triggers HALT; unresolved
positions remain visible and SESSION_END reports incomplete. No replacement candle,
price or successful liquidation marker is invented. Partial fills remain unsupported.
All journals and reports distinguish synthetic fixtures from authorized local paper
data; neither is a real broker execution or real trading profit.
