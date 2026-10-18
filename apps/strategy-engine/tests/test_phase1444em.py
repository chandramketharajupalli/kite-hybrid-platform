"""Pure fixture checks invoked directly, without pytest or process creation."""

from strategy_engine.phase1444em.runner_policy import (
    BUDGET_FIELDS,
    FORBIDDEN_FLAGS,
    IDENTITY_FIELDS,
    REQUIRED_FLAGS,
    evaluate_fixture,
    real_execution_authorized,
)

CASES = (
    ("wrong_executable_hash", "executable_hash", "wrong"),
    ("wrong_command", "command", "wrong"),
    ("wrong_baseline", "baseline", "wrong"),
    ("missing_job", "job", None),
    ("missing_creation", "creation_time", None),
    ("pid_reuse", "creation_time", 99),
    ("parent_pid_reuse", "parent_creation_time", 99),
    ("unauthorized_child", "child_owned", False),
    ("missing_timeout", "wall_seconds", None),
    ("missing_cpu", "cpu_seconds", None),
    ("missing_memory", "memory_bytes", None),
    ("missing_output", "output_bytes", None),
    ("filesystem_write", "filesystem_write", True),
    ("sealed_access", "sealed_access", True),
    ("repository_write", "repository_write", True),
    ("credential_inheritance", "credential_inheritance", True),
    ("network_egress", "network_egress", True),
    ("dns_egress", "dns_egress", True),
    ("proxy_inheritance", "proxy_inheritance", True),
    ("missing_cleanup", "cleanup_authority", None),
    ("wrong_destination", "evidence_destination", "wrong"),
    ("expired", "expires", 10),
    ("revoked", "revoked", True),
    ("replayed", "replayed", True),
    ("missing_owner_authentication", "owner_authenticated", False),
    ("synthetic_to_real", "domain", "REAL"),
    ("unauthorized_pytest", "operation", "PYTEST"),
    ("unauthorized_supervisor", "operation", "LIVE_SUPERVISOR"),
    ("unauthorized_database", "operation", "DATABASE_TEST"),
    ("unauthorized_series_v2", "operation", "SERIES_V2"),
    ("no_real_kite", "operation", "KITE_REQUEST"),
)


def run_checks() -> list[dict[str, object]]:
    expected: dict[str, object] = {name: "fixture:" + name for name in IDENTITY_FIELDS}
    expected.update(pid=1, creation_time=2, parent_pid=3, parent_creation_time=4)
    observed = dict(expected)
    observed.update({name: True for name in REQUIRED_FLAGS})
    observed.update({name: False for name in FORBIDDEN_FLAGS})
    observed.update(domain="SYNTHETIC_ONLY", operation="PURE_POLICY_MODEL", not_before=1, expires=20)
    limits = {name: 100 for name in BUDGET_FIELDS}
    observed.update({name: 1 for name in BUDGET_FIELDS})
    assert not evaluate_fixture(observed, expected, limits, 10)
    assert real_execution_authorized() is False
    results: list[dict[str, object]] = [{"case": "positive_fixture_no_authority", "status": "PASS"}]
    for name, field, value in CASES:
        changed = dict(observed)
        changed[field] = value
        errors = evaluate_fixture(changed, expected, limits, 10)
        assert errors, name
        assert real_execution_authorized() is False, name
        results.append({"case": name, "status": "PASS", "rejections": list(errors)})
    assert len(results) == 32
    return results


def test_pure_policy_cases() -> None:
    run_checks()
