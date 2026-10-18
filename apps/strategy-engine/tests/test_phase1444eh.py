"""Pure in-memory authorization fixtures; no native process qualification."""

import ast
import datetime as dt
from dataclasses import replace
from pathlib import Path

import pytest
from strategy_engine.phase1444eh import policy as p


def fixture() -> tuple[dict[str, object], dict[str, object], p.SyntheticContext]:
    envelope: dict[str, object] = {
        "contract": p.CONTRACT,
        "status": "TEST_ONLY",
        "git_baseline": p.BASELINE,
        "supervisor_fingerprint": p.SUPERVISOR,
        "executable_path": p.IMAGE,
        "executable_sha256": p.IMAGE_SHA,
        "catalog_signer": p.SIGNER,
        "catalog_evidence_reference": "TEST_ONLY_CATALOG",
        "catalog_evidence_sha256": "a" * 64,
        "required_role": "CONSOLE_HOST_FOR_SYNTHETIC_EXECUTION",
        "command_identity": "b" * 64,
        "creation_policy": "HANDLE_PID_CREATION_MATCH",
        "parent_policy": "ROOT_PID_CREATION_LIFETIME",
        "job_policy": "RETAINED_OWNED_JOB",
        "console_policy": "EXPLICIT_ASSOCIATION_REQUIRED",
        "owner_identity": "TEST_ONLY_OWNER",
        "authorization_identity": "TEST_ONLY_DECISION",
        "approval_evidence": "TEST_ONLY",
        "approval_timestamp": "2026-10-17T00:00:00+00:00",
        "expiry": "2026-10-19T00:00:00+00:00",
        "revocation_status": "TEST_ONLY_CURRENT",
        "replay_identifier": "TEST_ONLY_NONCE",
        "payload_fingerprint": None,
    }
    envelope["payload_fingerprint"] = p.fingerprint(envelope)
    observation: dict[str, object] = {
        "fixture": "TEST_ONLY",
        "catalog_verified": True,
        "revocation_checked_at": "2026-10-18T00:00:00+00:00",
        "pid": 20,
        "handle_pid": 20,
        "creation": 200,
        "handle_creation": 200,
        "parent_pid": 10,
        "parent_creation": 100,
        "parent_exit": 300,
        "console_association": "TEST_ONLY_CONSOLE",
        "job_identity": "TEST_ONLY_JOB",
        "job_member": True,
        "job_owned": True,
        "image_error": None,
        "image_verified": True,
        "exited": False,
        "executable_path": p.IMAGE,
        "executable_sha256": p.IMAGE_SHA,
    }
    context = p.SyntheticContext(
        dt.datetime(2026, 10, 18, tzinfo=dt.UTC),
        "b" * 64,
        "TEST_ONLY_CATALOG",
        "a" * 64,
        20,
        200,
        10,
        100,
        "TEST_ONLY_JOB",
        "TEST_ONLY_CONSOLE",
    )
    return envelope, observation, context


CASES = [
    "valid",
    "wrong-hash",
    "wrong-path",
    "wrong-catalog",
    "missing-catalog",
    "stale-revocation",
    "missing-creation",
    "wrong-parent-creation",
    "pid-reuse",
    "parent-pid-reuse",
    "missing-console",
    "job-mismatch",
    "unrelated-helper",
    "wrong-command",
    "wrong-baseline",
    "wrong-supervisor",
    "missing-authorization",
    "expired",
    "revoked",
    "replayed",
    "conflicting",
    "winerror5",
    "winerror31",
    "exited",
    "unknown-identity",
    "no-pid-termination",
    "no-credentials",
    "no-network",
    "no-database-tests",
    "no-series-v2",
    "no-kite",
]


