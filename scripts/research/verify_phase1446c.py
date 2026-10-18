"""Non-launching preparation verifier; grants no implementation authority."""
import hashlib
import json
import re
from pathlib import Path
from xml.etree import ElementTree

BASELINE = "5143eeb804a5f2d20812069efb783ee194b500f5"
PLAN = "9352be0961252f497230d209d094ff7e33fea0a481168b39a0d6efbed38751b8"
PERMISSIONS = {"/api/trading/" + n: "trading:" + n.replace("/status", "") + ":read"
               for n in ("strategies", "instruments", "qualifications", "signals",
                         "positions", "orders", "risk/status")}


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def require(condition, label):
    if not condition:
        raise ValueError(label)


def fixture_decision(request):
    """Synthetic policy only, never a principal or real authorization."""
    if request.get("synthetic") is not True:
        return "SYNTHETIC_TO_REAL_REJECTED"
    if request.get("baseline") != BASELINE:
        return "WRONG_BASELINE"
    if request.get("enabled") is not True:
        return "DISABLED"
    if request.get("method") != "GET":
        return "METHOD_DENIED"
    path = request.get("path")
    if path == "/api/trading/status":
        return "STATIC_ONLY"
    if path not in PERMISSIONS:
        return "UNKNOWN_ENDPOINT"
    if request.get("backend") is not True:
        return "BACKEND_UNAVAILABLE"
    if request.get("verified_identity") is not True:
        return "UNAUTHENTICATED"
    if request.get("expired") is not False or request.get("revoked") is not False:
        return "INVALID_AUTHENTICATION"
    if request.get("role") != "SYNTHETIC_READER":
        return "UNKNOWN_ROLE"
    if PERMISSIONS[path] not in request.get("permissions", ()):
        return "FORBIDDEN"
    if request.get("account_scope") is not True:
        return "FORBIDDEN"
    limit = request.get("limit")
    if type(limit) is not int or not 1 <= limit <= 100:
        return "INVALID_PAGINATION"
    if request.get("source_available") is not True:
        return "DATA_UNAVAILABLE"
    return "SYNTHETIC_READ_ONLY"


