"""Synthetic contracts; no genuine tokens, exchange calendar or market observations."""

import ast
from datetime import UTC, datetime, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any

import pytest
from test_phase14 import tariff

from strategy_engine.backtest.dataset import NSE
from strategy_engine.phase14.engine import FIVE, Candle, Series, Session
from strategy_engine.phase1415.execution import execution_fingerprint, liquidity_bar
from strategy_engine.phase1415.research import ResearchWindow, standard_window
from strategy_engine.phase1419.scheduler import Calendar
from strategy_engine.phase1428.resources import load
from strategy_engine.phase1439 import pipeline as p

NOW = datetime(2026, 10, 1, tzinfo=NSE).astimezone(UTC)
POLICY = p.sha(b"SYNTHETIC_MEMORY_ONLY_EXPLICIT_EXPIRY")


def fixture() -> tuple[p.Master, p.CandleBatch, ResearchWindow, p.Lifecycle]:
    window = standard_window(NOW, 6)
    records = tuple(p.Instrument(symbol=s, token=str(i + 1))
                    for i, s in enumerate(load()["symbols"]))
    mapping = p.Master(batch_id="synthetic-master-1", observed_at=window.start,
                       expires_at=NOW + timedelta(days=1),
                       source_digest=p.sha(p.canonical(
                           [r.model_dump(mode="json") for r in records])), records=records)
    at = window.holdout_start + timedelta(hours=9, minutes=15)
    bars = tuple(Candle(start=at + i * FIVE, open=Decimal(100 + i % 4),
                        high=Decimal(105 + i % 4), low=Decimal(95 + i % 4),
                        close=Decimal(101 + i % 4), volume=10000, open_interest=None)
                 for i in range(40))
    holidays = tuple((window.start + timedelta(days=i)).astimezone(NSE).date()
                     for i in range((window.end - window.start).days)
                     if (window.start + timedelta(days=i)).astimezone(NSE).date()
                     != at.astimezone(NSE).date())
    calendar = Calendar(version="SYNTHETIC_SPARSE_NOT_NSE_HISTORY",
                        source_hash=p.sha(b"SYNTHETIC_CALENDAR"),
                        sessions=(Session(start=at, end=bars[-1].end),), holidays=holidays)
    source = p.sha(p.canonical([b.model_dump(mode="json") for b in bars]))
    series = Series(symbol=mapping.records[0].symbol, instrument_token="1", source="SYNTHETIC",
                    synthetic=True, source_hash=source, master_hash=p.fingerprint(mapping),
                    calendar_hash=calendar.fingerprint, as_of=NOW, from_inclusive=window.start,
                    to_exclusive=window.end, corporate_actions_reviewed=True,
                    sessions=calendar.sessions, non_trading_days=holidays, bars=bars)
    batch = p.CandleBatch(batch_id="synthetic-candles-1", source_batch_id=mapping.batch_id,
                          mapping_generation=p.fingerprint(mapping), source_digest=source,
                          observed_at=NOW, series=series, calendar=calendar,
                          calendar_verified=True, corporate_actions_verified=True,
                          listing_suspension_verified=True)
    life = p.Lifecycle(identity="synthetic-session-1", starts_at=window.start,
                       expires_at=NOW + timedelta(hours=1), retention_policy=POLICY)
    return mapping, batch, window, life


def bind(batch: p.CandleBatch, series: Series) -> p.CandleBatch:
    source = p.sha(p.canonical([b.model_dump(mode="json") for b in series.bars]))
    return batch.model_copy(update={"series": series.model_copy(update={"source_hash": source}),
                                    "source_digest": source})


def master_check(mapping: p.Master, **kwargs: Any) -> p.Master:
    mapping = mapping.model_copy(update={"source_digest": p.sha(p.canonical(
        [r.model_dump(mode="json") for r in mapping.records]))})
    raw = p.canonical(mapping.model_dump(mode="json"))
    return p.master(raw, p.sha(raw), NOW, **kwargs)


@pytest.mark.parametrize("operation", ["INSTRUMENT_MASTER", "HISTORICAL_CANDLES", "ORDERS"])
def test_no_acquisition(operation: str, monkeypatch: pytest.MonkeyPatch) -> None:
    def forbidden(*args: Any, **kwargs: Any) -> None:
        pytest.fail("unexpected credential/filesystem/network access")
    import socket
    monkeypatch.setattr(socket, "socket", forbidden)
    monkeypatch.setattr(Path, "read_bytes", forbidden)
    monkeypatch.setattr(Path, "write_bytes", forbidden)
    monkeypatch.setenv("ENABLE_LIVE_TRADING", "true")
    with pytest.raises(ValueError, match="ACQUISITION_NOT_AUTHORIZED"):
        p.Boundary().acquire(operation)


