"""Observe importlib collection without changing test items or assertions."""

import json
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-K/generation-1")
FILES = (
    "apps/strategy-engine/tests/test_phase1444ej.py",
    "scripts/research/test_phase1444ej.py",
)


class Inventory:
    def __init__(self) -> None:
        self.deselected = 0

    def pytest_deselected(self, items: list[pytest.Item]) -> None:
        self.deselected += len(items)

    def pytest_collection_finish(self, session: pytest.Session) -> None:
        nodes = []
        for item in session.items:
            module = getattr(item, "module", None)
            nodes.append({
                "nodeid": item.nodeid,
                "canonical_nodeid": item.path.relative_to(ROOT).as_posix()
                + "::" + item.nodeid.split("::", 1)[1],
                "module_name": getattr(module, "__name__", None),
                "module_file": getattr(module, "__file__", None),
            })
        with (CUSTODY / "collected-inventory.json").open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(json.dumps({
                "nodes": nodes, "deselected": self.deselected,
                "collection_errors": session.testsfailed,
            }, sort_keys=True, indent=2) + "\n")


if __name__ == "__main__":
    raise SystemExit(pytest.main([
        "--collect-only", "-q", "--import-mode=importlib", "-p", "no:cacheprovider", *FILES,
    ], plugins=[Inventory()]))
