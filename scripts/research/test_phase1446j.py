"""Non-launching synthetic endpoint policy fixtures; no real approval or retrieval."""
import copy


def run(policy, validate_pending, helper, trust, approval, urls, null_fields):
    results = []

    def check(name, value):
        if not value:
            raise ValueError("POLICY:" + name)
        results.append(dict(case=name, result="PASS", authority="NONE_SYNTHETIC"))

    fixture = dict(synthetic=True, real_action=False, owner_approved=True, endpoint_verified=True, tls_valid=True,
                   trust_store_approved=True, dns_approved=True, destination_approved=True, acl_verified=True,
                   path_safe=True, trusted_time=True, ledger_verified=True, revoked=False, replayed=False,
                   credentials=False, proxy=False, mirror_fallback=False, overwrite=False,
                   destination="SYNTHETIC_ISOLATED_OUTPUT", now=1, expiry=2, total_bytes=786432,
                   requests=[dict(url=u, response_url=u, method="GET", bytes=262144, redirects=0, retries=0,
                                  connect_seconds=5, read_seconds=10, content_type="application/xml", status=200) for u in urls])
    check("boundary_equality", policy(fixture, urls) == "SYNTHETIC_ONLY_PASS")
    for field in ("owner_approved", "endpoint_verified", "tls_valid", "trust_store_approved", "dns_approved",
                  "destination_approved", "acl_verified", "path_safe", "trusted_time", "ledger_verified"):
        bad = copy.deepcopy(fixture)
        bad[field] = False
        check("reject_missing_" + field, policy(bad, urls) == "REJECT")
    for field, value in (("revoked", True), ("replayed", True), ("credentials", True), ("proxy", True),
                         ("mirror_fallback", True), ("overwrite", True), ("real_action", True), ("synthetic", False),
                         ("destination", None), ("expiry", 1), ("total_bytes", 786433), ("total_bytes", True)):
        bad = copy.deepcopy(fixture)
        bad[field] = value
        check("reject_" + field + "_" + str(value), policy(bad, urls) == "REJECT")
    variants = [("url", urls[0].replace("repo.maven.apache.org", "evil.invalid")),
                ("url", urls[0].replace("https:", "http:")), ("url", urls[0].replace(".pom", ".jar")),
                ("url", urls[0].replace("3.5.16", "3.5.16-SNAPSHOT")), ("method", "POST"),
                ("redirects", 1), ("retries", 1), ("bytes", 262145), ("bytes", -1), ("bytes", True),
                ("connect_seconds", 6), ("read_seconds", 11), ("read_seconds", float("inf")),
                ("response_url", "https://evil.invalid/file.pom"), ("content_type", "application/java-archive"), ("status", 302)]
    for index, (field, value) in enumerate(variants):
        bad = copy.deepcopy(fixture)
        bad["requests"][0][field] = value
        check("reject_request_" + str(index), policy(bad, urls) == "REJECT")
    bad = copy.deepcopy(fixture)
    bad["requests"].append(copy.deepcopy(bad["requests"][0]))
    check("reject_fourth_request", policy(bad, urls) == "REJECT")
    bad = copy.deepcopy(fixture)
    bad["requests"][1] = copy.deepcopy(bad["requests"][0])
    check("reject_duplicate_request", policy(bad, urls) == "REJECT")
    check("pending_packet", validate_pending(trust, approval, urls))
    for field in null_fields:
        bad = copy.deepcopy(approval)
        bad[field] = "SYNTHETIC_UNAPPROVED"
        try:
            validate_pending(trust, bad, urls)
        except ValueError:
            check("packet_reject_" + field, True)
        else:
            raise ValueError("UNEXPECTED_AUTHORITY:" + field)
    bad = copy.deepcopy(approval)
    bad["proposed_limits"]["maximum_requests"] = 4
    try:
        validate_pending(trust, bad, urls)
    except ValueError:
        check("reject_limit_escalation", True)
    else:
        raise ValueError("LIMIT_ESCALATION")
    for index, packet in enumerate((trust, approval)):
        schema = helper["snapshot_schema"](packet)
        check("schema_" + str(index), helper["accepts"](schema, packet))
        bad = copy.deepcopy(packet)
        bad["unexpected"] = True
        check("schema_extra_" + str(index), not helper["accepts"](schema, bad))
        del bad["unexpected"]
        del bad["baseline"]
        check("schema_missing_" + str(index), not helper["accepts"](schema, bad))
        check("deterministic_" + str(index), helper["canonical"](schema) == helper["canonical"](helper["snapshot_schema"](packet)))
    return results
