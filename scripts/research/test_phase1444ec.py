"""In-memory diagnostic and pending-packet rejection checks; no application imports."""

import copy
import unittest

from jsonschema import ValidationError
from verify_phase1444ec import DATA, classify_current, read, validate_packet


class DiagnosticChecks(unittest.TestCase):
    def test_pass_alone_does_not_prove_cache_cause(self) -> None:
        self.assertEqual(classify_current("", 0, ""), "MYPY_ROOT_CAUSE_UNVERIFIED")

    def test_trace_and_control_required(self) -> None:
        trace = "FileNotFoundError: [WinError 3] NUL\\3.12"
        self.assertEqual(classify_current(trace, 0, ""), "MYPY_ROOT_CAUSE_UNVERIFIED")
        self.assertEqual(classify_current(trace, 1, trace), "MYPY_ROOT_CAUSE_UNVERIFIED")
        self.assertEqual(classify_current(trace, 0, trace), "MYPY_TOOL_INTERNAL_DEFECT")

    def test_pending_packet(self) -> None:
        envelope = read(DATA / "environment-approval.json")
        validate_packet(envelope["packet"], envelope["schema"])

    def test_promotions_rejected(self) -> None:
        envelope = read(DATA / "environment-approval.json")
        for field, value in (
            ("status", "AUTHORIZED"),
            ("owner_identity", "TEST_ONLY"),
            ("expiry", "2099-01-01T00:00:00Z"),
            ("provider_identity", "TEST_ONLY_PROVIDER"),
            ("permitted_execution_operations", ["RUN_ORIGINAL31"]),
            ("baseline", "wrong"),
            ("unknown", "field"),
        ):
            with self.subTest(field=field):
                packet = copy.deepcopy(envelope["packet"])
                packet[field] = value
                with self.assertRaises((ValueError, ValidationError)):
                    validate_packet(packet, envelope["schema"])

    def test_missing_duplicate_inventory(self) -> None:
        envelope = read(DATA / "environment-approval.json")
        for ids in (envelope["packet"]["test_ids"][:-1], ["TEST_ONLY"] * 31):
            with self.subTest(ids=ids):
                packet = copy.deepcopy(envelope["packet"])
                packet["test_ids"] = ids
                with self.assertRaises(ValueError):
                    validate_packet(packet, envelope["schema"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