def test_boundary_cannot_authorize() -> None:
    with pytest.raises(ValueError):
        p.Boundary.model_validate({"instrument_master": "APPROVED"})


def test_full_canonical_mapping() -> None:
    mapping, _, _, _ = fixture()
    assert len(master_check(mapping).records) == 1115
    assert tuple(r.symbol for r in mapping.records) == tuple(load()["symbols"])


@pytest.mark.parametrize("case", ["duplicate", "missing", "unexpected", "reuse", "order",
                                  "token", "exchange", "stale", "future", "genuine"])
def test_bad_master(case: str) -> None:
    mapping, _, _, _ = fixture()
    data = mapping.model_dump(mode="json")
    rows = data["records"]
    if case == "duplicate":
        rows[1]["symbol"] = rows[0]["symbol"]
    elif case == "missing":
        rows.pop()
    elif case == "unexpected":
        rows[0]["symbol"] = "SYNTHETIC_UNEXPECTED"
    elif case == "reuse":
        rows[1]["token"] = rows[0]["token"]
    elif case == "order":
        rows.reverse()
    elif case == "token":
        rows[0]["token"] = "0"
    elif case == "exchange":
        rows[0]["exchange"] = "BSE"
    elif case == "stale":
        data["expires_at"] = NOW.isoformat()
    elif case == "future":
        data["observed_at"] = (NOW + timedelta(seconds=1)).isoformat()
    else:
        data["source_type"] = "GENUINE"
    raw = p.canonical(data)
    with pytest.raises(ValueError):
        p.master(raw, p.sha(raw), NOW)


def test_source_digest_and_duplicate_json() -> None:
    with pytest.raises(ValueError, match="SOURCE_UNAVAILABLE"):
        p.master(b"{}", "0" * 64, NOW)
    raw = b'{"batch_id":"a","batch_id":"b"}'
    with pytest.raises(ValueError, match="DUPLICATE_JSON_KEY"):
        p.master(raw, p.sha(raw), NOW)
    mapping, _, _, _ = fixture()
    raw = p.canonical(mapping.model_dump(mode="json") | {"source_digest": "0" * 64})
    with pytest.raises(ValueError, match="SOURCE_UNAVAILABLE"):
        p.master(raw, p.sha(raw), NOW)


def test_token_generation_change_and_reuse() -> None:
    mapping, _, _, _ = fixture()
    records = list(mapping.records)
    records[0] = records[0].model_copy(update={"token": "9000"})
    newer = mapping.model_copy(update={"records": tuple(records), "batch_id": "generation-2",
                                      "observed_at": mapping.observed_at + timedelta(days=1)})
    assert p.fingerprint(master_check(newer, previous=mapping)) != p.fingerprint(mapping)
    records[1] = records[1].model_copy(update={"token": "1"})
    with pytest.raises(ValueError, match="TOKEN_REUSE_CONFLICT"):
        master_check(newer.model_copy(update={"records": tuple(records)}), previous=mapping)


@pytest.mark.parametrize("field,value", [("mapping_generation", "0" * 64),
                                         ("source_digest", "0" * 64),
                                         ("source_batch_id", "other")])
def test_batch_bindings(field: str, value: str) -> None:
    mapping, batch, _, _ = fixture()
    with pytest.raises(ValueError):
        p.candles(batch.model_copy(update={field: value}), mapping, NOW)


@pytest.mark.parametrize("case", ["duplicate", "missing", "ohlc", "negative", "nonfinite",
                                  "outside", "future", "token", "interval", "calendar"])
def test_bad_candles(case: str) -> None:
    mapping, batch, _, _ = fixture()
    series = batch.series
    bars = list(series.bars)
    updates: dict[str, Any] = {}
    if case == "duplicate":
        bars[1] = bars[0]
    elif case == "missing":
        bars.pop(3)
    elif case == "ohlc":
        bars[0] = bars[0].model_copy(update={"high": Decimal(1)})
    elif case == "negative":
        bars[0] = bars[0].model_copy(update={"volume": -1})
    elif case == "nonfinite":
        bars[0] = bars[0].model_copy(update={"close": Decimal("NaN")})
    elif case == "outside":
        bars[-1] = bars[-1].model_copy(update={"start": bars[-1].end})
    elif case == "future":
        updates["as_of"] = series.bars[0].start
    elif case == "token":
        updates["instrument_token"] = "99999"
    elif case == "interval":
        updates["interval"] = "minute"
    else:
        updates["non_trading_days"] = ()
    changed = bind(batch, series.model_copy(update=updates | {"bars": tuple(bars)}))
    report = p.completeness(changed, mapping, NOW)
    assert report["status"] == ("PARTIAL" if case == "missing" else "INVALID")
    if case == "missing":
        assert len(report["missing"]) == 1


