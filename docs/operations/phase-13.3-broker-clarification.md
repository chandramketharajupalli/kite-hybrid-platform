# Phase 13.3 broker clarification — DRAFT / NOT_SENT

Subject: Authoritative NSE equity MIS collateral availability and cash-field mapping

This refines the Phase 13.2 unsent draft. We are documenting evidence for NSE
cash-equity BUY / MARKET / MIS / DAY / REGULAR requests. The unresolved issue
is current eligibility and the cash-field contract, not whether collateral can
generally be used intraday. No order, instrument or quantity is proposed here.

1. **Eligible adjusted aggregate.** Which exact Kite API JSON field or broker
   report identifies haircut-adjusted pledged collateral eligible for this
   product/account after category and security restrictions? Please distinguish
   liquid/cash-equivalent, noncash and additional securities, pending/unconfirmed
   pledges, haircut changes and member limits. Is `available.collateral` that
   eligible aggregate, or only a broader total?
2. **Currently free portion.** Which authoritative field/report gives the amount
   remaining after positions, pending orders, collateral utilisation, reservations
   and encumbrances? Does it already deduct either `utilised.stock_collateral` or
   `utilised.liquid_collateral`? Please specify any supported computation and
   overlap rules so the same usage is not counted twice. If no API supplies it,
   identify the alternative and its update frequency.
3. **Exact cash requirement and category precedence.** For this NSE equity MIS
   shape, what positive-cash or cash-equivalent requirement applies, and when
   does a 50:50 condition apply? Please reconcile the positive-cash prerequisite
   in pledging terms with approved-list/category guidance allowing cash-equivalent
   coverage without separate cash. Which rule takes precedence for each category
   and does the F&O-specific ratio language have any applicability here? Please
   distinguish broker RMS policy from clearing-member allocation requirements;
   we do not infer an exemption from historical circular paragraph 18.
4. **Qualifying cash field.** Which exact equity funds JSON path or documented
   computation proves this prerequisite: `available.cash`, `opening_balance`,
   `live_balance`, or a different field? Is it net of pending commitments, charges,
   payout, unsettled sales, pay-ins and P&L? How are cash-equivalent pledges treated?
   Official UI articles describe Available cash both as current/mixed funds and
   prior closing balance; please identify the current UI/API mapping and superseded
   wording. The application's separate reserve will remain cash-only.
5. **Validity and request scope.** Can `/margins/orders` plus account funds establish
   any of these terms, or is additional broker-attested evidence needed? What
   timestamp/sequence/version and expiry identify a consistent snapshot? How
   must changes in positions, pending orders, charges, price/haircut and utilisation
   invalidate prior observations immediately before a future submission? Do any
   fields reserve capacity, or is a final race unavoidable?

Please reply with the applicable official source or confirmed field dictionary,
effective date, segment/product/account-category scope and any exceptions.
Please explicitly say when the requested term cannot be established from Kite
Connect. A general article or successful calculator response alone cannot prove
today's free amount for a specific account.

Use **synthetic examples only** to explain the mapping, for example an invented
adjusted aggregate of INR 8,000, recorded usage of INR 3,000, a pending commitment
of INR 1,000 and three deliberately different invented cash fields. State whether
the pending amount is already included in usage and which field/rule governs.
These are explanatory placeholders, not account values or an order-sizing request.
Do not infer that subtracting those examples is the supported broker formula.

Sources and conflicting descriptions are catalogued in
[the public-source review](../architecture/kite-mis-authoritative-evidence.md).
No personal identifier, account balance, holding, request/access token or credential
is included. This draft has not been sent and must not be sent automatically.
