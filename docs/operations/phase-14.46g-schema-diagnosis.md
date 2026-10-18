# Original schema diagnosis

The committed scripts/research/verify_phase1446f.py has SHA-256 a94f70655631146aefaa872acef8f255b1e104027c41a7d3c4125672559f31ae. Static inspection finds neither a schema generator function nor prefixItems in that file. It is preserved unchanged.

The function responsible was schema(x) in the prior maintenance Python invocation retained in this conversation. Its list branch unconditionally returned type=array, prefixItems=[schema(v) for v in x], minItems=len(x), maxItems=len(x). For x=[] this yields prefixItems: []. No durable original generator source file/hash was retained; the G test original_schema is a labeled transcript reconstruction, not a historical source attestation.

Original failure receipt SHA-256: 2f4814c2a3974d25e26d2cd71b70e9761167ace0c02a1381baff6e37bc28a659. It identifies boot_import_index[0].exclusions, SchemaError, FAIL. The empty prefixItems array violates Draft 2020-12's nonempty schema-array metaschema constraint. Empty instance arrays themselves are valid.

Intended semantics: an exact snapshot of an array, including exact length and element constraints. F validation.md omits the failure and remains unchanged. The new regression fixture independently reproduces the same invalid structure and expected SchemaError. F remains PARTIAL ? PRESERVED_IN_GIT; its manifest/certificate were never created and its replays never executed.
