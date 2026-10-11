# DiagnosticAssuranceContract.v2 proposal

Version: DiagnosticAssuranceContract.v2, draft 1, 2026-10-11.
Status: PROPOSED / APPROVAL_PENDING / CONTRACT_CHANGE_PENDING_APPROVAL.
Baseline: a191b9c455c5bc916519846e431ba8341bc799dc. Not the contract in force.
Exact-byte SHA-256 is recorded in phase-13.5g-validation.md; revisions require a
new draft identifier and digest. The Phase F v1 proposal and historical Phase 13.5
approval remain unchanged. Stage B ZERO_CALL_ABORT is preserved.

## Normative claim and material change

The only proposed trading-state claim is: **the isolated diagnostic does not mutate
trading state**. Admission MUST independently establish that the owner/request path
has no direct or indirect trading DB or trading-write authority for its lifetime.
Package, OS identity, filesystem, network and deployment evidence are all required.
This proposal itself proves none of those live conditions. Global trading DB
nonmutation is NOT_PROVEN: other writers may remain active, including privileged
administrators and remote processes. No matching snapshot can prove otherwise.

Removing the old global integrity requirement is a material contract change,
not a new way to satisfy the old approval. Security/authentication, runtime/DB,
packaging/logging and user/operator reviewers MUST explicitly approve the narrower
claim and residual risk separately. Until then old guards stay intact and NO_GO
continues. If global invariance remains required, reject v2 as insufficient.

| Assurance | Old Phase 13.5 requirement and actual limit | Proposed v2 | Approval consequence |
|---|---|---|---|
| Diagnostic DB noninterference | Restricted observer connection; SELECT/SHOW and before/after checks; no lifecycle writes | No trading DB access or indirect mutation authority at all; independently certified minimal owner | Different prevention argument; unimplemented |
| Global DB invariance | Exclusive observation window and inability of other writers to reconnect required; snapshots alone insufficient | Explicitly no claim about other writers or global state equality | Removed requirement is MATERIAL; new approval mandatory |
| Session provenance | Existing authenticated in-memory session plus independent intended-account binding required; synthetic reference handoff does not supply it | Owner-originated legitimate user-authorized authentication; bound account/application/owner/session attestation | New trust chain; AUTHENTICATED_OWNERSHIP_NOT_ESTABLISHED |
| HALT attestation | Stable known local HALT epoch plus independently verified runtime conditions | Separate trusted authority attests complete execution-runtime scope, freshness, epoch and revocation | Local latch cannot replace live issuer; NOT_ESTABLISHED |
| One-use HTTP budget | Exact GET; static AtomicBoolean and request-object guard enforce local budget; old approval forbids replenishing across runs | Durable linearizable claim before dispatch, no reopening after crash/uncertainty, bound owner incarnation | Cross-process enforcement DESIGNED only |
| Credential custody | No restoration/startup; private same-JVM references; official handoff rejected | Nonexporting isolated owner, no trading token import or portable session | New lifecycle needs separate authorization; no loader here |
| Log containment | Disable unsafe sinks/dumps; code checks known wire/header DEBUG and fixture redaction | Closed event/sink allowlist enforced at startup and immediately before dispatch; external sink certification | Existing tests do not certify host/IDE/container/proxy sinks |
| Response/funding provenance | Strict bounded parsing, session-bound snapshot and redacted observation; no funding promotion | Owner-bound authenticated receipt tied privately to consumed request and fresh postchecks; observation only | No eligible/free collateral, cash-rule or cash-field proof; MIS NOT_READY |

## Admission bundle

Every field is required unless stated otherwise. This is a logical specification,
not an implemented JSON schema or service. Reject unknown versions, duplicate
fields, extensions, noncanonical encodings and invalid signatures. Signatures use
domain-separated canonical bytes with a fixed reviewed algorithm/key policy;
algorithm negotiation and untrusted key URLs are forbidden.

