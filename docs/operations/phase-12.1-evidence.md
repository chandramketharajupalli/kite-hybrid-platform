# Phase 12.1 evidence review

Primary result: **UNIVERSE_CERTIFICATION_UNRESOLVED**. This review improves the
metadata and resolves the specific LT Realty concern, but does not certify the
entire frozen universe. No instrument is removed or replaced. No candle, chart,
return, trade or strategy result was used to decide certification.

Policy was frozen first on October 8, 2026, followed by public research, the
27-source evidence manifest, then deterministic classification and offline replay.
Source titles, organizations, dates, supported findings, limitations and available
download hashes are in [the evidence manifest](../../research/phase-12.1/evidence-manifest.json).
All sources were accessed October 8. These retrospective metadata findings are
not falsely represented as a policy registered before the historical window.

Generation 1 is retained unchanged under `research/phase-12.1/generation-1/`.
Generation 2 corrects source authorship/type: issuer-authored filings hosted on
NSE remain ISSUER_PRIMARY, while NSE listing circulars are EXCHANGE_PRIMARY.
The policy, factual findings, coverage decisions, classifications and boundaries
are unchanged. This explicit provenance correction changes evidence and certificate
fingerprints rather than silently reinterpreting the original generation.

## Coverage and access

The target is [2026-02-02,2026-07-01), Asia/Kolkata. NSE action queries also cover
January 30-February 1 as a small entry boundary. Direct bounded official API GETs
succeeded after rendered action pages exposed only a shell. Five action responses
and five announcement indexes were retrieved; the latter contain respectively
70, 90, 77, 72 and 107 records in symbol order. A separate 21-entry July LT
announcement query located the filing describing June-end scheme status. It
contained metadata, not July prices or SBIN TEST observations.

Exactly 11 successful direct NSE JSON metadata GETs occurred. One direct attempt
to download the 12.7 MB LT June-results filing timed out; the smaller official
issuer copy was successfully inspected using the web reader. Raw JSON is ignored
under `data/phase121-reference/`, not included in the review inventory. The web
reader also inspected primary filings and pages; its internal fetch count is not
exposed, so 11 is not claimed as a count of all public web requests. Broker
reference reads, profile reads and historical candle GETs are all zero.

A complete dated action listing is stronger than a failed search. Nevertheless,
an action feed is not a security-master/series-change ledger. General-update
attachments and earlier-announced events effective inside the window were not
exhaustively resolved. A blank ICICIBANK action response is corroborated by a later
dividend record date, but is not independently treated as proof of every negative
corporate-action assertion. The full twelve-category checklist remains unresolved
where coverage is insufficient. No split, bonus or other event is claimed absent
merely because a search/index did not show it.

## Frozen member reviews

All mappings below are the already-reviewed Phase 12.0 current ZERODHA mappings,
not newly fetched historical reference evidence. The symbol/ISIN observations
below do not prove uninterrupted EQ trading for all 99 sessions. Each member's
security identity, symbol continuity and series continuity therefore remains
UNRESOLVED under the frozen policy. Intraday and cross-session certification
also remains UNRESOLVED overall even when an individual action's mechanics are known.

### HDFCBANK

