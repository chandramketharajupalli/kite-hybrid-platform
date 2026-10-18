"""Pure offline architecture assessment. No authentication or execution authority."""
import hashlib
import json
from pathlib import Path

BASELINE = "a03769be3b6b54f1db3f233d8b51bb6367c47953"
PERMISSIONS = {"/api/trading/" + name: "trading:" + name.replace("/status", "") + ":read"
               for name in ("strategies", "instruments", "qualifications", "signals",
                            "positions", "orders", "risk/status")}


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(value):
    return hashlib.sha256(value).hexdigest()


def authorize_implementation(_evidence):
    return False


def fixture_decision(request):
    """Synthetic policy model; a fixture result is never an authenticated principal."""
    if request.get("synthetic") is not True:
        return "SYNTHETIC_TO_REAL_REJECTED"
    if request.get("enabled") is not True:
        return "DISABLED"
    if request.get("method") != "GET":
        return "METHOD_DENIED"
    path = request.get("path")
    if path == "/api/trading/status":
        return "STATIC_METADATA_ONLY"
    if path not in PERMISSIONS:
        return "UNKNOWN_ENDPOINT"
    if request.get("backend") is not True:
        return "AUTHENTICATION_UNAVAILABLE"
    if request.get("verified_identity") is not True:
        return "UNAUTHENTICATED"
    if request.get("expired") is not False or request.get("revoked") is not False:
        return "INVALID_AUTHENTICATION"
    if request.get("role") != "TEST_ONLY_READER":
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
    fixture = dict(synthetic=True, enabled=True, method="GET", path="/api/trading/strategies",
                   backend=True, verified_identity=True, expired=False, revoked=False,
                   role="TEST_ONLY_READER", permissions=("trading:strategies:read",),
                   account_scope=True, limit=25, source_available=True)
    mutations = [
        ("synthetic", False, "SYNTHETIC_TO_REAL_REJECTED"),
        ("enabled", False, "DISABLED"), ("method", "POST", "METHOD_DENIED"),
        ("path", "/api/trading/paper/orders", "UNKNOWN_ENDPOINT"),
        ("backend", False, "AUTHENTICATION_UNAVAILABLE"),
        ("verified_identity", False, "UNAUTHENTICATED"),
        ("verified_identity", 1, "UNAUTHENTICATED"),
        ("expired", True, "INVALID_AUTHENTICATION"),
        ("revoked", True, "INVALID_AUTHENTICATION"),
        ("role", "ADMIN_HEADER", "UNKNOWN_ROLE"),
        ("permissions", ("paper:orders:write",), "FORBIDDEN"),
        ("permissions", ("*",), "FORBIDDEN"), ("account_scope", False, "FORBIDDEN"),
        ("limit", 0, "INVALID_PAGINATION"), ("limit", 101, "INVALID_PAGINATION"),
        ("limit", True, "INVALID_PAGINATION"), ("limit", "25", "INVALID_PAGINATION"),
        ("source_available", False, "DATA_UNAVAILABLE"),
        ("path", "/api/trading/status", "STATIC_METADATA_ONLY"),
    ]
    cases = [(key + "_" + str(value), dict(fixture, **{key: value}), expected)
             for key, value, expected in mutations]
    for path, permission in PERMISSIONS.items():
        cases.append((path, dict(fixture, path=path, permissions=(permission,)),
                      "SYNTHETIC_READ_ONLY"))
    forged = dict(fixture, verified_identity=False, identity_header="TEST_ONLY",
                  kite_token="TEST_ONLY")
    cases.append(("header_and_broker_token_not_identity", forged, "UNAUTHENTICATED"))
    results = []
    for name, request, expected in cases:
        actual = fixture_decision(request)
        if actual != expected or authorize_implementation(request) is not False:
            raise ValueError("POLICY_FAILURE:" + name)
        results.append(dict(case=name, result="PASS", outcome=actual,
                            real_authorization=False))
    return results


def verify(root, inputs):
    root = Path(root)
    for name, expected in inputs["hashes"].items():
        p = Path(name)
        if not p.is_absolute():
            p = root / p
        if sha(p.read_bytes()) != expected:
            raise ValueError("PRESERVATION:" + name)
    if any(Path(n).exists() for n in inputs["historical_missing"]):
        raise ValueError("HISTORICAL_RECEIPT_APPEARED")
    packet = json.loads((root / inputs["packet"]).read_bytes())
    if packet["git_sha"] != BASELINE or packet["status"] != "NOT_AUTHORIZED":
        raise ValueError("BASELINE_OR_AUTHORITY")
    if any(v is not None for v in packet["authority"].values()):
        raise ValueError("UNKNOWN_APPROVAL_POPULATED")
    if packet["future_paths"]["selected_path"] is not None:
        raise ValueError("IMPLEMENTATION_PATH_SELECTED")
    if len(set(packet["java_inventory"])) != 8:
        raise ValueError("SCOPE")
    if any((root / n).exists() for n in packet["java_inventory"]):
        raise ValueError("JAVA_IMPLEMENTATION")
    assessment = json.loads((root / inputs["assessment"]).read_bytes())
    if assessment["decision"] != "DEFER_PROTECTED_ENDPOINT_ACTIVATION":
        raise ValueError("ACTIVATION")
    return dict(classification="PHASE_14_46B_AUTHENTICATION_PENDING", baseline=BASELINE,
                protected_files=len(inputs["hashes"]), historical_missing=73,
                policies=policy_checks(), implementation="NOT_AUTHORIZED",
                runtime="NOT_AUTHORIZED", live_orders="DISABLED")