| Binding | Required content |
|---|---|
| Contract | Exact v2 draft digest, approved policy digest and explicit approval references |
| Identity | Authenticated authorizer, requester, exact owner principal AND boot/incarnation, approved artifact/deployment digest |
| Account/session | Protected opaque reference resolved by owner and authentication authority to intended account, broker application and current session epoch; never caller-selected account or token hash |
| Operation | Fixed enum EQUITY_MARGIN_SHAPE_OBSERVATION; method GET; literal https://api.kite.trade/user/margins/equity; no query, fragment, userinfo, alternate port or host |
| Budget | Unique unpredictable approval ID, attempt budget exactly 1, ledger domain/epoch; no wildcard scope or batch |
| Time | UTC issued-at, not-before, expiry; trusted time uncertainty policy; approval lifetime at most 30 seconds, never beyond session/HALT/policy validity |
| Preconditions | Current provenance assertion, scoped HALT evidence and epoch, ledger revocation status, packaging/network/sink certification IDs |
| Authorization | Independent approvals of contract and implementation do not replace separately issued exact real-read authorization |

Private approval/attestation records are access-controlled control-plane data, not
public logs. Bare approval IDs or signed envelopes do not grant authority without
authenticated requester and exact owner binding. No bearer credential, account ID,
cookie, session object, token-derived hash or raw balance is an accepted request
field. Reject arbitrary URLs, credential imports, callback/login/reset parameters,
unbounded metadata and user-supplied error strings.

## Lifecycle and outcomes

Validate identity, version, scope, trusted time, provenance, HALT, revocation,
packaging and sinks before claim. Atomically change ISSUED to CONSUMED in the
non-trading authority ledger before any broker request creation/dispatch. The
winning live invocation alone can proceed after a fresh pre-dispatch validation.
Consumption is permanent even if subsequent validation denies before network I/O.
An ambiguous claim acknowledgment denies dispatch and leaves the authorization
spent or quarantined; no retry can retrieve a new dispatch grant.

The owner dispatches at most one exact HTTP attempt. No retries, redirects,
automatic authentication, proxy retries, supplementary endpoints or renewal.
Timeout, crash, dropped connection, lost reply or uncertain dispatch yields
INDETERMINATE with a consumed budget. Never promise exactly-once broker delivery.
Post-dispatch recheck the same account/session/owner epoch, HALT scope and epoch,
revocation, trusted time, isolation and sink configuration before accepting a
result. Failure discards the result; it cannot retract a sent request.

Public outcomes: DENIED_BEFORE_CLAIM, CONSUMED_NO_DISPATCH, OBSERVATION_ACCEPTED,
REJECTED_RESULT, INDETERMINATE. Public attempt count is conservative 0 or 1, with
1 on any consumed/uncertain attempt; it is not proof of broker receipt. No exception
text or raw response is emitted. Internal ledger outcome updates cannot reset
consumption. No response is replayed to authorize another read.

## Response and funding boundary

TLS peer verification, exact route, authentic owner provenance, consumed approval
binding, bounded strict JSON, decimal validation and fresh receipt time are all
necessary. An official-origin flag or BROKER_AUTHENTICATED_OBSERVATION enum alone
is not independent authentication evidence. The current mapper's response shape
is not certified against a real account in this phase. Preserve the existing
64 KiB wire/decoded bound, rejection of duplicates/trailing JSON and conservative
five-second result freshness (stricter expiry wins). No body/account field in logs.

A funds response is an observation. It does not establish eligible haircut-adjusted
collateral, actually free collateral, the applicable NSE MIS cash requirement or
the qualifying cash-field mapping. Those four authoritative terms remain
UNKNOWN/CONFLICTING as recorded historically; no new broker terms were obtained.
There is no risk approval, execution permit, quantity selection or trading promotion.
Collateral-assisted MIS remains NOT_READY; cash reserve, CNC policy and INR 10,000
full buffered-notional ceiling remain unchanged.

## Acceptance gates and provenance

1. Design completeness: engineering review of this proposal and the owner boundary.
2. Synthetic validation: scoped tests of implemented controls, with gaps explicit.
3. Human contract approval: all four independent roles PENDING; no inferred assent.
4. Separately authorized implementation and live certification: absent.
5. Separately authorized exact real read under this digest: NONE.
6. Trading authorization: outside scope and never derived from this observation.

Source authority: original phase-13.5-real-read-approval.md and Stage B continuation;
Phase F kite-db-free-assurance-contract.md and ownership decision; actual baseline
KiteEquityReadHarness.run(), KiteEquityReadIntegrity.capture(),
KiteEquityReadRequestFactory.createRequest(URI,HttpMethod),
KiteEquityMarginReadAdapter.read()/observation(Snapshot), and tests mapped in the
security decision matrix. This draft makes ledger mechanics, immutable owner
incarnation, sink policy and approval separation explicit; it does not amend v1
or silently inherit any approval. Operational NO_GO under every Phase G outcome.
