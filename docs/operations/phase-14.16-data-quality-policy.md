# Offline data trust and quality policy

Supported input: strict UTF-8 HistoricalResearchDataset.five-minute.v1 JSON exports.
Duplicate JSON keys, invalid encoding, excessive sizes, unsupported intervals,
non-NSE or conflicting identities, invalid OHLCV, unordered/duplicate candles and
fingerprint mismatches reject import. No CSV heuristic or parallel candle schema.
Export timestamps retain UTC interval-start semantics; explicit calendar dates and
sessions use Asia/Kolkata. Weekdays are never treated as exchange-calendar evidence.
Each calendar uses the existing Java-compatible fingerprint and at most 32 dates.

SYNTHETIC_FIXTURE remains labeled synthetic and cannot qualify as genuine performance.
AUTHORIZED_LOCAL_EXPORT requires exact permission, source and retention evidence.
Missing permission demotes it to UNVERIFIED_LOCAL_DATA and quarantine. Other critical
quality gaps quarantine it even if permission is present. A locally present file,
an arbitrary token, or a hash alone is not evidence of a resolved genuine instrument.

Permission JSON must contain a nonblank `reviewer`, `purpose: OFFLINE_RESEARCH`,
`source`, ordered `raw_hashes`, `retention_until`, and the exact identity/window scope.
Scope keys are `symbol`, `instrument_id`, `instrument_token`, `start`, `end`;
timestamps in evidence use Python UTC ISO text, including `+00:00`.
Mapping evidence must bind that scope, `master_hash`, `exchange: NSE`,
`resolution: RESOLVED`, `candidates: 1`. It records a reviewed existing offline
resolution; this Python adapter does not acquire or manufacture a dated master.
Calendar evidence binds ordered `calendar_hashes` and `authoritative: true`.
Corporate-action evidence binds scope, `reviewed: true` and
`policy: PROVIDER_UNSPECIFIED_NO_LOCAL_ADJUSTMENT`. Every evidence file has an
explicit raw hash and reviewer. Review the substantive evidence before authorizing
its use; these fields cannot independently authenticate a publisher or reviewer.

Missing authoritative calendar, unresolved corporate actions, unverified mapping,
missing mandatory bars and unexpected bars cannot yield genuine performance.
No interpolation, implicit adjustment, token substitution or automatic trust promotion.
Retention is checked both at import and job execution/replay using an injected UTC
time. Expired data cannot execute. The application never auto-deletes accepted data;
an operator must separately authorize retention cleanup after reviewing references.

Full-family qualification remains 13,380 hypotheses, 70% observed holdout win rate,
frozen minimum trade counts, costs, drawdown and multiple-testing controls. Missing
data produces null metrics. Reported metrics use the holdout period; checkpoints retain
full training, validation, total and holdout details. No available dataset means no
qualified opportunity, irrespective of completeness of the reporting matrix.
