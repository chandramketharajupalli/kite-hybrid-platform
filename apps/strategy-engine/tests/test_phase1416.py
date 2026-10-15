"""Offline synthetic import, trust, restart and full-family regression tests."""

import json
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

import pytest
from test_phase14 import tariff
from test_phase1415 import END

from strategy_engine.backtest.dataset import NSE, Calendar, digest
from strategy_engine.phase14.engine import Settings
from strategy_engine.phase14.imports import FiveMinuteExport
from strategy_engine.phase1415.hardening import ROOT, frozen_policy
from strategy_engine.phase1415.research import evaluate, standard_window
from strategy_engine.phase1415.strategies import registry
from strategy_engine.phase1416.imports import (
    Imported,
    Input,
    Request,
    canonical,
    import_local,
    publish,
    read_json,
    sha,
)
from strategy_engine.phase1416.jobs import Selection, execute, plan, validate

NOW = datetime(2026, 10, 15, tzinfo=UTC)


def request(tmp_path: Path, raw: dict[str, Any] | None = None) -> Request:
    fixture = ROOT / "contracts/fixtures/v1/historical-five-minute.synthetic.json"
    value = json.loads(fixture.read_bytes()) if raw is None else raw
    path = tmp_path / "export.json"
    path.write_bytes(canonical(value))
    return Request(
        symbol="SBIN",
        instrument_id=value["instrument_id"],
        instrument_token="123",
        source="SYNTHETIC",
        source_timestamp=NOW,
        source_class="SYNTHETIC_FIXTURE",
        exports=(Input(path=str(path), sha256=sha(path.read_bytes())),),
        calendars=(value["calendar"],),
        start=value["from_inclusive"],
        end=value["to_exclusive"],
        master_hash="a" * 64,
        retention_until=NOW + timedelta(days=10),
    )


def test_import_valid_idempotent_and_timezone(tmp_path: Path) -> None:
    req = request(tmp_path)
    data = import_local(req, tmp_path / "store", now=NOW)
    assert data.status == "READY" and data.source_class == "SYNTHETIC_FIXTURE"
    assert data.dataset and data.dataset.series.bars[0].start.hour == 3
    assert data.dataset.series.quality() == ((), ())
    assert data == import_local(req, tmp_path / "store", now=NOW)
    assert len(list((tmp_path / "store").glob("*.json"))) == 1


@pytest.mark.parametrize(
    "field,value",
    [
        ("source_class", "UNVERIFIED_LOCAL_DATA"),
        ("source_class", "AUTHORIZED_LOCAL_EXPORT"),
    ],
)
def test_unverified_data_quarantined(tmp_path: Path, field: str, value: Any) -> None:
    req = request(tmp_path).model_copy(update={field: value})
    data = import_local(req, tmp_path / "store", now=NOW)
    assert data.status == "DATA_QUARANTINED" and data.dataset is None
    assert data.source_class == "UNVERIFIED_LOCAL_DATA"
    assert "SOURCE_PERMISSION_UNVERIFIED" in data.reasons
    assert "CALENDAR_INCOMPLETE" in data.reasons
    assert "CORPORATE_ACTION_UNCERTAINTY" in data.reasons
    assert "MAPPING_UNVERIFIED" in data.reasons


@pytest.mark.parametrize(
    "field,value",
    [
        ("exchange", "BSE"),
        ("interval", "MINUTE"),
        ("instrument_token", "456"),
        ("symbol", "INVALID"),
        ("from_inclusive", "invalid"),
    ],
)
def test_bad_export_rejected(tmp_path: Path, field: str, value: Any) -> None:
    raw = json.loads(
        (ROOT / "contracts/fixtures/v1/historical-five-minute.synthetic.json").read_bytes()
    )
    raw[field] = value
    with pytest.raises(ValueError):
        import_local(request(tmp_path, raw), tmp_path / "store", now=NOW)
    assert not (tmp_path / "store").exists()


@pytest.mark.parametrize("mutation", ["ohlcv", "duplicate", "missing", "timestamp", "hash"])
def test_bad_candles_rejected(tmp_path: Path, mutation: str) -> None:
    raw = json.loads(
        (ROOT / "contracts/fixtures/v1/historical-five-minute.synthetic.json").read_bytes()
    )
    if mutation == "ohlcv":
        raw["bars"][0]["high"] = "1"
    elif mutation == "duplicate":
        raw["bars"].append(raw["bars"][0].copy())
    elif mutation == "missing":
        raw["bars"] = []
    elif mutation == "timestamp":
        raw["bars"][0]["start"] = "2026-07-30T00:00:00Z"
    else:
        raw["content_fingerprint"] = "b" * 64
    with pytest.raises(ValueError):
        import_local(request(tmp_path, raw), tmp_path / "store", now=NOW)


