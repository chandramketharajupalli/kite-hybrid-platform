# Phase 14.41 methodology compatibility

Candidate status PROPOSED_NOT_ADOPTED. No active Series schema, entry point, strategy, execution or accounting code changed. The compatibility module has no filesystem, credential or network client. Genuine acquisition and retention remain unauthorized.

Series v1 (phase14.engine.Series) contains a single symbol and token, source/master/calendar SHA-256 references, UTC bounds/as_of, sessions/non-trading dates, Candle tuple, reviewed boolean and exactly PROVIDER_UNSPECIFIED_NO_LOCAL_ADJUSTMENT. Candle uses the existing Decimal Bar, five-minute completion and alignment checks. Series checks order/window/calendar coverage; quality() checks missing/outside intervals. It does not contain a mapping timeline, stable security identity, corporate-action source completeness or explicit provider adjustment basis. Phase14.40 adds those evidence gates externally and refuses action-bearing or multi-token integration. A v1 instance alone is not proof of action completeness or methodology compatibility.

Candidate v2 stores only synthetic period/segment evidence. It is not a Series subtype or registered adapter. Legacy check accepts an already constructed v1 Series, verifies exact hashes and identity, and returns a fingerprint; it never transforms candles or fabricates a homogeneous token. Existing research evaluation and deterministic Phase14.40 report are replayed unchanged.

| Scenario | Required evidence and adjustment implications | Identity and availability | v1 decision / comparability |
|---|---|---|---|
| No action | Complete authoritative no-event coverage; source basis | One compatible identity, evidence known at boundary | Existing Phase14.40 gates only; unchanged legacy family |
| Split | Ex-date and ratio convention; price/volume basis and rounding unresolved | Same security or evidenced transition; announcement distinct from effect | Block local adjustment; separate proposed methodology |
| Bonus | Issued-to-held ratio distinct from post/pre share factor | Entitlement/ex-date and availability evidence | Block; no reuse of split factors without convention |
| Cash dividend | Gross/net cash, ex-date, total-return versus price-return choice | Announcement and effective dates | Block; no assumed volume adjustment |
| Rights | Subscription terms, participation and pricing policy | Security entitlements and availability | Block; no generic factor |
| Merger | Consideration/cash/share conversion and continuity | May be distinct securities | Block; separate result family |
| Demerger | Allocation and valuation methodology | One-to-many identities cannot be flattened | Block; separate result family |
| Symbol change | Verified exchange transition | Explicit old/new symbols and stable identity evidence | Timeline representable; v1 conversion blocked |
| Delisting | Final trading date/settlement evidence | End coverage explicitly; no inferred terminal value | Block pending policy |
| Suspension | Verified interval and reopen evidence | Absence of candles does not prove suspension | Existing calendar rules plus evidence; candidate does not fill gaps |
| Token replacement | Historical mapping generations and transition proof | Same security, contiguous half-open periods | Timeline representable; no single-token substitution |
| Provider adjusted | Provider basis/version and action provenance | Historical versus retrospective availability labeled | Unspecified v1 literal cannot assert verified adjustment |
| Locally adjusted | Separately adopted methodology and source linkage | Explicit retrospective normalization if applicable | Not authorized; no transformation engine |
| Mixed bases | Per-segment basis and reconciliation | Comparability unresolved | Reject combining families |

All fourteen are assessed conservatively. Blocking v1 conversion is an assessed compatibility outcome, not adoption of v2. Genuine performance remains null and qualified pairs zero. Full-universe metadata is preserved separately from representative one-symbol integration.
