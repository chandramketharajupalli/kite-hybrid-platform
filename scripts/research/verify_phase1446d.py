"""Read-only Phase 14.46D preparation verification; creates no authority."""
import copy
import hashlib
import json
import re
from pathlib import Path
from xml.etree import ElementTree

BASELINE = "defde6dc49c89acc23188d241f18c2865f026e68"
PLAN = "d5b5e8dff29007bb04663cbced683bd41a1cbb0030d416bec8cf52c52164819c"


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def require(condition, label):
    if not condition:
        raise ValueError(label)


def validate_packets(decision, authorization):
    require(decision["baseline"] == authorization["baseline"] == BASELINE, "BASELINE")
    require(decision["plan_sha256"] == authorization["plan_sha256"] == PLAN, "PLAN")
    require(decision["submitted_preference"] == "PENDING", "SUBMITTED_PREFERENCE")
    require(decision["submitted_scope"] == "DESIGN_ONLY", "SUBMITTED_SCOPE")
    require(decision["effective_selected_provider"] is None, "PROVIDER_PROMOTION")
    require(decision["owner_preference_status"] == "PENDING_OWNER_SELECTION", "SELECTION_STATUS")
    require(decision["decision_provenance"] == "EXPLICIT_OWNER_CHAT_INSTRUCTION", "PROVENANCE")
    require(decision["decision_classification"] == "PROPOSED_NOT_AUTHENTICATED", "AUTHENTICATION")
    require(decision["authentication_status"] == decision["operator_authentication"] == "NOT_ESTABLISHED", "IDENTITY")
    require(decision["provider_decision"] == "DEFER_PENDING_APPROVED_IDENTITY_PROVIDER", "DEFERRAL")
    for field in ("implementation_authorization", "dependency_acquisition_authorization",
                  "runtime_execution_authorization"):
        require(decision[field] == "NOT_AUTHORIZED", "AUTHORITY:" + field)
    require(decision["protected_endpoint_activation"] == decision["live_order_placement"] == "DISABLED", "ACTIVATION")
    for field in ("provider_fields", "approval_fields"):
        require(all(v is None for v in decision[field].values()), "UNKNOWN_FIELDS:" + field)
    require(authorization["status"] == "NOT_AUTHORIZED", "IMPLEMENTATION_AUTHORITY")
    require(all(v is None for v in authorization["approval_fields"].values()), "APPROVAL_FIELDS")
    require(set(authorization["independent_gates"]) == {"dependency_acquisition", "java_implementation",
            "provider_registration", "runtime_execution", "live_trading"}, "GATE_INVENTORY")
    require(all(v == "NOT_AUTHORIZED" for v in authorization["independent_gates"].values()), "GATE_PROMOTION")
    require(authorization["protected_endpoint_activation"] == "DISABLED", "ENDPOINT_ACTIVATION")
    require(authorization["operational_readiness"] == "NO_GO", "READINESS")
    require(len(set(authorization["java_inventory"])) == 8, "JAVA_SCOPE")
    return True


def policy_checks(decision, authorization):
    validate_packets(decision, authorization)
    mutations = [
        ("decision", ("baseline",), "wrong"),
        ("decision", ("plan_sha256",), "wrong"),
        ("decision", ("submitted_preference",), "SELECTED"),
        ("decision", ("submitted_scope",), "IMPLEMENT"),
        ("decision", ("effective_selected_provider",), "SYNTHETIC_PROVIDER"),
        ("decision", ("owner_preference_status",), "APPROVED"),
        ("decision", ("decision_classification",), "AUTHENTICATED"),
        ("decision", ("authentication_status",), "ESTABLISHED"),
        ("decision", ("operator_authentication",), "ESTABLISHED"),
        ("decision", ("decision_provenance",), "TECHNICAL_RECOMMENDATION"),
        ("decision", ("provider_decision",), "SELECTED"),
        ("decision", ("implementation_authorization",), "AUTHORIZED"),
        ("decision", ("runtime_execution_authorization",), "AUTHORIZED"),
        ("decision", ("dependency_acquisition_authorization",), "AUTHORIZED"),
        ("decision", ("protected_endpoint_activation",), "ENABLED"),
        ("decision", ("live_order_placement",), "ENABLED"),
        ("decision", ("provider_fields", "issuer"), "SYNTHETIC_ISSUER"),
        ("decision", ("approval_fields", "trusted_approval_time"), "SYNTHETIC_TIME"),
        ("decision", ("approval_fields", "expiry"), "SYNTHETIC_EXPIRY"),
        ("decision", ("approval_fields", "revocation"), "SYNTHETIC_CLEAR"),
        ("decision", ("approval_fields", "replay_ledger"), "IN_MEMORY_FIXTURE"),
        ("authorization", ("status",), "AUTHORIZED"),
        ("authorization", ("approval_fields", "signature"), "SYNTHETIC_SIGNATURE"),
        ("authorization", ("baseline",), "wrong"),
        ("authorization", ("plan_sha256",), "wrong"),
        ("authorization", ("protected_endpoint_activation",), "ENABLED"),
        ("authorization", ("operational_readiness",), "GO"),
        ("authorization", ("java_inventory",), []),
    ]
    for gate in authorization["independent_gates"]:
        mutations.append(("authorization", ("independent_gates", gate), "AUTHORIZED"))
    results = [dict(case="pending_packet", result="PASS", outcome="PENDING_ONLY", real_authority=False)]
    for target, path, value in mutations:
        dec, auth = copy.deepcopy(decision), copy.deepcopy(authorization)
        current = dec if target == "decision" else auth
        for key in path[:-1]:
            current = current[key]
        current[path[-1]] = value
        try:
            validate_packets(dec, auth)
        except ValueError as error:
            results.append(dict(case=target + "." + ".".join(path), result="PASS",
                                outcome="REJECTED:" + str(error), real_authority=False))
        else:
            raise ValueError("POLICY_ACCEPTED_INVALID:" + ".".join(path))
    return results


