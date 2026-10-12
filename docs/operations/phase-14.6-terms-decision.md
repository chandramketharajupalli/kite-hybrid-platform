# Terms decision ledger

TERMS_UNRESOLVED. Public sources retrieved within2026-10-12T10:57:14Z to10:57:27Z;
these are measured observation bounds, not publisher versions or approval timestamps.
[Terms](https://kite.trade/terms/), [instrument API](https://kite.trade/docs/connect/v3/market-quotes/#instruments),
[support channel](https://support.zerodha.com/). No account/contract or authenticated
session inspected; no raw broker content acquired.

| Use | Clause/source | Decision / unresolved question |
|---|---|---|
| Private authenticated download | Terms2,2(a); Instruments endpoint | Documented mechanism; account-specific entitlement PENDING |
| Temporary CSV | Instrument storage guidance;4(b) | Caching recommended, authorized duration unclear |
| Persistent mappings |4(b) | Copy/database restriction; permission PENDING |
| Derived token-symbol records |4(b) | Derivative-content scope needs clarification |
| Research database/backtests |2(a),4(b) | Mock-trading restriction versus private research unresolved |
| Audit hashes/metadata |5(a),4(b) | Audit obligation does not automatically authorize raw retention |
| Redistribution/sharing |2(a),4(b) | Restrictions apply; no redistribution proposed |
| Subscription termination |9(b),2(a) | Termination deletion addressed; subscription lapse equivalence unclear |
| Exchange/broker licensing |1,2,13 Conflict | Account/exchange agreements may govern; applicability unverified |

Instrument documentation recommends daily database storage and exchange+tradingsymbol
identity rather than permanent numeric-token identity. This does not resolve the
specific retention and private research scope above. We do not conclude all personal
storage is prohibited; we decline to infer permission for this project.

Decision attribution: Phase14.6 engineering assessment at Git1b26394f5f66e4844db12adb3d9f8d19aa4ade41,
not legal advice, an owner signature, independent review or a broker response.
Required closure: attributable clarification/contract basis per category, retention
and deletion policy, entitlement and separate explicit owner decision. The consolidated
draft is unsent; no fictitious vendor answer, expiry or consent has been recorded.
