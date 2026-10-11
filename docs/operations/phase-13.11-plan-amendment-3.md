# Phase 13.11 plan amendment 3 — final fixture audit hardening

Parent plan SHA-256: b913dba5cfadc1f8d432f3fa356b55fbbee2a17cb463ebcc94030042d1279807.
Amendment 2 SHA-256: a4a5c4b973f5d55547fa788185d14c1313d753d7c2cc2d4bc733e68e85bc4fba.

The complete standalone run passed 95 unit and 129 integration cases. Final source
review identified two narrowly bounded test-fixture improvements before closure:

- Resolve the locally constructed image to its immutable image ID before container
  creation and assert that each container uses that ID. The official base was already
  digest-pinned, and recorded inspections bind earlier executions to their actual
  images; do not describe a mutable local build tag as intrinsically immutable.
- Match a numeric EPERM value exactly, not with a prefix that could also match 13.
  The observed Linux JDK uses the explicit `Operation not permitted` wording. Unknown
  errors continue to fail; no deny assertion is relaxed.

These affect only new Phase 13.11 test fixtures. Preserve the passing pre-audit run
and rerun the complete standalone suite after the corrections. Root production code
and its concurrently running regression suite are unchanged; no new root production
test cycle is justified solely by these fixture changes. No acquisition, policy or
operational scope change and no extension of the 120-minute phase budget.
