# Additive correction

New snapshot_schema in scripts/research/verify_phase1446g.py declares https://json-schema.org/draft/2020-12/schema and checks its own metaschema.

For an empty list or fixed tuple: type=array, minItems=0, maxItems=0, items=false, with prefixItems omitted. Nonempty arrays retain per-position prefixItems, exact min/max length and items=false. JSON instances are arrays; tuple support is only a generator input convenience.

Objects preserve all required fields by default and additionalProperties=false. Explicit optional_fields only makes named root fields optional, retaining their schemas when present. Scalars have explicit JSON types plus const. Nested arrays and objects remain constrained. Non-JSON values, non-string keys, nonfinite numbers, cycles, unknown optional fields and wrong schema drafts reject.

No original source, packet or failure evidence is repaired. This adapter is a fixed-snapshot validator, not a general application schema or Maven dependency resolver.