def test_input_hash_retention_and_storage_guards(tmp_path: Path) -> None:
    req = request(tmp_path)
    with pytest.raises(ValueError, match="RETENTION_EXPIRED"):
        import_local(req, tmp_path, now=NOW + timedelta(days=11))
    with pytest.raises(ValueError, match="EXTERNAL"):
        import_local(req, ROOT / "research", now=NOW)
    Path(req.exports[0].path).write_text("{}")
    with pytest.raises(ValueError, match="FINGERPRINT"):
        import_local(req, tmp_path / "store", now=NOW)


def test_limits_encoding_duplicate_keys_and_interruption(tmp_path: Path) -> None:
    p = tmp_path / "input"
    p.write_bytes(b"0" * 100)
    with pytest.raises(ValueError, match="LIMIT"):
        read_json(p, 10)
    p.write_bytes(b"\xff")
    with pytest.raises(UnicodeError):
        read_json(p)
    p.write_text('{"a":1,"a":2}')
    with pytest.raises(ValueError, match="DUPLICATE_JSON_KEY"):
        read_json(p)
    target = tmp_path / "accepted.json"
    target.with_suffix(".json.pending").write_bytes(b"partial")
    with pytest.raises(FileExistsError):
        publish(target, b"complete")
    assert not target.exists()


@pytest.mark.parametrize("change", ["software", "schema", "policy", "duplicate", "dataset"])
def test_replay_binding_rejection(tmp_path: Path, change: str) -> None:
    job = plan(END, Settings(), tariff(), (), as_of=NOW)
    if change == "software":
        job = job.model_copy(update={"software_inventory": {}})
    elif change == "schema":
        job = job.model_copy(update={"schema_version": "old"})
    elif change == "policy":
        job = job.model_copy(
            update={"family": job.family.model_copy(update={"policy_hash": "a" * 64})}
        )
    else:
        ref = Selection(
            symbol="SBIN", months=6, record=Input(path=str(tmp_path / "missing"), sha256="a" * 64)
        )
        job = job.model_copy(update={"datasets": (ref, ref) if change == "duplicate" else (ref,)})
    with pytest.raises((ValueError, FileNotFoundError)):
        validate(job, NOW)


