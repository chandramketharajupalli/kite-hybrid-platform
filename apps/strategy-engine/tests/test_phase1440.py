"""Synthetic calendar and source evidence; no real exchange observations."""

import json
from datetime import timedelta
from decimal import Decimal
from typing import Any

import pytest
from test_phase14 import tariff
from test_phase1439 import NOW, fixture

from strategy_engine.backtest.dataset import NSE
from strategy_engine.phase14.engine import FIVE, Session
from strategy_engine.phase1439 import pipeline as p
from strategy_engine.phase1440 import datasets as d

SOFTWARE = p.sha(b"SYNTHETIC_TEST_SOFTWARE")


def inputs() -> tuple[bytes, d.Scope, p.CandleBatch, p.Master]:
    mapping, batch, window, lifecycle = fixture()
    first = window.start.astimezone(NSE).date()
    last = (window.end - timedelta(microseconds=1)).astimezone(NSE).date()
    active = {s.start.astimezone(NSE).date() for s in batch.calendar.sessions}
    days = tuple(d.Day(day=first + timedelta(days=i),
                       kind="SPECIAL_SESSION_VERIFIED" if first + timedelta(days=i) in active
                       else "VERIFIED_NON_TRADING_DAY", description="SYNTHETIC_SPARSE_FIXTURE",
                       source=p.sha(b"SYNTHETIC_DAY"), available_at=window.start)
                 for i in range((last - first).days + 1))
    scope = d.Scope(dataset_id=batch.batch_id, symbol=batch.series.symbol, window=window,
                    calendar=batch.calendar, days=days, calendar_verified=True,
                    listing_start=window.start, listing_end=window.end,
                    listing_evidence=p.sha(b"SYNTHETIC_LISTING"), listing_available_at=window.start,
                    suspension_evidence=p.sha(b"SYNTHETIC_NO_SUSPENSIONS"),
                    suspension_available_at=window.start,
                    suspension_verified=True,
                    identity=d.Identity(complete=True, periods=(d.Period(
                        instrument=mapping.records[0], generation=p.fingerprint(mapping),
                        source=mapping.source_digest, observed_at=window.start,
                        start=window.start, end=window.end),)),
                    actions=d.Actions(symbol=batch.series.symbol, coverage_start=window.start,
                                      coverage_end=window.end, available_at=window.start,
                                      complete=True, source="SYNTHETIC_COMPLETE_NO_EVENTS",
                                      source_hash=p.sha(b"SYNTHETIC_NO_EVENTS")),
                    lifecycle=lifecycle, as_of=NOW)
    records = [d.Record(candle=b, token="1", mapping_generation=p.fingerprint(mapping),
                        source_batch=batch.batch_id, available_at=b.end).model_dump(mode="json")
               for b in batch.series.bars]
    return p.canonical(records), scope, batch, mapping


def assess(raw: bytes, scope: d.Scope) -> dict[str, Any]:
    return d.assess(raw, p.sha(raw), scope)


def test_complete_and_integration() -> None:
    raw, scope, batch, mapping = inputs()
    report = assess(raw, scope)
    assert report["status"] == "SYNTHETIC_DATASET_CERTIFIED"
    assert report["expected"] == report["valid"] == 40
    assert report["coverage_percent"] == "100"
    result = d.integrate(raw, p.sha(raw), scope, SOFTWARE, batch, mapping, tariff())
    assert len(result["research"]["results"]) == 6
    assert result["research"]["genuine_qualified"] == 0
    assert result["research"]["genuine_performance"] is None
    assert not result["full_universe_end_to_end"]


@pytest.mark.parametrize("minutes,kind,count", [
    (375, "VERIFIED_SESSION", 75), (60, "SPECIAL_SESSION_VERIFIED", 12),
    (200, "SPECIAL_SESSION_VERIFIED", 40), (5, "SPECIAL_SESSION_VERIFIED", 1),
])
def test_expected_session_lengths(minutes: int, kind: str, count: int) -> None:
    _, scope, batch, _ = inputs()
    start = batch.series.sessions[0].start
    session = Session(start=start, end=start + timedelta(minutes=minutes))
    calendar = scope.calendar.model_copy(update={"sessions": (session,)})
    days = tuple(day.model_copy(update={"kind": kind})
                 if day.day == start.astimezone(NSE).date() else day for day in scope.days)
    ledger = d.expected(scope.model_copy(update={"calendar": calendar, "days": days}))
    assert len(ledger["timestamps"]) == count
    assert ledger["timestamps"][0] == start.isoformat()
    assert ledger["timestamps"][-1] == (session.end - FIVE).isoformat()


