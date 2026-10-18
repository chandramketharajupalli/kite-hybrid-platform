"""Synthetic rejection specifications; no guest or production execution."""

import unittest
from typing import Any

from verify_phase1444ed import REJECTIONS, inspect_synthetic


class PolicyTests(unittest.TestCase):
    def test_positive_fixture_has_no_execution_authority(self) -> None:
        scenario: dict[str, Any] = dict.fromkeys(REJECTIONS, False)
        scenario["source"] = "TEST_ONLY"
        self.assertEqual(inspect_synthetic(scenario), "TEST_ONLY_NO_EXECUTION_AUTHORITY")

    def test_real_promotion_rejected(self) -> None:
        scenario: dict[str, Any] = dict.fromkeys(REJECTIONS, False)
        scenario["source"] = "REAL"
        with self.assertRaisesRegex(ValueError, "SYNTHETIC_PROMOTION"):
            inspect_synthetic(scenario)


def rejection_case(key: str) -> Any:
    def test(self: PolicyTests) -> None:
        scenario: dict[str, Any] = dict.fromkeys(REJECTIONS, False)
        scenario["source"] = "TEST_ONLY"
        scenario[key] = True
        with self.assertRaisesRegex(ValueError, key):
            inspect_synthetic(scenario)

    return test


for rejection in REJECTIONS:
    setattr(PolicyTests, "test_" + rejection, rejection_case(rejection))


if __name__ == "__main__":
    unittest.main(verbosity=2)
