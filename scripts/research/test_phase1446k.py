"""Explicit synthetic rejection cases; passing fixtures carry no real authority."""
import copy


def run(policy, validate, helper, endpoint, decision, urls, nulls):
    cases = []

    def check(name, condition):
        if not condition:
            raise ValueError("K_CASE:" + name)
        cases.append(dict(case=name, result="PASS", authority="NONE_SYNTHETIC"))

    fixture = dict(synthetic=True, real_action=False, owner_approved=True, endpoint_verified=True,
                   tls_valid=True, trust_store_approved=True, dns_approved=True, destination_approved=True,
                   acl_verified=True, path_safe=True, trusted_time=True, ledger_verified=True,
                   revoked=False, replayed=False, credentials=False, proxy=False, mirror_fallback=False,
                   overwrite=False, destination="SYNTHETIC_ISOLATED_OUTPUT", now=1, expiry=2, total_bytes=786432,
                   requests=[dict(url=u, response_url=u, method="GET", bytes=262144, redirects=0, retries=0,
                                  connect_seconds=5, read_seconds=10, content_type="application/xml", status=200)
                             for u in urls])
    check("boundary_equality_synthetic_only", policy(fixture, urls) == "SYNTHETIC_ONLY_PASS")
    for name, key, value in [
        ("missing_approval", "owner_approved", False), ("unverified_endpoint", "endpoint_verified", False),
        ("excess_total_bytes", "total_bytes", 786433), ("credentials", "credentials", True),
        ("proxy_fallback", "proxy", True), ("missing_output_custody", "destination", None),
        ("expired_approval", "expiry", 1), ("revoked_approval", "revoked", True),
        ("replayed_approval", "replayed", True), ("synthetic_to_real", "real_action", True),
        ("mirror_fallback", "mirror_fallback", True), ("missing_trust_store", "trust_store_approved", False),
        ("invalid_tls", "tls_valid", False), ("unapproved_dns", "dns_approved", False),
        ("untrusted_time", "trusted_time", False), ("missing_durable_ledger", "ledger_verified", False),
        ("unsafe_path", "path_safe", False), ("unverified_acl", "acl_verified", False),
        ("overwrite", "overwrite", True), ("boolean_bytes", "total_bytes", True),
        ("non_synthetic", "synthetic", False)]:
        bad = copy.deepcopy(fixture)
        bad[key] = value
        check("reject_" + name, policy(bad, urls) == "REJECT")
    for name, key, value in [
        ("wrong_hostname", "url", urls[0].replace("repo.maven.apache.org", "wrong.invalid")),
        ("http", "url", urls[0].replace("https:", "http:")),
        ("redirect", "redirects", 1), ("extra_url", "url", "https://repo.maven.apache.org/maven2/extra.pom"),
        ("jar", "url", urls[0].replace(".pom", ".jar")),
        ("snapshot", "url", urls[0].replace("3.5.16", "3.5.16-SNAPSHOT")),
        ("wrong_method", "method", "POST"), ("oversized_response", "bytes", 262145),
        ("retry", "retries", 1), ("negative_bytes", "bytes", -1),
        ("connect_timeout", "connect_seconds", 6), ("read_timeout", "read_seconds", 11),
        ("nonfinite_elapsed", "read_seconds", float("inf")),
        ("response_url_mismatch", "response_url", "https://wrong.invalid/x.pom")]:
        bad = copy.deepcopy(fixture)
        bad["requests"][0][key] = value
        check("reject_" + name, policy(bad, urls) == "REJECT")
    bad = copy.deepcopy(fixture)
    bad["requests"].append(copy.deepcopy(bad["requests"][0]))
    check("reject_fourth_request", policy(bad, urls) == "REJECT")
    bad = copy.deepcopy(fixture)
    bad["requests"][1] = copy.deepcopy(bad["requests"][0])
    check("reject_duplicate_url", policy(bad, urls) == "REJECT")
    check("pending_packets", validate(endpoint, decision, urls))
    for field in nulls:
        bad = copy.deepcopy(decision)
        bad[field] = "SYNTHETIC_UNAPPROVED"
        try:
            validate(endpoint, bad, urls)
        except ValueError:
            check("reject_packet_" + field, True)
        else:
            raise ValueError("AUTHORITY_ACCEPTED:" + field)
    for field, value in [("submitted_preference", "MAVEN_CENTRAL"),
                         ("effective_repository", "https://repo.maven.apache.org"),
                         ("endpoint_identity", "VERIFIED")]:
        bad = copy.deepcopy(endpoint)
        bad[field] = value
        try:
            validate(bad, decision, urls)
        except ValueError:
            check("reject_endpoint_" + field, True)
        else:
            raise ValueError("ENDPOINT_AUTHORITY_ACCEPTED:" + field)
    for index, packet in enumerate((endpoint, decision)):
        schema = helper["snapshot_schema"](packet)
        check("schema_valid_" + str(index), helper["accepts"](schema, packet))
        bad = copy.deepcopy(packet)
        bad["unexpected"] = True
        check("schema_extra_" + str(index), not helper["accepts"](schema, bad))
        del bad["unexpected"]
        del bad["baseline"]
        check("schema_missing_" + str(index), not helper["accepts"](schema, bad))
        check("schema_deterministic_" + str(index),
              helper["canonical"](schema) == helper["canonical"](helper["snapshot_schema"](packet)))
    return cases
