# Phase 12.1A — Historical security identity and ISIN reconciliation

Primary result: **UNIVERSE_CERTIFICATION_UNRESOLVED**. The historical security
identity objective is now closed for the five frozen NSE EQ instruments at the
daily reference level. All five advance from `IDENTITY_UNRESOLVED` to
`CORPORATE_ACTION_UNRESOLVED`. Acquisition remains **BLOCK**; strategy evaluation
remains disabled. No instrument was removed and no candle was requested.

## Baseline and preservation

Initial branch: `develop`. HEAD and the locally stored `origin/develop` both:
`0e71039705fc97f98cc25c747e05ba58f404444e`. No fetch, commit or push was performed.
The only initial working-tree entry was the untracked
`docs/operations/phase-12.1-revalidation.md`. It was preserved byte-for-byte.

The new generation is `research/phase-12.1a/generation-1/`. Original Phase 12.1
artifacts, including both evidence generations and the frozen policy, are
unchanged. The only production source change admits the new generation label
`phase-12.1a-g1` in `EvidenceManifest`; defaults, classifiers, policy pin, feature
behavior and Java gate logic are unchanged. Original Phase 12.1 offline replay
still produces its original fingerprint and classifications.

## Missing evidence addressed

The parent manifest lacked a dated start/end security reference and intervening
symbol/series coverage for every member. ICICIBANK had no observed series;
RELIANCE had issuer endpoint observations only. HDFCBANK and SBIN additionally
had conflicting ISIN fields between the action API and listing circulars.

This pass retrieved **99/99 official daily NSE CM MII security masters**, one for
each session in the frozen plan, February 2 through June 30, 2026. Each file's
complete download was SHA-256 hashed. The retained, canonically ordered CSV has
**5,148 identity/reference rows**, including **495 target EQ rows** and alternate
series for the same five symbols. It excludes price bands, OHLC, returns, trades,
account data and performance. Full downloaded files remain ignored scratch data.