def test_muhurat_session() -> None:
    _, scope, batch, _ = inputs()
    start = batch.series.sessions[0].start + timedelta(hours=9)
    special = Session(start=start, end=start + timedelta(hours=1))
    cal = scope.calendar.model_copy(update={"sessions": (special,)})
    assert len(d.expected(scope.model_copy(update={"calendar": cal}))["timestamps"]) == 12


@pytest.mark.parametrize("change", ["unverified", "missing_day", "duplicate_day", "future_day",
                                    "missing_closed", "ordinary_short", "suspension_unknown"])
def test_calendar_fails_closed(change: str) -> None:
    raw, scope, _, _ = inputs()
    updates: dict[str, Any] = {}
    if change == "unverified":
        updates["calendar_verified"] = False
    elif change == "missing_day":
        updates["days"] = scope.days[1:]
    elif change == "duplicate_day":
        updates["days"] = scope.days + (scope.days[0],)
    elif change == "future_day":
        updates["days"] = (scope.days[0].model_copy(update={"available_at": NOW}),) + scope.days[1:]
    elif change == "missing_closed":
        updates["calendar"] = scope.calendar.model_copy(update={"holidays": ()})
    elif change == "ordinary_short":
        updates["days"] = tuple(day.model_copy(update={"kind": "VERIFIED_SESSION"})
                                if day.kind == "SPECIAL_SESSION_VERIFIED" else day
                                for day in scope.days)
    else:
        updates["suspension_verified"] = False
    assert assess(raw, scope.model_copy(update=updates))["status"] == "CALENDAR_UNVERIFIED"


def test_listing_and_verified_suspension() -> None:
    raw, scope, batch, _ = inputs()
    start = batch.series.bars[0].start
    scope = scope.model_copy(update={"listing_start": start + FIVE,
                                     "listing_end": start + FIVE * 39,
                                     "suspensions": (Session(start=start + FIVE * 3,
                                                              end=start + FIVE * 5),)})
    values = json.loads(raw)
    wanted = [v for i, v in enumerate(values) if 1 <= i < 39 and i not in (3, 4)]
    result = assess(p.canonical(wanted), scope)
    assert result["status"] == "SYNTHETIC_DATASET_CERTIFIED"
    assert result["expected"] == 36


def test_missing_runs_and_duplicates() -> None:
    raw, scope, _, _ = inputs()
    values = json.loads(raw)
    missing = assess(p.canonical(values[:2] + values[5:]), scope)
    assert missing["status"] == "SYNTHETIC_DATASET_PARTIAL"
    assert missing["missing_count"] == 3 and missing["missing_runs"][0]["count"] == 3
    duplicate = assess(p.canonical(values + [values[0]]), scope)
    assert duplicate["status"] == "DATASET_INVALID" and duplicate["duplicate_count"] == 1
    assert duplicate["valid"] == 40 and duplicate["coverage_percent"] == "100"


@pytest.mark.parametrize("field,value", [
    ("open", "0"), ("open", "NaN"), ("close", "Infinity"), ("high", "1"),
    ("low", "200"), ("volume", -1), ("volume", 1.5), ("volume", True),
    ("open", "1.12345678901"), ("open", 100.0), ("start", "2026-09-01T00:00:01Z"),
])
def test_invalid_ohlcv_retained(field: str, value: Any) -> None:
    raw, scope, _, _ = inputs()
    records = json.loads(raw)
    records[0]["candle"][field] = value
    result = assess(p.canonical(records), scope)
    assert result["status"] == "DATASET_INVALID" and result["invalid_count"] == 1
    assert result["invalid"][0]["index"] == 0 and result["observed"] == 40


def test_zero_volume_is_not_missing() -> None:
    raw, scope, _, _ = inputs()
    records = json.loads(raw)
    records[0]["candle"]["volume"] = 0
    assert assess(p.canonical(records), scope)["status"] == "SYNTHETIC_DATASET_CERTIFIED"


@pytest.mark.parametrize("field,value", [("interval", "minute"), ("source_batch", "wrong"),
                                        ("mapping_generation", "0" * 64), ("token", "999")])
def test_record_binding(field: str, value: str) -> None:
    raw, scope, _, _ = inputs()
    records = json.loads(raw)
    records[0][field] = value
    assert assess(p.canonical(records), scope)["status"] == "DATASET_INVALID"


def test_outside_session_and_order() -> None:
    raw, scope, batch, _ = inputs()
    records = json.loads(raw)
    bar = batch.series.bars[0].model_copy(update={"start": batch.series.bars[0].start - FIVE})
    records[0]["candle"] = bar.model_dump(mode="json")
    result = assess(p.canonical(records), scope)
    assert result["status"] == "DATASET_INVALID" and len(result["out_of_session"]) == 1
    assert assess(p.canonical(list(reversed(json.loads(raw)))), scope)["status"] == (
        "DATASET_INVALID")


