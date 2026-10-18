"""Offline source/allowlist preparation; never performs acquisition."""
import hashlib
import json
from pathlib import Path

BASELINE = "72e5c9914317320dca038ded075785df95048282"
PLAN = "df330ebfdda69fba8c962245a86af0c263b96fd1c0842757f759ba5a13049742"
NULL_APPROVAL = ("approved_repository", "network_endpoint", "maximum_requests", "maximum_bytes",
                 "timeout_seconds", "retry_limit", "destination", "operator_identity", "approval_evidence",
                 "trusted_timestamp", "expiry", "revocation", "durable_replay_protection", "cleanup_authority")
ROOTS = (("org.springframework.boot", "spring-boot-starter-security", "3.5.16", "compile"),
         ("org.springframework.boot", "spring-boot-starter-oauth2-client", "3.5.16", "compile"),
         ("org.springframework.security", "spring-security-test", "6.5.11", "test"))


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def require(condition, label):
    if not condition:
        raise ValueError(label)


def validate_pending(sources, allowlist, decision):
    for packet in (sources, allowlist, decision):
        require(packet["baseline"] == BASELINE and packet["plan_sha256"] == PLAN, "baseline/plan")
    require(sources["effective_selected_repository"] is None, "selected repository")
    require(sources["network_requests"] == 0 and sources["credentials_read"] is False, "source side effects")
    require(sources["publisher_provenance"] == "UNVERIFIED", "source provenance")
    require(len(sources["candidates"]) == 5, "candidate count")
    for candidate in sources["candidates"]:
        require(candidate["approved_repository_identity"] is None and candidate["publisher_identity"] is None, "candidate trust")
        require(candidate["approval_status"] == "NOT_AUTHORIZED" and candidate["network_verified"] is False, "candidate authorization")
    require(allowlist["status"] == "PARTIAL" and allowlist["complete"] is False and allowlist["executable"] is False, "partial allowlist")
    require(allowlist["transitive_allowlist"] is None, "invented closure")
    require(allowlist["dependency_closure"] == allowlist["publisher_provenance"] == "UNVERIFIED", "allowlist provenance")
    require(allowlist["known_root_count"] == 3 and allowlist["known_root_file_requests"] == 6, "root counts")
    require(len(allowlist["entries"]) == 3, "entry count")
    for entry, expected in zip(allowlist["entries"], ROOTS):
        require(tuple(entry[k] for k in ("group_id", "artifact_id", "version", "scope")) == expected, "root coordinates")
        require(entry["type"] == "jar" and entry["classifier"] == "", "type/classifier")
        require(entry["transitive_dependency_status"] == "MISSING_METADATA" and entry["required_transitives"] is None, "transitives")
        require(all(entry[k] is None for k in ("repository_origin", "license", "publisher_identity")), "unknown provenance")
        require(entry["acquisition_status"] == "NOT_AUTHORIZED", "entry authorization")
        require([x["type"] for x in entry["required_artifacts"]] == ["pom", "jar"], "file types")
        for artifact in entry["required_artifacts"]:
            require(artifact["classifier"] == "" and artifact["status"] == "MISSING", "archive status")
            require(all(artifact[k] is None for k in ("expected_sha256", "actual_sha256", "size_limit_bytes")), "invented archive identity")
    require(decision["status"] == "NOT_AUTHORIZED" and decision["decision"] == "DEFER_PENDING_SOURCE_VERIFICATION", "decision promotion")
    require(decision["allowlist_fingerprint"] == sha(canonical(allowlist)), "allowlist binding")
    require(all(decision[k] is None for k in NULL_APPROVAL), "unapproved authority/limits")
    require(all(decision[k] == "NOT_AUTHORIZED" for k in ("dependency_acquisition", "java_implementation", "runtime_validation", "gate_a_b")), "authority")
    require(decision["network_requests"] == decision["downloads"] == 0, "acquisition side effects")
    require(decision["live_orders"] == decision["protected_endpoint_activation"] == "DISABLED", "activation")
    require(decision["operational_readiness"] == "NO_GO", "readiness")
    return True


def helpers(root):
    namespace = {}
    source = (Path(root) / "scripts/research/verify_phase1446g.py").read_bytes()
    require(sha(source) == "96d580b759bc13966d7a462e052d3849beb0fa9a765551ae1189d3a9c9fc1505", "G helper source")
    exec(compile(source, "verify_phase1446g.py", "exec"), namespace)
    return namespace


def verify(root, inputs):
    root = Path(root)
    require(inputs["baseline"] == BASELINE and inputs["plan"] == PLAN, "input baseline")
    for name, expected in inputs["hashes"].items():
        p = Path(name)
        require(sha((p if p.is_absolute() else root / p).read_bytes()) == expected, "preservation:" + name)
    require(len(inputs["historical_missing"]) == 73 and not any(Path(n).exists() for n in inputs["historical_missing"]), "historical receipts")
    base = root / "research/phase-14.46h/generation-1"
    packets = [json.loads((base / n).read_bytes()) for n in ("source-assessment.json", "artifact-allowlist.json", "acquisition-decision.json")]
    validate_pending(*packets)
    for name, expected in packets[0]["inspected_pom_hashes"].items():
        require(sha(Path(name).read_bytes()) == expected, "source metadata drift")
    helper = helpers(root)
    reconciliation = helper["reconcile"](root)
    require(packets[1]["graph_fingerprint"] == reconciliation["graph_fingerprint"], "F graph binding")
    fgraph = json.loads((root / "research/phase-14.46f/generation-1/dependency-graph.json").read_bytes())
    for entry, original in zip(packets[1]["entries"], fgraph["top_level"]):
        require(entry["parent_bom_binding"]["source"] == original["management_source"] and entry["parent_bom_binding"]["sha256"] == original["management_sha256"], "management binding")
    failure = Path(inputs["failure_receipt"])
    require(sha(failure.read_bytes()) == "2f4814c2a3974d25e26d2cd71b70e9761167ace0c02a1381baff6e37bc28a659", "F failure")
    require(not (root / "research/phase-14.46f/generation-1/evidence-manifest.json").exists(), "F manifest")
    require(not any("replay" in p.name or "certificate" in p.name for p in failure.parent.iterdir()), "F completion evidence")
    namespace = {}
    exec(compile((root / "scripts/research/test_phase1446h.py").read_bytes(), "test_phase1446h.py", "exec"), namespace)
    receipts = namespace["run"](validate_pending, canonical, sha, NULL_APPROVAL, helper, packets)
    return dict(classification="PHASE_14_46H_SOURCE_VERIFICATION_PENDING", baseline=BASELINE, plan=PLAN,
                policy_receipts=receipts, reconciliation=reconciliation, protected_inputs=len(inputs["hashes"]),
                allowlist_fingerprint=sha(canonical(packets[1])), source_decision=packets[0]["decision"],
                acquisition="NOT_AUTHORIZED", implementation="NOT_AUTHORIZED", runtime="NOT_AUTHORIZED",
                original_f="PARTIAL_PRESERVED_IN_GIT", historical_unavailable=73, live_orders="DISABLED", operational_readiness="NO_GO")