def policy_checks():
    fixture = dict(synthetic=True, baseline=BASELINE, enabled=True, method="GET",
                   path="/api/trading/strategies", backend=True, verified_identity=True,
                   expired=False, revoked=False, role="SYNTHETIC_READER",
                   permissions=["trading:strategies:read"], account_scope=True,
                   limit=25, source_available=True)
    mutations = [
        ("synthetic", False, "SYNTHETIC_TO_REAL_REJECTED"),
        ("baseline", "wrong", "WRONG_BASELINE"), ("enabled", False, "DISABLED"),
        ("method", "POST", "METHOD_DENIED"), ("method", "DELETE", "METHOD_DENIED"),
        ("path", "/api/trading/paper/orders", "UNKNOWN_ENDPOINT"),
        ("backend", False, "BACKEND_UNAVAILABLE"),
        ("verified_identity", False, "UNAUTHENTICATED"),
        ("verified_identity", 1, "UNAUTHENTICATED"),
        ("expired", True, "INVALID_AUTHENTICATION"),
        ("expired", None, "INVALID_AUTHENTICATION"),
        ("revoked", True, "INVALID_AUTHENTICATION"),
        ("revoked", None, "INVALID_AUTHENTICATION"),
        ("role", "UNTRUSTED_HEADER_ADMIN", "UNKNOWN_ROLE"),
        ("permissions", [], "FORBIDDEN"), ("permissions", ["*"], "FORBIDDEN"),
        ("permissions", ["paper:orders:write"], "FORBIDDEN"),
        ("account_scope", False, "FORBIDDEN"),
        ("limit", 0, "INVALID_PAGINATION"), ("limit", 101, "INVALID_PAGINATION"),
        ("limit", True, "INVALID_PAGINATION"), ("limit", "25", "INVALID_PAGINATION"),
        ("source_available", False, "DATA_UNAVAILABLE"),
        ("path", "/api/trading/status", "STATIC_ONLY"),
    ]
    cases = [(key + "=" + str(value), dict(fixture, **{key: value}), expected)
             for key, value, expected in mutations]
    for path, permission in PERMISSIONS.items():
        cases.append((path, dict(fixture, path=path, permissions=[permission]),
                      "SYNTHETIC_READ_ONLY"))
    cases.append(("broker_and_header_not_identity", dict(fixture, verified_identity=False,
                  identity_header="SYNTHETIC", broker_token="SYNTHETIC"), "UNAUTHENTICATED"))
    results = []
    for name, request, expected in cases:
        result = fixture_decision(request)
        require(result == expected, "POLICY:" + name)
        results.append(dict(case=name, outcome=result, result="PASS", real_authority=False))
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
    require(len(inputs["historical_missing"]) == 73, "MISSING_RECEIPT_COUNT")
    require(not any(Path(n).exists() for n in inputs["historical_missing"]), "HISTORICAL_RECONSTRUCTION")
    identity = json.loads((root / inputs["identity"]).read_bytes())
    dependency = json.loads((root / inputs["dependency"]).read_bytes())
    require(identity["baseline"] == dependency["baseline"] == BASELINE, "BASELINE")
    require(identity["plan_sha256"] == PLAN, "PLAN_BINDING")
    require(identity["selected_provider"] is None, "PROVIDER_SELECTED")
    require(identity["identity_provider_status"] == "IDENTITY_PROVIDER_NOT_SELECTED", "PROVIDER_STATUS")
    require(all(v is None for v in identity["provider_fields"].values()), "PROVIDER_FIELDS")
    require(all(v is None for v in identity["governance_authority"].values()), "AUTHORITY_FIELDS")
    require(identity["implementation_authorization"] == identity["runtime_authorization"] == "NOT_AUTHORIZED", "AUTHORITY")
    require(identity["protected_activation"] == identity["live_orders"] == "DISABLED", "ACTIVATION")
    require(len(set(identity["java_inventory"])) == 8, "JAVA_SCOPE")
    require(not any((root / n).exists() for n in identity["java_inventory"]), "JAVA_IMPLEMENTED")
    actual = {x["path"]: x["permission"] for x in identity["permission_matrix"] if x["protected"]}
    require(actual == PERMISSIONS, "PERMISSION_MATRIX")
    require(dependency["boot_version"] == "3.5.16" and dependency["security_bom_version"] == "6.5.11", "BOM_BINDING")
    require(dependency["offline_dependency_closure"] == "NOT_ESTABLISHED", "CLOSURE")
    require(len(dependency["candidates"]) == 3, "DEPENDENCY_COUNT")
    for candidate in dependency["candidates"]:
        expected = "6.5.11" if candidate["artifact"] == "spring-security-test" else "3.5.16"
        require(candidate["managed_version"] == expected, "DEPENDENCY_VERSION")
        require(candidate["jar_sha256"] is None and candidate["pom_sha256"] is None, "INVENTED_ARCHIVE")
    proposals = json.loads(Path(inputs["proposals"]).read_bytes())
    require(proposals["applied"] is False and proposals["java_compiled"] is False, "PROPOSAL_ACTIVATED")
    for proposal in proposals["proposals"]:
        path = root / proposal["path"]
        if proposal["before_sha256"] is None:
            require(not path.exists(), "NEW_IMPLEMENTATION")
            original = ""
        else:
            require(sha(path.read_bytes()) == proposal["before_sha256"], "PROPOSAL_SOURCE")
            original = path.read_text(encoding="utf-8")
        expected = proposal["proposed_text"].replace("\r\n", "\n")
        require(apply_proposed_diff(original, proposal["diff"]) == expected, "PATCH_ROUNDTRIP")
        require(sha(proposal["proposed_text"].encode()) == proposal["after_sha256"], "PROPOSAL_HASH")
        if path.suffix == ".xml":
            ElementTree.fromstring(expected)
    return dict(classification="PHASE_14_46C_IDENTITY_PROVIDER_PENDING", baseline=BASELINE,
                plan=PLAN, protected_inputs=len(inputs["hashes"]), historical_missing=73,
                policies=policy_checks(), managed_boot="3.5.16", managed_security="6.5.11",
                offline_closure="NOT_ESTABLISHED", provider="IDENTITY_PROVIDER_NOT_SELECTED",
                implementation="NOT_AUTHORIZED", runtime="NOT_AUTHORIZED", live_orders="DISABLED")
