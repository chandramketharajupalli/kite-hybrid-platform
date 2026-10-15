"""Fresh reconstruction acceptance tests, distinct from recovered historical tests."""

import json
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest
from test_phase14 import tariff
from test_phase1415 import END, run

from strategy_engine.backtest.dataset import NSE
from strategy_engine.phase14.engine import Settings
from strategy_engine.phase1415.research import ResearchWindow, standard_window
from strategy_engine.phase1415.strategies import registry


@pytest.mark.parametrize("field", ["software_hash", "strategy_version"])
def test_checkpoint_version_binding_rejects_replay(tmp_path: Path, field: str) -> None:
    members = [dict(symbol="M&M", exchange="NSE", enabled="TRUE")]

    def collect() -> int:
        return len(
            list(
                run(
                    members,
                    registry(),
                    (standard_window(END, 6),),
                    lambda symbol, months: None,
                    Settings(),
                    tariff(),
                    tmp_path,
                )
            )
        )

    assert collect() == 6
    path = tmp_path / "configuration.json"
    config = json.loads(path.read_bytes())
    if field == "software_hash":
        config[field] = "0" * 64
    else:
        config["definitions"][0]["version"] = "prior-version"
    path.write_text(json.dumps(config, sort_keys=True, separators=(",", ":")), encoding="utf-8")
    with pytest.raises(ValueError, match="CHECKPOINT_CONFIGURATION_CONFLICT"):
        collect()


@pytest.mark.parametrize("months", [6, 12])
def test_direct_window_and_all_splits_enforce_ist_midnight(months: int) -> None:
    window = standard_window(END, months)  # type: ignore[arg-type]
    for boundary in (
        window.start,
        window.end,
        window.validation_start,
        window.holdout_start,
        *window.folds,
    ):
        assert boundary.astimezone(NSE).time().isoformat() == "00:00:00"
    values = window.model_dump()
    values["validation_start"] += timedelta(minutes=5)
    with pytest.raises(ValueError, match="IST_MIDNIGHT_BOUNDARY_REQUIRED"):
        ResearchWindow.model_validate(values)
    with pytest.raises(ValueError, match="IST_MIDNIGHT_END_REQUIRED"):
        standard_window(datetime(2026, 10, 1, tzinfo=UTC), months)  # type: ignore[arg-type]