def test_full_unavailable_family_and_replay(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    import strategy_engine.phase1416.jobs as jobs

    job = plan(END, Settings(), tariff(), (), as_of=NOW)
    original = evaluate
    calls = 0

    def interrupt(*args: Any, **kwargs: Any) -> Any:
        nonlocal calls
        calls += 1
        if calls == 13:
            raise ValueError("INJECTED_INTERRUPTION")
        return original(*args, **kwargs)

    monkeypatch.setattr(jobs, "evaluate", interrupt)
    with pytest.raises(ValueError, match="INJECTED_INTERRUPTION"):
        execute(job, tmp_path / "run", now=NOW)
    assert len(list((tmp_path / "run/checkpoints").glob("*.json"))) == 1
    assert not (tmp_path / "run/run-summary.json").exists()
    assert list((tmp_path / "run").glob("failure-*.json"))
    monkeypatch.setattr(jobs, "evaluate", original)
    result = execute(job, tmp_path / "run", now=NOW)
    assert result["rows"] == 13380 and result["qualified"] == 0
    assert result["statuses"] == {"UNAVAILABLE": 13380}
    assert result["windows"] == {"6": 6690, "12": 6690}
    rows = json.loads((tmp_path / "run/stock-strategy-results.json").read_bytes())
    assert len({(r["symbol"], r["strategy"], r["months"]) for r in rows}) == 13380
    assert all(r["net"] is None and r["trade_count"] is None for r in rows)
    assert execute(job, tmp_path / "run", now=NOW) == result
    checkpoint = next((tmp_path / "run/checkpoints").glob("*.json"))
    value = json.loads(checkpoint.read_bytes())
    value["rows"].pop()
    value["sha256"] = sha(canonical(value["rows"]))
    checkpoint.write_bytes(canonical(value))
    with pytest.raises(ValueError, match="INCOMPLETE_PARTITION"):
        execute(job, tmp_path / "run", now=NOW)


def test_import_record_identity_rejected(tmp_path: Path) -> None:
    data = import_local(request(tmp_path), tmp_path / "store", now=NOW)
    path = tmp_path / "record.json"
    path.write_text(data.model_dump_json())
    selection = Selection(
        symbol="SBIN", months=6, record=Input(path=str(path), sha256=sha(path.read_bytes()))
    )
    job = plan(END, Settings(), tariff(), (selection,), as_of=NOW)
    with pytest.raises(ValueError, match="DATASET_IDENTITY"):
        validate(job, NOW)


def test_existing_export_contract_is_reused(tmp_path: Path) -> None:
    req = request(tmp_path)
    chunk = FiveMinuteExport.model_validate_json(Path(req.exports[0].path).read_bytes())
    data = import_local(req, tmp_path / "store", now=NOW)
    assert data.dataset and data.dataset.series.bars == chunk.bars
    assert data.dataset.dataset_sha256 == digest(data.dataset.series.model_dump_json())
    assert Imported.model_validate_json(data.model_dump_json()) == data


def calendar(days: list[dict[str, Any]], source: str = "Synthetic test calendar") -> Calendar:
    text = "test-v1\n" + source + "\n"
    for day in days:
        sessions = ", ".join(
            "Session[open=" + s["open"] + ", close=" + s["close"] + "]" for s in day["sessions"]
        )
        text += f"{day['date']}=Day[status={day['status']}, sessions=[{sessions}]]\n"
    return Calendar.model_validate(
        dict(version="test-v1", source=source, fingerprint=digest(text), days=days)
    )


@pytest.mark.parametrize("months", [6, 12])
def test_calendar_window_import_and_synthetic_separation(tmp_path: Path, months: int) -> None:
    assert months in (6, 12)
    window = standard_window(END, months)  # type: ignore[arg-type]
    req = request(tmp_path)
    raw = json.loads(Path(req.exports[0].path).read_bytes())
    raw["dataset_cutoff"] = NOW.isoformat().replace("+00:00", "Z")
    req = request(tmp_path, raw)
    days = []
    day = window.start.astimezone(NSE).date()
    while day < END.astimezone(NSE).date():
        days.append(dict(date=str(day), status="NON_TRADING_DAY", sessions=[]))
        day += timedelta(days=1)
    special = req.calendars[0].days[0].model_dump(mode="json")
    # Calendar times serialize with seconds, while the Java fingerprint uses HH:mm.
    special["sessions"] = [{"open": "09:15", "close": "09:20"}]
    for i, value in enumerate(days):
        if value["date"] == special["date"]:
            days[i] = special
    calendars = tuple(calendar(days[i : i + 32]) for i in range(0, len(days), 32))
    req = req.model_copy(update=dict(start=window.start, end=window.end, calendars=calendars))
    data = import_local(req, tmp_path / "store", now=NOW)
    assert data.dataset and data.dataset.series.quality() == ((), ())
    assert data.dataset.series.from_inclusive == window.start
    assert data.dataset.series.to_exclusive == END
    result = evaluate(
        "SBIN", registry()[0], window, data.dataset, Settings(), tariff(), frozen_policy()
    )
    assert result.data_status == "SYNTHETIC" and not result.qualified
    assert result.total and result.total.basic.trades == 0
    assert "SYNTHETIC_NOT_HISTORICAL_PERFORMANCE" in result.reasons


def test_valid_hash_missing_candles_and_holiday_quarantine(tmp_path: Path) -> None:
    req = request(tmp_path)
    raw = json.loads(Path(req.exports[0].path).read_bytes())
    raw["bars"] = []
    raw["content_fingerprint"] = digest("historical-bars-v1\n")
    req = request(tmp_path, raw)
    result = import_local(req, tmp_path / "store", now=NOW)
    assert result.status == "DATA_QUARANTINED"
    assert result.reasons == ("INCOMPLETE_MANDATORY_SESSIONS",)
    assert result.dataset is None


def test_missing_calendar_day_fails_closed(tmp_path: Path) -> None:
    req = request(tmp_path)
    req = req.model_copy(update={"start": req.start - timedelta(days=1)})
    with pytest.raises(ValueError):
        import_local(req, tmp_path / "store", now=NOW)


def test_permission_hash_is_not_optional(tmp_path: Path) -> None:
    req = request(tmp_path)
    p = tmp_path / "permission.json"
    p.write_text('{"reviewer":"test reviewer","purpose":"OFFLINE_RESEARCH"}')
    req = req.model_copy(
        update=dict(
            source_class="AUTHORIZED_LOCAL_EXPORT", permission=Input(path=str(p), sha256="b" * 64)
        )
    )
    data = import_local(req, tmp_path / "store", now=NOW)
    assert data.dataset is None and data.source_class == "UNVERIFIED_LOCAL_DATA"


def test_software_change_and_stale_lock_fail_closed(tmp_path: Path) -> None:
    job = plan(END, Settings(), tariff(), (), as_of=NOW)
    directory = tmp_path / "run"
    directory.mkdir()
    (directory / "run.lock").write_bytes(b"")
    with pytest.raises(FileExistsError):
        execute(job, directory, now=NOW)
    assert not (directory / "run-summary.json").exists()


def test_authorization_contract_with_mock_review_evidence(tmp_path: Path) -> None:
    # All bytes are test-generated. This tests acceptance mechanics, not real provenance.
    req = request(tmp_path)
    raw = json.loads(Path(req.exports[0].path).read_bytes())
    cal = calendar(
        [
            dict(
                date="2026-07-30",
                status="EXPECTED_SESSION",
                sessions=[dict(open="09:15", close="09:20")],
            )
        ],
        source="MOCK_EXCHANGE_REVIEW",
    )
    raw["calendar"] = cal.model_dump(mode="json")
    raw["calendar"]["days"][0]["sessions"] = [{"open": "09:15", "close": "09:20"}]
    raw["provenance"][0]["calendar_fingerprint"] = cal.fingerprint
    raw["provenance"][0]["source"] = "MOCK_LOCAL_EXPORT"
    req = request(tmp_path, raw).model_copy(
        update=dict(source="MOCK_LOCAL_EXPORT", source_class="AUTHORIZED_LOCAL_EXPORT")
    )
    scope = dict(
        symbol=req.symbol,
        instrument_id=req.instrument_id,
        instrument_token=req.instrument_token,
        start=req.start.isoformat(),
        end=req.end.isoformat(),
    )

    def ref(name: str, value: dict[str, Any]) -> Input:
        path = tmp_path / (name + ".json")
        path.write_bytes(canonical(dict(value, reviewer="MOCK_REVIEWER_TEST_ONLY")))
        return Input(path=str(path), sha256=sha(path.read_bytes()))

    req = req.model_copy(
        update=dict(
            permission=ref(
                "permission",
                dict(
                    **scope,
                    source=req.source,
                    purpose="OFFLINE_RESEARCH",
                    raw_hashes=[x.sha256 for x in req.exports],
                    retention_until=req.retention_until.isoformat(),
                ),
            ),
            calendar_evidence=ref(
                "calendar-review", dict(calendar_hashes=[cal.fingerprint], authoritative=True)
            ),
            corporate_action_evidence=ref(
                "actions",
                dict(**scope, reviewed=True, policy="PROVIDER_UNSPECIFIED_NO_LOCAL_ADJUSTMENT"),
            ),
            mapping_evidence=ref(
                "mapping",
                dict(
                    **scope,
                    master_hash=req.master_hash,
                    resolution="RESOLVED",
                    exchange="NSE",
                    candidates=1,
                ),
            ),
        )
    )
    data = import_local(req, tmp_path / "store", now=NOW)
    assert data.status == "READY" and data.source_class == "AUTHORIZED_LOCAL_EXPORT"
    assert data.dataset and not data.dataset.series.synthetic
    # Missing corporate-action review remains quarantined even with valid permission.
    rejected = import_local(
        req.model_copy(update={"corporate_action_evidence": None}), tmp_path / "store", now=NOW
    )
    assert rejected.status == "DATA_QUARANTINED" and rejected.dataset is None
    assert rejected.reasons == ("CORPORATE_ACTION_UNCERTAINTY",)


@pytest.mark.parametrize("kind", ["duplicate", "unordered"])
def test_valid_content_hash_does_not_mask_bad_order(tmp_path: Path, kind: str) -> None:
    req = request(tmp_path)
    raw = json.loads(Path(req.exports[0].path).read_bytes())
    raw["bars"].append(raw["bars"][0].copy())
    if kind == "unordered":
        raw["bars"][1]["start"] = "2026-07-30T03:40:00Z"
    text = "historical-bars-v1\n"
    for bar in raw["bars"]:
        text += (
            "|".join(
                [
                    raw["instrument_id"],
                    raw["interval"],
                    bar["start"],
                    bar["open"],
                    bar["high"],
                    bar["low"],
                    bar["close"],
                    str(bar["volume"]),
                    "ABSENT",
                ]
            )
            + "\n"
        )
    raw["content_fingerprint"] = digest(text)
    # The existing export hash is valid; Series ordering must still reject the input.
    FiveMinuteExport.model_validate(raw)
    with pytest.raises(ValueError):
        import_local(request(tmp_path, raw), tmp_path / "store", now=NOW)
