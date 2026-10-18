"""Read-only preparation model, not Spring runtime or acquisition authority."""
import copy
import hashlib
import json
import re
from pathlib import Path
from xml.etree import ElementTree

BASELINE = "7075ab142caf757e07a6e10983fa64073862f55d"
PLAN = "fe69c42c50e6c4361a914529051bafb16b63dd3a9a48f333e2bbe2476a6c5235"
PERMISSIONS = {"/api/trading/" + name: "trading:" + name.replace("/status", "") + ":read"
               for name in ("strategies", "instruments", "qualifications", "signals",
                            "positions", "orders", "risk/status")}


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def require(condition, label):
    if not condition:
        raise ValueError(label)


def mapping_inventory(root):
    result = []
    pattern = r"@(RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\s*\((.*?)\)"
    for path in sorted((Path(root) / "apps/trading-core/src/main/java").rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        prefix = ""
        boundary = text.find("public ")
        for match in re.finditer(pattern, text, re.S):
            kind, expression = match.groups()
            values = re.search(r'(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*")', expression)
            paths = re.findall(r'"([^"\n]*)"', values.group(1)) if values else re.findall(r'^\s*"([^"\n]*)"', expression)
            if kind == "RequestMapping" and match.start() < boundary:
                require(len(paths) == 1, "CLASS_MAPPING")
                prefix = paths[0]
                continue
            methods = re.findall(r"RequestMethod\.([A-Z]+)", expression) if kind == "RequestMapping" else [kind[:-7].upper()]
            require(bool(methods) and bool(paths), "UNRESOLVED_MAPPING")
            for route in paths:
                for method in methods:
                    result.append((method, prefix + route, path.relative_to(root).as_posix()))
    return sorted(result)


def future_fixture(request):
    """Canonical-path policy only; never a real principal or servlet matcher."""
    if request.get("synthetic") is not True:
        return "SYNTHETIC_TO_REAL_REJECTED"
    path = request.get("path", "")
    if any(x in path for x in ("%", ";", "\\", "..", "//")):
        return "PATH_REJECTED"
    if path != "/api/trading" and not path.startswith("/api/trading/"):
        return "UNMATCHED_EXISTING_CONTROLS"
    if request.get("method") != "GET":
        return "TRADING_METHOD_DENIED"
    if path == "/api/trading/status":
        return "STATIC_ONLY"
    if path not in PERMISSIONS:
        return "TRADING_ROUTE_DENIED"
    if request.get("future_enabled") is not True:
        return "PROTECTED_DISABLED"
    if request.get("verified_operator") is not True or request.get("backend") is not True:
        return "UNAUTHENTICATED"
    if request.get("expired") is not False or request.get("revoked") is not False:
        return "INVALID_SESSION"
    if request.get("role") != "SYNTHETIC_READER":
        return "UNKNOWN_ROLE"
    if PERMISSIONS[path] not in request.get("permissions", ()) or request.get("account_scope") is not True:
        return "FORBIDDEN"
    return "SYNTHETIC_FUTURE_READ_ONLY"


def policy_checks(assessment):
    base = dict(synthetic=True, path="/api/trading/strategies", method="GET", future_enabled=True,
                verified_operator=True, backend=True, expired=False, revoked=False,
                role="SYNTHETIC_READER", permissions=["trading:strategies:read"], account_scope=True)
    changes = [
        ("synthetic", False, "SYNTHETIC_TO_REAL_REJECTED"),
        ("method", "POST", "TRADING_METHOD_DENIED"),
        ("method", "HEAD", "TRADING_METHOD_DENIED"),
        ("method", "OPTIONS", "TRADING_METHOD_DENIED"),
        ("method", "DELETE", "TRADING_METHOD_DENIED"),
        ("future_enabled", False, "PROTECTED_DISABLED"),
        ("verified_operator", False, "UNAUTHENTICATED"),
        ("verified_operator", 1, "UNAUTHENTICATED"),
        ("backend", False, "UNAUTHENTICATED"),
        ("expired", True, "INVALID_SESSION"),
        ("expired", None, "INVALID_SESSION"),
        ("revoked", True, "INVALID_SESSION"),
        ("role", "HEADER_ADMIN", "UNKNOWN_ROLE"),
        ("permissions", [], "FORBIDDEN"),
        ("permissions", ["*"], "FORBIDDEN"),
        ("account_scope", False, "FORBIDDEN"),
        ("path", "/api/trading", "TRADING_ROUTE_DENIED"),
        ("path", "/api/trading/", "TRADING_ROUTE_DENIED"),
        ("path", "/api/trading/status/", "TRADING_ROUTE_DENIED"),
        ("path", "/api/trading/status", "STATIC_ONLY"),
        ("path", "/api/trading/paper/orders", "TRADING_ROUTE_DENIED"),
        ("path", "/api/tradingevil/status", "UNMATCHED_EXISTING_CONTROLS"),
        ("path", "/unknown", "UNMATCHED_EXISTING_CONTROLS"),
        ("path", "/api/trading/%2e%2e/status", "PATH_REJECTED"),
        ("path", "/api/trading//status", "PATH_REJECTED"),
        ("path", "/api/trading/status;test", "PATH_REJECTED"),
    ]
    cases = [(key + "=" + str(value), dict(base, **{key: value}), expected)
             for key, value, expected in changes]
    for path, permission in PERMISSIONS.items():
        cases.append((path, dict(base, path=path, permissions=[permission]), "SYNTHETIC_FUTURE_READ_ONLY"))
    for route in assessment["routes"]:
        if route["new_chain"] != "MATCH_TRADING":
            cases.append((route["method"] + ":" + route["path"],
                          dict(base, path=route["path"], method=route["method"]), "UNMATCHED_EXISTING_CONTROLS"))
    cases.append(("broker_token_header_not_identity", dict(base, verified_operator=False,
                  broker_token="SYNTHETIC", identity_header="SYNTHETIC"), "UNAUTHENTICATED"))
    results = []
    for name, request, expected in cases:
        result = future_fixture(request)
        require(result == expected, "POLICY:" + name)
        results.append(dict(case=name, result="PASS", outcome=result, real_authority=False))
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
    assessment = json.loads((root / inputs["assessment"]).read_bytes())
    packet = json.loads((root / inputs["packet"]).read_bytes())
    require(assessment["baseline"] == packet["baseline"] == BASELINE, "BASELINE")
    require(assessment["plan_sha256"] == packet["plan_sha256"] == PLAN, "PLAN")
    require(assessment["matcher"] == "/api/trading/**" and assessment["filter_chain_order"] == 1, "CHAIN")
    require(assessment["fallback_chain"] is None, "GLOBAL_FALLBACK")
    require(assessment["unmatched_policy"] == "NO_NEW_MATCHING_CHAIN_EXISTING_CONTROLS_ONLY_NOT_OPERATOR_AUTHENTICATED", "NO_MATCH")
    require(assessment["activation"] == assessment["live_orders"] == "DISABLED", "ACTIVATION")
    require(assessment["effective_provider"] is None, "PROVIDER")
    require(assessment["implementation"] == assessment["runtime"] == packet["status"] == "NOT_AUTHORIZED", "AUTHORITY")
    require(not any((root / p).exists() for p in assessment["java_inventory"]), "JAVA")
    recorded = sorted((x["method"], x["path"], x["controller"]) for x in assessment["routes"])
    require(recorded == mapping_inventory(root) and len(recorded) == 54, "ROUTE_COVERAGE")
    permissions = {x["path"]: x["permission"] for x in assessment["permission_matrix"] if x["protected"]}
    require(permissions == PERMISSIONS, "PERMISSIONS")
    require(packet["dependency_downloads"] == 0 and packet["dependency_closure"] == "UNVERIFIED", "ACQUISITION")
    for field in ("transitive_inventory", "plugin_closure", "acquisition_destination",
                  "approved_network_boundary", "approved_dependency_inventory", "maximum_acquisition_scope"):
        require(packet[field] is None, "UNKNOWN:" + field)
    require(all(v is None for v in packet["authority"].values()), "ACQUISITION_AUTHORITY")
    for coordinate in packet["coordinates"]:
        for field in ("repository_origin", "expected_archive_sha256", "local_archive_sha256", "license"):
            require(coordinate[field] is None, "UNKNOWN_PROVENANCE")
    proposals = json.loads(Path(inputs["proposals"]).read_bytes())
    require(proposals["applied"] is False and proposals["java_compiled"] is False, "PROPOSAL_STATE")
    for item in proposals["proposals"]:
        path = root / item["path"]
        if item["before_sha256"] is None:
            require(not path.exists(), "IMPLEMENTATION_FILE")
            original = ""
        else:
            require(sha(path.read_bytes()) == item["before_sha256"], "SOURCE_HASH")
            original = path.read_text(encoding="utf-8")
        expected = item["proposed_text"].replace(chr(13) + chr(10), chr(10))
        require(apply_proposed_diff(original, item["diff"]) == expected, "PATCH")
        require(sha(item["proposed_text"].encode()) == item["after_sha256"], "PROPOSAL_HASH")
        if path.suffix == ".xml":
            ElementTree.fromstring(expected)
    return dict(classification="PHASE_14_46E_DEPENDENCY_PENDING", baseline=BASELINE,
                plan=PLAN, explicit_routes=54, controllers=5, protected_inputs=len(inputs["hashes"]),
                policies=policy_checks(assessment), acquisition="NOT_AUTHORIZED",
                implementation="NOT_AUTHORIZED", runtime="NOT_AUTHORIZED", dependency_closure="UNVERIFIED",
                effective_provider=None, live_orders="DISABLED", historical_missing=73)
