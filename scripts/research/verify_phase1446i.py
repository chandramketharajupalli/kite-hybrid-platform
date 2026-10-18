"""Offline metadata proposal checks; imports no HTTP client and performs no retrieval."""
import copy
import hashlib
import json
from pathlib import Path
from urllib.parse import urlsplit

BASELINE = "12f743bda73d0573b2ae77c2f206145d1e946392"
PLAN = "173eba46bff6f7b773d2ca1adf6503625d4e2437cde1cee022cfbd858245cafe"
ORIGIN = "https://repo.maven.apache.org"
APPROVAL_NULL = ("output_destination", "operator_identity", "approval_evidence", "trusted_timestamp",
                 "expiry", "revocation", "durable_replay_protection", "cleanup_authority")
LIMIT_NULL = ("approved_repository_origin", "approved_exact_url_allowlist", "maximum_request_count",
              "maximum_response_bytes", "total_transfer_cap", "connection_timeout", "read_timeout",
              "retry_count", "destination_directory", "cleanup_authority")
ROOTS = (("org.springframework.boot", "spring-boot-starter-security", "3.5.16"),
         ("org.springframework.boot", "spring-boot-starter-oauth2-client", "3.5.16"),
         ("org.springframework.security", "spring-security-test", "6.5.11"))


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def require(condition, label):
    if not condition:
        raise ValueError(label)


def expected_url(root):
    group, artifact, version = root
    return ORIGIN + "/maven2/" + group.replace(".", "/") + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".pom"


def validate_url(url):
    require(type(url) is str and url in [expected_url(r) for r in ROOTS], "exact URL allowlist")
    parsed = urlsplit(url)
    require(parsed.scheme == "https" and parsed.netloc == "repo.maven.apache.org", "origin")
    require(not parsed.query and not parsed.fragment and parsed.username is None and parsed.port is None, "URL components")
    require(not any(c in url for c in ("%", "\\", "*", "\r", "\n")) and "/../" not in url, "unsafe path")
    return True


def validate_pending(selection, allowlist, authorization):
    for p in (selection, allowlist, authorization):
        require(p["baseline"] == BASELINE and p["plan_sha256"] == PLAN, "baseline/plan")
    require(selection["effective_owner_selected_repository"] is None and selection["approved_repository"] is None, "repository selection")
    require(selection["approval_status"] == "NOT_APPROVED" and selection["technical_recommendation"] == "DEFER_PENDING_REPOSITORY_VERIFICATION", "source promotion")
    require(selection["repository_ownership_evidence"] is None and selection["tls_verification"] is None, "invented verification")
    require(selection["network_requests"] == 0, "network")
    require(allowlist["status"] == "PARTIAL" and allowlist["complete"] is False and allowlist["executable"] is False, "allowlist promotion")
    require(allowlist["required_transitive_metadata"] is None and len(allowlist["entries"]) == 3, "transitive scope")
    for entry, root in zip(allowlist["entries"], ROOTS):
        validate_url(entry["url"])
        require(entry["url"] == expected_url(root) and entry["url"] == entry["repository_origin"] + entry["relative_path"], "URL binding")
        require(tuple(entry[k] for k in ("group_id", "artifact_id", "version")) == root, "coordinate binding")
        require(entry["http_method"] == "GET" and entry["metadata_type"] == "POM", "method/type")
        require(entry["expected_sha256"] is None and entry["maximum_object_size"] is None, "invented hash/limit")
        require(entry["approval_status"] == "NOT_AUTHORIZED", "entry approval")
    require(authorization["allowlist_fingerprint"] == sha(canonical(allowlist)), "allowlist fingerprint")
    require(authorization["proposed_url_allowlist"] == [expected_url(r) for r in ROOTS], "packet URL binding")
    require(all(authorization[k] is None for k in APPROVAL_NULL), "unverified authority")
    limits = authorization["network_resource_limits"]
    require(all(limits[k] is None for k in LIMIT_NULL), "unapproved limits")
    require(limits["redirect_policy"] == "DENY_ALL_REDIRECTS" and limits["credential_policy"] == "NO_CREDENTIALS", "redirects/credentials")
    require(limits["tls_policy"] == "REQUIRE_CERTIFICATE_CHAIN_AND_HOSTNAME_VERIFICATION", "TLS")
    require(all(authorization[k] == "NOT_AUTHORIZED" for k in ("status", "retrieval_authorization", "jar_acquisition", "java_implementation", "runtime_validation", "gate_a_b")), "authority promotion")
    require(authorization["network_requests"] == 0 and authorization["live_orders"] == authorization["protected_endpoint_activation"] == "DISABLED", "side effects")
    return True


