"""Fail-closed package resources, independently pinned and explicitly allowlisted."""

import csv
import io
import json
from importlib.resources import files
from importlib.resources.abc import Traversable
from pathlib import Path, PurePosixPath
from typing import Any

from strategy_engine.phase14.universe import normalize
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1428.contract import MANIFEST_HASH

PACKAGE = "strategy_engine.phase1428"
VERSION = "Phase1428Resources.v1"
ALLOWLIST = {"universe": "universe.csv", "configuration": "configuration.json"}


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def read(root: Traversable, name: str) -> bytes:
    path = PurePosixPath(name)
    require(
        bool(name) and not path.is_absolute() and ".." not in path.parts
        and "\\" not in name and ":" not in name and str(path) == name,
        "RESOURCE_PATH",
    )
    node = root
    for part in path.parts:
        node = node.joinpath(part)
        if isinstance(node, Path):
            require(not node.is_symlink(), "RESOURCE_SYMLINK")
    try:
        raw = node.read_bytes()
    except (FileNotFoundError, IsADirectoryError) as exc:
        raise ValueError("RESOURCE_MISSING:" + name) from exc
    require(len(raw) <= 4 * 1024 * 1024, "RESOURCE_BUDGET")
    return raw


def load(root: Traversable | None = None) -> dict[str, Any]:
    """An explicit alternate root is for offline tests; never fall back on failure."""
    resource_root = root if root is not None else files(PACKAGE).joinpath("data")
    if isinstance(resource_root, Path):
        require(not resource_root.is_symlink(), "RESOURCE_SYMLINK")
    require(
        {p.name for p in resource_root.iterdir()}
        == {"manifest.json", *ALLOWLIST.values()},
        "RESOURCE_INVENTORY",
    )
    raw = read(resource_root, "manifest.json")
    require(sha(raw) == MANIFEST_HASH, "RESOURCE_MANIFEST_HASH")
    manifest = json.loads(raw)
    require(manifest["version"] == VERSION, "RESOURCE_VERSION")
    require(set(manifest["resources"]) == set(ALLOWLIST), "RESOURCE_ALLOWLIST")
    content = {}
    for logical, name in ALLOWLIST.items():
        entry = manifest["resources"][logical]
        require(entry["path"] == name and entry["encoding"] == "utf-8", "RESOURCE_SCHEMA")
        content[logical] = read(resource_root, name)
        require(sha(content[logical]) == entry["sha256"], "RESOURCE_HASH:" + logical)
    reader = csv.DictReader(io.StringIO(content["universe"].decode("utf-8"), newline=""))
    require(reader.fieldnames == ["symbol", "exchange", "enabled"], "RESOURCE_COLUMNS")
    rows = list(reader)
    members, duplicates = normalize(rows)
    symbols = [row["symbol"] for row in members]
    require(len(rows) == len(symbols) == 1115 and not duplicates, "RESOURCE_SYMBOL_COUNT")
    require(sha(canonical(symbols)) == manifest["symbol_order_fingerprint"], "RESOURCE_ORDER")
    configuration = json.loads(content["configuration"])
    require(
        configuration["version"] == "Phase1428OfflineConfiguration.v1"
        and configuration["scope"] == "SYNTHETIC_OFFLINE"
        and configuration["operational"] == "NO_GO"
        and configuration["genuine_qualified"] == 0,
        "RESOURCE_CONFIGURATION",
    )
    package_root = files("strategy_engine")
    for name, expected in manifest["software_inventory"].items():
        require(sha(read(package_root, name)) == expected, "RESOURCE_SOFTWARE:" + name)
    require(
        sha(canonical(manifest["software_inventory"])) == manifest["software_fingerprint"],
        "RESOURCE_SOFTWARE_BINDING",
    )
    return dict(manifest=manifest, symbols=symbols, configuration=configuration)
