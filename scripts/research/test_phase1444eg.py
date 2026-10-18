"""Pure synthetic policy tests; no native supervisor or process workload."""

import ast
import datetime as dt
import unittest
from pathlib import Path
from typing import Any

import verify_phase1444eg as v

NOW = dt.datetime(2026, 10, 18, tzinfo=dt.UTC)


def fixture() -> dict[str, Any]:
    return {
        "context": "TEST_ONLY",
        "authorized": True,
        "approval_fingerprint": "TEST_ONLY",
        "expected_approval_fingerprint": "TEST_ONLY",
        "path": v.IMAGE,
        "sha256": v.IMAGE_SHA,
        "signature": "TEST_ONLY_VERIFIED",
        "signature_evidence": "TEST_ONLY",
        "not_before": "2026-10-17T00:00:00+00:00",
        "expiry": "2026-10-19T00:00:00+00:00",
        "creation": 20,
        "observed_creation": 20,
        "pid": 2,
        "handle_pid": 2,
        "parent_pid": 1,
        "approved_root_pid": 1,
        "parent_creation": 10,
        "root_creation": 10,
        "parent_exit": 30,
        "owned_job": True,
        "job_member": True,
        "job_handle": 8,
        "expected_job_handle": 8,
        "image_error": None,
        "exited": False,
    }


class PolicyTests(unittest.TestCase):
    def reject(self, **changes: Any) -> None:
        e = fixture()
        e.update(changes)
        self.assertNotEqual(v.synthetic_policy(e, NOW), "TEST_ONLY_POLICY_ACCEPTED")

    def test_01_exact_identity_requires_authorization(self) -> None:
        self.assertEqual(v.synthetic_policy(fixture(), NOW), "TEST_ONLY_POLICY_ACCEPTED")
        self.reject(authorized=False)
        self.reject(context="REAL")

    def test_02_wrong_hash(self) -> None:
        self.reject(sha256="0" * 64)

    def test_03_wrong_path(self) -> None:
        self.reject(path="C:/Users/test/conhost.exe")

    def test_04_unsigned_or_untrusted(self) -> None:
        self.reject(signature="UNSIGNED")
        self.reject(signature="UNTRUSTED")

    def test_05_missing_signature(self) -> None:
        self.reject(signature_evidence=None)

    def test_06_missing_creation(self) -> None:
        self.reject(creation=None)

    def test_07_pid_reuse(self) -> None:
        self.reject(handle_pid=3)
        self.reject(observed_creation=21)

    def test_08_parent_reuse(self) -> None:
        self.reject(parent_creation=19)
        self.reject(parent_exit=19)

    def test_09_console_association_not_ownership(self) -> None:
        self.reject(console_association=True, owned_job=False)

    def test_10_job_mismatch(self) -> None:
        self.reject(job_member=False)
        self.reject(job_handle=9)

    def test_11_unrelated_console(self) -> None:
        self.reject(parent_pid=999)

    def test_12_missing_authorization(self) -> None:
        self.reject(authorized=None)

    def test_13_expired(self) -> None:
        self.reject(expiry="2026-10-18T00:00:00+00:00")

    def test_14_changed_approval(self) -> None:
        self.reject(approval_fingerprint="CHANGED")

    def test_15_unknown_image(self) -> None:
        self.assertEqual(v.classify_image(False, None, False), "PROCESS_IDENTITY_UNVERIFIED")
        self.reject(path=None)

    def test_16_access_denied(self) -> None:
        self.reject(image_error=5)

    def test_17_winerror31(self) -> None:
        self.assertEqual(v.classify_image(False, 31, True), "PROCESS_IMAGE_QUERY_FAILED")

    def test_18_winerror5(self) -> None:
        self.assertEqual(v.classify_image(False, 5, True), "PROCESS_ACCESS_DENIED")

    def test_19_exit(self) -> None:
        self.assertEqual(v.classify_image(True, 5, False), "PROCESS_EXITED")
        self.reject(exited=True)

    def source(self) -> str:
        return Path(v.__file__).read_text()

    def test_20_no_pid_termination(self) -> None:
        for text in ("os.kill(", "TerminateProcess", "TerminateJobObject", "taskkill"):
            self.assertNotIn(text, self.source())

    def test_21_no_credentials(self) -> None:
        self.assertNotIn("os.environ", self.source())
        self.assertNotIn(".env", self.source())

    def test_22_no_network_or_launch(self) -> None:
        imports = {
            n.names[0].name for n in ast.walk(ast.parse(self.source())) if isinstance(n, ast.Import)
        }
        self.assertTrue(imports.isdisjoint({"socket", "requests", "httpx", "subprocess", "ctypes"}))

    def test_23_no_database_execution(self) -> None:
        self.assertNotIn("sqlite3", self.source())
        self.assertNotIn("import replay", self.source())

    def test_24_no_series_v2(self) -> None:
        self.reject(context="SERIES_V2")

    def test_25_no_kite(self) -> None:
        self.assertNotIn("kiteconnect", self.source())


if __name__ == "__main__":
    unittest.main(verbosity=2)
