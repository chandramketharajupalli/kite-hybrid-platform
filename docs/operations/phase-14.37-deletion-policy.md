# Synthetic deletion and recovery controls

No genuine files are deleted or restored. Tests use in-memory disposable synthetic records. Completion receipts bind object hash, exact policy fingerprint, copy identity, unique receipt identity, completion time and separately retained verification evidence; independently trusted receipt hashes are required. Reject altered/untrusted receipts, duplicates, wrong policies/objects, unknown copies and missing backup/copy receipts.

An interrupted or ambiguous deletion remains DELETION_UNRESOLVED. Do not issue a completion claim, silently retry or restore expired data. Recovery re-evaluates authority, permitted scope and lifetime. Any later repair needs a separate explicit procedure and evidence. Preserve failed receipts and inventories.

Receipt completeness attests to the declared copy inventory only. It does not prove that undiscovered replicas do not exist, establish secure physical erasure, or prove cloud/backup deletion without independent storage evidence. Deletion authority, permitted receipt retention and backup lifecycle remain unresolved for genuine data. Synthetic passing checks cannot authorize deletion of protected research or original correspondence.
