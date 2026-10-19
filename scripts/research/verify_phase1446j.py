"""Offline approval design and synthetic limit model; no retrieval implementation."""
import hashlib
import json
import math
from pathlib import Path
from urllib.parse import urlsplit

BASELINE = "85574f42eadfbce6faf51d0138d0bb1ac60cb246"
PLAN = "0bb6c6cad50a90497e8a5c3fbacb20d49d8c74615ca075e50f2b33ab83f62b88"
SOURCE_HASH = "3461af016b10457fa0db7ce0cb8f134ca95307e4262675bead97e2d7497efcd6"
NULL_FIELDS = ("approved_limits", "output_destination", "operator_identity", "owner_identity", "approval_evidence",
               "trusted_timestamp", "expiry", "revocation", "durable_replay_protection")
LIMITS = dict(contract="Phase1446JMetadataLimits.v1", maximum_requests=3, maximum_response_bytes=262144,
              maximum_total_bytes=786432, connection_timeout_seconds=5, read_timeout_seconds=10,
              retries=0, redirects=0, http_method="GET", credentials="NONE", status="PROPOSED_NOT_APPROVED")


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def require(condition, name):
    if not condition:
        raise ValueError(name)


def source_urls(root):
    p = Path(root) / "research/phase-14.46i/generation-1/metadata-allowlist.json"
    require(sha(p.read_bytes()) == SOURCE_HASH, "sealed I allowlist")
    entries = json.loads(p.read_bytes())["entries"]
    require(len(entries) == 3, "root count")
    urls = [e["url"] for e in entries]
    require(len(set(urls)) == 3, "duplicate root")
    for entry in entries:
        url = entry["url"]
        parsed = urlsplit(url)
        require(parsed.scheme == "https" and parsed.netloc == "repo.maven.apache.org", "origin")
        require(not parsed.query and not parsed.fragment and parsed.username is None and parsed.port is None, "URL components")
        require(entry["http_method"] == "GET" and entry["metadata_type"] == "POM" and url.endswith(".pom"), "POM only")
        require(url == entry["repository_origin"] + entry["relative_path"], "exact relative path")
        require(not any(c in url for c in ("*", "%", "\\", "SNAPSHOT", "LATEST", "..")), "unsafe/dynamic URL")
    return urls


def validate_pending(trust, approval, urls):
    require(all(p["baseline"] == BASELINE and p["plan_sha256"] == PLAN for p in (trust, approval)), "baseline/plan")
    require(trust["exact_urls"] == approval["exact_url_allowlist"] == urls, "exact URLs")
    require(approval["allowlist_fingerprint"] == sha(canonical(urls)), "allowlist fingerprint")
    require(trust["endpoint_identity"] == "UNVERIFIED" and trust["repository_approval"] == "NOT_APPROVED", "trust promotion")
    require(all(trust[k] is None for k in ("effective_owner_selected_repository", "approved_trust_store", "endpoint_verification_evidence", "dns_network_approval")), "invented trust")
    require(all(approval[k] is None for k in NULL_FIELDS), "effective authority")
    require(approval["proposed_limits"] == LIMITS, "proposed limits changed")
    require(all(approval[k] == "NOT_AUTHORIZED" for k in ("status", "effective_retrieval_authorization", "jar_acquisition", "java_implementation", "runtime_validation", "gate_a_b")), "authorization promotion")
    require(approval["network_requests"] == approval["downloads"] == trust["network_requests"] == 0, "side effects")
    custody = approval["output_custody"]
    require(all(custody[k] is None for k in ("destination", "approved_owner", "approved_acl", "cleanup_authority")), "output authority")
    require(custody["proposed_raw_files"] == [u.rsplit("/", 1)[1] for u in urls], "output inventory")
    require(custody["reparse_policy"] == custody["overwrite_policy"] == "REJECT", "custody policy")
    return True


