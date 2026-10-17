"""Standalone resources and provenance fail closed without source-tree fallback."""

import json
import shutil
from importlib.resources import files
from pathlib import Path
from typing import Any

import pytest

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1428.provenance import classify
from strategy_engine.phase1428.resources import PACKAGE, load, read
from strategy_engine.phase1428.runtime import assess


def test_canonical_resources_and_order() -> None:
    result = load()
    assert len(result["symbols"]) == len(set(result["symbols"])) == 1115
    assert files(PACKAGE).joinpath("data/universe.csv").read_bytes() == (
        ROOT / "config/universes/phase14_unique_universe.csv"
    ).read_bytes()
    assert result["manifest"]["source_rows"] == 1140
    assert result["manifest"]["duplicates_removed"] == 25


def test_deterministic_synthetic_runtime() -> None:
    result = assess()
    assert result == assess()
    assert result["family_size"] == 13380 and result["strategies"] == 6
    assert result["genuine_qualified"] == 0 and result["real_kite_requests"] == 0
    assert result["operational"] == "NO_GO"


@pytest.mark.parametrize("fault", [
    "missing_manifest", "missing_universe", "universe_bytes", "symbol_count",
    "reorder", "duplicate", "configuration", "unknown_version", "fingerprint",
    "unexpected_path", "traversal", "unexpected_file", "corrupt_json",
])
def test_tampering(tmp_path: Path, fault: str) -> None:
    target = tmp_path / "resources"
    shutil.copytree(Path(str(files(PACKAGE).joinpath("data"))), target)
    manifest = target / "manifest.json"
    universe = target / "universe.csv"
    if fault.startswith("missing_"):
        (manifest if fault == "missing_manifest" else universe).unlink()
    elif fault in {"universe_bytes", "symbol_count", "reorder", "duplicate"}:
        rows = universe.read_bytes().splitlines(keepends=True)
        if fault == "reorder":
            rows[1], rows[2] = rows[2], rows[1]
        elif fault == "duplicate":
            rows[2] = rows[1]
        elif fault == "symbol_count":
            rows.pop()
        else:
            rows[1] = rows[1].replace(b",", b"X,", 1)
        universe.write_bytes(b"".join(rows))
    elif fault == "configuration":
        (target / "configuration.json").write_bytes(b"{}\n")
    elif fault == "unexpected_file":
        (target / "extra.json").write_bytes(b"{}\n")
    elif fault == "corrupt_json":
        manifest.write_bytes(b"{\n")
    else:
        body = json.loads(manifest.read_bytes())
        if fault == "unknown_version":
            body["version"] = "unknown"
        elif fault == "fingerprint":
            body["software_fingerprint"] = "0" * 64
        else:
            body["resources"]["universe"]["path"] = (
                "../universe.csv" if fault == "traversal" else "other.csv"
            )
        manifest.write_bytes(canonical(body) + b"\n")
    with pytest.raises(ValueError, match="RESOURCE"):
        load(target)


@pytest.mark.parametrize("name", ["../x", "/absolute", "C:/x", "a\\b", "a/../b"])
def test_path_rejection(tmp_path: Path, name: str) -> None:
    with pytest.raises(ValueError, match="RESOURCE_PATH"):
        read(tmp_path, name)


def test_no_cwd_fallback(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.chdir(tmp_path)
    (tmp_path / "universe.csv").write_bytes(b"malicious\n")
    assert len(load()["symbols"]) == 1115
    empty = tmp_path / "missing"
    empty.mkdir()
    with pytest.raises(ValueError, match="RESOURCE_INVENTORY"):
        load(empty)


@pytest.mark.parametrize("mode", ["archive", "record", "lock", "unverified", "wrong_hash"])
def test_provenance_classification(tmp_path: Path, mode: str) -> None:
    record: dict[str, Any] = dict(
        name="synthetic", version="1", status="VERIFIED_INSTALLED_RECORD",
        files_fingerprint="a" * 64,
    )
    archive = tmp_path / "synthetic.whl"
    archive.write_bytes(b"synthetic archive counterexample")
    hashes = ["sha256:" + sha(archive.read_bytes())]
    archives = (archive,) if mode in {"archive", "wrong_hash"} else ()
    version = "1"
    if mode == "lock":
        record["status"] = "UNVERIFIED"
    if mode == "unverified":
        version = "2"
    if mode == "wrong_hash":
        hashes = ["sha256:" + "0" * 64]
    result = classify(record, version, hashes, archives)
    expected = {
        "archive": "ARCHIVE_HASH_VERIFIED", "record": "INSTALLED_RECORD_VERIFIED",
        "lock": "LOCK_VERSION_ONLY", "unverified": "UNVERIFIED",
        "wrong_hash": "INSTALLED_RECORD_VERIFIED",
    }
    assert result["classification"] == expected[mode]
    assert bool(result["archive_matches"]) == (mode == "archive")
