"""Offline dependency integrity and frozen full-family research identity."""

import ast
import hashlib
import json
from datetime import datetime
from functools import cached_property
from pathlib import Path

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import Frozen, Hash, digest
from strategy_engine.phase14.engine import Policy, Settings
from strategy_engine.phase14.universe import normalize, records
from strategy_engine.phase1415.research import ResearchWindow, Result, standard_window
from strategy_engine.phase1415.strategies import Definition, registry

ROOT = Path(__file__).resolve().parents[5]
SOURCE = ROOT / "apps/strategy-engine/src"
UNIVERSE = ROOT / "config/universes/phase14_unique_universe.csv"
SYMBOL_COUNT = 1115
STRATEGY_IDS = tuple(f"S{i:02d}" for i in range(1, 7))
MONTHS = (6, 12)
SCHEMA = "Phase1415Checkpoint.v2"
DEPENDENCIES = tuple(
    sorted(
        [
            "strategy_engine/__init__.py",
            "strategy_engine/backtest/__init__.py",
            "strategy_engine/backtest/costs.py",
            "strategy_engine/backtest/dataset.py",
            "strategy_engine/phase14/__init__.py",
            "strategy_engine/phase14/engine.py",
            "strategy_engine/phase14/imports.py",
            "strategy_engine/phase14/reporting.py",
            "strategy_engine/phase14/universe.py",
        ]
        + [
            f"strategy_engine/phase1415/{n}.py"
            for n in (
                "__init__",
                "execution",
                "jobs",
                "paper",
                "reports",
                "research",
                "strategies",
                "hardening",
            )
        ]
    )
)


def canonical(value: object) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False)


def family_size() -> int:
    return SYMBOL_COUNT * len(STRATEGY_IDS) * len(MONTHS)


class Dependency(Frozen):
    path: str
    sha256: Hash


def validate_dependencies(items: tuple[Dependency, ...]) -> str:
    if tuple(x.path for x in items) != DEPENDENCIES:
        raise ValueError("DEPENDENCY_INVENTORY_MISSING_DUPLICATE_OR_UNEXPECTED")
    return digest(canonical([x.model_dump() for x in items]))


def dependencies(source: Path = SOURCE) -> tuple[Dependency, ...]:
    """Read raw bytes only; verify the explicit transitive local import closure."""
    actual = tuple(
        sorted(
            p.relative_to(source).as_posix()
            for p in (source / "strategy_engine/phase1415").glob("*.py")
        )
    )
    expected = tuple(p for p in DEPENDENCIES if "/phase1415/" in p)
    if actual != expected:
        raise ValueError("UNEXPECTED_PACKAGE_DEPENDENCY_SET")
    result = []
    for name in DEPENDENCIES:
        path = source / name
        if not path.is_file() or path.is_symlink():
            raise ValueError("DEPENDENCY_UNAVAILABLE:" + name)
        raw = path.read_bytes()
        tree = ast.parse(raw)
        for node in ast.walk(tree):
            modules: list[str] = []
            if isinstance(node, ast.Import):
                modules = [x.name for x in node.names]
            elif isinstance(node, ast.ImportFrom):
                if node.level:
                    raise ValueError("UNVERIFIABLE_RELATIVE_DEPENDENCY")
                modules = [node.module or ""]
            for module in modules:
                if module == "strategy_engine" or module.startswith("strategy_engine."):
                    rel = module.replace(".", "/")
                    if rel + ".py" not in DEPENDENCIES and rel + "/__init__.py" not in DEPENDENCIES:
                        raise ValueError("UNREGISTERED_LOCAL_DEPENDENCY:" + module)
        result.append(Dependency(path=name, sha256=hashlib.sha256(raw).hexdigest()))
    items = tuple(result)
    validate_dependencies(items)
    return items


def frozen_policy(policy: Policy | None = None) -> Policy:
    expected = Policy(family_size=family_size())
    actual = policy or expected
    if actual != expected:
        raise ValueError("FROZEN_FULL_FAMILY_POLICY_REQUIRED")
    return actual


class FamilyBinding(Frozen):
    schema_version: str = SCHEMA
    universe_hash: Hash
    symbols: tuple[str, ...]
    registry_hash: Hash
    windows: tuple[ResearchWindow, ...]
    policy_hash: Hash
    software_hash: Hash
    dependency_inventory: tuple[Dependency, ...]
    settings_hash: Hash
    costs_hash: Hash
    hypotheses: int

    @cached_property
    def run_id(self) -> str:
        return digest(self.model_dump_json())


