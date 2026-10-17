"""Synthetic candidate evidence; no methodology adoption or production integration."""

from datetime import timedelta
from decimal import Decimal
from typing import Any

import pytest
from test_phase14 import tariff
from test_phase1440 import inputs

from strategy_engine.phase1439 import pipeline as p
from strategy_engine.phase1440 import datasets as d
from strategy_engine.phase1441 import compatibility as c


def candidate() -> tuple[c.Candidate, p.CandleBatch]:
    _, scope, batch, _ = inputs()
    series = batch.series
    item = c.Period(security="SYNTHETIC_SECURITY", symbol=series.symbol,
                    token=series.instrument_token, generation=series.master_hash,
                    source=series.source_hash, start=series.from_inclusive,
                    end=series.to_exclusive, observed=series.from_inclusive,
                    available=series.from_inclusive, valid_until=series.to_exclusive,
                    reason="INITIAL")
    identity = c.Timeline(security=item.security, start=item.start, end=item.end, periods=(item,))
    return c.Candidate(identity=identity, raw_segments=(p.fingerprint(series),),
                       source=series.source_hash, calendar=series.calendar_hash,
                       actions_fingerprint=p.sha(p.canonical([])), action_source_complete=True,
                       action_source_available=item.start, retention=p.fingerprint(scope.lifecycle),
                       window=p.fingerprint(scope.window)), batch


def transition(symbol: bool = False) -> c.Timeline:
    value, _ = candidate()
    a = value.identity.periods[0]
    middle = a.start + (a.end - a.start) / 2
    first = a.model_copy(update={"end": middle})
    second = a.model_copy(update={"start": middle, "token": "9876",
        "symbol": "SYNTHETIC_NEW" if symbol else a.symbol,
        "reason": "SYMBOL_CHANGE" if symbol else "TOKEN_REPLACEMENT",
        "generation": p.sha(b"NEW_GENERATION"), "transition": p.sha(b"SYNTHETIC_TRANSITION")})
    return value.identity.model_copy(update={"periods": (first, second)})


def test_no_action_legacy_bytes() -> None:
    value, batch = candidate()
    before = p.canonical(batch.series.model_dump(mode="json"))
    assert c.legacy_check(value, batch.series, batch.series.as_of) == p.fingerprint(batch.series)
    assert before == p.canonical(batch.series.model_dump(mode="json"))


@pytest.mark.parametrize("symbol", [False, True])
def test_valid_transition_not_v1(symbol: bool) -> None:
    value, batch = candidate()
    identity = transition(symbol)
    assert c.timeline(identity, batch.series.as_of)
    changed = value.model_copy(update={"identity": identity,
                                      "raw_segments": (p.sha(b"A"), p.sha(b"B"))})
    with pytest.raises(ValueError, match="UNSUPPORTED_SERIES_V1"):
        c.legacy_check(changed, batch.series, batch.series.as_of)


@pytest.mark.parametrize("change", ["overlap", "gap", "reuse", "evidence", "stale",
                                     "future", "security", "symbol", "generation"])
def test_identity_rejections(change: str) -> None:
    identity = transition()
    first, second = identity.periods
    updates: dict[str, Any] = {
        "overlap": {"start": first.end - timedelta(seconds=1)},
        "gap": {"start": first.end + timedelta(seconds=1)},
        "evidence": {"transition": None}, "stale": {"valid_until": second.start},
        "future": {"available": second.start + timedelta(days=1)},
        "security": {"security": "OTHER"}, "symbol": {"symbol": "OTHER"},
        "generation": {"generation": first.generation}, "reuse": {}}
    identity = identity.model_copy(update={"periods": (first, second.model_copy(
        update=updates[change]))})
    other = second.model_copy(update={"security": "OTHER_SECURITY"})
    with pytest.raises(ValueError):
        c.timeline(identity, identity.end, (other,) if change == "reuse" else ())


@pytest.mark.parametrize("kind", ["SPLIT", "BONUS", "DIVIDEND", "RIGHTS", "MERGER",
                                  "DEMERGER", "SYMBOL_CHANGE", "DELISTING", "SUSPENSION"])
def test_action_evidence_no_transformation(kind: str) -> None:
    value, batch = candidate()
    item = value.identity.periods[0]
    action = d.Action.model_validate(dict(symbol=item.symbol, kind=kind, announced_at=item.start,
        effective_at=item.start + timedelta(days=1), available_at=item.start,
        observed_at=item.start, ratio=Decimal("2"), amount=Decimal("1"),
        source="SYNTHETIC", source_hash=p.sha(b"ACTION")))
    value = value.model_copy(update={"actions": (action,), "actions_fingerprint": p.sha(
        p.canonical([action.model_dump(mode="json")]))})
    assert c.assess(value, batch.series.as_of)["legacy"] == "UNSUPPORTED_CONVERSION"
    with pytest.raises(ValueError):
        c.legacy_check(value, batch.series, batch.series.as_of)


@pytest.mark.parametrize("basis", ["PROVIDER_ADJUSTED", "LOCALLY_ADJUSTED", "MIXED",
                                   "ADJUSTMENT_UNVERIFIED"])
