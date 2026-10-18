"""Offline provenance preparation; not Maven resolution or acquisition authority."""
import copy
import hashlib
import json
import re
from pathlib import Path
from xml.etree import ElementTree as ET

BASELINE = "309415049eadf32b78b0e08b2e90d4a19bc138c0"
PLAN = "c89ad93ce0927b13622b5768753daa50a7e33d2fc0925c62e0172d15058fa0b6"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
NULL_FIELDS = ("exact_artifact_allowlist", "approved_repository_identity", "repository_url",
               "expected_hashes", "artifact_size_limits", "total_download_cap", "timeout",
               "retry_count", "network_restrictions", "output_destination", "operator_identity",
               "approval_evidence", "trusted_timestamp", "expiry", "revocation",
               "durable_replay_protection", "cleanup_authority")


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def require(condition, label):
    if not condition:
        raise ValueError(label)


def resolve(value, properties, seen=()):
    if value is None:
        return None
    for key in re.findall(r"\$\{([^}]+)\}", value):
        if key in seen or key not in properties:
            return None
        replacement = resolve(properties[key], properties, seen + (key,))
        if replacement is None:
            return None
        value = value.replace(chr(36) + "{" + key + "}", replacement)
    return value


def properties(element):
    return {x.tag.split("}")[-1]: x.text for x in element.findall("m:properties/*", NS)}


def text(element, path, default=None):
    return element.findtext(path, default, namespaces=NS)


def dependency(element, props):
    return dict(group=text(element, "m:groupId"), artifact=text(element, "m:artifactId"),
                version=resolve(text(element, "m:version"), props),
                type=text(element, "m:type", "jar"), classifier=text(element, "m:classifier", ""),
                scope=text(element, "m:scope", "compile"), optional=text(element, "m:optional", "false"),
                exclusions=[(text(x, "m:groupId"), text(x, "m:artifactId"))
                            for x in element.findall("m:exclusions/m:exclusion", NS)])


def fixture_reachable(edge, scope, direct=False, exclusions=()):
    """Bounded synthetic reachability, not the Maven effective model."""
    if (edge["group"], edge["artifact"]) in exclusions:
        return False
    if edge["optional"] == "true" and not direct:
        return False
    if edge["scope"] in ("provided", "system", "import") and not direct:
        return False
    if edge["scope"] == "test" and not direct:
        return False
    return scope == "test" or edge["scope"] == "compile"


def fixture_mediate(candidates):
    """Known synthetic paths only; absent paths prevent real mediation."""
    return min(candidates, key=lambda x: (x["depth"], x["order"]))["version"]


def validate_pending(packet, graph):
    require(packet["baseline"] == graph["baseline"] == BASELINE, "BASELINE")
    require(packet["plan_sha256"] == graph["plan_sha256"] == PLAN, "PLAN")
    require(packet["status"] == "NOT_AUTHORIZED", "STATUS")
    require(packet["dependency_graph_fingerprint"] == sha(canonical(graph)), "GRAPH_BINDING")
    require(graph["complete"] is False and graph["status"] == "PARTIAL", "FALSE_CLOSURE")
    require(graph["transitive_dependency_edges"] is None, "FABRICATED_EDGES")
    for field in NULL_FIELDS:
        require(packet[field] is None, "UNAPPROVED:" + field)
    for field in ("dependency_acquisition", "implementation", "runtime"):
        require(packet[field] == "NOT_AUTHORIZED", "AUTHORITY:" + field)
    require(packet["downloads_performed"] == 0, "DOWNLOAD")


def policy_checks(packet, graph):
    results = []

    def check(name, condition):
        require(condition, "POLICY:" + name)
        results.append(dict(case=name, result="PASS", real_authority=False))

    check("literal_version", resolve("6.5.11", {}) == "6.5.11")
    check("property", resolve("${v}", {"v": "6.5.11"}) == "6.5.11")
    check("nested_property", resolve("${v}", {"v": "${w}", "w": "21"}) == "21")
    check("unknown_property", resolve("${missing}", {}) is None)
    check("self_cycle", resolve("${v}", {"v": "${v}"}) is None)
    check("mutual_cycle", resolve("${v}", {"v": "${w}", "w": "${v}"}) is None)
    check("child_override", resolve("${java.version}", {**{"java.version": "17"}, **{"java.version": "21"}}) == "21")
    xml = '<dependency xmlns="http://maven.apache.org/POM/4.0.0"><groupId>fixture</groupId><artifactId>sample</artifactId><version>${v}</version><type>test-jar</type><classifier>tests</classifier><scope>test</scope><optional>true</optional><exclusions><exclusion><groupId>fixture</groupId><artifactId>blocked</artifactId></exclusion></exclusions></dependency>'
    edge = dependency(ET.fromstring(xml), {"v": "1"})
    check("preserve_classifier_type", edge["type"] == "test-jar" and edge["classifier"] == "tests")
    check("preserve_optional_scope", edge["optional"] == "true" and edge["scope"] == "test")
    check("preserve_exclusion", edge["exclusions"] == [("fixture", "blocked")])
    check("optional_not_transitive", not fixture_reachable(edge, "test"))
    check("direct_optional_test", fixture_reachable(edge, "test", direct=True))
    check("test_not_compile", not fixture_reachable(edge, "compile", direct=True))
    normal = dict(edge, optional="false", scope="compile")
    check("compile_reachable", fixture_reachable(normal, "compile"))
    check("path_exclusion", not fixture_reachable(normal, "compile", exclusions=(("fixture", "sample"),)))
    check("other_path_unaffected", fixture_reachable(normal, "compile", exclusions=(("fixture", "other"),)))
    check("provided_not_transitive", not fixture_reachable(dict(normal, scope="provided"), "test"))
    check("nearest_wins", fixture_mediate([dict(depth=2, order=0, version="1"), dict(depth=1, order=1, version="2")]) == "2")
    check("declaration_order_tie", fixture_mediate([dict(depth=1, order=2, version="1"), dict(depth=1, order=1, version="2")]) == "2")
    validate_pending(packet, graph)
    check("pending_packet", True)
    mutations = [(field, "SYNTHETIC_UNAPPROVED") for field in NULL_FIELDS]
    mutations += [("status", "AUTHORIZED"), ("baseline", "wrong"), ("plan_sha256", "wrong"),
                  ("dependency_graph_fingerprint", "wrong"), ("dependency_acquisition", "AUTHORIZED"),
                  ("implementation", "AUTHORIZED"), ("runtime", "AUTHORIZED"), ("downloads_performed", 1)]
    for field, value in mutations:
        forged = copy.deepcopy(packet)
        forged[field] = value
        try:
            validate_pending(forged, graph)
        except ValueError:
            check("reject_" + field, True)
        else:
            raise ValueError("ACQUISITION_PROMOTION:" + field)
    for field, value in (("complete", True), ("transitive_dependency_edges", [])):
        forged_graph = copy.deepcopy(graph)
        forged_graph[field] = value
        forged_packet = copy.deepcopy(packet)
        forged_packet["dependency_graph_fingerprint"] = sha(canonical(forged_graph))
        try:
            validate_pending(forged_packet, forged_graph)
        except ValueError:
            check("reject_graph_" + field, True)
        else:
            raise ValueError("FALSE_GRAPH_CLOSURE")
    return results


