"""Canonical risk identity and explicit legacy compatibility regression tests."""

import json
from decimal import Decimal, localcontext
from pathlib import Path

import pytest
from pydantic import ValidationError
from test_phase1418 import control, journal, policy, request

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1418.canonical_policy import VERSION, normalize, payload
from strategy_engine.phase1418.portfolio import Control, Policy, run

D = Decimal


def test_sector_order_and_roundtrip() -> None:
    p = policy()
    q = p.model_copy(update={"sectors": dict(reversed(list(p.sectors.items())))})
    assert p.fingerprint == q.fingerprint
    assert (
        Policy.model_validate_json(canonical(p.model_dump(mode="json"))).fingerprint
        == p.fingerprint
    )
    assert Policy.model_validate_json(p.model_dump_json()).fingerprint == p.fingerprint
    assert p.fingerprint == sha(payload(p.model_dump(mode="python"), VERSION))


def test_recursive_order_unicode_and_sequence_semantics() -> None:
    a = {"outer": {"z": D("1.00"), "a": [True, {"न": None, "k": "value"}]}, "second": 2}
    b = {"second": 2, "outer": {"a": [True, {"k": "value", "न": None}], "z": D("1")}}
    assert payload(a, VERSION) == payload(b, VERSION)
    assert payload([1, 2], VERSION) != payload([2, 1], VERSION)
    assert payload(D(1), VERSION) != payload("1", VERSION)
    assert payload(D(1), VERSION) != payload({"decimal": "1"}, VERSION)
    assert payload(True, VERSION) != payload(1, VERSION)
    assert json.loads(payload(a, VERSION).decode("utf-8"))["schema"] == VERSION


def test_decimal_precision_exactness_and_repeated_hashing() -> None:
    value = {"precise": D("12345678901234567890.123456789012345678900"), "zero": D("-0.000")}
    with localcontext() as ctx:
        ctx.prec = 6
        a = payload(value, VERSION)
        p = policy().fingerprint
        ctx.prec = 50
        b = payload(value, VERSION)
        q = policy().fingerprint
    assert a == b and p == q
    assert normalize(D("-0.000")) == normalize(D(0))
    assert payload(value, VERSION) != payload(
        dict(value, precise=D("12345678901234567890.123456789012345678901")), VERSION
    )
    assert len({sha(payload(value, VERSION)) for _ in range(100)}) == 1


@pytest.mark.parametrize(
    "value",
    [
        float("nan"),
        float("inf"),
        0.1,
        D("NaN"),
        D("sNaN"),
        D("Infinity"),
        D("-Infinity"),
        {1: "bad"},
        {1, 2},
        object(),
    ],
)
def test_invalid_numeric_and_unsupported_values(value: object) -> None:
    with pytest.raises(ValueError):
        payload(value, VERSION)


@pytest.mark.parametrize("field", ["strategy_exposure", "sector_exposure"])
def test_changed_exposure_changes_identity(field: str) -> None:
    p = policy()
    assert p.fingerprint != p.model_copy(update={field: D(9999)}).fingerprint


def test_changed_nested_risk_and_decimal_equivalence() -> None:
    p = policy()
    changed = p.model_copy(update={"base": p.base.model_copy(update={"risk_per_trade": D(99)})})
    equivalent = p.model_copy(
        update={"base": p.base.model_copy(update={"risk_per_trade": D("100.00")})}
    )
    assert p.fingerprint != changed.fingerprint
    assert p.fingerprint == equivalent.fingerprint
    with pytest.raises(ValueError, match="NONFINITE"):
        _ = p.model_copy(update={"sector_exposure": D("NaN")}).fingerprint


@pytest.mark.parametrize("version", ["Phase1418RiskPolicyCanonical.v1", "future", ""])
def test_unknown_versions_reject(version: str) -> None:
    p = policy()
    with pytest.raises(ValidationError):
        Policy.model_validate(dict(p.model_dump(), fingerprint_version=version))
    with pytest.raises(ValueError, match="VERSION"):
        _ = p.model_copy(update={"fingerprint_version": version}).fingerprint
    with pytest.raises(ValueError, match="VERSION"):
        payload({}, version)
    ctl = control(p)
    with pytest.raises(ValidationError):
        Control.model_validate(dict(ctl.model_dump(), policy_fingerprint_version=version))