def test_adjustment_basis_and_double_adjustment_rejected(basis: str) -> None:
    value, batch = candidate()
    changed = value.model_copy(update={"basis": basis})
    assert c.family(value) != c.family(changed)
    with pytest.raises(ValueError):
        c.legacy_check(changed, batch.series, batch.series.as_of)


@pytest.mark.parametrize("field", ["source", "calendar", "raw_segments", "action_source_complete",
                                   "actions_fingerprint"])
def test_changed_evidence(field: str) -> None:
    value, batch = candidate()
    replacement: Any = p.sha(b"CHANGED")
    if field == "raw_segments":
        replacement = (replacement,)
    if field == "action_source_complete":
        replacement = False
    with pytest.raises(ValueError):
        c.legacy_check(value.model_copy(update={field: replacement}),
                       batch.series, batch.series.as_of)


def test_changed_mapping() -> None:
    value, batch = candidate()
    item = value.identity.periods[0].model_copy(update={"generation": p.sha(b"CHANGED")})
    identity = value.identity.model_copy(update={"periods": (item,)})
    with pytest.raises(ValueError):
        c.legacy_check(value.model_copy(update={"identity": identity}),
                       batch.series, batch.series.as_of)


def test_retrospective_label_not_historical_knowledge() -> None:
    value, batch = candidate()
    item = value.identity.periods[0]
    late = item.model_copy(update={"observed": item.start + timedelta(days=1),
                                   "available": item.start + timedelta(days=1)})
    identity = value.identity.model_copy(update={"periods": (late,)})
    with pytest.raises(ValueError, match="RETROACTIVE_MAPPING"):
        c.timeline(identity, batch.series.as_of)
    identity = identity.model_copy(update={"availability": "RETROSPECTIVELY_NORMALIZED"})
    result = c.assess(value.model_copy(update={"identity": identity}), batch.series.as_of)
    assert result["legacy"] == "UNSUPPORTED_CONVERSION"


def test_future_action_evidence() -> None:
    value, batch = candidate()
    with pytest.raises(ValueError, match="FUTURE_ACTION_SOURCE"):
        c.assess(value.model_copy(update={"action_source_available": batch.series.as_of
                                         + timedelta(days=1)}), batch.series.as_of)


@pytest.mark.parametrize("packet", [{}, {"status": "APPROVED"}, {"fingerprint": "wrong"}])
def test_migration_not_authorized(packet: dict[str, Any]) -> None:
    with pytest.raises(ValueError, match="METHODOLOGY_NOT_AUTHORIZED"):
        c.migration_approval(packet)


def test_legacy_signal_and_cash_invariance() -> None:
    raw, scope, batch, mapping = inputs()
    value, _ = candidate()
    before = d.integrate(raw, p.sha(raw), scope, p.sha(b"SOFTWARE"), batch, mapping, tariff())
    c.legacy_check(value, batch.series, scope.as_of)
    after = d.integrate(raw, p.sha(raw), scope, p.sha(b"SOFTWARE"), batch, mapping, tariff())
    assert p.canonical(before) == p.canonical(after)
    assert len(after["research"]["results"]) == 6


def test_no_genuine_claims_or_transport() -> None:
    value, batch = candidate()
    result = c.assess(value, batch.series.as_of)
    assert result["genuine_performance"] is None
    assert result["genuine_qualified_pairs"] == result["real_kite_requests"] == 0
    assert value.status == "PROPOSED_NOT_ADOPTED" and value.migration == "NOT_AUTHORIZED"
    assert len(c.matrix()) == 14


@pytest.mark.parametrize("change", ["future", "retroactive", "ratio", "amount", "source_hash"])
def test_action_evidence_failures(change: str) -> None:
    value, batch = candidate()
    item = value.identity.periods[0]
    effective = item.start + timedelta(days=1)
    action = d.Action(symbol=item.symbol, kind="DIVIDEND" if change == "amount" else "SPLIT",
                      announced_at=item.start, effective_at=effective, available_at=item.start,
                      observed_at=item.start, ratio=Decimal("2"), amount=Decimal("1"),
                      source="SYNTHETIC", source_hash=p.sha(b"ACTION"))
    updates: dict[str, Any] = {"future": {"available_at": batch.series.as_of + timedelta(days=1)},
        "retroactive": {"available_at": effective + timedelta(days=1)},
        "ratio": {"ratio": None}, "amount": {"amount": None}, "source_hash": {}}
    action = action.model_copy(update=updates[change])
    fingerprint = p.sha(p.canonical([action.model_dump(mode="json")]))
    if change == "source_hash":
        fingerprint = p.sha(b"WRONG")
    value = value.model_copy(update={"actions": (action,), "actions_fingerprint": fingerprint})
    with pytest.raises(ValueError):
        c.assess(value, batch.series.as_of)


def test_candidate_not_in_production_entry_point() -> None:
    import inspect

    assert "phase1441" not in inspect.getsource(p.research)
    assert "phase1441" not in inspect.getsource(d.integrate)
