# Authorization contract

Phase1444EIPolicySupervisorIntegration.v1 uses the strict field set in integration.FIELDS. Canonical fingerprint excludes only fingerprint, uses sorted compact ASCII-escaped JSON without NaN encoded UTF8. Unknown fields reject in fixtures. Real authorization always rejects.

Phase1444EILiveExecutionAuthorization.v1 requires independently authenticated owner/role, trust root, exact Git/integration/policy/supervisor/helper/command/test identities, all resource limits, trusted UTC approval time/expiry, fresh revocation, durable atomic replay consumption and separately scoped cleanup/rollback authority. None is established by synthetic tokens or chat. Unknown real fields are null; status NOT_AUTHORIZED. No validity window starts.

Synthetic predicates model consumed/conflicting nonces using injected immutable sets; this does not prove durable replay protection. Synthetic UTC time is not independent trusted time. Every verdict has execution_authorized=false. No fallback or activation API exists. Real acceptance would require a separately authorized implementation, trusted clock, durable state and verified signatures, not a change of status in JSON.
