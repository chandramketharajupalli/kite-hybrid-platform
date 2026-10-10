# Draft clarification for Zerodha — not sent

Subject: Authoritative collateral availability and cash-field mapping for NSE equity MIS

We are documenting a read-only funding evidence contract for NSE cash-equity
BUY / MARKET / MIS / DAY / REGULAR requests. We understand that pledged margin
can support equity intraday. Our questions concern current account-specific
eligibility and the exact data needed to establish it. No order is proposed.

1. Which exact Kite Connect API field or Console statement reports currently
   available, haircut-adjusted pledged collateral eligible for this product
   after utilisation, encumbrances, debit restrictions and pending obligations?
   Does `available.collateral` already deduct either utilised collateral field?
   Please explain why available and utilised collateral might have equal values
   without allowing us to count or subtract the same amount twice.
2. How do cash-equivalent/liquid and non-liquid categories, approved versus
   additional securities, member pledge limits and pending pledge/unpledge
   requests affect that available amount? Is there an authoritative eligible
   aggregate, or a per-category/per-security adjusted contribution and timestamp?
3. What precise positive-cash and cash-component requirements apply to this
   NSE cash-equity MIS shape? Does a 50:50 rule apply, under which categories or
   conditions, and how does it differ from the documented F&O rule? How should
   the approved-list cash-equivalent wording be reconciled with the positive-
   cash prerequisite? Please identify the effective policy/version.
4. Which exact `equity` funds JSON field (or other broker ledger field) proves
   compliance: `available.cash`, `opening_balance`, `live_balance`, or another
   field? Please specify pay-in, unsettled-sale proceeds, P&L, ad-hoc credit,
   cash-equivalent collateral and utilisation exclusions. The application's
   separate cash-only reserve is not intended to redefine broker policy.
5. Can `/margins/orders` together with funds and holdings establish final
   collateral/cash eligibility? If not, which additional broker-authoritative
   assertion is required, with what account/product/request scope and validity?
   Does the calculator merely estimate margin and charges, or certify any cash
   or collateral condition? Please identify a documented response field rather
   than relying on successful calculation as implicit proof.
6. How should changing positions, pending/partially filled orders, charges,
   realised obligations and collateral utilisation be reconciled immediately
   before submission? Which timestamps/sequence markers permit a consistent
   observation, and what race/expiry limits remain between sequential reads?

Please provide current official documentation or a broker-confirmed field
dictionary, segment/product applicability and effective date. If no such API
field exists, please state that explicitly and identify the authoritative
alternative. No account identifier, credential, balance, holding or order
quantity is included in this draft. Do not send it automatically.
