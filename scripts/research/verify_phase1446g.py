"""Additive offline schema recovery; no dependency acquisition or runtime authority."""
import hashlib
import json
import math
from pathlib import Path

from jsonschema import Draft202012Validator

DRAFT = "https://json-schema.org/draft/2020-12/schema"
BASELINE = "7c9e02b75cbcc0ec2b9043b0ae000e6c0d624420"
PLAN = "e21ca78de5a1cec3a190b21f30b5bd0b1e4056ac83f3e6a154ed987878a7c073"


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def require(condition, label):
    if not condition:
        raise ValueError(label)


def snapshot_schema(value, optional_fields=()):
    """Fixed snapshot semantics; optional_fields applies only to the root object."""
    require(type(optional_fields) in (tuple, list), "optional_fields must be a sequence")
    require(all(type(k) is str for k in optional_fields), "optional names")
    require(len(set(optional_fields)) == len(optional_fields), "duplicate optional names")
    require(not optional_fields or type(value) is dict, "optional fields require an object")
    require(not optional_fields or set(optional_fields) <= set(value), "unknown optional field")
    active = set()

    def build(item, root=False):
        kind = type(item)
        if kind in (dict, list, tuple):
            require(id(item) not in active, "cyclic input")
            active.add(id(item))
            try:
                if kind is dict:
                    require(all(type(k) is str for k in item), "non-string object key")
                    return dict(type="object", properties={k: build(v) for k, v in item.items()},
                                required=[k for k in item if not root or k not in optional_fields],
                                additionalProperties=False)
                result = dict(type="array", minItems=len(item), maxItems=len(item), items=False)
                if item:
                    result["prefixItems"] = [build(v) for v in item]
                return result
            finally:
                active.remove(id(item))
        kinds = {str: "string", int: "integer", float: "number", bool: "boolean", type(None): "null"}
        require(kind in kinds, "non-JSON input")
        require(kind is not float or math.isfinite(item), "nonfinite input")
        return dict(type=kinds[kind], const=item)

    result = build(value, root=True)
    result["$schema"] = DRAFT
    check_schema(result)
    return result


def check_schema(schema):
    require(type(schema) is dict and schema.get("$schema") == DRAFT, "wrong or missing schema draft")
    Draft202012Validator.check_schema(schema)


def accepts(schema, instance):
    check_schema(schema)
    return Draft202012Validator(schema).is_valid(instance)


def reconcile(root):
    root = Path(root)
    base = root / "research/phase-14.46f/generation-1"
    graph = json.loads((base / "dependency-graph.json").read_bytes())
    inventory = json.loads((base / "artifact-inventory.json").read_bytes())
    packet = json.loads((base / "acquisition-approval.json").read_bytes())
    require(len(inventory["artifacts"]) == 28, "artifact count")
    require(len(inventory["local_hashes"]) == 76, "local hash count")
    require(len(inventory["missing_paths"]) == 40, "missing count")
    for name, expected in inventory["local_hashes"].items():
        require(sha(Path(name).read_bytes()) == expected, "local artifact drift:" + name)
    require(not any(Path(n).exists() for n in inventory["missing_paths"]), "missing archive appeared")
    require(len(graph["boot_import_index"]) == 43, "BOM import count")
    for entry in graph["boot_import_index"]:
        require(entry["pom_present"] and sha(Path(entry["pom_path"]).read_bytes()) == entry["sha256"], "BOM drift")
    require(len(graph["top_level"]) == 3, "root count")
    for entry in graph["top_level"]:
        require(entry["transitive_status"] == "MISSING_METADATA" and entry["required_transitive_edges"] is None, "root classification")
        records = [a for a in inventory["artifacts"] if all(a[k] == entry[k] for k in ("group", "artifact", "version"))]
        require(len(records) == 1 and not records[0]["pom_present"] and not records[0]["archive_present"], "root archives")
    require(graph["status"] == "PARTIAL" and graph["complete"] is False and graph["transitive_dependency_edges"] is None, "graph promotion")
    require(graph["provenance_status"] == "UNVERIFIED_PROVENANCE", "publisher promotion")
    require(packet["dependency_graph_fingerprint"] == sha(canonical(graph)), "graph binding")
    require(all(packet[k] == "NOT_AUTHORIZED" for k in ("status", "dependency_acquisition", "implementation", "runtime")), "authority promotion")
    require(packet["downloads_performed"] == 0, "acquisition")
    require(not any((root / p).exists() for p in graph["original_java_inventory"]), "Java implementation")
    return dict(artifacts=28, local_files=76, missing_paths=40, imported_boms=43,
                missing_roots=3, graph_status="PARTIAL", dependency_closure="UNVERIFIED",
                publisher_provenance="UNVERIFIED", graph_fingerprint=sha(canonical(graph)))


def verify(root, inputs):
    root = Path(root)
    require(inputs["baseline"] == BASELINE and inputs["plan"] == PLAN, "baseline/plan")
    for name, expected in inputs["hashes"].items():
        path = Path(name)
        require(sha((path if path.is_absolute() else root / path).read_bytes()) == expected, "preservation:" + name)
    require(len(inputs["historical_missing"]) == 73, "historical count")
    require(not any(Path(n).exists() for n in inputs["historical_missing"]), "historical reconstruction")
    failure = Path(inputs["failure_receipt"])
    require(sha(failure.read_bytes()) == "2f4814c2a3974d25e26d2cd71b70e9761167ace0c02a1381baff6e37bc28a659", "original failure")
    require(not (root / "research/phase-14.46f/generation-1/evidence-manifest.json").exists(), "F manifest")
    require(not any("replay" in p.name or "certificate" in p.name for p in failure.parent.iterdir()), "F completion evidence")
    namespace = {}
    source = (root / "scripts/research/test_phase1446g.py").read_bytes()
    exec(compile(source, "test_phase1446g.py", "exec"), namespace)
    cases = namespace["run"](snapshot_schema, accepts, check_schema, canonical)
    reconciliation = reconcile(root)
    return dict(classification="PHASE_14_46G_DEPENDENCY_PENDING", baseline=BASELINE, plan=PLAN,
                original_phase_f="PARTIAL_PRESERVED_IN_GIT", original_schema_validation="FAILED",
                schema_draft=DRAFT, regression_cases=cases, reconciliation=reconciliation,
                protected_hashes=len(inputs["hashes"]), historical_receipts_unavailable=73,
                dependency_acquisition="NOT_AUTHORIZED", implementation="NOT_AUTHORIZED",
                runtime="NOT_AUTHORIZED", live_orders="DISABLED", operational_readiness="NO_GO")
