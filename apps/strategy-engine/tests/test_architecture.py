"""Guard dependency boundaries, not every source filename.

These tests are regression guards, not a security sandbox. Deployment credentials
and network policy must enforce separation when runtime transport is introduced.
"""
import ast
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ALLOWED_PROJECT_DEPENDENCIES = {"pydantic", "pydantic-settings"}
FORBIDDEN_IMPORTS = {"kiteconnect", "requests", "httpx", "urllib", "socket", "redis",
                     "psycopg", "psycopg2", "sqlalchemy", "subprocess", "ctypes"}


def test_strategy_process_has_no_execution_dependencies() -> None:
    project = tomllib.loads((ROOT / "pyproject.toml").read_text())
    names = {entry.split(">")[0].split("=")[0] for entry in project["project"]["dependencies"]}
    assert names == ALLOWED_PROJECT_DEPENDENCIES
    for source in (ROOT / "src").rglob("*.py"):
        for node in ast.walk(ast.parse(source.read_text())):
            if isinstance(node, ast.Import):
                imports = [alias.name for alias in node.names]
            elif isinstance(node, ast.ImportFrom):
                imports = [node.module or ""]
            else:
                continue
            assert not ({name.split(".")[0] for name in imports} & FORBIDDEN_IMPORTS), source


def test_strategy_public_ports_produce_signals_only() -> None:
    from typing import get_type_hints

    from strategy_engine.domain import SignalEvent
    from strategy_engine.strategies import Strategy

    assert get_type_hints(Strategy.evaluate)["return"] == tuple[SignalEvent, ...]
