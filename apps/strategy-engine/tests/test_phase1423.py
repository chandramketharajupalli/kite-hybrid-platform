"""Synthetic causal benchmark regressions; accepted prices never change."""

import ast
import copy
import json
from datetime import timedelta
from decimal import Decimal as D
from decimal import localcontext
from pathlib import Path
from typing import Any

import pytest

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1423.quality import (
    Fill,
    Reference,
    accepted_fills,
    assess,
    attribute,
    fixture_reference,
    measure,
    run,
    seal_reference,
)


def source() -> dict[str, Any]:
    return dict(
        json.loads(
            (ROOT / "research/phase-14.20/generation-1/synthetic-multi-sessions.json").read_bytes()
        )["scenarios"]["consecutive"]
    )


@pytest.fixture(scope="module")
def fills() -> tuple[Fill, ...]:
    s = source()
    return accepted_fills(s, sha(canonical(s)))


@pytest.mark.parametrize(
    "index,reference,signed",
    [
        (0, "101", "10"),
        (0, "103", "-10"),
        (1, "103", "10"),
        (1, "101", "-10"),
        (0, "102", "0"),
        (1, "102", "0"),
    ],
)
def test_side_sign_and_zero(
    fills: tuple[Fill, ...], index: int, reference: str, signed: str
) -> None:
    f = fills[index]
    r = Reference.model_validate(fixture_reference(f, D(reference)))
    v = measure(f, r)
    assert D(v["signed"]) == D(signed)
    assert D(v["absolute"]) == abs(D(signed))
    assert D(v["per_share"]) == D(signed) / 10


def test_bps_and_precision(fills: tuple[Fill, ...]) -> None:
    f = fills[0]
    ref = Reference.model_validate(fixture_reference(f, D("100")))
    a = measure(f, ref)
    with localcontext() as c:
        c.prec = 5
        b = measure(f, ref)
    assert a == b and D(a["bps"]) == 200 and D(a["signed"]) == 20


def test_accepted_fill_cash_and_identity(fills: tuple[Fill, ...]) -> None:
    assert len(fills) == 4 and len({f.fill_id for f in fills}) == 4
    assert fills[0].side == "BUY" and fills[1].side == "SELL"
    assert fills[0].price == fills[1].price == D("102")
    assert fills[0].cash_delta == D("-1020.43019520")
    assert fills[1].cash_delta == D("1019.34540480")
    assert fills[0].at == fills[0].cutoff


def test_attribution_and_fee_separation(fills: tuple[Fill, ...]) -> None:
    before = canonical([f.model_dump(mode="json") for f in fills])
    refs = [fixture_reference(f, f.price - 1 if f.side == "BUY" else f.price + 1) for f in fills]
    result = attribute(fills, refs)
    p = result["portfolio"]
    assert p["signed_total"] == "40" and p["absolute_total"] == "40"
    assert D(p["fees"]) == D("2.16958080") and D(p["cash_delta"]) == D("-2.16958080")
    assert p["slippage_added_to_cash"] == "0"
    for key, count in [("strategy_slippage", 6), ("symbol_slippage", 1115)]:
        rows = result[key]
        assert len(rows) == count
        assert sum(D(row["measured_subset_signed"] or "0") for row in rows) == 40
        assert sum(row["measured_fills"] for row in rows) == 4
        assert sum(row["status"] == "NOT_COMPUTABLE" for row in rows) == count - 1
    assert canonical([f.model_dump(mode="json") for f in fills]) == before


def test_missing_and_partial_coverage(fills: tuple[Fill, ...]) -> None:
    empty = attribute(fills, [])
    assert empty["portfolio"]["signed_total"] is None
    assert empty["portfolio"]["measured_subset_signed"] is None
    assert empty["portfolio"]["not_computable"] == 4
    partial = attribute(fills, [fixture_reference(fills[0], fills[0].price)])
    assert partial["portfolio"]["signed_total"] is None
    assert partial["portfolio"]["measured_subset_signed"] == "0"
    assert partial["portfolio"]["coverage"] == "0.25"
    assert partial["portfolio"]["not_computable"] == 3


@pytest.mark.parametrize(
    "field,value",
    [
        ("symbol", "TCS"),
        ("strategy_id", "S05"),
        ("fill_id", "f" * 64),
        ("quantity", 11),
        ("side", "SELL"),
        ("run_id", "a" * 64),
        ("session_date", "2026-10-06"),
        ("schema_version", "future.v99"),
        ("source", "UNVERIFIED_LOCAL_DATA"),
        ("reference_price", "0"),
        ("reference_price", "NaN"),
        ("reference_price", "Infinity"),
        ("reference_price", "-1"),
        ("reference_price", None),
        ("reference_price", 101.0),
        ("reference_type", "POST_TRADE_BENCHMARK"),
        ("fill_price", "101"),
        ("policy_hash", "b" * 64),
        ("quantity", True),
    ],
)
def test_invalid_reference(fills: tuple[Fill, ...], field: str, value: Any) -> None:
    raw = fixture_reference(fills[0], D("101"))
    raw[field] = value
    result = attribute(fills, [seal_reference(raw)])
    assert len(result["rejections"]) == 1
    assert result["portfolio"]["measured_fills"] == 0
    assert result["fill_coverage"][0]["status"] == "NOT_COMPUTABLE"


