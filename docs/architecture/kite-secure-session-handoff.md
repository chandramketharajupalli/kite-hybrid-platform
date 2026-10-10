# Synthetic Kite session handoff and isolation contract

Phase 13.5A, 2026-10-10. Baseline dc614fa78bc4cc0c07c9209d9243826ec1faa7f7.
REAL_HANDOFF_NOT_ESTABLISHED. No real broker operation is enabled by this design.
The preserved Phase 13.5 Stage B outcome is ZERO_CALL_ABORT, zero real requests.

## Trust boundaries

KiteEquityReadHandoff is package-private and has no Spring bean, controller,
scheduler, CLI or normal startup wiring. It accepts only the existing loopback
wire factory. It rejects the official-origin factory before request construction.
It receives an already authenticated synthetic KiteSession; it never calls login,
profile validation, install, restore, reset or a token store. Authentication and
risk/funding policy are unchanged. No second broker client or risk engine exists.

The trusted test composition privately holds Owner and Recipient object references.
A consumer must possess those exact objects and the handoff object. The private
UUID is a unique label, not authentication; it is not exposed or accepted as an
input. The handoff never returns a session, token, authorization header or mutable
snapshot. There is no portable credential or capability artifact. Java native
serialization is unsupported; JSON field/getter autodetection is explicitly disabled
even for a mapper configured to detect private fields. JSON produces only an empty
object, never a usable portable capability or session. toString
is a constant redacted label. This is an object-capability boundary inside trusted
code, not protection against reflection, agents, hostile code in the same JVM,
process compromise or OS administrators. No authenticated local IPC is implemented.

## Lifecycle and dispatch

Issue binds session execution identity, HALT object and epoch, source owner,
recipient, local clock, expiry (at most 30 seconds) and isolation lease. The fixed
scope is GET /user/margins/equity; no caller URL is accepted by consume. Inspect
returns only eligibility to attempt consumption, never secret metadata. Wrong
owner/recipient revokes. Atomic consumption chooses one winner; the losing thread
cannot dispatch. Invalid consumption burns the capability. Revocation follows
owner closure, logout/auth loss, session replacement, HALT epoch/state change,
failed witness, time reversal observed at a check or expiry. Once rejected it does
not become valid when a clock or witness recovers. Restart has no recovery path;
new owner/recipient objects cannot consume an old capability. Process termination
loses all in-memory objects; fixture lock cleanup is independently tested.

The handoff supplies checks before the database baseline, after it, at request
creation, immediately before HTTP execute and after the response. It closes its
harness after consumption. The factory retains exact URI/method checks, disabled
retries/redirects and a process-wide official one-attempt budget. Synthetic factories
have per-instance budgets; a consumed handoff cannot obtain another factory or
renew a budget. A reserved request may report one attempt even if a final guard
prevents network dispatch; peer counts are the authoritative synthetic HTTP counts.
Response loss spends the attempt. No automatic auth restoration follows errors.

Checks fence observations, not time itself: arbitrary external changes between
checks cannot be prevented by an in-process boolean or JDBC snapshot. During-read
loss discards evidence and cannot dispatch another request. The synthetic witness
models controlled fixture exclusion; it is not accepted for any official wire.

## Isolation and PostgreSQL

KiteEquityReadIsolation is an expiring, revocable lease over a separate witness.
Unavailable/throwing/false witness fails closed. Tests use an OS file lock in a
unique disposable directory, including another child JVM's lock denial and crash
release. This proves cooperating fixture exclusion only. It does not cover actors
which ignore the lock, alternative paths, other hosts, Windows services/scheduled
tasks, WSL, Docker writers, administrators or users holding broker credentials.
No real global lock is installed and no unrelated process is stopped.

The existing database observer uses only hardcoded SELECT/SHOW on a caller-owned
connection. It requires server transaction_read_only=on, autocommit READ COMMITTED,
pg_read_all_stats membership, no other client backend/prepared transaction, no
superuser/creation/replication/bypass privileges and no other role memberships.
It rejects database CREATE/TEMP, application schema CREATE, executable
application-defined functions, trading-table write/TRIGGER rights, sequence
USAGE/UPDATE and RLS-hidden tables. Function denial is deliberately conservative,
including apparently harmless application functions; no function is executed to
probe safety. PostgreSQL system functions are outside this application-function
check; safety also depends on the fixed query set, transaction-read-only state
and restricted role. This is not a general-purpose safe SQL console.

The previously fixed CASE guard ensures sequence privilege checks are evaluated
only for sequence relations. Required tables, one encrypted token row, zero
allowed authorization rows and complete SELECT access remain mandatory. Private
fingerprints cover canonical sorted row text and counts plus column schema shape
(name/order/type/modifier/nullability), detecting empty-table schema changes too.
No raw rows or fingerprints are emitted. Each read is a fresh autocommit snapshot;
there is no claimed atomic multi-table snapshot. Statistics are checked at both
ends. A writer which connects later, writes then rolls back, or writes then restores
identical state between observations cannot be excluded by these fingerprints.
Disposable tests distinguish a rejected concurrent connection, committed same-count
change, schema change and rolled-back changes. External isolation must close that
gap before a future real review. No live DB privilege proof was collected.

## HALT, logs and evidence

The handoff binds one HALT object's epoch and requires startup and runtime HALT.
This does not establish HALT in another process. RuntimeTradingHalt treats an
unavailable startup supplier conservatively as halted for safety, so its boolean
alone is not positive evidence of a known live configuration; independent witness
and later live review remain required. Architecture guards forbid execution,
operator, strategy, risk, startup, token-store and auth-lifecycle dependencies.
No operational permit, candidate quantity or order can be returned.

Captured synthetic logs are checked for marker tokens, headers, payload text and
balances; verbose Apache wire/header DEBUG aborts before HTTP. Such tests do not
prove actual live log sink permissions, agent instrumentation, tracing or dump
configuration. A future reviewer must verify all sinks, retention, ACLs and debug
settings independently. Committed output contains only bounded outcomes and
synthetic provenance. Ignored Maven reports contain synthetic fixtures only.

The existing equity observation and V1 collateral contract are unchanged.
SYNTHETIC_TRANSPORT never becomes broker attestation. Eligible adjusted collateral,
free collateral, exact NSE MIS cash rule and qualifying cash-field mapping remain
unproven (public-source conflicts remain recorded separately). Collateral-assisted
MIS stays NOT_READY. Cash reserve, CNC and INR 10,000 full buffered notional remain
unchanged. Successful engineering tests grant no real-read or trading authority.
