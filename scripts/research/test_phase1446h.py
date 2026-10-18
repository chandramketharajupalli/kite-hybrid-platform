"""Synthetic offline policy checks only; no pytest invocation or acquisition."""
import copy


def run(validate, canonical, sha, null_fields, helper, packets):
    results = []

    def check(name, condition):
        if not condition:
            raise ValueError("POLICY:" + name)
        results.append(dict(case=name, result="PASS", authority="NONE_SYNTHETIC"))

    check("pending_packet", validate(*packets))
    for field in null_fields:
        mutated = copy.deepcopy(packets)
        mutated[2][field] = "SYNTHETIC_UNAPPROVED"
        try:
            validate(*mutated)
        except ValueError:
            check("reject_" + field, True)
        else:
            raise ValueError("UNEXPECTED_APPROVAL:" + field)
    cases = [(0, "effective_selected_repository", "SYNTHETIC"), (0, "network_requests", 1),
             (0, "publisher_provenance", "VERIFIED"), (1, "complete", True), (1, "executable", True),
             (1, "status", "COMPLETE"), (1, "transitive_allowlist", []), (1, "dependency_closure", "VERIFIED"),
             (2, "status", "AUTHORIZED"), (2, "allowlist_fingerprint", "wrong"), (2, "baseline", "wrong"),
             (2, "plan_sha256", "wrong"), (2, "dependency_acquisition", "AUTHORIZED"),
             (2, "java_implementation", "AUTHORIZED"), (2, "runtime_validation", "AUTHORIZED"),
             (2, "downloads", 1), (2, "live_orders", "ENABLED")]
    for index, field, value in cases:
        mutated = copy.deepcopy(packets)
        mutated[index][field] = value
        if index == 1:
            mutated[2]["allowlist_fingerprint"] = sha(canonical(mutated[1]))
        try:
            validate(*mutated)
        except ValueError:
            check("reject_" + str(index) + "_" + field, True)
        else:
            raise ValueError("UNEXPECTED_PROMOTION:" + field)
    for field, value in (("version", "LATEST"), ("group_id", "*"), ("repository_origin", "unapproved")):
        mutated = copy.deepcopy(packets)
        mutated[1]["entries"][0][field] = value
        mutated[2]["allowlist_fingerprint"] = sha(canonical(mutated[1]))
        try:
            validate(*mutated)
        except ValueError:
            check("reject_entry_" + field, True)
        else:
            raise ValueError("UNEXPECTED_ENTRY:" + field)
    for index, packet in enumerate(packets):
        schema = helper["snapshot_schema"](packet)
        check("schema_" + str(index), helper["accepts"](schema, packet))
        check("deterministic_" + str(index), canonical(schema) == canonical(helper["snapshot_schema"](packet)))
        bad = copy.deepcopy(packet)
        bad["unexpected"] = True
        check("reject_extra_" + str(index), not helper["accepts"](schema, bad))
        bad = copy.deepcopy(packet)
        del bad["baseline"]
        check("reject_missing_" + str(index), not helper["accepts"](schema, bad))
    return results