@pytest.mark.parametrize("change", ["missing", "overlap", "reuse", "future", "stale"])
def test_identity(change: str) -> None:
    raw, scope, _, _ = inputs()
    period = scope.identity.periods[0]
    periods: tuple[d.Period, ...] = (period,)
    if change == "missing":
        periods = ()
    elif change == "overlap":
        periods = (period, period)
    elif change == "reuse":
        periods = (period, period.model_copy(update={"instrument": p.Instrument(
            symbol="ANOTHER", token=period.instrument.token)}))
    elif change == "future":
        periods = (period.model_copy(update={"observed_at": NOW}),)
    else:
        periods = (period.model_copy(update={"end": period.start + FIVE}),)
    result = assess(raw, scope.model_copy(update={"identity": scope.identity.model_copy(
        update={"periods": periods})}))
    assert result["status"] in ("INSTRUMENT_IDENTITY_UNVERIFIED", "DATASET_INVALID")


@pytest.mark.parametrize("change", ["incomplete", "late", "coverage", "wrong_symbol"])
def test_corporate_action_coverage(change: str) -> None:
    raw, scope, _, _ = inputs()
    update: dict[str, Any] = {"complete": False}
    if change == "late":
        update = {"available_at": NOW + FIVE}
    elif change == "coverage":
        update = {"coverage_start": scope.window.start + FIVE}
    elif change == "wrong_symbol":
        update = {"symbol": "OTHER"}
    altered = scope.model_copy(update={"actions": scope.actions.model_copy(update=update)})
    assert assess(raw, altered)["status"] == "CORPORATE_ACTION_UNVERIFIED"


@pytest.mark.parametrize("kind", ["SPLIT", "BONUS", "DIVIDEND", "RIGHTS", "MERGER", "DEMERGER",
                                  "SYMBOL_CHANGE", "DELISTING", "SUSPENSION", "IDENTITY_CHANGE"])
def test_actions_cannot_be_silently_adjusted(kind: Any) -> None:
    raw, scope, batch, _ = inputs()
    at = batch.series.bars[0].start
    action = d.Action(symbol=scope.symbol, kind=kind, announced_at=scope.window.start,
                      effective_at=at, available_at=scope.window.start, ratio=Decimal(2),
                      amount=Decimal(1), source="SYNTHETIC_EVENT", observed_at=scope.window.start,
                      source_hash=p.sha(b"SYNTHETIC_EVENT"))
    scope = scope.model_copy(update={"actions": scope.actions.model_copy(
        update={"events": (action,)})})
    result = assess(raw, scope)
    assert result["status"] == "ADJUSTMENT_UNVERIFIED"
    assert result["adjustment"] == "ADJUSTMENT_REQUIRED"


def test_double_adjustment_and_expiry_and_genuine() -> None:
    raw, scope, _, _ = inputs()
    assert assess(raw, scope.model_copy(update={"adjustment": "ADJUSTED_VERIFIED"}))["status"] == (
        "ADJUSTMENT_UNVERIFIED")
    expired = scope.model_copy(update={"as_of": scope.lifecycle.expires_at})
    assert assess(raw, expired)["status"] == "RETENTION_NOT_AUTHORIZED"
    assert assess(raw, scope.model_copy(update={"source_type": "GENUINE"}))["status"] == (
        "GENUINE_DATA_UNAVAILABLE")
    with pytest.raises(ValueError, match="RETENTION_NOT_AUTHORIZED"):
        d.Certification().persist()


def test_source_integrity_and_duplicate_json_keys() -> None:
    raw, scope, _, _ = inputs()
    assert d.assess(raw, "0" * 64, scope)["status"] == "DATASET_INVALID"
    assert assess(b'[{"candle":{},"candle":{}}]', scope)["status"] == "DATASET_INVALID"
    assert assess(b'not json', scope)["status"] == "DATASET_INVALID"


def test_availability_and_future_signal_invariance() -> None:
    raw, scope, batch, _ = inputs()
    records = json.loads(raw)
    records[0]["available_at"] = records[0]["candle"]["start"]
    assert assess(p.canonical(records), scope)["status"] == "DATASET_INVALID"
    at = batch.series.bars[10].end
    original = p.available_signals(batch.series.bars, at)
    bars = tuple(b.model_copy(update={"volume": b.volume * 10, "high": b.high * 10})
                 if b.start >= at else b for b in batch.series.bars)
    assert p.available_signals(bars, at) == original


@pytest.mark.parametrize("field", ["source", "calendar", "mapping", "corporate_actions",
                                   "adjustment", "retention", "software", "version"])