[NSE/MSD/60315](https://nsearchives.nseindia.com/content/circulars/MSD60315.pdf)
establishes daily publication and filename semantics.
[NSE/MSD/67344](https://nsearchives.nseindia.com/content/circulars/MSD67344.pdf)
distinguishes NSE-listed and BSE-exclusive references.
[NSE/CMTR/73927, PART-D.xlsx](https://nsearchives.nseindia.com/content/circulars/CMTR73927.zip)
supplies the ISO-tag mapping in Annexure 10 and field meanings in Annexure 1:
equities `0`, listed `0`, normal-market eligible `1`, and not deleted `N`.
Encoded date fields and raw face-value fields are retained without inventing
Unix-epoch interpretations or undocumented unit conversions.

For every frozen session, the target EQ security has the same NSE ID, symbol,
ISIN, face-value field, lot size, equity type, listing status and eligibility.
BE rows exist but are ineligible throughout. T0 rows are separate references;
changes in T0 eligibility do not replace or interrupt the continuously eligible
EQ row. Scanning the complete masters for the five target ISINs found no other
EQ symbol/security ID. These observations establish daily identity/series
continuity; they do not assert uninterrupted intraday trading or broker-token
history.

## Start, intervening and end identity

The research window remains **[2026-02-02,2026-07-01)**, Asia/Kolkata.
Both endpoint records and all 97 intervening frozen sessions agree:

| Symbol | NSE security ID | Start ISIN | End ISIN | Series throughout | Coverage |
| --- | --- | --- | --- | --- | --- |
| HDFCBANK | 1333 | INE040A01034 | INE040A01034 | EQ | 99/99 |
| ICICIBANK | 4963 | INE090A01021 | INE090A01021 | EQ | 99/99 |
| LT | 11483 | INE018A01030 | INE018A01030 | EQ | 99/99 |
| RELIANCE | 2885 | INE002A01018 | INE002A01018 | EQ | 99/99 |
| SBIN | 3045 | INE062A01020 | INE062A01020 | EQ | 99/99 |

Original endpoint sources:
[February 2 master](https://nsearchives.nseindia.com/content/cm/NSE_CM_security_02022026.csv.gz)
and [June 30 master](https://nsearchives.nseindia.com/content/cm/NSE_CM_security_30062026.csv.gz).
The inventory lists every intervening dated URL and original download hash.
The NSE security ID is distinct from the broker instrument token; no historical
broker-token continuity is inferred from these records.

Frozen InstrumentIds and retained current broker mappings, with no fresh broker read:

| Symbol | InstrumentId | ZERODHA token |
| --- | --- | --- |
| HDFCBANK | 0dce64b8-7a7f-3960-bb69-725f2c4a456a | 341249 |
| ICICIBANK | b940f917-be88-36d4-85dc-89d747386c53 | 1270529 |
| LT | 12d53f0d-2e49-3374-83f9-82c91bf01a50 | 2939649 |
| RELIANCE | 31b3a1e4-ab49-3447-8b8c-fbef31c1b7af | 738561 |
| SBIN | 050f94dd-e639-364f-97a6-595594de6543 | 779521 |

## HDFCBANK reconciliation

`INE040A01018`, `INE040A01026` and `INE040A01034` belong to successive
denominations of HDFC Bank Limited's domestic equity, not three interchangeable
2026 instruments. The original FY2007-08 issuer report identifies `01018` with
NSE symbol HDFCBANK and distinguishes it from its overseas ADS. The FY2011-12
report identifies `01026` and describes the July 2011 subdivision of one INR10
share into five INR2 shares. The June 2011 results disclosure specifies approval
on July 6 and record date July 16. This chain is an inference from original issuer
records before and after the disclosed split; the original 2011 NSE conversion
circular and exact NSE ex-date were not obtained and are not invented.

The original 2019 MSE circular explicitly maps `01026` to `01034` for the INR2
to INR1 subdivision, effective September 20. NSE Clearing independently sets
the normal-market ex-date at September 19, 2019. NSE circular 73892 then directly
identifies the INR1 fully paid HDFCBANK EQ equity as `INE040A01034`, with further
ESOP shares admitted April 27, 2026. The June-quarter issuer filing and all 99
daily NSE masters corroborate that identity.

**Conclusion:** the action API's `INE040A01018` is a legacy predecessor identifier
and is not valid evidence of the 2026 EQ identity. This is a documented
reconciliation from stronger dated records, not a claim that NSE corrected or
explained the API. No identity segmentation is required inside the study window
by these older splits. No historical split is added to the 2026 action boundaries.

## SBIN reconciliation

Original MCX-SX circular 2345/2014 explicitly maps `INE062A01012` to
`INE062A01020` for the subdivision of one INR10 equity share into ten INR1 equity
shares. It specifies November 21, 2014 as record/effective date and November 20
as its exchange's ex-date. That source proves the issuer event and identifier
lineage; its ex-date is not silently promoted to an NSE ex-date.

NSE circular 69252 independently identifies SBIN EQ, `INE062A01020`, INR1 fully
paid equity, with the further QIP listing effective July 23, 2025. The May 8,
2026 issuer dividend filing corroborates NSE symbol SBIN and INR1 fully paid
equity. All 99 study-period NSE masters independently establish `INE062A01020`.

**Conclusion:** the action API's `INE062A01012` is the pre-split equity identifier,
not a contemporaneous alternative EQ security. Both identifiers concern the same
issuer's equity lineage, but are not interchangeable across the 2014 split. No
study-period identity break follows from that old event. The source of the stale
API field is still unexplained; no exchange correction is claimed.

## Exact evidence inventory and authoritative destinations

[source-inventory.json](../../research/phase-12.1a/generation-1/source-inventory.json)
contains **99 dated master sources and 17 freshly inspected document observations**.
Of the 17, nine add destinations absent from the parent review; eight freshly
inspect parent destinations and add their original-byte hashes under new evidence
IDs. The new manifest has **143 evidence entries**: 27 retained parent entries,
99 masters and 17 document observations. It has **135 distinct authoritative
destinations**, because eight parent destinations were inspected again.

Each daily URL follows the observed official archive pattern
`https://nsearchives.nseindia.com/content/cm/NSE_CM_security_DDMMYYYY.csv.gz`.
All 99 exact dates, URLs, download SHA-256 values, source row counts and retained
row counts are enumerated in the inventory. The 17 document observations are:

| New evidence ID (prefix `a-`) | Original authoritative record | Scope |
| --- | --- | --- |
| nse-master-spec | [NSE/MSD/60315](https://nsearchives.nseindia.com/content/circulars/MSD60315.pdf) | Daily master publication |
| nse-master-scope | [NSE/MSD/67344](https://nsearchives.nseindia.com/content/circulars/MSD67344.pdf) | NSE/BSE reference distinction |
| nse-cm-format-zip | [NSE/CMTR/73927](https://nsearchives.nseindia.com/content/circulars/CMTR73927.zip) | PART-D.xlsx field definitions |
| hdfc-2008-report | [FY2007-08 issuer report](https://v.hdfcbank.com/content/dam/hdfc-aem-microsites/common-pdfs/pdf/corporate/annual_report_07_08.pdf) | PDF p96, printed p97: old domestic equity ISIN |
| hdfc-2012-report | [FY2011-12 issuer report](https://www.hdfc.bank.in/content/dam/hdfcbankpws/in/en/pdf/annual-reports/2011-12/annual-report-2011-12.pdf) | PDF pp157–158, printed pp155–156: split and successor ISIN |
| hdfc-2011-record | [June 2011 issuer results](https://www.hdfc.bank.in/content/dam/hdfcbankpws/in/en/pdf/press-release/2011/q3-jul-sep/july/hdfcbanklimitedfinancialresultsindiangaapforthequarterendedjune302011.pdf) | p2: split approval and record date |
| hdfc-2019-clearing | [NCL/CMPT/42091](https://nsearchives.nseindia.com/content/circulars/CMPT42091.pdf) | NSE ex-date and old ISIN |
| hdfc-2019-isin | [MSE/LIST/8106/2019](https://www.msei.in/SX-Content/Circulars/2019/September/Circular-8106.pdf) | Original old/new ISIN mapping |
| sbin-2014-isin | [MCX-SX/LIST/2345/2014](https://www.msei.in/SX-Content/Circulars/2014/November/Circular-2345.pdf) | Original old/new ISIN mapping |
| hdfc-2026-listing | [NSE/CML/73892](https://nsearchives.nseindia.com/content/circulars/CML73892.pdf) | pp1,4: NSE EQ, ISIN, denomination, listing date |
| sbin-2025-listing | [NSE/CML/69252](https://nsearchives.nseindia.com/content/circulars/CML69252.pdf) | pp1,4: NSE EQ, ISIN, denomination, listing date |
| hdfc-june-identity | [July 18 issuer filing](https://nsearchives.nseindia.com/corporate/ixbrl/INTEGRATED_FILING_BANKING_175753_18072026160136_iXBRL_WEB.html) | June-quarter issuer equity identity |
| icici-identity | [April 18 issuer filing](https://nsearchives.nseindia.com/corporate/ixbrl/INTEGRATED_FILING_BANKING_151430_18042026180833_iXBRL_WEB.html) | ICICI Bank equity identity |
| lt-identity | [May 6 issuer filing](https://nsearchives.nseindia.com/corporate/ixbrl/INTEGRATED_FILING_INDAS_155858_06052026192958_iXBRL_WEB.html) | Larsen & Toubro equity identity |
| reliance-before | [January 16 issuer filing](https://nsearchives.nseindia.com/corporate/ixbrl/INTEGRATED_FILING_INDAS_135487_16012026193821_iXBRL_WEB.html) | Before-window issuer equity identity |
| reliance-after | [July 17 issuer filing](https://nsearchives.nseindia.com/corporate/ixbrl/INTEGRATED_FILING_INDAS_175608_17072026195004_iXBRL_WEB.html) | June-quarter issuer equity identity |
| sbin-dividend | [May 8 issuer filing](https://nsearchives.nseindia.com/corporate/SBIN_08052026154613_BSE_NSE_DividendRecordDate_08052026.pdf) | NSE symbol and fully paid INR1 equity |

Issuer documents hosted by NSE remain issuer evidence. MSE documents support
issuer event mechanics; NSE daily masters establish NSE continuity. Search
snippets were used for navigation only. No long copied source passages or raw
document dumps were added to the repository.

## Access limitations and residual work

The exact attempts and failures are in
[access-log.json](../../research/phase-12.1a/generation-1/access-log.json).
No depository-sourced identity finding is asserted:

| Attempted source | Access result | Missing record/question |
| --- | --- | --- |
| `https://nsdl.co.in/master_search.php` | Web reader internal error | Dated depository ISIN status/change history at February 2 and June 30 |
| `https://nsdl.co.in/downloadables/Issuer%20Details%20May%202020.pdf` | Direct connection closed; web reader internal error | Legacy denomination corroboration only; not 2026 coverage |
| `https://nsdl.co.in/downloadables/July-2011.pdf` | Direct connection closed; web reader HTTP 502 | Historical depository corroboration; no conversion finding inferred |
| `https://www.cdslindia.com/Publications/IssuerList.aspx` | HTTP 200 containing an internal-error page | No issuer records returned; historical status remains unverified by CDSL |
| `https://www.cdslindia.com/isin/isinissuersearch.aspx` | HTTP 200 containing an internal-error page | No equity ISIN record returned |

The guessed `.pdf` paths for NSE/MSD/55276 and NSE/CMTR/73927 returned 404.
The actual CMTR73927 `.zip` link was then obtained from NSE's circular index;
after one read timeout, its direct download succeeded and the original workbook
was inspected. The MSE SBI PDF screenshot timed out in the web reader, but the
original PDF downloaded successfully and its identifier table was rendered and
visually inspected locally. These are access failures, not negative findings.

The 2011 HDFCBANK and 2014 SBIN original NSE conversion notices remain unlocated;
their exact NSE historical ex-dates are not necessary to identify the already
converted securities in 2026. No provider-token history is certified.

The remaining blocking task is the parent's full corporate-action completeness
review for each issuer: unreviewed general-update attachments, earlier
announcements with in-window effect, and the remaining action taxonomy. A stable
ISIN can coexist with a bonus or an economic restructuring. Daily master identity
coverage therefore does not promote corporate-action completeness to certified.

## Actual certification and boundaries

| Symbol | Security identity | Symbol | Series | Intraday continuity | Cross-session continuity | Action completeness | Primary classification | Acquisition |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| HDFCBANK | CERTIFIED | CERTIFIED | CERTIFIED | UNRESOLVED | UNRESOLVED | UNRESOLVED | CORPORATE_ACTION_UNRESOLVED | BLOCK |
| ICICIBANK | CERTIFIED | CERTIFIED | CERTIFIED | UNRESOLVED | UNRESOLVED | UNRESOLVED | CORPORATE_ACTION_UNRESOLVED | BLOCK |
| LT | CERTIFIED | CERTIFIED | CERTIFIED | UNRESOLVED | UNRESOLVED | UNRESOLVED | CORPORATE_ACTION_UNRESOLVED | BLOCK |
| RELIANCE | CERTIFIED | CERTIFIED | CERTIFIED | UNRESOLVED | UNRESOLVED | UNRESOLVED | CORPORATE_ACTION_UNRESOLVED | BLOCK |
| SBIN | CERTIFIED | CERTIFIED | CERTIFIED | UNRESOLVED | UNRESOLVED | UNRESOLVED | CORPORATE_ACTION_UNRESOLVED | BLOCK |

Classification counts: `CORPORATE_ACTION_UNRESOLVED=5`; every other class zero.
Blocking members: all five. The frozen classifier requires both identity and
action completeness before certifying overall intraday/cross-session continuity.

Inherited known dividend boundaries remain HDFCBANK June 19 (INR13), LT May 22
(INR38), RELIANCE June 5 (INR6), and SBIN May 15 (INR17.35; record May 16).
ICICIBANK's disclosed August 3 record date remains outside the window, without
an invented NSE ex-date. The retained LT Realty-specific no-segmentation finding
is unchanged; this pass does not reclassify the scheme or pretend to complete
LT's economic-action review. Its dedicated nine-part discussion remains in the
[Phase 12.1 evidence report](phase-12.1-evidence.md#lt).

Raw OHLC remains unadjusted: no local split/dividend adjustment or synthetic
total-return series. Ordinary dividend boundaries invalidate opening-gap/
previous-close interpretation, not the action's same-session intraday bars.
No new in-window identity or segmentation boundary was found in the reference
records. The known boundaries are not asserted to be exhaustive economic events.

| Fingerprint | SHA-256 |
| --- | --- |
| Frozen policy v1 | `a350a24be35b6a844a0a769e8f14bd0821be3ca8bed44d4f8df5d32fc124f5f4` |
| Frozen universe | `638f11d8805f8d45724e8ade9a74e37652564046b699acd03033988ea4150395` |
| Parent evidence | `b97221c38ac0f2258a30ede00335a0b59d23ebe6a4b3d712d15cdd9fc9a8cce9` |
| Phase 12.1A evidence | `c981d1e10ed4a085adb5758213828fbcbf318f23736dc96315c200d6a71e08e8` |
| Phase 12.1A certification | `e6322b88e23e85e57c4154b7907ef09f219135f918bd7510ad7e6cf9dd628c36` |

## Replay, validation and file inventory

`run_phase121a.py` verifies the frozen input hashes, parent evidence, complete
session coverage and canonical reference ordering before invoking the existing
classifier and gate. It runs twice and compares classifications, boundaries,
fingerprint and gate. Its write mode creates missing derived artifacts only;
different retained output requires a new generation. Replay imports no network
client or study runner and does not read ignored downloads.

Fresh validation: **23 Python tests passed**, including a socket-disabled replay,
tampered-input rejection and rejection of an intervening missing EQ observation
even when the CSV is rehashed. **19 Java gate/acquisition tests passed**, including
counting-fake zero-provider-call denials. Ruff passed; strict mypy passed 35 files.
The original Phase 12.1 replay also passed unchanged. Final preservation hashes,
secret scan and whitespace results are recorded in `validation.json`.

One modified tracked file:
`apps/strategy-engine/src/strategy_engine/research/continuity.py` (generation
literal only). New Phase 12.1A files:

```text
apps/strategy-engine/tests/test_phase121a.py
scripts/research/run_phase121a.py
docs/operations/phase-12.1a-identity.md
research/phase-12.1a/generation-1/access-log.json
research/phase-12.1a/generation-1/acquisition-gate.json
research/phase-12.1a/generation-1/baseline.json
research/phase-12.1a/generation-1/continuity-boundaries.json
research/phase-12.1a/generation-1/evidence-freeze.json
research/phase-12.1a/generation-1/evidence-manifest.json
research/phase-12.1a/generation-1/instrument-certifications.json
research/phase-12.1a/generation-1/isin-reconciliation.json
research/phase-12.1a/generation-1/security-reference.csv
research/phase-12.1a/generation-1/source-inventory.json
research/phase-12.1a/generation-1/validation.json
```

The pre-existing untracked revalidation report remains an additional working-tree
entry. Ignored scratch downloads are not proposed Git changes.

Reproduce from the repository root:

```powershell
$env:PYTHONDONTWRITEBYTECODE = '1'
uv run --project apps/strategy-engine python scripts/research/run_phase121a.py
uv run --project apps/strategy-engine python scripts/research/run_phase121.py
uv run --project apps/strategy-engine pytest apps/strategy-engine/tests/test_continuity.py apps/strategy-engine/tests/test_phase121a.py -q
.\mvnw.cmd -pl apps/trading-core '-Dtest=HistoricalContinuityGateTest,MultiInstrumentCorpusTest' test
```

Historical candle requests, broker/account reads, order mutations, strategy
evaluations, database access/mutations, token access/mutations and HALT resume:
**zero**. H1/H2, strategy features and July TEST remain untouched. Only synthetic
metadata/feature fixtures execute in tests. No commit or push. Phase 12.2 has
not started.