@pytest.mark.parametrize("field,status", [("calendar_verified", "CALENDAR_UNVERIFIED"),
                                          ("listing_suspension_verified", "CALENDAR_UNVERIFIED"),
                                          ("corporate_actions_verified",
                                           "CORPORATE_ACTION_UNVERIFIED")])
def test_evidence_required(field: str, status: str) -> None:
    mapping, batch, _, _ = fixture()
    assert p.completeness(batch.model_copy(update={field: False}), mapping, NOW)["status"] == status


def test_special_session_holiday_and_empty() -> None:
    mapping, batch, _, _ = fixture()
    # The supplied sparse calendar is explicitly synthetic, including partial session length.
    assert p.completeness(batch, mapping, NOW)["status"] == "COMPLETE"
    assert len(batch.calendar.holidays) > 170
    shortened = batch.calendar.model_copy(update={"sessions": (
        Session(start=batch.series.bars[0].start, end=batch.series.bars[2].end),)})
    changed = bind(batch.model_copy(update={"calendar": shortened}), batch.series.model_copy(
        update={"sessions": shortened.sessions, "calendar_hash": shortened.fingerprint,
                "bars": batch.series.bars[:3]}))
    assert p.completeness(changed, mapping, NOW)["expected"] == 3
    empty = bind(batch, batch.series.model_copy(update={"bars": ()}))
    assert p.completeness(empty, mapping, NOW)["status"] == "DATA_UNAVAILABLE"


def test_future_price_volume_invariance() -> None:
    _, batch, _, _ = fixture()
    bars = batch.series.bars
    at = bars[20].start
    changed = bars[:20] + tuple(b.model_copy(update={"open": Decimal(500),
        "high": Decimal(999), "low": Decimal(1), "close": Decimal(800), "volume": 999999})
        for b in bars[20:])
    assert p.available_signals(bars, at) == p.available_signals(changed, at)
    assert liquidity_bar(batch.series, at) == bars[19]
    assert execution_fingerprint() == (
        "2078a10ecad7df0faef638e3e6e37eb4808c4a8c33af5fb81883b427d0ed5925")


def test_explicit_evening_session_and_holiday_rejection() -> None:
    mapping, batch, _, _ = fixture()
    # A declared synthetic evening special session, not a real NSE schedule assertion.
    bars = tuple(b.model_copy(update={"start": b.start + timedelta(hours=9)})
                 for b in batch.series.bars)
    calendar = batch.calendar.model_copy(update={"sessions": (
        Session(start=bars[0].start, end=bars[-1].end),)})
    changed = bind(batch.model_copy(update={"calendar": calendar}), batch.series.model_copy(
        update={"sessions": calendar.sessions, "calendar_hash": calendar.fingerprint,
                "bars": bars}))
    assert p.completeness(changed, mapping, NOW)["status"] == "COMPLETE"
    holiday_bars = tuple(b.model_copy(update={"start": b.start + timedelta(days=1)}) for b in bars)
    holiday = bind(changed, changed.series.model_copy(update={"bars": holiday_bars}))
    assert p.completeness(holiday, mapping, NOW)["status"] == "INVALID"


def test_research_transport_guard(monkeypatch: pytest.MonkeyPatch) -> None:
    import socket
    mapping, batch, window, life = fixture()
    def forbidden(*args: Any, **kwargs: Any) -> None:
        pytest.fail("unexpected network access")
    monkeypatch.setattr(socket, "socket", forbidden)
    session = p.Transient(life)
    session.accept(batch, mapping, NOW)
    result = p.research(batch, mapping, NOW, window, tariff(), session)
    assert result["real_kite_requests"] == 0


