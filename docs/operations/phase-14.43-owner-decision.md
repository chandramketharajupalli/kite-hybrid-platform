# Phase1443OwnerMethodologyDecision.v1

Current PENDING_OWNER_DECISION. States: PENDING_OWNER_DECISION,APPROVED,REJECTED,DEFERRED,EXPIRED,REVOKED. No state transition occurs automatically. Expired approvals fail at verification even if the stored historical state still says APPROVED; originals are not rewritten.

owner-decision.json separates proposed bindings from approved fields. Proposed source baseline,candidate schema/source,corporate-action options,identity options,comparability,scope and rollback hashes are fixed for review. Owner identity,decision time,evidence,approved bindings,selected options,rollback authority,expiry,stage and signature remain null. This prompt is preparation authority only.

A future authenticated decision must identify the owner and approved authentication method, exact decision bytes, baseline,policy and scope fingerprints, selected alternatives, exact stage/content hashes, expiry and rollback authority. Independently retained authentication/custody evidence and conflict/revocation checks are mandatory at every restart and stage boundary. Hash equality alone proves integrity, not identity or consent. Authenticated policy selection alone does not activate a stage.

Current code authenticates synthetic fixtures only: an explicitly supplied synthetic key signs canonical decision fields with a test-only domain separator. No key discovery, credential-file read, email access or real identity claim occurs. Altered evidence/signature/policy/expiry is rejected. A valid synthetic result explicitly says real implementation NOT_AUTHORIZED; activate_stage always rejects. No production authentication mechanism is claimed.

Owner choices remain unresolved. Recommended identity option B with C segmentation and D rejection is advice only. Corporate-action choices A/B/C/D remain unselected. A future scope amendment must replace null content commitments with reviewed exact hashes and establish authenticated authority; no existing tracked changes are permitted by this proposal.