def test_missing_versions_do_not_reinterpret_legacy() -> None:
    p = policy().model_dump()
    del p["fingerprint_version"]
    with pytest.raises(ValidationError):
        Policy.model_validate(p)
    ctl = control(policy()).model_dump()
    del ctl["policy_fingerprint_version"]
    with pytest.raises(ValidationError):
        Control.model_validate(ctl)


def test_legacy_checkpoint_rejected_without_writes(tmp_path: Path) -> None:
    old = json.loads(
        (ROOT / "research/phase-14.18/generation-1/synthetic-portfolio-examples.json").read_bytes()
    )["examples"]["simultaneous"]["binding"]
    path = tmp_path / "portfolio-plan.json"
    raw = canonical(old)
    path.write_bytes(raw)
    p = policy()
    with pytest.raises(ValueError, match="INCOMPATIBLE_CHECKPOINT_VERSION"):
        run((request(p), request(p, "TCS")), p, control(p), tmp_path)
    assert path.read_bytes() == raw and list(tmp_path.iterdir()) == [path]


def test_unknown_checkpoint_and_control_reject(tmp_path: Path) -> None:
    p = policy()
    r = request(p)
    ctl = control(p)
    with pytest.raises(ValueError, match="UNSUPPORTED_POLICY_FINGERPRINT_VERSION"):
        run(
            (r,),
            p,
            ctl.model_copy(update={"policy_fingerprint_version": "future"}),
            tmp_path / "control",
        )
    assert not (tmp_path / "control").exists()
    run((r,), p, ctl, tmp_path / "checkpoint")
    path = tmp_path / "checkpoint/portfolio-plan.json"
    value = json.loads(path.read_bytes())
    value["policy_fingerprint_version"] = "future"
    path.write_bytes(canonical(value))
    with pytest.raises(ValueError, match="INCOMPATIBLE_CHECKPOINT_VERSION"):
        run((r,), p, ctl, tmp_path / "checkpoint")


@pytest.mark.parametrize("scenario", ["simultaneous", "missing-eod", "cancelled"])
def test_three_corrected_examples(tmp_path: Path, scenario: str) -> None:
    from strategy_engine.phase1415.paper import event

    p = policy()
    a = request(p)
    requests = (a, request(p, "TCS")) if scenario == "simultaneous" else (a,)
    ctl = control(p)
    if scenario == "missing-eod":
        ctl = control(p, delivery_end=a.data.series.bars[4].start)
    if scenario == "cancelled":
        signal = event(
            "SBIN", a.definition, a.data.series.bars[3], a.data.series.bars[3].end
        ).idempotency_key
        ctl = control(p, cancel_signals=(signal,))
    result = run(requests, p, ctl, tmp_path)
    binding = json.loads((tmp_path / "portfolio-plan.json").read_bytes())
    restored = Policy.model_validate(binding["policy"])
    restored_control = Control.model_validate(binding["control"])
    assert restored.fingerprint == p.fingerprint == restored_control.policy_hash
    assert result["schema"] == "Phase1418PortfolioSummary.v2"
    assert result["risk_policy_fingerprint"] == p.fingerprint
    assert all(
        e["risk_policy_fingerprint_version"] == VERSION
        and e["risk_policy_fingerprint"] == p.fingerprint
        for e in journal(tmp_path)
    )
    assert run(requests, restored, restored_control, tmp_path) == result
    if scenario == "simultaneous":
        assert result["simulation_complete"] and not result["account"]["positions"]
    elif scenario == "missing-eod":
        assert result["halted"] and result["account"]["positions"]
    else:
        assert result["account"]["cash"] == "10000" and result["account"]["reserved"] == "0"