@pytest.mark.parametrize("case", CASES)
def test_policy(case: str) -> None:
    envelope, observation, context = fixture()
    changes: dict[str, tuple[str, object]] = {
        "wrong-hash": ("executable_sha256", "0" * 64),
        "wrong-path": ("executable_path", "C:/fake/conhost.exe"),
        "wrong-catalog": ("catalog_signer", "UNKNOWN"),
        "missing-catalog": ("catalog_evidence_reference", None),
        "wrong-command": ("command_identity", "0" * 64),
        "wrong-baseline": ("git_baseline", "0" * 40),
        "wrong-supervisor": ("supervisor_fingerprint", "0" * 64),
        "missing-authorization": ("approval_evidence", None),
        "expired": ("expiry", "2026-10-18T00:00:00+00:00"),
        "revoked": ("revocation_status", "REVOKED"),
        "no-series-v2": ("required_role", "SERIES_V2"),
    }
    observed_changes: dict[str, tuple[str, object]] = {
        "stale-revocation": ("revocation_checked_at", "2026-10-17T00:00:00+00:00"),
        "missing-creation": ("creation", None),
        "wrong-parent-creation": ("parent_creation", 99),
        "pid-reuse": ("handle_creation", 201),
        "parent-pid-reuse": ("parent_pid", 11),
        "missing-console": ("console_association", None),
        "job-mismatch": ("job_identity", "OTHER"),
        "unrelated-helper": ("job_owned", False),
        "winerror5": ("image_error", 5),
        "winerror31": ("image_error", 31),
        "exited": ("exited", True),
        "unknown-identity": ("image_verified", False),
    }
    if case in changes:
        key, value = changes[case]
        envelope[key] = value
        envelope["payload_fingerprint"] = p.fingerprint(envelope)
    if case in observed_changes:
        key, value = observed_changes[case]
        observation[key] = value
    if case == "replayed":
        context = replace(context, consumed_nonces=frozenset({"TEST_ONLY_NONCE"}))
    if case == "conflicting":
        context = replace(context, conflicting_decisions=frozenset({"TEST_ONLY_DECISION"}))
    static_cases = {
        "no-pid-termination",
        "no-credentials",
        "no-network",
        "no-database-tests",
        "no-kite",
    }
    if case in static_cases:
        source = Path(p.__file__).read_text()
        forbidden = {
            "no-pid-termination": ["os.kill", "TerminateProcess", "taskkill"],
            "no-credentials": ["os.environ", "open("],
            "no-network": ["socket", "requests", "httpx"],
            "no-database-tests": ["sqlite3", "phase1444c"],
            "no-kite": ["kiteconnect"],
        }
        assert not any(token in source for token in forbidden[case])
        modules = {
            node.names[0].name
            for node in ast.walk(ast.parse(source))
            if isinstance(node, ast.Import)
        }
        assert modules.isdisjoint({"ctypes", "subprocess", "os", "socket"})
    result = p.evaluate_synthetic(envelope, observation, context)
    assert result.test_accepted == (case == "valid" or case in static_cases), result
    assert result.execution_authorized is False
    assert p.evaluate_authorization(envelope).execution_authorized is False
    assert not p.evaluate_authorization(envelope).test_accepted
    if case in ("winerror5", "winerror31", "exited", "unknown-identity"):
        assert (
            result.reason
            == {
                "winerror5": "PROCESS_ACCESS_DENIED",
                "winerror31": "PROCESS_IMAGE_QUERY_FAILED",
                "exited": "PROCESS_EXITED",
                "unknown-identity": "PROCESS_IDENTITY_UNVERIFIED",
            }[case]
        )


def test_schema_fingerprint_and_real_promotion_rejected() -> None:
    envelope, observation, context = fixture()
    envelope["unexpected"] = "TEST_ONLY"
    assert p.evaluate_synthetic(envelope, observation, context).reason == "SCHEMA"
    envelope.pop("unexpected")
    envelope["executable_sha256"] = "0" * 64
    assert p.evaluate_synthetic(envelope, observation, context).reason == "PAYLOAD_FINGERPRINT"
    envelope, observation, context = fixture()
    envelope["status"] = "NOT_AUTHORIZED"
    envelope["payload_fingerprint"] = p.fingerprint(envelope)
    assert not p.evaluate_synthetic(envelope, observation, context).test_accepted
    assert p.evaluate_authorization(envelope).reason == "OWNER_AUTHENTICATION_NOT_ESTABLISHED"


def test_observed_bytes_and_time_boundaries() -> None:
    envelope, observation, context = fixture()
    observation["executable_sha256"] = "0" * 64
    assert not p.evaluate_synthetic(envelope, observation, context).test_accepted
    envelope, observation, context = fixture()
    assert not p.evaluate_synthetic(
        envelope, observation, replace(context, now=dt.datetime(2026, 10, 18))
    ).test_accepted
    assert not p.evaluate_synthetic(
        envelope,
        observation,
        replace(context, consumed_decisions=frozenset({"TEST_ONLY_DECISION"})),
    ).test_accepted
