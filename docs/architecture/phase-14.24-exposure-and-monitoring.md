# Offline exposure and monitoring architecture

The additive phase1424 analytics module consumes Phase 14.20 synthetic session evidence.
Phase 14.21 validates and replays that evidence, and Phase 14.23 supplies accepted fills.
No executor, cash accounting, risk enforcement, qualification or indicator code is replaced.
All historical source and reports remain immutable.

Exposure observations represent the complete active-position snapshot at an explicit UTC
instant. Each position is linked to an accepted order and checked for quantity, strategy and
symbol ownership. Explicit synthetic constant-quote validity intervals are the only price
source admitted in this version. They are assumptions for measuring synthetic exposure,
not authoritative markets or executable prices. Quote observation and availability precede
valuation; validity must cover the whole declared interval. An accepted fill inside an
interval requires splitting the interval. Cash plus marked value must equal supplied equity.

The observation ledger must include session open, every covered interval and a terminal
session-close snapshot. Gaps remain gaps. Holidays are absent from the calendar's session
list; special-session start and end determine duration. A missing terminal boundary blocks
full metrics even when observed duration happens to equal the session duration.

Independent incident schedules and emitted alert ledgers are separate inputs. Schedule
fixtures are authored without inspecting alert output; their provenance and hashes are
recorded. Matching never mutates upstream alerts, acknowledgments, positions or HALT.
The historical Phase 14.20 alert model and Phase 14.21 acknowledgment verifier are reused.
Resolution requires separate explicit evidence and does not clear HALT.

Canonical hashes bind inputs and policies. External publication reuses the exclusive pending
file and atomic rename contract. Identical replay is idempotent; changed bindings or an
interrupted pending file fail closed. Local hashes cannot detect a complete rollback of all
local authority and do not establish production or multi-host durability.

Input limits are 64 MiB and 50,000 records. Matching is limited to two million incident-alert
comparisons; oversized workloads reject rather than consume unbounded time. Existing
upstream session and candle budgets also apply. No throughput claim is made.
