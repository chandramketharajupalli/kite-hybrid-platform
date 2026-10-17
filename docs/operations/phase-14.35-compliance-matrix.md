# Compliance evidence matrix

Original numbered response, sender identity, message timestamp, message ID/ticket, original source location, custody restrictions and raw evidence hash are unavailable in the identified local records. Source authenticity: UNVERIFIED. Topic IDs below are internal identifiers, not reconstructed response numbering. Exact source statement for every row is UNAVAILABLE; topic descriptions are user-reported paraphrases, not quotations. No private correspondence is published.

| Topic | Reported subject | Operation/use | Evidence classification | Missing clarification / retention | Authorization |
|---|---|---|---|---|---|
| T01 | NSE instrument-master access | Instrument-master GET candidate | UNVERIFIED_SOURCE | Exact endpoint entitlement and access conditions; retention unresolved | NOT_AUTHORIZED |
| T02 | Daily refresh and 1,115-symbol mapping | Master refresh and mapping | UNVERIFIED_SOURCE | Refresh authorization, snapshot retention, stale-token policy; retention unresolved | NOT_AUTHORIZED |
| T03 | Exchange plus symbol stable key | Mapping identity | UNVERIFIED_SOURCE | Symbol changes and token-reuse handling; retention unresolved | NOT_AUTHORIZED |
| T04 | Personal-use limitations | All data use | UNVERIFIED_SOURCE | Scope, distribution and derivative-output restrictions; retention unresolved | NOT_AUTHORIZED |
| T05 | Historical analysis and backtesting | Historical candle GET candidate | UNVERIFIED_SOURCE | Exact interval/window entitlement and use permission; retention unresolved | NOT_AUTHORIZED |
| T06 | Permanent API-data storage restrictions | Raw and derived storage | UNVERIFIED_SOURCE | Permitted duration, deletion and backup conditions; retention unresolved | NOT_AUTHORIZED |
| T07 | Live-data paper-trading restrictions | Live streaming/paper use | UNVERIFIED_SOURCE | Explicit permission absent; keep disabled; retention unresolved | NOT_AUTHORIZED |
| T08 | Order-rate thresholds | Order API | UNVERIFIED_SOURCE | No order authorization inferred; retention unresolved | NOT_AUTHORIZED |
| T09 | Registered public static IP | Production/API access | UNVERIFIED_SOURCE | Applicability and approved configuration unresolved; retention unresolved | NOT_AUTHORIZED |

Historical sources inspected: Phase 14.11 compliance-submission evidence describes a user-reported sent message, not a verified reply; Phase 14.9 response protocol and terms matrix retain unresolved questions. Their historical states remain unchanged. No public terms were refreshed or legal conclusions drawn in this offline phase.

Future classification must separately record EXPLICIT_WRITTEN_PERMISSION, EXPLICIT_RESTRICTION, AMBIGUOUS_LANGUAGE, UNVERIFIED_SOURCE or INTERNAL_POLICY_ASSUMPTION with exact clause evidence. Reported restrictions are reasons to retain the stricter gate, not independently authenticated legal findings. Conflicting clauses block acquisition pending clarification. Personal use, backtesting, rate thresholds and daily refresh never imply indefinite retention, live paper trading or order permission.