- InstrumentId: `0dce64b8-7a7f-3960-bb69-725f2c4a456a`; ZERODHA `341249`.
- [NSE circular 73892](https://nsearchives.nseindia.com/content/circulars/CML73892.pdf)
  dated April 24, effective April 27, records EQ, ISIN `INE040A01034`, INR 1
  face value, and pari-passu ESOP allotment. The July 18 June-quarter filing
  corroborates this ISIN. The action feed instead returns `INE040A01018`.
  This is a recorded source conflict; no silent correction or inferred split.
- [NSE dated actions](https://www.nseindia.com/api/corporates-corporateActions?index=equities&symbol=HDFCBANK&from_date=30-01-2026&to_date=30-06-2026)
  supplies INR 13 cash dividend, ex/record June 19. The
  [April issuer filing](https://nsearchives.nseindia.com/corporate/HDFCBANK_18042026145701_SEResultOutcome18042026.pdf)
  corroborates the dividend/record-date disclosure.
- Invalid price-gap boundary: June 19; ordinary dividend volume history retained.
  No additional boundary is certified absent. No raw adjustment.
- Corporate-action completeness: UNRESOLVED. Primary: **IDENTITY_UNRESOLVED**.
  Acquisition: **BLOCK**.
- Needed: authoritative reconciliation of the action-feed ISIN, dated security
  reference/series-change coverage through June 30, and completion of the remaining
  action/announcement review, including any pre-window effective notices.

### ICICIBANK

- InstrumentId: `b940f917-be88-36d4-85dc-89d747386c53`; ZERODHA `1270529`.
- [April 18 exchange-hosted filing](https://nsearchives.nseindia.com/corporate/ixbrl/INTEGRATED_FILING_BANKING_151430_18042026180833_iXBRL_WEB.html)
  identifies ICICI Bank Limited equity as `ICICIBANK`, ISIN `INE090A01021`.
  It is not evidence of every intervening NSE trading series.
- [NSE dated actions](https://www.nseindia.com/api/corporates-corporateActions?index=equities&symbol=ICICIBANK&from_date=30-01-2026&to_date=30-06-2026)
  returns an empty valid JSON list. The
  [June 29 issuer notice](https://www.icici.bank.in/content/dam/icicibank/india/managed-assets/docs/about-us/2026/NSEBSE_29062026.pdf)
  fixes August 3 dividend record date and August 21 AGM: announced inside,
  entitlement outside. No NSE ex-date is invented from the record date.
- No confirmed in-window boundary; this is not a complete no-action certificate.
  [Issuer 2026 disclosures](https://www.icici.bank.in/about-us/disclosures-to-stock-exchanges/2026)
  include ESOS/ESUS allotments, distinct from an automatic split of existing shares.
- Corporate-action completeness: UNRESOLVED. Primary: **IDENTITY_UNRESOLVED**.
  Acquisition: **BLOCK**.
- Needed: dated NSE EQ/security reference and change-history coverage, plus complete
  action/identity review rather than reliance on the empty action response.

### LT

- InstrumentId: `12d53f0d-2e49-3374-83f9-82c91bf01a50`; ZERODHA `2939649`.
- [May exchange-hosted filing](https://nsearchives.nseindia.com/corporate/ixbrl/INTEGRATED_FILING_INDAS_155858_06052026192958_iXBRL_WEB.html)
  identifies Larsen & Toubro Limited equity, `LT`, `INE018A01030`.
  [NSE action metadata](https://www.nseindia.com/api/corporates-corporateActions?index=equities&symbol=LT&from_date=30-01-2026&to_date=30-06-2026)
  identifies EQ and INR 38 cash dividend, ex/record May 22.
- Invalid price-gap boundary: May 22; volume references retained for this dividend.
- Specific Realty scheme: **NO segmentation required for this event in the target
  window**, for the reasons below. This does not certify every identity dimension.
- Corporate-action completeness: UNRESOLVED. Primary: **IDENTITY_UNRESOLVED**.
  Acquisition: **BLOCK**. Needed: full-window NSE identity/series coverage and
  remaining action review. LT is neither removed nor given a shorter window.

#### Does the Phase 12.0 L&T restructuring concern require segmentation of NSE:LT during [2026-02-02,2026-07-01)?

**NO.** This answer concerns the identified Realty Undertaking scheme, not a
blanket certification of LT's complete action history.

1. The scheme transfers L&T's Realty Undertaking as a going concern/slump sale
   to its wholly owned L&T Realty Properties Limited (LTRPL).
2. The transferor is the listed Larsen & Toubro Limited; the transferee is LTRPL.
   It is not a merger of the listed LT security into another listed security.
3. The parent participates legally, but the described consideration consists of
   LTRPL shares issued to L&T itself. The reviewed terms do not distribute a new
   security to LT public shareholders or exchange/cancel their LT shares.
4. No LT split, bonus, public share entitlement or symbol/ISIN change is specified
   by this scheme. That statement is scoped to the scheme, not all possible events.
5. Board approval: December 8, 2025. Proposed appointed date: April 1, 2026 or
   another agreed date. NSE observation letter: March 19. NCLT first-motion orders:
   June 12/16; issuer received them June 24. These directed a shareholder meeting,
   rather than declaring the scheme effective. The issuer flagged a typographical
   date error in an order; no erroneous December date is adopted.
6. The proposal and legal process intersect the window. The July 28 filing for
   June 30 still says requisite approvals are pending and the undertaking remains
   included in the company's results. The appointed date is not a proven trading
   effective date.
7. These facts support the inference that this scheme creates no mechanical
   LT raw-price/security boundary during February-June. No April 1 reset is added.
8. Segmentation for this named concern: NO. No synthetic adjustment is appropriate.
9. Primary support:
   [Board report](https://investors.larsentoubro.com/board-report.aspx),
   [March results note iv](https://investors.larsentoubro.com/upload/Quarterly/FY2026QuarterlyLTResultMarch2026.pdf),
   [NSE observation](https://nsearchives.nseindia.com/corporate/PAM_19032026161557_NSE_BSE_Response.pdf),
   [June 24 filing](https://nsearchives.nseindia.com/corporate/PAM_24062026190028_Reg3024062026signed1.pdf),
   [June-quarter results, standalone note ii](https://investors.larsentoubro.com/upload/Quarterly/FY2027QuarterlyLTJune2026-website.pdf).

Separate events must not be conflated: SuFin business transfer to a wholly owned
subsidiary on April 1; L&T Power Development's Nabha subsidiary-stake divestment
on June 25; and the April 29 Hyderabad Metro share-sale agreement. The latter's
original expected June completion was revised to expected September closing
conditions in the June-quarter filing. These are business/subsidiary transactions,
not evidence of an LT shareholder split or distribution. Their disclosure does
not by itself prove exhaustive historical security continuity.

### RELIANCE

- InstrumentId: `31b3a1e4-ab49-3447-8b8c-fbef31c1b7af`; ZERODHA `738561`.
- [January filing](https://nsearchives.nseindia.com/corporate/ixbrl/INTEGRATED_FILING_INDAS_135487_16012026193821_iXBRL_WEB.html)
  and [June-quarter filing](https://nsearchives.nseindia.com/corporate/ixbrl/INTEGRATED_FILING_INDAS_175608_17072026195004_iXBRL_WEB.html)
  both identify Reliance Industries Limited equity, `RELIANCE`, `INE002A01018`.
  Matching endpoint observations do not prove no intervening series change.
- [NSE dated actions](https://www.nseindia.com/api/corporates-corporateActions?index=equities&symbol=RELIANCE&from_date=30-01-2026&to_date=30-06-2026)
  supplies INR 6 dividend and EQ series, ex/record June 5;
  [May 27 notice](https://www.ril.com/sites/default/files/2026-05/SE_27052026.pdf)
  independently confirms the record date. Invalid gap boundary: June 5.
- No older Reliance bonus/demerger is imported as an in-window event. Full
  split/bonus/spinoff/restructuring coverage is not asserted from that omission.
- Corporate-action completeness: UNRESOLVED. Primary: **IDENTITY_UNRESOLVED**.
  Acquisition: **BLOCK**. Needed: dated NSE series/change coverage and complete
  action-review coverage for this issuer, not another Reliance group company.

### SBIN

- InstrumentId: `050f94dd-e639-364f-97a6-595594de6543`; ZERODHA `779521`.
- [NSE listing circular 69252](https://nsearchives.nseindia.com/content/circulars/CML69252.pdf)
  records EQ, INR 1, `INE062A01020`. The bounded action feed gives `INE062A01012`.
  The older circular is examined only to investigate that conflict; neither is
  silently treated as a complete current-period security master.
- [May 8 issuer filing](https://nsearchives.nseindia.com/corporate/SBIN_08052026154613_BSE_NSE_DividendRecordDate_08052026.pdf)
  confirms INR 17.35, record May 16, payment June 4.
  [NSE dated action record](https://www.nseindia.com/api/corporates-corporateActions?index=equities&symbol=SBIN&from_date=30-01-2026&to_date=30-06-2026)
  independently establishes the NSE ex-date as May 15. It is not inferred from
  the Saturday record date or another exchange's calendar.
- Invalid ordinary gap boundary: May 15; dividend alone preserves intraday OHLC
  and volume-reference eligibility. Overall identity certification remains blocked.
- Corporate-action completeness: UNRESOLVED. Primary: **IDENTITY_UNRESOLVED**.
  Acquisition: **BLOCK**. Needed: authoritative ISIN reconciliation and complete
  dated NSE security/series/action coverage.
- Phase 11 opening-gap diagnostics have a newly normalized known dividend-boundary
  limitation. Their artifacts, feature code, H1/H2 registration and results are
  not rewritten. Any corrected analysis requires a new study generation.

## Handoff

The narrow next work is official historical NSE security/series change coverage
for the five fixed members, reconciliation of two ISIN discrepancies, and the
remaining completeness review of effective corporate actions. Obtain exchange
reference/correction records or issuer/exchange filings sufficient to close those
specific dimensions. No new stocks, price-gap inference or strategy runs can
substitute for them. Acquisition remains blocked; Phase 12.2 is not started.
