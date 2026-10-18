"""Pure integration fixtures; imports no native supervisor and launches no workload."""

import datetime as dt
from dataclasses import replace

import pytest
from strategy_engine.phase1444ei import integration as i


def fixture() -> tuple[dict[str, object], dict[str, object], i.Context]:
    p: dict[str, object] = dict(i.BINDINGS)
    p.update(
        status="TEST_ONLY",
        command_identity="TEST_ONLY_COMMAND",
        envelope_fingerprint="TEST_ONLY_ENVELOPE",
        trust_root="TEST_ONLY",
        revocation_evidence="TEST_ONLY",
        replay_reference="TEST_ONLY_NONCE",
        approval_timestamp="2026-01-01T00:00:00+00:00",
        expiry="2026-01-08T00:00:00+00:00",
        owner="TEST_ONLY",
        authentication="TEST_ONLY",
        cleanup_authority="TEST_ONLY",
        limits=dict(i.LIMITS),
        test_ids=list(i.TEST_IDS),
    )
    o: dict[str, object] = {k: 0 for k in i.LIMITS}
    o.update(
        fixture="TEST_ONLY",
        pid=2,
        handle_pid=2,
        creation=20,
        handle_creation=20,
        parent_pid=1,
        parent_creation=10,
        image=i.IMAGE,
        image_sha256=i.IMAGE_SHA,
        job="TEST_ONLY_JOB",
        console="TEST_ONLY_CONSOLE",
        job_owned=True,
        job_member=True,
        assigned_before_resume=True,
        image_verified=True,
    )
    c = i.Context(
        dt.datetime(2026, 1, 2, tzinfo=dt.UTC),
        "TEST_ONLY_COMMAND",
        "TEST_ONLY_ENVELOPE",
        2,
        20,
        1,
        10,
        "TEST_ONLY_JOB",
        "TEST_ONLY_CONSOLE",
    )
    return p, o, c


CASES = [
    "valid",
    "baseline",
    "policy_sha256",
    "supervisor_sha256",
    "image_sha256",
    "image",
    "command_identity",
    "role",
    "console",
    "job_member",
    "creation",
    "pid",
    "parent_pid",
    "owner",
    "authentication",
    "expiry",
    "revocation_evidence",
    "replayed",
    "conflicting",
    "limits",
    "processes",
    "cpu_seconds",
    "memory_bytes",
    "output_bytes",
    "cleanup_authority",
    "test_ids",
    "promotion",
    "live",
    "database",
    "series",
    "kite",
]


@pytest.mark.parametrize("case", CASES)
def test_integration(case: str) -> None:
    p, o, c = fixture()
    if case in ("processes", "cpu_seconds", "memory_bytes", "output_bytes"):
        o[case] = i.LIMITS[case] + 1
    elif case in ("console", "job_member", "creation", "pid", "parent_pid"):
        o[case] = None
    elif case == "replayed":
        c = replace(c, consumed=frozenset({"TEST_ONLY_NONCE"}))
    elif case == "conflicting":
        c = replace(c, conflicts=frozenset({"TEST_ONLY_NONCE"}))
    elif case == "expiry":
        p[case] = "2026-01-01T01:00:00+00:00"
    elif case == "promotion":
        p["status"] = "AUTHORIZED"
    elif case in ("live", "database", "series", "kite"):
        p["operation"] = case
    elif case != "valid":
        p[case] = None
    p["fingerprint"] = i.fingerprint(p)
    result = i.evaluate_fixture(p, o, c)
    assert result.fixture_accepted is (case == "valid")
    assert result.execution_authorized is False
    assert i.real_authorization(p).execution_authorized is False


@pytest.mark.parametrize("key", list(i.LIMITS))
def test_budget_boundaries(key: str) -> None:
    p, o, c = fixture()
    o[key] = i.LIMITS[key]
    p["fingerprint"] = i.fingerprint(p)
    assert i.evaluate_fixture(p, o, c).fixture_accepted
    o[key] = True
    assert not i.evaluate_fixture(p, o, c).fixture_accepted


def test_strict_schema_and_observed_identity() -> None:
    p, o, c = fixture()
    p["unexpected"] = "TEST_ONLY"
    p["fingerprint"] = i.fingerprint(p)
    assert not i.evaluate_fixture(p, o, c).fixture_accepted
    del p["unexpected"]
    p["fingerprint"] = i.fingerprint(p)
    o["image_sha256"] = "WRONG"
    assert not i.evaluate_fixture(p, o, c).fixture_accepted
