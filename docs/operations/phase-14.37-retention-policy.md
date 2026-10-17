# Retention policy contract

Phase1437DataRetentionPolicy.v1 records category-specific source, purpose, location, permitted period, maximum lifetime, deletion trigger/authority, backup/recovery handling, audit metadata and policy evidence. All genuine values remain unresolved and status NOT_AUTHORIZED. Persistent storage is BLOCKED for every category. Unknown periods never mean unlimited retention.

Categories: raw instrument CSV, parsed mapping, historical candles, derived statistics, source hashes, retrieval timestamps, audit records, temporary files, backups and recovery copies. Each requires verified legal/contractual basis and separate owner-approved custody. Metadata, results and deletion receipts are not automatically exempt from retention restrictions.

The offline lifecycle evaluator checks a resolved category policy, explicit allowed operation, trusted-policy fingerprint, authority, bounded positive lifetime and timezone-aware acquisition/current times. Expiry at the exact boundary rejects use. Permanent storage and backup require explicit operations; recovery never bypasses expiry. No genuine storage or deletion service is implemented. The policy JSON is an unresolved category inventory, not an executable authorization.
