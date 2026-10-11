# Four-role collection packets v1 — DRAFT / NOT_SENT

No recipients selected; no requests sent. Each packet concerns only
DiagnosticAssuranceContract.v2 draft 1, SHA-256
`5B9F1E717DD444CE5BBF6697012BF11887DBA780FB8C1099AC24D0A5D7D79CE7`.
The proposed guarantee is isolated own-process noninterference. Global trading DB
nonmutation remains NOT_PROVEN; snapshot comparison cannot detect write-and-restore.
This material narrowing needs new explicit decisions. Old one-GET authority does
not transfer. No implementation, authentication, token access, deployment, read or
trading authorization is requested or implied.

Controlling reading for every packet: [V2](../architecture/kite-db-free-assurance-contract-v2-proposal.md),
[boundary](../architecture/kite-credential-owner-service-boundary.md),
[security review](../architecture/kite-credential-owner-security-review.md),
[G decisions](phase-13.5g-security-decision-matrix.md),
[H approval evidence](phase-13.5h-approval-matrix.md),
[H readiness](phase-13.5h-implementation-readiness.md), and
[frozen coordination protocol](phase-13.5i-plan.md).

## Security/authentication — DRAFT / NOT_SENT

Scope: G D2-D4, D8, D10. For the exact version/hash above, answer yes/no/conditional:

- Is Architecture C's nonexporting custody and privilege boundary acceptable?
- Are independent broker-compliant origin, user consent, account/application binding,
  caller identity, owner incarnation, expiry/revocation and trusted clock requirements sufficient?
- Are replay resistance, durable claim-before-dispatch and uncertain-outcome fencing adequate?
- Does response provenance remain distinct from funding eligibility and authentication proof?

Present: design and historical synthetic evidence. Missing: verified legitimate
session origin, independent custody/caller verification and durable ledger proof.
A UUID, synthetic session, signed local assertion or transferred bearer is insufficient.
State whether missing engineering evidence is a contract-decision prerequisite or
a separately gated future implementation prerequisite; do not treat it as satisfied.

## Runtime/DB — DRAFT / NOT_SENT

Scope: G D1, D4-D6, D10. For the exact version/hash above, answer yes/no/conditional:

- Do you explicitly accept own-process-only assurance and unproven global invariance?
- Are direct/indirect writer exclusion, durable acknowledgment, rollback fencing,
  partition/split-brain refusal and restart consumption requirements sufficient?
- Does independent HALT cover every relevant runtime, with issuer competence,
  epoch continuity, trusted clock, freshness, revocation and unknown-state denial?
- Can the proposed dependency/OS/network boundary deny DB and order authority
  regardless of another runtime's activity?

Missing: independent all-runtime live HALT, deployed isolation and durable crash
proof. Local knownHaltedAt and disposable PostgreSQL fixtures do not supply these.

## Packaging/logging — DRAFT / NOT_SENT

Scope: G D6-D7, D10. For the exact version/hash above, answer yes/no/conditional:

- Are minimal artifact/SBOM/transitive closure and startup-side-effect checks adequate?
- Do immutable configuration, egress/mount/environment constraints exclude trading
  startup, persistence, execution and credential export?
- Does the sink allowlist cover console/IDE, appenders, wire/proxy logs, telemetry,
  traces, exception handling, debug agents, heap/crash dumps, OS/container and CI sinks?
- Are startup and pre-dispatch drift refusal, retention and redaction ownership sufficient?

Missing: independently verified minimal artifact, runtime reachability, sink
containment and retention ownership. Source redaction is not external-sink proof.
The current trading-core JAR is not a certified minimal deployed owner.

## User/operator — DRAFT / NOT_SENT

Scope: G D1, D9-D10. For the exact version/hash above, answer yes/no/conditional:

- Do you explicitly accept the narrowed own-process claim and external-writer residual risk?
- Do you accept that global DB nonmutation remains NOT_PROVEN, including invisible
  write-and-restore between snapshots?
- Do you preserve MIS NOT_READY, cash/CNC rules and INR 10,000 full buffered-notional ceiling?
- Do you accept separate gates for contract approval, implementation, authentication,
  deployment, live certification, one read and trading, with no inherited old GET approval?

Missing: attributable V2 risk acceptance and role authority. A phase execution
request is not approval. Stage B remains ZERO_CALL_ABORT.

## Required response format (unfilled; applies to all four packets)

Provide role; reviewer identity/organizational authority and safe verification
reference; delegation and conflict disclosure; exact version and full hash;
APPROVE/REJECT/CONDITIONAL/PENDING (or preserved H vocabulary per frozen mapping);
actual UTC decision timestamp and provenance; rationale and accepted residual risks;
supporting evidence reference/digest; validity/expiry and revocation terms;
conditions with owner, closure evidence and required confirmation; explicit prior
decision supersession if applicable. Do not insert credentials or private contacts.

An authorized custodian must record actual UTC receipt timestamp/provenance and
independently verify authenticity and role authority, with method/status/verifier
reference. Hash identity alone does not verify a person. Sensitive originals stay
outside Git under authorized custody; retain only safe references and digests.
No reviewer identity, signature or timestamp has been filled in by Codex.

Sending requires separate explicit authorization and confirmed recipients/content.
Until then these packets remain DRAFT / NOT_SENT.

## Collection-generation response and rejection instructions

This additive packet preserves the Phase I questions; the original is unchanged.
Use [collection protocol](four-role-decision-collection-plan.md). Each role may
explicitly REJECT with rationale if the narrower guarantee or assumptions are
unacceptable, or specify CONDITIONAL with enumerated owner, proof and confirmation
requirements. Preserve original decision wording; APPROVE_WITH_CONDITIONS maps
explicitly to source CONDITIONAL. Provide timezone/offset with the actual decision
time; a custodian records actual capture time and provenance on receipt.
No reviewer or custodian has been assigned or independently verified here.
A job title alone is insufficient; multiple roles require separate documented
authority and separate decisions. Missing details will be requested upon receipt.
No receipt time is generated merely because this draft exists.