def apply_proposed_diff(original, diff):
    """Apply unified text diff only in memory and validate every context line."""
    lines = original.splitlines(True)
    result = []
    cursor = 0
    patch = diff.splitlines(True)
    require(patch[0].startswith("--- ") and patch[1].startswith("+++ "), "PATCH_HEADER")
    for line in patch[2:]:
        if line.startswith("@@ "):
            match = re.match(r"@@ -(\d+)(?:,\d+)? \+\d+(?:,\d+)? @@", line)
            require(match is not None, "PATCH_HUNK")
            start = max(0, int(match.group(1)) - 1)
            require(start >= cursor, "PATCH_ORDER")
            result.extend(lines[cursor:start])
            cursor = start
        elif line.startswith("+"):
            result.append(line[1:])
        elif line.startswith((" ", "-")):
            require(cursor < len(lines) and lines[cursor] == line[1:], "PATCH_CONTEXT")
            if line[0] == " ":
                result.append(lines[cursor])
            cursor += 1
        else:
            raise ValueError("PATCH_LINE")
    result.extend(lines[cursor:])
    return "".join(result)

def verify(root, inputs):
    root = Path(root)
    for name, expected in inputs["hashes"].items():
        path = Path(name)
        if not path.is_absolute():
            path = root / path
        require(sha(path.read_bytes()) == expected, "PRESERVATION:" + name)
    require(len(inputs["historical_missing"]) == 73, "HISTORICAL_COUNT")
    require(not any(Path(n).exists() for n in inputs["historical_missing"]), "HISTORICAL_RECONSTRUCTION")
    decision = json.loads((root / inputs["decision"]).read_bytes())
    authorization = json.loads((root / inputs["authorization"]).read_bytes())
    validate_packets(decision, authorization)
    require(not any((root / n).exists() for n in authorization["java_inventory"]), "JAVA_CREATED")
    require(sha((root / "docs/operations/phase-14.46d-plan.md").read_bytes()) == PLAN, "FROZEN_PLAN")
    for n, h in authorization["source_bindings"].items():
        require(sha((root / n).read_bytes()) == h, "SOURCE_BINDING")
    dependency = json.loads(Path(inputs["dependency"]).read_bytes())
    require(dependency["boot_version"] == "3.5.16" and dependency["security_bom_version"] == "6.5.11", "BOM")
    require(dependency["closure_status"] == "UNVERIFIED", "DEPENDENCY_CLOSURE")
    require(dependency["dependencies_added"] is False, "DEPENDENCY_ADDED")
    require(len(dependency["candidates"]) == 3, "DEPENDENCIES")
    for candidate in dependency["candidates"]:
        require(candidate["pom_sha256"] is None and candidate["jar_sha256"] is None, "ARCHIVE_FABRICATION")
    proposals = json.loads(Path(inputs["proposals"]).read_bytes())
    require(proposals["applied"] is False and proposals["java_compiled"] is False, "IMPLEMENTATION")
    for proposal in proposals["proposals"]:
        path = root / proposal["path"]
        if proposal["before_sha256"] is None:
            require(not path.exists(), "UNAPPROVED_FILE")
            original = ""
        else:
            require(sha(path.read_bytes()) == proposal["before_sha256"], "BEFORE_HASH")
            original = path.read_text(encoding="utf-8")
        require(sha(proposal["proposed_text"].encode()) == proposal["after_sha256"], "PROPOSED_HASH")
        expected = proposal["proposed_text"].replace("\r\n", "\n")
        require(apply_proposed_diff(original, proposal["diff"]) == expected, "PATCH_RECONSTRUCTION")
        if path.suffix == ".xml":
            ElementTree.fromstring(expected)
    return dict(classification="PHASE_14_46D_IDENTITY_PROVIDER_PENDING", baseline=BASELINE,
                plan=PLAN, submitted_preference="PENDING", submitted_scope="DESIGN_ONLY",
                effective_provider=None, authentication="NOT_ESTABLISHED",
                protected_inputs=len(inputs["hashes"]), historical_missing=73,
                policies=policy_checks(decision, authorization),
                dependency_closure="UNVERIFIED", implementation="NOT_AUTHORIZED",
                runtime="NOT_AUTHORIZED", protected_endpoints="DISABLED", live_orders="DISABLED")