def test_receipt_tampering(field: str) -> None:
    raw, scope, _, _ = inputs()
    seal = d.receipt(raw, p.sha(raw), scope, SOFTWARE)
    altered = {**seal, "receipt": {**seal["receipt"], field: "ALTERED"}}
    with pytest.raises(ValueError, match="REPLAY_CONFLICT"):
        d.Certification().accept(raw, p.sha(raw), scope, SOFTWARE, altered)


def test_replay_and_idempotency_and_dataset_aggregation() -> None:
    raw, scope, _, _ = inputs()
    first = d.Certification().accept(raw, p.sha(raw), scope, SOFTWARE)
    assert first == d.Certification().accept(raw, p.sha(raw), scope, SOFTWARE, first)
    session = d.Certification()
    session.accept(raw, p.sha(raw), scope, SOFTWARE)
    with pytest.raises(ValueError, match="REPLAY_CONFLICT"):
        session.accept(raw, p.sha(raw), scope, SOFTWARE)
    result = d.dataset_report([assess(raw, scope)])
    assert result["status"] == "SYNTHETIC_DATASET_CERTIFIED"
    assert not result["full_universe_coverage"]
    assert result["canonical_symbol_count"] == 1115 and result["genuine_qualified_pairs"] == 0
    with pytest.raises(ValueError, match="DUPLICATE_SYMBOL"):
        d.dataset_report([assess(raw, scope), assess(raw, scope)])


def test_no_transport_or_persistence_dependency() -> None:
    import ast
    from pathlib import Path

    tree = ast.parse(Path(d.__file__).read_text())
    imports = {n.module for n in ast.walk(tree) if isinstance(n, ast.ImportFrom)}
    imports |= {a.name for n in ast.walk(tree) if isinstance(n, ast.Import) for a in n.names}
    assert not imports & {"os", "socket", "http", "requests", "httpx", "kiteconnect", "pathlib"}
    with pytest.raises(ValueError, match="ACQUISITION_NOT_AUTHORIZED"):
        p.Boundary().acquire("HISTORICAL_CANDLES")


def test_invalid_duplicate_counted_without_extra_coverage() -> None:
    raw, scope, _, _ = inputs()
    records = json.loads(raw)
    invalid = json.loads(raw)[0]
    invalid["candle"]["low"] = "999"
    result = assess(p.canonical(records + [invalid]), scope)
    assert result["invalid_count"] == result["duplicate_count"] == 1
    assert result["valid"] == result["observed_intervals"] == 40
    assert result["status"] == "DATASET_INVALID"


def test_future_suspension_cannot_erase_missing_candles() -> None:
    raw, scope, _, _ = inputs()
    future = scope.model_copy(update={"suspension_available_at": scope.as_of})
    assert assess(raw, future)["status"] == "CALENDAR_UNVERIFIED"


def test_token_transition_and_integration_binding() -> None:
    raw, scope, batch, mapping = inputs()
    original = scope.identity.periods[0]
    at = batch.series.bars[20].start
    next_generation = p.sha(b"NEXT_SYNTHETIC_MAPPING")
    periods = (original.model_copy(update={"end": at}), original.model_copy(update={
        "start": at, "observed_at": at, "generation": next_generation,
        "instrument": p.Instrument(symbol=scope.symbol, token="999")}))
    changed = scope.model_copy(update={"identity": scope.identity.model_copy(
        update={"periods": periods})})
    records = json.loads(raw)
    for record in records[20:]:
        record.update(token="999", mapping_generation=next_generation)
    source = p.canonical(records)
    assert assess(source, changed)["status"] == "SYNTHETIC_DATASET_CERTIFIED"
    with pytest.raises(ValueError, match="INTEGRATION_BINDING"):
        d.integrate(source, p.sha(source), changed, SOFTWARE, batch, mapping, tariff())


@pytest.mark.parametrize("kind", ["source", "calendar", "identity", "action", "policy"])
def test_changed_evidence_replay_rejected(kind: str) -> None:
    raw, scope, _, _ = inputs()
    seal = d.receipt(raw, p.sha(raw), scope, SOFTWARE)
    if kind == "source":
        records = json.loads(raw)
        records[0]["candle"]["volume"] += 1
        raw = p.canonical(records)
    elif kind == "calendar":
        scope = scope.model_copy(update={"calendar": scope.calendar.model_copy(
            update={"version": "CHANGED"})})
    elif kind == "identity":
        period = scope.identity.periods[0].model_copy(update={"source": "0" * 64})
        scope = scope.model_copy(update={"identity": scope.identity.model_copy(
            update={"periods": (period,)})})
    elif kind == "action":
        scope = scope.model_copy(update={"actions": scope.actions.model_copy(
            update={"source_hash": "0" * 64})})
    else:
        scope = scope.model_copy(update={"adjustment_policy": "UNAPPROVED"})
    with pytest.raises(ValueError):
        d.Certification().accept(raw, p.sha(raw), scope, SOFTWARE, seal)
