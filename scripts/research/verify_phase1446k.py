"""Offline K preparation verifier; no network or real authorization."""
import hashlib
import json
from pathlib import Path
from urllib.parse import urlsplit

BASELINE = "6129b885f2d365df688ca10725206d40206b0f22"
PLAN = "d962c6b13bb8e0057559e5daf8744035da03f0c59e803c6023a5e11ecfd4d3df"
PROSPECTIVE = "a2cbe34c0db2e5d90dc484657a7813f985203df7eb17b9908ad1ad089f37b7b9"
LIMITS = dict(maximum_requests=3, maximum_response_bytes=262144,
              maximum_total_bytes=786432, connect_timeout_seconds=5,
              read_timeout_seconds=10, retries=0, redirects=0, method="GET", credentials="NONE")
NULLS = ("approved_limits", "endpoint_identity_evidence", "operator_identity", "owner_identity",
         "owner_approval_evidence", "trusted_timestamp", "expiry", "revocation",
         "durable_replay_protection", "restricted_output_custody")


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False).encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def require(condition, name):
    if not condition:
        raise ValueError(name)


def load_helpers(root):
    path = Path(root) / "scripts/research/verify_phase1446j.py"
    require(sha(path.read_bytes()) == "28d1d991616c70ce83373a22a5d536919d641a6827989df489380036070e8681", "J verifier")
    namespace = {}
    exec(compile(path.read_bytes(), "sealed_J", "exec"), namespace)
    return namespace, namespace["helpers"](root)


def validate_packets(endpoint, decision, urls):
    for packet in (endpoint, decision):
        require(packet["baseline"] == BASELINE and packet["plan_sha256"] == PLAN, "baseline/plan")
        require(packet["origin"] == "https://repo.maven.apache.org", "origin")
        require(packet["exact_urls"] == urls, "allowlist")
    require(endpoint["contract"] == "Phase1446KEndpointIdentity.v1", "endpoint contract")
    require(decision["contract"] == "Phase1446KMetadataRetrievalDecision.v1", "decision contract")
    require(endpoint["endpoint_identity"] == "UNVERIFIED", "endpoint promotion")
    require(endpoint["repository_approval"] == "NOT_APPROVED", "repository promotion")
    require(all(endpoint[k] is None for k in (
        "approved_trust_store", "endpoint_verification_evidence", "trusted_observation_time",
        "dns_network_approval", "submitted_preference", "owner_preference_provenance",
        "effective_repository", "independent_approval_evidence")), "invented endpoint authority")
    require(all(decision[k] is None for k in NULLS), "invented retrieval authority")
    require(decision["proposed_limits"] == LIMITS, "limit drift")
    require(decision["allowlist_fingerprint"] == sha(canonical(urls)), "allowlist fingerprint")
    require(decision["effective_authorization"] == decision["status"] == "NOT_AUTHORIZED", "self approval")
    for key in ("jar_acquisition", "java_implementation", "runtime_validation", "gate_a_b"):
        require(decision[key] == "NOT_AUTHORIZED", key)
    require(decision["historical_j_recovery_receipt"] == "RECOVERY_RECEIPT_UNAVAILABLE", "historical promotion")
    require(decision["accepted_prospective_receipt_sha256"] == PROSPECTIVE, "prospective binding")
    require(endpoint["network_requests"] == decision["network_requests"] == decision["downloads"] == 0, "side effect")
    require(decision["operational_readiness"] == "NO_GO", "readiness")
    return True


def verify(root, inputs):
    root = Path(root)
    require(inputs["baseline"] == BASELINE and inputs["plan_sha256"] == PLAN, "verification inputs")
    for name, digest in inputs["hashes"].items():
        path = Path(name)
        require(sha((path if path.is_absolute() else root / path).read_bytes()) == digest, "preservation:" + name)
    require(len(inputs["historical_missing"]) == 73, "historical missing count")
    require(not any(Path(n).exists() for n in inputs["historical_missing"]), "historical reconstruction")
    require(len(inputs["java_absent"]) == 8 and not any((root / n).exists() for n in inputs["java_absent"]), "Java")
    require(not (root / "research/phase-14.46f/generation-1/evidence-manifest.json").exists(), "F manifest")
    failure = Path(inputs["failure_receipt"])
    require(sha(failure.read_bytes()) == "2f4814c2a3974d25e26d2cd71b70e9761167ace0c02a1381baff6e37bc28a659", "F failure")
    require(not any("replay" in p.name or "certificate" in p.name for p in failure.parent.iterdir()), "F completion")
    jbase = root / "research/phase-14.46j/generation-1"
    jtrust = json.loads((jbase / "endpoint-trust.json").read_bytes())
    jdecision = json.loads((jbase / "metadata-approval.json").read_bytes())
    j, helper = load_helpers(root)
    urls = j["source_urls"](root)
    require(urls == jtrust["exact_urls"] == jdecision["exact_url_allowlist"], "sealed three URLs")
    require(len(urls) == len(set(urls)) == 3, "three unique URLs")
    for url in urls:
        parsed = urlsplit(url)
        require(parsed.scheme == "https" and parsed.netloc == "repo.maven.apache.org", "URL origin")
        require(url.endswith(".pom") and not any(x in url for x in ("*", "%", "SNAPSHOT", ".jar", "..")), "URL path")
    base = root / "research/phase-14.46k/generation-1"
    endpoint = json.loads((base / "endpoint-assessment.json").read_bytes())
    decision = json.loads((base / "retrieval-decision.json").read_bytes())
    validate_packets(endpoint, decision, urls)
    tests = {}
    exec(compile((root / "scripts/research/test_phase1446k.py").read_bytes(), "K_tests", "exec"), tests)
    cases = tests["run"](j["synthetic_policy"], validate_packets, helper, endpoint, decision, urls, NULLS)
    schemas = [helper["snapshot_schema"](p) for p in (endpoint, decision)]
    return dict(classification="PHASE_14_46K_ENDPOINT_IDENTITY_PENDING", baseline=BASELINE, plan_sha256=PLAN,
                accepted_prospective_receipt_sha256=PROSPECTIVE, historical_j_recovery_receipt="RECOVERY_RECEIPT_UNAVAILABLE",
                exact_urls=urls, cases=cases, schema_fingerprints=[sha(canonical(s)) for s in schemas],
                protected_hashes=len(inputs["hashes"]), baseline_files=1989, historical_missing=73,
                absent_java_files=8, newline_reconciliations=13, frozen_universe=1115, research_family=13380,
                endpoint_identity="UNVERIFIED", repository_approval="NOT_APPROVED", metadata_retrieval="NOT_AUTHORIZED",
                approved_limits=None, operational_readiness="NO_GO")
