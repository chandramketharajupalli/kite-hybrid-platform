# Correspondence custody boundary

Only exact explicitly approved private source paths may be inspected. No mailbox, portal or recursive private-directory discovery is implemented. The bounded .eml inspector verifies raw SHA-256 before parsing, rejects duplicate custody fingerprints and reparse/symlink ancestors, and returns separate derived metadata. Original bytes are never normalized, copied, overwritten or deleted. Attachments are inventoried in memory without extraction or execution. Unsupported .msg/ticket formats fail closed pending a reviewed parser or authorized transfer.

Available headers remain claims, not proof of sender, ticket, provenance or contractual authority. Missing headers/ticket are explicit gaps. Raw integrity and exact-body equality do not authenticate the sender. Private paths, identifiers, headers and attachment names belong only in approved private custody; real inspector output must not be automatically published to Git. Current repository evidence contains null original fields.

Fourteen-answer comparison accepts explicit answer boundaries from independently reviewed sources; it does not guess numbering by splitting email text. Exactly fourteen nonempty answer strings are required when a source is supplied. Comparison preserves whitespace/newline differences and returns UNVERIFIED where either source is missing. Fingerprints of decoded strings are distinct from raw-message hashes. No text is silently rewritten to force a match.

The preparatory approval checker verifies packet/decision binding and separation only; it cannot activate acquisition. Future executable approval needs separately reviewed expiry, complete scope, credential, retention and governance validation. All present packets remain NOT_AUTHORIZED and execution disabled.
