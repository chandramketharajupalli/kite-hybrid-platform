"""Offline assessment verifier. No application imports, process creation or authority issuance."""
import hashlib
import json
from pathlib import Path

BASELINE = "2acf229dfb1a53140b6dfcb33f83b992f2bce4df"


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def authorize_real(_evidence):
    return False


def fixture_rejection(evidence):
    """Synthetic reason classifier only; never a signature verifier or permission issuer."""
    rules = (
        ("baseline", BASELINE, "WRONG_BASELINE"),
        ("scope", "EXACT_EIGHT_JAVA_ADDITIONS", "WRONG_SCOPE"),
        ("identity", "TEST_ONLY", "MISSING_IDENTITY"),
        ("role", "TEST_ONLY_IMPLEMENTER", "WRONG_ROLE"),
        ("root", "TEST_ONLY", "UNKNOWN_TRUST_ROOT"),
        ("signature_valid", True, "INVALID_SIGNATURE"),
        ("payload_bound", True, "PAYLOAD_MISMATCH"),
        ("trusted_time", True, "TRUSTED_TIME_UNVERIFIED"),
        ("expired", False, "EXPIRED"),
        ("revoked", False, "REVOKED"),
        ("consumed", False, "REPLAYED"),
        ("checkpoint", True, "ROLLBACK_PROTECTION_UNVERIFIED"),
        ("synthetic", True, "SYNTHETIC_TO_REAL_PROMOTION"),
    )
    for key, expected, reason in rules:
        if type(evidence.get(key)) is not type(expected) or evidence.get(key) != expected:
            return reason
    return "SYNTHETIC_ONLY_NOT_AUTHORIZED"


def policy_checks():
    fixture = dict(baseline=BASELINE, scope="EXACT_EIGHT_JAVA_ADDITIONS",
                   identity="TEST_ONLY", role="TEST_ONLY_IMPLEMENTER", root="TEST_ONLY",
                   signature_valid=True, payload_bound=True, trusted_time=True,
                   expired=False, revoked=False, consumed=False, checkpoint=True, synthetic=True)
    cases = [("synthetic_complete", fixture, "SYNTHETIC_ONLY_NOT_AUTHORIZED")]
    mutations = (
        ("baseline", "wrong", "WRONG_BASELINE"), ("scope", "ALL", "WRONG_SCOPE"),
        ("identity", None, "MISSING_IDENTITY"), ("role", "UNKNOWN", "WRONG_ROLE"),
        ("root", None, "UNKNOWN_TRUST_ROOT"), ("signature_valid", False, "INVALID_SIGNATURE"),
        ("payload_bound", False, "PAYLOAD_MISMATCH"),
        ("trusted_time", False, "TRUSTED_TIME_UNVERIFIED"),
        ("expired", True, "EXPIRED"), ("revoked", True, "REVOKED"),
        ("consumed", True, "REPLAYED"),
        ("checkpoint", False, "ROLLBACK_PROTECTION_UNVERIFIED"),
        ("synthetic", False, "SYNTHETIC_TO_REAL_PROMOTION"),
        ("signature_valid", 1, "INVALID_SIGNATURE"),
    )
    cases.extend((key + "_" + str(value), dict(fixture, **{key: value}), reason)
                 for key, value, reason in mutations)
    receipts = []
    for name, value, expected in cases:
        actual = fixture_rejection(value)
        if actual != expected or authorize_real(value) is not False:
            raise ValueError("POLICY_FAILURE:" + name)
        receipts.append(dict(case=name, result="PASS", rejection=actual,
                             real_authorization=False))
    if authorize_real(None) is not False:
        raise ValueError("NULL_AUTHORIZATION")
    receipts.append(dict(case="null_real_evidence", result="PASS", real_authorization=False))
    return receipts


def verify(root, inputs):
    root = Path(root)
    for name, expected in inputs["hashes"].items():
        path = Path(name)
        if not path.is_absolute():
            path = root / path
        if sha(path.read_bytes()) != expected:
            raise ValueError("PRESERVATION:" + name)
    for name in inputs["historical_missing"]:
        if Path(name).exists():
            raise ValueError("HISTORICAL_RECEIPT_APPEARED")
    packet = json.loads((root / inputs["packet"]).read_bytes())
    if packet["git_sha"] != BASELINE or packet["authorization_status"] != "NOT_AUTHORIZED":
        raise ValueError("AUTHORIZATION_STATE")
    if any(value is not None for value in packet["authority"].values()):
        raise ValueError("UNSUPPORTED_AUTHORITY")
    if len(packet["java_inventory"]) != 8 or len(set(packet["java_inventory"])) != 8:
        raise ValueError("SCOPE")
    if any((root / name).exists() for name in packet["java_inventory"]):
        raise ValueError("IMPLEMENTATION_OCCURRED")
    if packet["runtime_authorization"] != "NOT_AUTHORIZED":
        raise ValueError("RUNTIME_AUTHORITY")
    return dict(classification="PHASE_14_46A_AUTHENTICATION_PENDING",
                git_sha=BASELINE, verified_files=len(inputs["hashes"]),
                historical_missing=len(inputs["historical_missing"]),
                policy_checks=policy_checks(), authorization="NOT_AUTHORIZED",
                runtime="NOT_AUTHORIZED", live_trading="DISABLED")
