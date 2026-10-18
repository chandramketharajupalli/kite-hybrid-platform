# Synthetic regression evidence

48 in-memory cases passed in the first recovery invocation; complete case receipts reside in restricted custody/schema-regression-receipt.json. Tests are preparation fixtures, not pytest, Java tests or historical46/18/31 workloads.

Coverage: original invalid structure and expected SchemaError; empty/nonempty exclusions; empty/nonempty fixed tuples; nested, required and optional arrays; null/boolean/numeric elements; exact lengths; wrong types/values; missing fields; extra properties/items; optional constraints; malformed schemas; wrong/missing draft; invalid prefixItems; nonfinite/non-JSON/cyclic input; deterministic serialization; valid legacy nonempty behavior.

Three committed F JSON packets passed corrected metaschema and instance checks. Expected invalid fixtures are recorded as successful rejections, not unexpected mandatory failures. These results confer no dependency provenance, owner authority or runtime approval.
