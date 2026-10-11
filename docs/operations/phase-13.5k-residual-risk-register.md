# Phase 13.5K residual-risk register v1

PROPOSED, no risk acceptance received. Supports SoloOperatorGovernanceAmendment.v1
draft 1 and unchanged V2 draft 1. Severity/gates follow the frozen K plan.
All likelihoods below are assumptions, not measurements. Proposed accountable owner
in every row: sole-maintainer role, identity/authority unverified and assignment
PENDING; domains specify responsibility, not invented personnel. Every owner review
must say SINGLE_OWNER_SELF_REVIEW and disclose the author/operator conflict.

Source keys: V = V2 proposal; B = owner boundary; S = owner security review;
H = phase-13.5h-implementation-readiness.md; G = phase-13.5g-security-decision-matrix.md;
J = research/phase-13.5j/generation-1/approval-ledger.json. Linked through
[amendment](../architecture/kite-solo-operator-governance-amendment-v1-proposal.md).

| ID / risk | Severity; likelihood assumption | Evidence / current limit | Compensating controls / responsible domains | Required closure | Gate classification |
|---|---|---|---|---|---|
| K01 Self-review, confirmation bias, unilateral policy drift | HIGH; correlated blind spots plausible, frequency UNKNOWN | User assertion; J four PENDING, no independence | All domains: four separate records, conflict labels, preregistered criteria, hostile examples, independently run checks, 24-hour second session; seek outside competence | Explicit attributable adoption/authority limits; four later decisions; applicability evidence; bias remains accepted residual only if explicitly acknowledged | ACCEPTABLE_FOR_DESIGN_REVIEW; BLOCKS_IMPLEMENTATION until authority/design review closes |
| K02 Unauthorized/ambiguous auth provenance | CRITICAL; UNKNOWN, not presumed rare | H H2 / V origin absent | Security/authentication: broker-origin chain, consent/account/app/owner binding, no import/restore, expiry/revocation and rotation seal | Concrete reviewed legitimate lifecycle before implementation; independently verified origin/custody before live read, separately authorized enrollment | BLOCKS_IMPLEMENTATION; BLOCKS_REAL_READ |
| K03 Credential leakage via logs/errors/dumps/artifacts | CRITICAL; privileged capture plausible, likelihood UNKNOWN | S all-sink design only; source redaction insufficient | Packaging/logging: closed sink list, bounded errors/no stacks, dump/attach/agent restrictions, synthetic leak injection, access/retention owners | Concrete artifact/sink enforcement design; all sink/runtime proofs before read; unknown privileged capture denies | BLOCKS_IMPLEMENTATION; BLOCKS_REAL_READ |
| K04 Durable ledger crash/ack/recovery uncertainty | CRITICAL; crashes/partitions possible, rates UNKNOWN | B ledger design; H H3 not implemented | Runtime/DB + Security/authentication: durable claim before dispatch, spent after uncertainty, epoch rollback fencing; no retry | Concrete store/quorum/ack/fencing/recovery specification; independently checked durable fault evidence before read | BLOCKS_IMPLEMENTATION; BLOCKS_REAL_READ |
| K05 External HALT forged/stale/incomplete | CRITICAL; UNKNOWN issuer/coverage | H H4 / B no live authority | Runtime/DB: trusted external issuer, complete inventory, signature/epoch/revocation, before/after checks, unknown denies | Identify competent authority and concrete coverage protocol; live scoped proof required before read; self-review does not replace independent attestation | BLOCKS_IMPLEMENTATION; BLOCKS_REAL_READ |
| K06 Privileged/noncooperative trading-state writers | HIGH; concurrent writers possible and not excluded | G historical snapshot/write-and-restore and nonparticipant counterexamples | Runtime/DB + User/operator: never claim global invariance; isolate owner's direct/indirect authority; disclose privileged-host assumption | Explicit narrower-claim acceptance and certified owner isolation; if global invariance required, reject V2 pathway | ACCEPTABLE_FOR_DESIGN_REVIEW; BLOCKS_REAL_READ until own-process boundary certified |
| K07 Artifact accidentally retains order/trading authority | CRITICAL; full JAR demonstrably broad, deployment likelihood UNKNOWN | H H5 / S excludes full trading-core | Packaging/logging + Runtime/DB: minimal SBOM/loadability, no runners/order/DB/permits, default-deny egress/OS/mounts | Concrete extraction/dependency design; actual artifact and confinement/reachability proof before read | BLOCKS_IMPLEMENTATION; BLOCKS_REAL_READ |
| K08 Margins misread as eligible/free collateral | CRITICAL for trading; interpretive mistake plausible | V funding terms unknown/conflicting, MIS NOT_READY | User/operator: bounded observation only, preserve cash/CNC/ceiling; no funding promotion | Authoritative eligible/free adjusted collateral, applicable cash rule and field mapping plus separate trading/risk approvals; outside amendment | ACCEPTABLE_FOR_DESIGN_REVIEW; BLOCKS_LIVE_TRADING |
| K09 Loss of access, compromise, revocation or unsafe recovery | CRITICAL; possibility known, likelihood UNKNOWN | H H7 / S fail-closed recovery not deployed | All domains: incident suspension, no refund/retry/restore, rotation/re-enrollment under new scope, sanitized audit custody | Concrete incident owner/revocation/recovery design before implementation; independently tested denial/recovery and current authority before read | BLOCKS_IMPLEMENTATION; BLOCKS_REAL_READ |
| K10 External obligation requires independent review / authority disputed | HIGH; applicability UNVERIFIED | No independent legal/organizational/broker-policy evidence supplied | User/operator: qualified applicability review, no claim of exemption, retain original path where required | Document applicable obligations and actual authority; obtain mandatory independent review, never self-waive | ACCEPTABLE_FOR_DESIGN_REVIEW; BLOCKS_IMPLEMENTATION; BLOCKS_REAL_READ |

All entries remain OPEN / NOT_ACCEPTED. Discussion is permissible; none authorizes
implementation. Gate-specific closures distinguish design obligations from later
runtime proof. Unknown CRITICAL/HIGH design or authority gaps block implementation
authorization; unknown critical runtime properties block real read. Separate bounded
offline remediation may be proposed later, never inferred from this register.
Acknowledgment cannot convert NOT_ESTABLISHED into VERIFIED. No numerical residual
probabilities, signatures, named owner assignments or closed conditions are invented.

Reassess at proposed 90-day cadence and immediately on material change/incident.
Append revisions with parent links, exact fingerprints and explicit reasons; never
delete negative evidence. Live trading remains out of scope regardless of closures.
