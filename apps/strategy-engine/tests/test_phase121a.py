"""Offline historical reference generation, not market-data or strategy tests."""
import hashlib
import importlib.util
import json
import shutil
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "phase121a", ROOT / "scripts/research/run_phase121a.py")
assert SPEC is not None and SPEC.loader is not None
RUNNER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUNNER)


def test_new_generation_replays_offline_and_preserves_action_block(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    import socket

    def deny_network(*args: object, **kwargs: object) -> None:
        raise AssertionError("OFFLINE_REPLAY_ATTEMPTED_NETWORK")

    monkeypatch.setattr(socket, "socket", deny_network)
    first = RUNNER.replay(ROOT)
    assert first == RUNNER.replay(ROOT)
    members = first["instrument-certifications.json"]["certification"]["members"]
    assert [m["identity"]["symbol"] for m in members] == [
        "HDFCBANK", "ICICIBANK", "LT", "RELIANCE", "SBIN"]
    for member in members:
        assert member["security_identity"] == "CERTIFIED"
        assert member["symbol_continuity"] == "CERTIFIED"
        assert member["series_continuity"] == "CERTIFIED"
        assert member["classification"] == "CORPORATE_ACTION_UNRESOLVED"
    assert first["acquisition-gate.json"]["acquisition_allowed"] is False
    assert first["acquisition-gate.json"]["strategy_evaluation_allowed"] is False
    for name, value in first.items():
        assert value == json.loads((ROOT / RUNNER.GENERATION / name).read_text())


@pytest.mark.parametrize("name", ["security-reference.csv", "evidence-manifest.json",
                                 "source-inventory.json"])
def test_reference_generation_rejects_changed_frozen_inputs(tmp_path: Path, name: str) -> None:
    destination = tmp_path / RUNNER.GENERATION
    shutil.copytree(ROOT / RUNNER.GENERATION, destination)
    with (destination / name).open("a", encoding="utf-8") as stream:
        stream.write(" ")
    with pytest.raises(ValueError, match="FROZEN_INPUT_CHANGED"):
        RUNNER.replay(tmp_path)


def test_even_rehashed_reference_with_missing_session_is_rejected(tmp_path: Path) -> None:
    destination = tmp_path / RUNNER.GENERATION
    shutil.copytree(ROOT / RUNNER.GENERATION, destination)
    for phase in ("phase-12.0", "phase-12.1"):
        shutil.copytree(ROOT / "research" / phase, tmp_path / "research" / phase)
    reference = destination / "security-reference.csv"
    lines = reference.read_text().splitlines(keepends=True)
    # Remove one frozen session's HDFCBANK EQ observation; endpoint-only evidence must fail.
    lines = [line for line in lines if not line.startswith("2026-02-03,1333,HDFCBANK,EQ,")]
    reference.write_text("".join(lines), encoding="utf-8")
    freeze_file = destination / "evidence-freeze.json"
    freeze = json.loads(freeze_file.read_text())
    freeze["input_sha256"]["security-reference.csv"] = hashlib.sha256(
        reference.read_bytes()).hexdigest()
    freeze_file.write_text(json.dumps(freeze), encoding="utf-8")
    with pytest.raises(ValueError, match="INSTRUMENT_DATE_COVERAGE_MISMATCH"):
        RUNNER.replay(tmp_path)