def verify(root, inputs):
    root = Path(root)
    for name, expected in inputs["hashes"].items():
        path = Path(name)
        if not path.is_absolute():
            path = root / path
        require(sha(path.read_bytes()) == expected, "PRESERVATION:" + name)
    require(len(inputs["historical_missing"]) == 73, "HISTORICAL_COUNT")
    require(not any(Path(n).exists() for n in inputs["historical_missing"]), "HISTORICAL_RECONSTRUCTION")
    graph = json.loads((root / inputs["graph"]).read_bytes())
    inventory = json.loads((root / inputs["inventory"]).read_bytes())
    packet = json.loads((root / inputs["packet"]).read_bytes())
    validate_pending(packet, graph)
    require(not any((root / n).exists() for n in graph["original_java_inventory"]), "JAVA_IMPLEMENTATION")
    require(inventory["cache_modified"] is False and inventory["credentials_read"] is False, "CACHE")
    for name, expected in inventory["local_hashes"].items():
        require(sha(Path(name).read_bytes()) == expected, "CACHE_BYTES:" + name)
    require(not any(Path(n).exists() for n in inventory["missing_paths"]), "MISSING_ARTIFACT_APPEARED")
    require(graph["java_release"] == "21" and graph["boot_version"] == "3.5.16" and graph["security_version"] == "6.5.11", "VERSIONS")
    poms = {Path(n).name: ET.fromstring(Path(n).read_bytes()) for n in graph["metadata_hashes"]}
    boot = poms["spring-boot-dependencies-3.5.16.pom"]
    parent = poms["spring-boot-starter-parent-3.5.16.pom"]
    security = poms["spring-security-bom-6.5.11.pom"]
    project = ET.fromstring((root / "pom.xml").read_bytes())
    module = ET.fromstring((root / "apps/trading-core/pom.xml").read_bytes())
    require(text(project, "m:parent/m:version") == "3.5.16", "PROJECT_PARENT")
    require(text(parent, "m:parent/m:artifactId") == "spring-boot-dependencies", "PARENT_CHAIN")
    props = {**properties(boot), **properties(parent), **properties(project), **properties(module)}
    require(resolve(props["maven.compiler.release"], props) == "21", "JAVA_OVERRIDE")
    managed = [dependency(e, properties(boot)) for e in boot.findall("m:dependencyManagement/m:dependencies/m:dependency", NS)]
    managed += [dependency(e, properties(security)) for e in security.findall("m:dependencyManagement/m:dependencies/m:dependency", NS)]
    require(len(graph["top_level"]) == 3, "ROOT_COUNT")
    for candidate in graph["top_level"]:
        matches = [x for x in managed if (x["group"], x["artifact"]) == (candidate["group"], candidate["artifact"])]
        require(matches and all(x["version"] == candidate["version"] for x in matches), "MANAGEMENT_BINDING")
        require(candidate["required_transitive_edges"] is None and candidate["transitive_status"] == "MISSING_METADATA", "ROOT_METADATA")
    return dict(classification="PHASE_14_46F_DEPENDENCY_CLOSURE_PARTIAL", baseline=BASELINE,
                plan=PLAN, protected_inputs=len(inputs["hashes"]), local_files=len(inventory["local_hashes"]),
                missing_paths=len(inventory["missing_paths"]), graph_fingerprint=sha(canonical(graph)),
                policies=policy_checks(packet, graph), acquisition="NOT_AUTHORIZED",
                implementation="NOT_AUTHORIZED", runtime="NOT_AUTHORIZED", historical_missing=73)