def family_binding(
    end: datetime, settings: Settings, costs: IntradayCostSchedule, policy: Policy | None = None
) -> FamilyBinding:
    members, duplicate = normalize(records(UNIVERSE))
    if duplicate or len(members) != SYMBOL_COUNT:
        raise ValueError("CANONICAL_UNIVERSE_REQUIRED")
    inventory = dependencies()
    return FamilyBinding(
        universe_hash=hashlib.sha256(UNIVERSE.read_bytes()).hexdigest(),
        symbols=tuple(m["symbol"] for m in members),
        registry_hash=digest(canonical([d.model_dump(mode="json") for d in registry()])),
        windows=(standard_window(end, 6), standard_window(end, 12)),
        policy_hash=digest(frozen_policy(policy).model_dump_json()),
        software_hash=validate_dependencies(inventory),
        dependency_inventory=inventory,
        settings_hash=digest(settings.model_dump_json()),
        costs_hash=digest(costs.model_dump_json()),
        hypotheses=family_size(),
    )


def verify_family(
    binding: FamilyBinding | None, settings: Settings, costs: IntradayCostSchedule, policy: Policy
) -> FamilyBinding:
    if binding is None:
        raise ValueError("FULL_FAMILY_BINDING_REQUIRED")
    if binding.schema_version != SCHEMA or len(binding.windows) != 2:
        raise ValueError("INCOMPATIBLE_CHECKPOINT_SCHEMA_OR_FAMILY")
    validate_dependencies(binding.dependency_inventory)
    expected = family_binding(binding.windows[0].end, settings, costs, policy)
    if binding != expected:
        raise ValueError("FULL_FAMILY_BINDING_CONFLICT")
    return binding


def verify_identity(binding: FamilyBinding) -> None:
    members, duplicate = normalize(records(UNIVERSE))
    expected_windows = (
        (standard_window(binding.windows[0].end, 6), standard_window(binding.windows[0].end, 12))
        if binding.windows
        else ()
    )
    if (
        binding.schema_version != SCHEMA
        or duplicate
        or len(members) != SYMBOL_COUNT
        or binding.symbols != tuple(m["symbol"] for m in members)
        or binding.universe_hash != hashlib.sha256(UNIVERSE.read_bytes()).hexdigest()
        or binding.hypotheses != family_size()
        or binding.windows != expected_windows
        or binding.policy_hash != digest(frozen_policy().model_dump_json())
        or binding.registry_hash
        != digest(canonical([d.model_dump(mode="json") for d in registry()]))
        or binding.software_hash != validate_dependencies(binding.dependency_inventory)
        or binding.dependency_inventory != dependencies()
    ):
        raise ValueError("FULL_FAMILY_BINDING_CONFLICT")


def partition_keys(
    symbols: tuple[str, ...],
    definitions: tuple[Definition, ...],
    windows: tuple[ResearchWindow, ...],
    binding: FamilyBinding,
) -> tuple[tuple[str, str, int], ...]:
    if not symbols or len(set(symbols)) != len(symbols) or not set(symbols) <= set(binding.symbols):
        raise ValueError("PARTITION_UNIVERSE_CONFLICT")
    full = {d.identifier: d for d in registry()}
    if not definitions or len({d.identifier for d in definitions}) != len(definitions):
        raise ValueError("PARTITION_REGISTRY_CONFLICT")
    if any(d != full.get(d.identifier) for d in definitions):
        raise ValueError("PARTITION_REGISTRY_CONFLICT")
    if not windows or len({w.months for w in windows}) != len(windows):
        raise ValueError("PARTITION_WINDOWS_CONFLICT")
    if any(w not in binding.windows for w in windows):
        raise ValueError("PARTITION_WINDOWS_CONFLICT")
    return tuple((s, d.identifier, w.months) for s in symbols for w in windows for d in definitions)


def bind_result(result: Result, binding: FamilyBinding) -> Result:
    if result.policy_hash != binding.policy_hash or result.window not in binding.windows:
        raise ValueError("RESULT_FAMILY_CONFLICT")
    reference = next((d for d in registry() if d.identifier == result.strategy_id), None)
    if (
        result.symbol not in binding.symbols
        or reference is None
        or result.strategy_hash != reference.fingerprint
        or result.strategy_version != reference.version
    ):
        raise ValueError("RESULT_FAMILY_CONFLICT")
    return result.model_copy(
        update={"family_run_id": binding.run_id, "hypothesis_family_size": binding.hypotheses}
    )