@pytest.mark.parametrize("field", ["observed_at", "available_at", "measurement_cutoff", "fill_at"])
def test_future_information(fills: tuple[Fill, ...], field: str) -> None:
    raw = fixture_reference(fills[0], D("101"))
    raw[field] = (fills[0].at + timedelta(minutes=5)).isoformat().replace("+00:00", "Z")
    result = attribute(fills, [seal_reference(raw)])
    assert result["portfolio"]["measured_fills"] == 0


def test_unavailable_before_cutoff(fills: tuple[Fill, ...]) -> None:
    raw = fixture_reference(fills[0], D("101"))
    raw["measurement_cutoff"] = (
        (fills[0].at - timedelta(seconds=1)).isoformat().replace("+00:00", "Z")
    )
    assert attribute(fills, [seal_reference(raw)])["rejections"]


def test_duplicate_identity_and_fill_reject_all(fills: tuple[Fill, ...]) -> None:
    raw = fixture_reference(fills[0], D("101"))
    result = attribute(fills, [raw, raw])
    assert len(result["rejections"]) == 2 and result["portfolio"]["measured_fills"] == 0
    second = dict(raw, evidence_id="c" * 64)
    result = attribute(fills, [raw, seal_reference(second)])
    assert {r["reason"] for r in result["rejections"]} == {"DUPLICATE_FILL_ATTRIBUTION"}


def test_hash_tampering(fills: tuple[Fill, ...]) -> None:
    raw = fixture_reference(fills[0], D("101"))
    raw["reference_price"] = "100"
    assert attribute(fills, [raw])["portfolio"]["measured_fills"] == 0


def test_order_invariance(fills: tuple[Fill, ...]) -> None:
    refs = [fixture_reference(f, D("101")) for f in fills]
    assert canonical(attribute(fills, refs)) == canonical(attribute(fills, list(reversed(refs))))


def test_no_earlier_change_from_future_reference(fills: tuple[Fill, ...]) -> None:
    first = fixture_reference(fills[0], D("101"))
    late = fixture_reference(fills[1], D("103"))
    a = attribute(fills, [first, late])
    late["reference_price"] = "104"
    b = attribute(fills, [first, seal_reference(late)])
    assert a["fill_coverage"][0] == b["fill_coverage"][0]
    assert a["portfolio"]["cash_delta"] == b["portfolio"]["cash_delta"]


def test_replay_and_publication(tmp_path: Path, fills: tuple[Fill, ...]) -> None:
    s = source()
    refs = [fixture_reference(f, D("101")) for f in fills]
    p, q = tmp_path / "source.json", tmp_path / "refs.json"
    p.write_bytes(canonical(s))
    q.write_bytes(canonical(refs))
    target = tmp_path / "result"
    a = run(p, sha(p.read_bytes()), q, sha(q.read_bytes()), target)
    before = (target / "execution-quality.json").read_bytes()
    assert run(p, sha(p.read_bytes()), q, sha(q.read_bytes()), target) == a
    assert (target / "execution-quality.json").read_bytes() == before
    assert json.loads(before) == assess(s, sha(canonical(s)), refs)
    with pytest.raises(ValueError, match="INPUT_HASH"):
        run(p, "0" * 64, q, sha(q.read_bytes()), target)


def test_accepted_source_tampering() -> None:
    s = source()
    changed = copy.deepcopy(s)
    changed["outcomes"][0]["value"]["daily"]["cash"] = "1"
    with pytest.raises(ValueError):
        accepted_fills(changed, sha(canonical(changed)))


def test_duplicate_accepted_fill(fills: tuple[Fill, ...]) -> None:
    with pytest.raises(ValueError, match="DUPLICATE_FILL"):
        attribute((fills[0], fills[0]), [])


def test_no_fills_is_not_zero_measurement() -> None:
    r = attribute((), [])
    assert r["portfolio"]["status"] == "NOT_COMPUTABLE"
    assert r["portfolio"]["signed_total"] is None and r["portfolio"]["coverage"] is None
    assert all(row["signed_total"] is None for row in r["symbol_slippage"])


def test_forged_later_cutoff_cannot_authorize_future_quote(fills: tuple[Fill, ...]) -> None:
    raw = fixture_reference(fills[0], D("101"))
    late = (fills[0].at + timedelta(minutes=5)).isoformat().replace("+00:00", "Z")
    for field in ("observed_at", "available_at", "measurement_cutoff", "fill_at"):
        raw[field] = late
    r = attribute(fills, [seal_reference(raw)])
    assert r["rejections"][0]["reason"] == "FILL_LINKAGE_MISMATCH"
    assert r["portfolio"]["measured_fills"] == 0


def test_no_broker_dependency() -> None:
    tree = ast.parse(
        (ROOT / "apps/strategy-engine/src/strategy_engine/phase1423/quality.py").read_text()
    )
    names = [n.module or "" for n in ast.walk(tree) if isinstance(n, ast.ImportFrom)]
    names += [alias.name for n in ast.walk(tree) if isinstance(n, ast.Import) for alias in n.names]
    assert not any(
        any(word in n for word in ("kiteconnect", "requests", "http", "socket")) for n in names
    )