def test_lifecycle_disposal_and_persistence(tmp_path: Path) -> None:
    mapping, batch, _, life = fixture()
    session = p.Transient(life)
    session.accept(batch, mapping, NOW)
    with pytest.raises(ValueError, match="RETENTION_NOT_AUTHORIZED"):
        session.persist()
    with pytest.raises(ValueError, match="EXPIRED"):
        session.active(life.expires_at)
    assert session.dispose()["discarded_checkpoints"] == 0
    with pytest.raises(ValueError, match="EXPIRED"):
        session.accept(batch, mapping, NOW)
    assert list(tmp_path.iterdir()) == []


def test_expiry_is_irreversible_with_earlier_clock() -> None:
    _, _, _, life = fixture()
    session = p.Transient(life)
    with pytest.raises(ValueError, match="EXPIRED"):
        session.active(life.expires_at)
    with pytest.raises(ValueError, match="EXPIRED"):
        session.active(NOW)


def test_genuine_lifecycle_rejected() -> None:
    _, _, _, life = fixture()
    with pytest.raises(ValueError):
        p.Transient(life.model_copy(update={"source": "GENUINE"}))


@pytest.mark.parametrize("field,value", [("version", "unknown"), ("source", "0" * 64),
                                         ("mapping", "0" * 64), ("accepted", 99),
                                         ("retention_policy", "0" * 64)])
def test_corrupt_checkpoint(field: str, value: Any) -> None:
    mapping, batch, _, life = fixture()
    checkpoint = p.Transient(life).accept(batch, mapping, NOW)
    checkpoint["checkpoint"][field] = value
    checkpoint["fingerprint"] = p.sha(p.canonical(checkpoint["checkpoint"]))
    with pytest.raises(ValueError, match="CHECKPOINT_INVALID"):
        p.Transient(life).accept(batch, mapping, NOW, checkpoint)


def test_recovery_duplicate_conflict_expired_and_partial() -> None:
    mapping, batch, _, life = fixture()
    first = p.Transient(life).accept(batch, mapping, NOW)
    session = p.Transient(life)
    assert session.accept(batch, mapping, NOW, first) == first
    with pytest.raises(ValueError, match="REPLAY_CONFLICT"):
        session.accept(batch, mapping, NOW)
    changed = bind(batch, batch.series.model_copy(update={"bars": tuple(
        b.model_copy(update={"volume": 10001}) for b in batch.series.bars)}))
    with pytest.raises(ValueError, match="CHECKPOINT_INVALID"):
        p.Transient(life).accept(changed, mapping, NOW, first)
    with pytest.raises(ValueError, match="CHECKPOINT_INVALID"):
        p.Transient(life).accept(batch, mapping, NOW, {})
    with pytest.raises(ValueError, match="EXPIRED"):
        p.Transient(life).accept(batch, mapping, life.expires_at, first)


def test_six_strategy_research_replay() -> None:
    mapping, batch, window, life = fixture()
    outputs = []
    for _ in range(2):
        session = p.Transient(life)
        session.accept(batch, mapping, NOW)
        result = p.research(batch, mapping, NOW, window, tariff(), session)
        assert [r["strategy_id"] for r in result["results"]] == [f"S0{i}" for i in range(1, 7)]
        assert all(r["data_status"] == "SYNTHETIC" for r in result["results"])
        assert result["genuine_qualified"] == 0 and result["genuine_performance"] is None
        outputs.append(p.canonical(result))
    assert outputs[0] == outputs[1]


def test_no_unaccepted_research() -> None:
    mapping, batch, window, life = fixture()
    with pytest.raises(ValueError, match="NOT_ACCEPTED"):
        p.research(batch, mapping, NOW, window, tariff(), p.Transient(life))


def test_separate_dry_plans_unknown_budgets() -> None:
    _, _, window, _ = fixture()
    a = p.request_plan("INSTRUMENT_MASTER", window.start, window.end)
    b = p.request_plan("HISTORICAL_CANDLES", window.start, window.end)
    assert a["endpoint"] != b["endpoint"]
    for plan in (a, b):
        assert plan["authorization"] == "NOT_AUTHORIZED"
        assert plan["estimated_requests"] is None and plan["permitted_requests"] == 0


def test_no_side_effect_imports() -> None:
    tree = ast.parse(Path(p.__file__).read_text())
    forbidden = {"socket", "requests", "httpx", "urllib", "os", "subprocess", "pathlib",
                 "kiteconnect", "shutil", "tempfile"}
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            assert not {a.name.split(".")[0] for a in node.names} & forbidden
        elif isinstance(node, ast.ImportFrom):
            assert (node.module or "").split(".")[0] not in forbidden