def synthetic_policy(fixture, urls):
    """Return only a fixture result. This function cannot issue real authorization."""
    if fixture.get("synthetic") is not True or fixture.get("real_action") is not False:
        return "REJECT"
    for field in ("owner_approved", "endpoint_verified", "tls_valid", "trust_store_approved", "dns_approved",
                  "destination_approved", "acl_verified", "path_safe", "trusted_time", "ledger_verified"):
        if fixture.get(field) is not True:
            return "REJECT"
    if any(fixture.get(k) is not False for k in ("revoked", "replayed", "credentials", "proxy", "mirror_fallback", "overwrite")):
        return "REJECT"
    if fixture.get("destination") != "SYNTHETIC_ISOLATED_OUTPUT":
        return "REJECT"
    now, expiry = fixture.get("now"), fixture.get("expiry")
    if type(now) is not int or type(expiry) is not int or now < 0 or now >= expiry:
        return "REJECT"
    requests = fixture.get("requests")
    if type(requests) is not list or not 1 <= len(requests) <= 3:
        return "REJECT"
    total = 0
    seen = set()
    for request in requests:
        url = request.get("url")
        if type(url) is not str or url not in urls or url in seen:
            return "REJECT"
        seen.add(url)
        if request.get("response_url") != url or request.get("method") != "GET":
            return "REJECT"
        if type(request.get("redirects")) is not int or request["redirects"] != 0:
            return "REJECT"
        if type(request.get("retries")) is not int or request["retries"] != 0:
            return "REJECT"
        size = request.get("bytes")
        if type(size) is not int or size < 0 or size > 262144:
            return "REJECT"
        total += size
        for key, maximum in (("connect_seconds", 5), ("read_seconds", 10)):
            elapsed = request.get(key)
            if type(elapsed) not in (int, float) or not math.isfinite(elapsed) or not 0 <= elapsed <= maximum:
                return "REJECT"
        if request.get("content_type") not in ("application/xml", "text/xml") or request.get("status") != 200:
            return "REJECT"
    reported = fixture.get("total_bytes")
    if type(reported) is not int or reported != total or not 0 <= reported <= 786432:
        return "REJECT"
    return "SYNTHETIC_ONLY_PASS"


def helpers(root):
    p = Path(root) / "scripts/research/verify_phase1446g.py"
    require(sha(p.read_bytes()) == "96d580b759bc13966d7a462e052d3849beb0fa9a765551ae1189d3a9c9fc1505", "G helper")
    ns = {}
    exec(compile(p.read_bytes(), "G_helpers", "exec"), ns)
    return ns


def verify(root, inputs):
    root = Path(root)
    require(inputs["baseline"] == BASELINE and inputs["plan"] == PLAN, "input binding")
    for name, expected in inputs["hashes"].items():
        p = Path(name)
        require(sha((p if p.is_absolute() else root / p).read_bytes()) == expected, "preservation:" + name)
    require(len(inputs["historical_missing"]) == 73 and not any(Path(n).exists() for n in inputs["historical_missing"]), "historical")
    base = root / "research/phase-14.46j/generation-1"
    trust = json.loads((base / "endpoint-trust.json").read_bytes())
    approval = json.loads((base / "metadata-approval.json").read_bytes())
    urls = source_urls(root)
    validate_pending(trust, approval, urls)
    helper = helpers(root)
    reconciliation = helper["reconcile"](root)
    failure = Path(inputs["failure_receipt"])
    require(sha(failure.read_bytes()) == "2f4814c2a3974d25e26d2cd71b70e9761167ace0c02a1381baff6e37bc28a659", "F failure")
    require(not (root / "research/phase-14.46f/generation-1/evidence-manifest.json").exists(), "F manifest")
    require(not any("replay" in p.name or "certificate" in p.name for p in failure.parent.iterdir()), "F completion")
    tests = {}
    exec(compile((root / "scripts/research/test_phase1446j.py").read_bytes(), "test_phase1446j.py", "exec"), tests)
    cases = tests["run"](synthetic_policy, validate_pending, helper, trust, approval, urls, NULL_FIELDS)
    return dict(classification="PHASE_14_46J_ENDPOINT_TRUST_PENDING", baseline=BASELINE, plan=PLAN,
                cases=cases, reconciliation=reconciliation, protected_inputs=len(inputs["hashes"]),
                exact_urls=urls, endpoint_identity="UNVERIFIED", metadata_retrieval="NOT_AUTHORIZED",
                implementation="NOT_AUTHORIZED", runtime="NOT_AUTHORIZED", operational_readiness="NO_GO")
