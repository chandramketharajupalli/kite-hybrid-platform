"""Pure in-memory policy tests. No guests, original31, databases or containment probes."""

import unittest
from collections.abc import Callable
from typing import Any

from verify_phase1444eb import deny_real_execution, inspect_fixture


def fixture() -> dict[str, Any]:
    return {
        "source": "TEST_ONLY",
        "provider": "TEST_ONLY_PROVIDER",
        "approved_provider": True,
        "image": "TEST_ONLY_IMAGE",
        "expected_image": "TEST_ONLY_IMAGE",
        "image_provenance": True,
        "runtime": "TEST_ONLY_PYTHON",
        "expected_runtime": "TEST_ONLY_PYTHON",
        "closure_complete": True,
        "source_hash": "a" * 64,
        "expected_source_hash": "a" * 64,
        "assertions_hash": "b" * 64,
        "expected_assertions_hash": "b" * 64,
        "ids": ["TEST_ONLY::one", "TEST_ONLY::two"],
        "expected_ids": ["TEST_ONLY::one", "TEST_ONLY::two"],
        "guest_maps_host": False,
        "sealed_access": False,
        "repo_write": False,
        "credential_inheritance": False,
        "production_db": False,
        "network_egress": False,
        "dns_egress": False,
        "nat_fallback": False,
        "child_escape": False,
        "creation_time": 100,
        "expected_creation_time": 100,
        "owned_handle": True,
        "parent_lifetime_valid": True,
        "timeout_enforced": True,
        "guest_crashed": False,
        "generation": 5,
        "external_generation": 5,
        "export_approved": True,
        "owner_approval_fixture": True,
        "not_before": 10,
        "now": 20,
        "expiry": 30,
        "revoked": False,
        "action": "INSPECT_FIXTURE",
    }


CASES: list[tuple[str, dict[str, Any], str]] = [
    ("unknown_provider", {"provider": "UNKNOWN"}, "UNKNOWN_PROVIDER"),
    ("unapproved_provider", {"approved_provider": False}, "UNAPPROVED_PROVIDER"),
    ("wrong_image", {"image": "WRONG"}, "WRONG_IMAGE"),
    ("missing_provenance", {"image_provenance": False}, "MISSING_IMAGE_PROVENANCE"),
    ("wrong_runtime", {"runtime": "WRONG"}, "WRONG_RUNTIME"),
    ("incomplete_closure", {"closure_complete": False}, "INCOMPLETE_CLOSURE"),
    ("source_mismatch", {"source_hash": "c" * 64}, "SOURCE_MISMATCH"),
    ("assertion_change", {"assertions_hash": "c" * 64}, "ASSERTION_CHANGE"),
    ("missing_id", {"ids": ["TEST_ONLY::one"]}, "MISSING_ID"),
    ("duplicate_id", {"ids": ["TEST_ONLY::one", "TEST_ONLY::one"]}, "DUPLICATE_ID"),
    ("host_mapping", {"guest_maps_host": True}, "HOST_CUSTODY_MAPPING"),
    ("sealed_access", {"sealed_access": True}, "HOST_SEALED_ACCESS"),
    ("repository_write", {"repo_write": True}, "REPOSITORY_WRITE"),
    (
        "credential_inheritance",
        {"credential_inheritance": True},
        "CREDENTIAL_INHERITANCE",
    ),
    ("production_database", {"production_db": True}, "PRODUCTION_DATABASE"),
    ("network_egress", {"network_egress": True}, "NETWORK_EGRESS"),
    ("dns_egress", {"dns_egress": True}, "DNS_EGRESS"),
    ("nat_fallback", {"nat_fallback": True}, "NAT_FALLBACK"),
    ("child_escape", {"child_escape": True}, "CHILD_ESCAPE"),
    ("pid_reuse", {"creation_time": 99}, "PID_REUSE"),
    ("unrelated_process", {"owned_handle": False}, "UNRELATED_PROCESS"),
    ("timeout_failure", {"timeout_enforced": False}, "TIMEOUT_FAILURE"),
    ("guest_crash", {"guest_crashed": True}, "GUEST_CRASH"),
    ("snapshot_rollback", {"generation": 4}, "SNAPSHOT_ROLLBACK"),
    ("unauthorized_export", {"export_approved": False}, "UNAUTHORIZED_EXPORT"),
    ("missing_owner", {"owner_approval_fixture": False}, "MISSING_OWNER_APPROVAL"),
    ("expired_approval", {"now": 30}, "EXPIRED_APPROVAL"),
    ("revoked_approval", {"revoked": True}, "REVOKED_APPROVAL"),
    (
        "original31_execution",
        {"action": "RUN_ORIGINAL31"},
        "UNAUTHORIZED_ORIGINAL_TESTS",
    ),
    ("series_v2", {"action": "IMPLEMENT_SERIES_V2"}, "UNAUTHORIZED_SERIES_V2"),
]


class QualificationPolicyTests(unittest.TestCase):
    def test_positive_fixture_never_grants_real_authority(self) -> None:
        self.assertEqual(inspect_fixture(fixture()), "TEST_ONLY_POLICY_MATCH")
        with self.assertRaisesRegex(ValueError, "EXECUTION_NOT_AUTHORIZED"):
            deny_real_execution()

    def test_synthetic_cannot_promote(self) -> None:
        with self.assertRaisesRegex(ValueError, "SYNTHETIC_PROMOTION"):
            inspect_fixture(fixture() | {"source": "REAL"})


def rejection(
    change: dict[str, Any], error: str
) -> Callable[[QualificationPolicyTests], None]:
    def test(self: QualificationPolicyTests) -> None:
        with self.assertRaisesRegex(ValueError, error):
            inspect_fixture(fixture() | change)

    return test


for case_name, case_change, case_error in CASES:
    setattr(
        QualificationPolicyTests,
        "test_" + case_name,
        rejection(case_change, case_error),
    )


if __name__ == "__main__":
    unittest.main(verbosity=2)
