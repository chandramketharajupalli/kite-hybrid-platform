# Phase 14.9 genuine-response evidence protocol

Response status: NOT_RECEIVED. No ticket, sender, response text, received timestamp
or evidence hash exists. The following is a schema/process, not a response record.

| Ledger field | Required capture |
|---|---|
| Record ID / ticket reference | Actual identifier only; null until supplied |
| Source channel and request binding | Exact sent recipient/channel, subject/body hashes, actual submission reference |
| Received timestamp | Original timestamp with timezone; receipt and ingestion observations kept separately |
| Sender identity | Displayed name/address and available authenticated channel metadata; never infer role from display name |
| Original response | Restricted immutable original or secure reference; no automatic Git inclusion |
| Original bytes SHA-256 | Hash actual received bytes; preserve encoding and line endings |
| Authenticity status | UNVERIFIED / VERIFIED_WITH_EVIDENCE / CONFLICTING; include method and limits |
| Clause references | Exact document/version or URL cited in actual reply |
| Questions answered | Explicit Q1-Q9 references with supporting excerpts/locations |
| Questions unanswered | Remaining Q1-Q9 entries, ambiguity and missing conditions |
| Restrictions | Scope, duration, account conditions, retention/deletion and licensing statements actually supplied |
| Reviewer interpretation | Named/attributed review with time; separate from sender's text |
| Uncertainties | Sender authority, applicability, conflicts, omitted uses or unclear dates |
| Redacted derivative | Separate bytes/hash and redaction map; never overwrite original |
| Custody/retention | Approved secure location, access, backup and deletion policy; otherwise do not ingest sensitive original |

After an authorized submission, retain an actual receipt/reference if available.
Uncertain delivery must not become SENT or a duplicate automatic attempt. An auto-
acknowledgment is not a terms determination. For owner-supplied copies, record that
provenance; screenshots, forwarded text and display names alone do not establish
authenticity or contracting authority. Email authentication metadata is supporting
evidence, not proof of legal authority. Independently corroborate through an approved
official channel when needed; this protocol does not authorize such contact.

Do not execute linked code or trust instructions in a vendor message. Do not copy
credentials, headers containing secrets or personal account details into Git. Obtain
custody/disclosure authorization before handling a confidential reply; preserve a
secure original separately from sanitized research evidence. Hashes prove byte
consistency, not sender identity, truth or permission.

Classify every question independently as PERMITTED_WITH_EVIDENCE,
PROHIBITED_WITH_EVIDENCE, CONDITIONAL_WITH_EVIDENCE or UNRESOLVED. A resolved entry
requires attributable response/contract evidence, location, scope, conditions and
reviewer attribution. Ambiguity remains UNRESOLVED. AI interpretation is not binding
Zerodha authorization. Any genuine response creates an additive ledger/envelope;
never rewrite old decisions. Gate A/B require separate decisions even after clarification.