def policy_checks(packets, helper):
    receipts = []

    def check(name, condition):
        require(condition, "POLICY:" + name)
        receipts.append(dict(case=name, result="PASS", authority="NONE_SYNTHETIC"))

    def rejected(name, callback):
        try:
            callback()
        except ValueError:
            check(name, True)
        else:
            raise ValueError("EXPECTED_REJECTION:" + name)

    check("pending_packet", validate_pending(*packets))
    for root in ROOTS:
        check("exact_" + root[1], validate_url(expected_url(root)))
    first = expected_url(ROOTS[0])
    invalid = [first.replace("https:", "http:"), first + "?download=1", first + "#x",
               first.replace("repo.maven.apache.org", "repo.maven.apache.org.evil.invalid"),
               first.replace("https://", "https://user@"), first.replace(".pom", ".jar"),
               first.replace(".pom", ".zip"), first.replace("/maven2/", "/maven2/../"),
               first.replace("/maven2/", "/maven2/%2e%2e/"), first.replace("3.5.16", "LATEST"),
               first.replace("3.5.16", "3.5.16-SNAPSHOT"), first + "\n", first.replace(".pom", "*.pom"),
               ORIGIN + "/maven2/maven-metadata.xml", first.replace("repo.maven.apache.org", "repo.maven.apache.org:443")]
    for i, url in enumerate(invalid):
        rejected("URL_" + str(i), lambda url=url: validate_url(url))
    for field in APPROVAL_NULL:
        bad = copy.deepcopy(packets)
        bad[2][field] = "SYNTHETIC_UNAPPROVED"
        rejected("authority_" + field, lambda bad=bad: validate_pending(*bad))
    for field in LIMIT_NULL:
        bad = copy.deepcopy(packets)
        bad[2]["network_resource_limits"][field] = 1
        rejected("limit_" + field, lambda bad=bad: validate_pending(*bad))
    for index, field, value in [(0, "effective_owner_selected_repository", "central"), (0, "tls_verification", "PASS"),
                                (1, "complete", True), (1, "required_transitive_metadata", []),
                                (2, "status", "AUTHORIZED"), (2, "retrieval_authorization", "AUTHORIZED"),
                                (2, "jar_acquisition", "AUTHORIZED"), (2, "allowlist_fingerprint", "wrong"),
                                (2, "baseline", "wrong"), (2, "network_requests", 1)]:
        bad = copy.deepcopy(packets)
        bad[index][field] = value
        if index == 1:
            bad[2]["allowlist_fingerprint"] = sha(canonical(bad[1]))
        rejected("promotion_" + field, lambda bad=bad: validate_pending(*bad))
    for i, packet in enumerate(packets):
        schema = helper["snapshot_schema"](packet)
        check("schema_" + str(i), helper["accepts"](schema, packet))
        check("deterministic_" + str(i), canonical(schema) == canonical(helper["snapshot_schema"](packet)))
        bad = copy.deepcopy(packet)
        bad["unexpected"] = True
        check("extra_" + str(i), not helper["accepts"](schema, bad))
        del bad["unexpected"]
        del bad["baseline"]
        check("missing_" + str(i), not helper["accepts"](schema, bad))
    return receipts


def helpers(root):
    source = (Path(root) / "scripts/research/verify_phase1446g.py").read_bytes()
    require(sha(source) == "96d580b759bc13966d7a462e052d3849beb0fa9a765551ae1189d3a9c9fc1505", "G helper hash")
    namespace = {}
    exec(compile(source, "G_helpers", "exec"), namespace)
    return namespace


def verify(root, inputs):
    root = Path(root)
    require(inputs["baseline"] == BASELINE and inputs["plan"] == PLAN, "input binding")
    for name, expected in inputs["hashes"].items():
        p = Path(name)
        require(sha((p if p.is_absolute() else root / p).read_bytes()) == expected, "preservation:" + name)
    require(len(inputs["historical_missing"]) == 73 and not any(Path(n).exists() for n in inputs["historical_missing"]), "historical")
    base = root / "research/phase-14.46i/generation-1"
    packets = [json.loads((base / n).read_bytes()) for n in ("repository-selection.json", "metadata-allowlist.json", "retrieval-authorization.json")]
    validate_pending(*packets)
    helper = helpers(root)
    reconciliation = helper["reconcile"](root)
    graph = json.loads((root / "research/phase-14.46f/generation-1/dependency-graph.json").read_bytes())
    for entry, original in zip(packets[1]["entries"], graph["top_level"]):
        require(entry["management_source"] == original["management_source"] and entry["management_sha256"] == original["management_sha256"], "management source")
    failure = Path(inputs["failure_receipt"])
    require(sha(failure.read_bytes()) == "2f4814c2a3974d25e26d2cd71b70e9761167ace0c02a1381baff6e37bc28a659", "F failure")
    require(not (root / "research/phase-14.46f/generation-1/evidence-manifest.json").exists(), "F manifest")
    require(not any("replay" in p.name or "certificate" in p.name for p in failure.parent.iterdir()), "F completion evidence")
    return dict(classification="PHASE_14_46I_REPOSITORY_SELECTION_PENDING", baseline=BASELINE, plan=PLAN,
                checks=policy_checks(packets, helper), reconciliation=reconciliation, protected_inputs=len(inputs["hashes"]),
                allowlist_fingerprint=sha(canonical(packets[1])), metadata_retrieval="NOT_AUTHORIZED", jar_acquisition="NOT_AUTHORIZED",
                implementation="NOT_AUTHORIZED", runtime="NOT_AUTHORIZED", operational_readiness="NO_GO")
