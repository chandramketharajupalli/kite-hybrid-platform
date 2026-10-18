"""Non-launching synthetic fixtures; no pytest or application workloads."""
from jsonschema import Draft202012Validator
from jsonschema.exceptions import SchemaError


def original_schema(value):
    """Transcript reconstruction of the maintenance function, not F verifier code."""
    if isinstance(value, dict):
        return dict(type="object", properties={k: original_schema(v) for k, v in value.items()},
                    required=list(value), additionalProperties=False)
    if isinstance(value, list):
        return dict(type="array", prefixItems=[original_schema(v) for v in value],
                    minItems=len(value), maxItems=len(value))
    return {"const": value}


def run(generate, accepts, check_schema, canonical):
    receipts = []

    def check(name, condition):
        if not condition:
            raise ValueError("REGRESSION:" + name)
        receipts.append(dict(case=name, result="PASS", evidence="SYNTHETIC_ONLY"))

    def rejects(name, action, errors=(ValueError, SchemaError)):
        try:
            action()
        except errors:
            check(name, True)
        else:
            raise ValueError("EXPECTED_REJECTION:" + name)

    original = original_schema({"boot_import_index": [{"exclusions": []}]})
    check("original_structure", original["properties"]["boot_import_index"]["prefixItems"][0]["properties"]["exclusions"]["prefixItems"] == [])
    rejects("original_failure_reproduced", lambda: Draft202012Validator.check_schema(original), (SchemaError,))
    fixtures = [("empty_exclusions", {"exclusions": []}),
                ("nonempty_exclusions", {"exclusions": [{"group": "fixture", "artifact": "excluded"}]}),
                ("empty_tuple", ()), ("nonempty_tuple", ("fixture", 1)),
                ("nested_arrays", [[], [1, ["fixture"]]]), ("empty_object", {}),
                ("required_arrays", {"items": []}), ("nullable_array", [None]),
                ("booleans", [True, False]), ("numbers", [1, 1.5])]
    for name, value in fixtures:
        schema = generate(value)
        check_schema(schema)
        instance = list(value) if isinstance(value, tuple) else value
        check(name, accepts(schema, instance))
        check(name + "_deterministic", canonical(schema) == canonical(generate(value)))
    check("empty_has_no_prefixItems", "prefixItems" not in generate([]))
    check("empty_rejects_item", not accepts(generate([]), [1]))
    check("nonempty_rejects_short", not accepts(generate([1, "x"]), [1]))
    check("nonempty_rejects_extra", not accepts(generate([1]), [1, 2]))
    check("wrong_element_type", not accepts(generate([1]), ["1"]))
    check("bool_is_not_integer", not accepts(generate([1]), [True]))
    check("wrong_scalar_value", not accepts(generate([1]), [2]))
    check("wrong_array_type", not accepts(generate([]), {}))
    check("missing_required", not accepts(generate({"items": []}), {}))
    check("unexpected_property", not accepts(generate({}), {"x": 1}))
    check("nested_wrong_type", not accepts(generate({"x": [[1]]}), {"x": [["1"]]}))
    optional = generate({"items": [], "required": 1}, optional_fields=("items",))
    check("optional_absent", accepts(optional, {"required": 1}))
    check("optional_present", accepts(optional, {"required": 1, "items": []}))
    check("optional_constrained", not accepts(optional, {"required": 1, "items": [1]}))
    check("other_required", not accepts(optional, {"items": []}))
    rejects("malformed_schema", lambda: check_schema({"$schema": generate([])["$schema"], "type": "invalid"}))
    rejects("wrong_draft", lambda: check_schema({"$schema": "http://json-schema.org/draft-07/schema#", "type": "array"}))
    rejects("missing_draft", lambda: check_schema({"type": "array"}))
    rejects("invalid_prefixItems", lambda: check_schema({"$schema": generate([])["$schema"], "type": "array", "prefixItems": []}))
    rejects("nonfinite_input", lambda: generate(float("nan")))
    rejects("nonstring_key", lambda: generate({1: "x"}))
    rejects("unsupported_input", lambda: generate({1, 2}))
    rejects("unknown_optional_field", lambda: generate({}, optional_fields=("x",)))
    cycle = []
    cycle.append(cycle)
    rejects("cyclic_input", lambda: generate(cycle))
    valid = {"x": [1, "fixture"], "flag": True}
    check("valid_legacy_behavior", Draft202012Validator(original_schema(valid)).is_valid(valid) and accepts(generate(valid), valid))
    check("canonical_key_order", canonical(generate({"b": 2, "a": 1})) == canonical(generate({"b": 2, "a": 1})))
    return receipts
