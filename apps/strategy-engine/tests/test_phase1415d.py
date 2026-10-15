"""Phase14.15D synthetic regression and external receipt integrity tests."""

import hashlib
import importlib.util
import json
from datetime import UTC, datetime, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any

import pytest
from test_phase14 import START, tariff
from test_phase1415 import END, prices, window_data

from strategy_engine.backtest.dataset import NSE, digest
from strategy_engine.phase14.engine import FIVE, Candle, Policy, Series, Session, Settings
from strategy_engine.phase1415 import hardening
from strategy_engine.phase1415.execution import execute
from strategy_engine.phase1415.hardening import (
    DEPENDENCIES,
    SOURCE,
    FamilyBinding,
    dependencies,
    family_binding,
    family_size,
    frozen_policy,
    partition_keys,
    validate_dependencies,
)
from strategy_engine.phase1415.jobs import export, run
from strategy_engine.phase1415.research import Dataset, ResearchWindow, evaluate, standard_window
from strategy_engine.phase1415.strategies import entry_signal, registry

D = Decimal
ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "receipt_verifier", ROOT / "scripts/research/verify_phase1415b.py"
)
assert SPEC and SPEC.loader
RECEIPTS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RECEIPTS)


def binding() -> FamilyBinding:
    return family_binding(END, Settings(), tariff())


def collect(
    path: Path, family: FamilyBinding | None, policy: Policy | None = None, symbol: str = "M&M"
) -> list[Any]:
    return list(
        run(
            [dict(symbol=symbol, exchange="NSE", enabled="TRUE")],
            registry(),
            (standard_window(END, 6),),
            lambda s, m: None,
            Settings(),
            tariff(),
            path,
            policy,
            family=family,
        )
    )


@pytest.mark.parametrize("name", DEPENDENCIES)
def test_each_dependency_change_denies_resume(
    name: str, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    family = binding()
    first = collect(tmp_path / "job", family)
    assert len(first) == 6 and collect(tmp_path / "job", family) == first
    copied = tmp_path / "source"
    for rel in DEPENDENCIES:
        target = copied / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((SOURCE / rel).read_bytes())
    with (copied / name).open("ab") as stream:
        stream.write(b"\n# disposable dependency change\n")
    changed = dependencies(copied)
    assert validate_dependencies(changed) != family.software_hash
    monkeypatch.setattr(hardening, "dependencies", lambda: changed)
    with pytest.raises(ValueError, match="BINDING_CONFLICT"):
        collect(tmp_path / "job", family)
    # Even rebuilding binding cannot reuse old configuration.
    with pytest.raises(ValueError, match="CHECKPOINT_CONFIGURATION_CONFLICT"):
        collect(tmp_path / "job", binding())


@pytest.mark.parametrize("fault", ["missing", "duplicate", "unexpected", "order"])
def test_dependency_inventory_rejects(fault: str) -> None:
    items = dependencies()
    if fault == "missing":
        items = items[:-1]
    elif fault == "duplicate":
        items = (*items, items[0])
    elif fault == "unexpected":
        items = (*items[:-1], items[-1].model_copy(update={"path": "extra.py"}))
    else:
        items = tuple(reversed(items))
    with pytest.raises(ValueError, match="DEPENDENCY_INVENTORY"):
        validate_dependencies(items)


def test_missing_source_and_unregistered_import(tmp_path: Path) -> None:
    for name in DEPENDENCIES:
        target = tmp_path / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes((SOURCE / name).read_bytes())
    victim = tmp_path / "strategy_engine/backtest/costs.py"
    victim.rename(victim.with_suffix(".missing"))
    with pytest.raises(ValueError, match="DEPENDENCY_UNAVAILABLE"):
        dependencies(tmp_path)
    victim.write_text("from strategy_engine.unknown import thing\n")
    with pytest.raises(ValueError, match="UNREGISTERED_LOCAL"):
        dependencies(tmp_path)


@pytest.mark.parametrize(
    "field,value",
    [
        ("family_size", 6),
        ("family_size", 2230),
        ("min_oos_trades", 30),
        ("min_fold_trades", 10),
        ("min_fold_win_rate", D("0.5")),
        ("max_drawdown", D("0.2")),
        ("alpha", D("0.1")),
    ],
)
def test_subset_cannot_weaken_full_family(field: str, value: Any, tmp_path: Path) -> None:
    policy = Policy.model_validate(frozen_policy().model_dump() | {field: value})
    with pytest.raises(ValueError, match="FROZEN_FULL_FAMILY"):
        collect(tmp_path, binding(), policy)


@pytest.mark.parametrize(
    "field,value",
    [
        ("schema_version", "Phase1415Checkpoint.v1"),
        ("universe_hash", "0" * 64),
        ("registry_hash", "0" * 64),
        ("policy_hash", "0" * 64),
        ("software_hash", "0" * 64),
        ("hypotheses", 6),
    ],
)
def test_family_identity_denial(field: str, value: Any, tmp_path: Path) -> None:
    altered = FamilyBinding.model_validate(binding().model_dump() | {field: value})
    with pytest.raises(ValueError):
        collect(tmp_path, altered)


def test_full_family_and_partitions(tmp_path: Path) -> None:
    family = binding()
    assert family_size() == family.hypotheses == 13380
    assert family.run_id == binding().run_id
    assert len(partition_keys(family.symbols, registry(), family.windows, family)) == 13380
    for symbol in ("M&M", family.symbols[0]):
        rows = collect(tmp_path / symbol, family, frozen_policy(), symbol)
        assert all(r.family_run_id == family.run_id and not r.qualified for r in rows)
        keys = partition_keys((symbol,), registry(), (family.windows[0],), family)
        result = export(
            iter(rows), tmp_path / (symbol + "-export"), family=family, expected_keys=keys
        )
        assert result["scope"] == "PARTITION" and result["rows"] == 6
        assert result["run_id"] == family.run_id
    with pytest.raises(ValueError, match="BINDING_REQUIRED"):
        collect(tmp_path / "missing", None)
    with pytest.raises(ValueError, match="UNIVERSE_CONFLICT"):
        collect(tmp_path / "foreign", family, symbol="UNLISTED_SYNTHETIC")


@pytest.mark.parametrize("fault", ["missing", "duplicate", "unbound"])
def test_incomplete_partition_export(fault: str, tmp_path: Path) -> None:
    family = binding()
    rows = collect(tmp_path / "job", family)
    keys = partition_keys(("M&M",), registry(), (family.windows[0],), family)
    if fault == "missing":
        rows = rows[:-1]
    elif fault == "duplicate":
        rows[-1] = rows[0]
    else:
        rows[0] = rows[0].model_copy(update={"family_run_id": None})
    with pytest.raises(ValueError):
        export(iter(rows), tmp_path / "export", family=family, expected_keys=keys)


@pytest.mark.parametrize("fault", ["schema", "strategy", "window", "universe"])
def test_stale_checkpoint_configuration(fault: str, tmp_path: Path) -> None:
    family = binding()
    collect(tmp_path, family)
    p = tmp_path / "configuration.json"
    config = json.loads(p.read_bytes())
    if fault == "schema":
        config["schema"] = "v1"
    elif fault == "strategy":
        config["definitions"][0]["stop"] = "0.01"
    elif fault == "window":
        config["windows"][0]["end"] = "2020-01-01T00:00:00Z"
    else:
        config["universe"][0]["symbol"] = "OTHER"
    p.write_text(json.dumps(config, sort_keys=True, separators=(",", ":")))
    with pytest.raises(ValueError, match="CONFIGURATION_CONFLICT"):
        collect(tmp_path, family)


@pytest.mark.parametrize(
    "reason,op,hi,lo,close,expected",
    [
        ("TARGET", 102, 105, 101, 104, "104.04"),
        ("TARGET", 110, 111, 109, 110, "104.04"),
        ("STOP", 102, 103, 99, 100, "99.96"),
        ("STOP", 90, 91, 89, 90, "90"),
        ("STOP", 102, 110, 90, 102, "99.96"),
        ("EOD", 102, 103, 101, 102, "102"),
    ],
)
def test_exact_execution_exits(
    reason: str, op: int, hi: int, lo: int, close: int, expected: str
) -> None:
    data = prices((100, 100, 100, 102, 102, 102))
    bars = list(data.bars)
    bars[5] = Candle(
        start=bars[5].start,
        open=D(op),
        high=D(hi),
        low=D(lo),
        close=D(close),
        volume=10000,
        open_interest=None,
    )
    data = Series.model_validate(data.model_dump() | {"bars": tuple(bars)})
    (trade,) = execute(data, registry()[5], Settings(slippage_bps=D(0)), tariff()).trades
    assert (trade.entry, trade.entry_at, trade.signal_at) == (
        D(102),
        START + 4 * FIVE,
        START + 4 * FIVE,
    )
    assert (trade.exit, trade.exit_at, trade.reason) == (D(expected), START + 6 * FIVE, reason)
    assert trade.net == trade.gross - trade.fees


def test_cutoff_and_no_next_candle() -> None:
    for values in ((100, 100, 100, 102), (100, 100, 100, 102, 102)):
        data = prices(values)
        result = execute(data, registry()[5], Settings(), tariff())
        assert result.trades == ()
        if len(values) == 5:
            assert result.no_fills == (
                f"{data.bars[-1].start.isoformat()}:ENTRY_LIMIT_OR_LIQUIDITY",
            )


@pytest.mark.parametrize(
    "field,value",
    [("allow_long", False), ("capital", D(1)), ("order_ceiling", D(1)), ("quantity", 1000)],
)
def test_entry_limits(field: str, value: Any) -> None:
    data = prices((100, 100, 100, 102, 102, 102))
    result = execute(
        data,
        registry()[5],
        Settings.model_validate(Settings().model_dump() | {field: value}),
        tariff(),
    )
    assert not result.trades
    assert result.no_fills == (f"{(START + 4 * FIVE).isoformat()}:ENTRY_LIMIT_OR_LIQUIDITY",)


@pytest.mark.parametrize("fault", ["missing", "open", "ohlc", "volume", "duplicate"])
def test_invalid_entry_data(fault: str) -> None:
    data = prices((100, 100, 100, 102, 102, 102))
    values = data.model_dump()
    if fault == "missing":
        values["bars"] = values["bars"][:4] + values["bars"][5:]
    elif fault == "duplicate":
        bars = list(values["bars"])
        bars[4] = bars[3]
        values["bars"] = tuple(bars)
    else:
        index = {"open": "open", "ohlc": "high", "volume": "volume"}[fault]
        values["bars"][4][index] = None if fault == "open" else -1
    with pytest.raises(ValueError):
        execute(Series.model_validate(values), registry()[5], Settings(), tariff())


@pytest.mark.parametrize("value", ["-1", "1001", "NaN"])
def test_invalid_slippage(value: str) -> None:
    with pytest.raises(ValueError):
        Settings(slippage_bps=D(value))


def test_invalid_cost_and_quarantine() -> None:
    with pytest.raises(ValueError):
        type(tariff()).model_validate(tariff().model_dump() | {"brokerage_rate": "2"})
    data = window_data().model_copy(update={"quarantined": True})
    result = evaluate(
        data.series.symbol,
        registry()[5],
        standard_window(END, 6),
        data,
        Settings(),
        tariff(),
        frozen_policy(),
    )
    assert result.data_status == "QUARANTINED" and result.total is None and result.trades == ()


def test_duplicate_signal_open_position_and_completed_prefix(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    data = prices((100,) * 10)
    observed = []

    def repeated(bars: tuple[Candle, ...], definition: Any) -> bool:
        observed.append(tuple(b.start for b in bars))
        return True

    monkeypatch.setattr("strategy_engine.phase1415.execution.entry_signal", repeated)
    result = execute(data, registry()[1], Settings(slippage_bps=D(0)), tariff())
    (trade,) = result.trades
    assert (trade.entry_at, trade.entry, trade.exit_at, trade.reason) == (
        START + FIVE,
        D(100),
        data.to_exclusive,
        "EOD",
    )
    assert observed == [(START,)]  # No duplicate signals while the position is open.


def test_executor_prefix_invariance() -> None:
    a = prices((100, 100, 100, 102, 102, 105, 100, 100, 100))
    b = prices((100, 100, 100, 102, 102, 105, 80, 120, 90))
    ta = execute(a, registry()[5], Settings(slippage_bps=D(0)), tariff()).trades[0]
    tb = execute(b, registry()[5], Settings(slippage_bps=D(0)), tariff()).trades[0]
    assert ta == tb
    assert ta.entry_at == START + 4 * FIVE and ta.exit_at == START + 6 * FIVE
    assert ta.reason == "TARGET" and ta.exit == D("104.04")
    # Shorter executor prefix keeps the earlier completed trade identical.
    prefix = prices((100, 100, 100, 102, 102, 105, 100))
    assert execute(prefix, registry()[5], Settings(slippage_bps=D(0)), tariff()).trades[0] == ta


@pytest.mark.parametrize("months", [6, 12])
@pytest.mark.parametrize("field", ["end", "validation_start", "holdout_start", "folds"])
def test_all_ist_boundaries(months: int, field: str) -> None:
    w = standard_window(END, months)  # type: ignore[arg-type]
    values = w.model_dump()
    if field == "folds":
        values[field] = (w.folds[0], w.folds[1] + FIVE, *w.folds[2:])
    else:
        values[field] += FIVE
    with pytest.raises(ValueError, match="IST_MIDNIGHT"):
        ResearchWindow.model_validate(values)
    assert w.end == datetime(2026, 9, 30, 18, 30, tzinfo=UTC)
    assert w.start.astimezone(NSE) == datetime(
        2026 if months == 6 else 2025, 4 if months == 6 else 10, 1, tzinfo=NSE
    )


def test_holiday_special_session_and_outside_warmup() -> None:
    data = prices((100, 100, 100, 102, 102, 102))
    with pytest.raises(ValueError, match="CALENDAR"):
        Series.model_validate(
            data.model_dump() | {"non_trading_days": (START.astimezone(NSE).date(),)}
        )
    # Holiday preceding an explicit session is allowed; no fabricated bars.
    holiday = START.astimezone(NSE).date() - timedelta(days=1)
    extended = Series.model_validate(
        data.model_dump()
        | {"from_inclusive": START - timedelta(days=1), "non_trading_days": (holiday,)}
    )
    assert (
        execute(extended, registry()[5], Settings(), tariff()).trades
        == execute(data, registry()[5], Settings(), tariff()).trades
    )
    with pytest.raises(ValueError, match="BAR_OUTSIDE_WINDOW"):
        Series.model_validate(
            data.model_dump()
            | {
                "from_inclusive": START + FIVE,
                "sessions": (Session(start=START + FIVE, end=data.to_exclusive),),
            }
        )
    shifted = Series.model_validate(
        data.model_dump()
        | {"bars": data.bars[1:], "sessions": (Session(start=START + FIVE, end=data.to_exclusive),)}
    )
    with pytest.raises(ValueError, match="OPENING_RANGE"):
        execute(shifted, registry()[5], Settings(), tariff())
    for d in registry()[:4]:
        assert execute(data, d, Settings(), tariff()).trades == ()
        assert not entry_signal(data.bars, d)


def split_data(holdout_price: int) -> Dataset:
    original = window_data()
    w = standard_window(END, 6)
    sessions: list[Session] = []
    bars: list[Candle] = []
    for day, values in (
        (w.start, (100, 100, 100, 102, 102, 102)),
        (w.validation_start, (100, 100, 100, 102, 102, 102)),
        (w.holdout_start, (100, 100, 100, holdout_price, holdout_price, holdout_price)),
    ):
        at = day + timedelta(hours=9, minutes=15)
        source = prices(values)
        chunk = tuple(
            b.model_copy(update={"start": at + i * FIVE}) for i, b in enumerate(source.bars)
        )
        bars.extend(chunk)
        sessions.append(Session(start=at, end=chunk[-1].end))
    active = {s.start.astimezone(NSE).date() for s in sessions}
    closed = tuple(
        (w.start + timedelta(days=i)).astimezone(NSE).date()
        for i in range((w.end - w.start).days)
        if (w.start + timedelta(days=i)).astimezone(NSE).date() not in active
    )
    series = Series.model_validate(
        original.series.model_dump()
        | {"bars": tuple(bars), "sessions": tuple(sessions), "non_trading_days": closed}
    )
    return Dataset.model_validate(
        original.model_dump()
        | {"series": series, "dataset_sha256": digest(series.model_dump_json())}
    )


def test_walk_forward_training_does_not_consume_holdout() -> None:
    w = standard_window(END, 6)
    a = split_data(102)
    b = split_data(90)
    ra = evaluate(a.series.symbol, registry()[5], w, a, Settings(), tariff(), frozen_policy())
    rb = evaluate(b.series.symbol, registry()[5], w, b, Settings(), tariff(), frozen_policy())
    assert ra.training == rb.training and ra.validation == rb.validation
    assert ra.training and ra.training.basic.trades == 1
    assert ra.trades[:2] == rb.trades[:2]
    assert ra.trades[0].entry_at == w.start + timedelta(hours=9, minutes=35)
    # Six bars in each session cannot borrow warmup from the other partitions.
    for d in registry()[:4]:
        result = evaluate(a.series.symbol, d, w, a, Settings(), tariff(), frozen_policy())
        assert result.trades == () and result.holdout and result.holdout.basic.trades == 0


def receipt(tmp_path: Path) -> dict[str, Any]:
    xml = (
        b'<testsuites><testsuite tests="1" failures="0" errors="0" skipped="0">'
        b'<testcase classname="synthetic" name="ok"/></testsuite></testsuites>'
    )
    path = tmp_path / "receipt.xml"
    path.write_bytes(xml)
    command = tmp_path / "command.json"
    record = dict(
        id="focused",
        scope="FRESH",
        path=str(path),
        sha256=hashlib.sha256(xml).hexdigest(),
        tests=1,
        failures=0,
        errors=0,
        skipped=0,
    )
    command.write_text(
        json.dumps(
            dict(
                name="focused",
                command=["python", "-B", "-m", "pytest"],
                exit_code=0,
                xml_sha256=record["sha256"],
            )
        )
    )
    record.update(
        command_receipt=str(command),
        command_sha256=hashlib.sha256(command.read_bytes()).hexdigest(),
    )
    return record


def test_receipt_positive(tmp_path: Path) -> None:
    record = receipt(tmp_path)
    assert RECEIPTS.verify_receipts([record], ["focused"], scope="FRESH", require_command=True) == [
        dict(id="focused", tests=1, status="PASS")
    ]


@pytest.mark.parametrize(
    "fault",
    [
        "missing",
        "hash",
        "malformed",
        "zero",
        "incomplete",
        "failure",
        "error",
        "skip",
        "count",
        "command_missing",
        "exit",
        "duplicate",
        "scope",
        "identity",
    ],
)
def test_receipt_negative(fault: str, tmp_path: Path) -> None:
    record = receipt(tmp_path)
    records = [record]
    path = Path(record["path"])
    if fault == "missing":
        record["path"] = str(tmp_path / "absent.xml")
    elif fault == "hash":
        record["sha256"] = "0" * 64
    elif fault in {"malformed", "zero", "incomplete", "failure", "error", "skip"}:
        xml = path.read_text()
        if fault == "malformed":
            xml = "<broken"
        elif fault == "zero":
            xml = '<testsuite tests="0" failures="0" errors="0" skipped="0"/>'
        elif fault == "incomplete":
            xml = xml.replace('tests="1"', 'tests="2"')
        else:
            tag, attr = {
                "failure": ("failure", "failures"),
                "error": ("error", "errors"),
                "skip": ("skipped", "skipped"),
            }[fault]
            xml = xml.replace(f'{attr}="0"', f'{attr}="1"').replace(
                'name="ok"/>', f'name="ok"><{tag}/></testcase>'
            )
            record[attr] = 1
        path.write_text(xml)
        record["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
    elif fault == "count":
        record["tests"] = 2
    elif fault == "command_missing":
        record.pop("command_receipt")
    elif fault == "exit":
        command = Path(record["command_receipt"])
        value = json.loads(command.read_bytes())
        value["exit_code"] = 1
        command.write_text(json.dumps(value))
        record["command_sha256"] = hashlib.sha256(command.read_bytes()).hexdigest()
    elif fault == "duplicate":
        records.append(record.copy())
    elif fault == "scope":
        record["scope"] = "HISTORICAL"
    else:
        record["id"] = "different"
    with pytest.raises(ValueError):
        RECEIPTS.verify_receipts(records, ["focused"], scope="FRESH", require_command=True)


@pytest.mark.parametrize("fault", ["strategy", "window", "duplicate_window"])
def test_partition_definition_mismatch(fault: str) -> None:
    family = binding()
    definitions = registry()
    windows = family.windows
    if fault == "strategy":
        definitions = (definitions[0].model_copy(update={"stop": D("0.01")}),)
    elif fault == "window":
        windows = (standard_window(END - timedelta(days=1), 6),)
    else:
        windows = (family.windows[0], family.windows[0])
    with pytest.raises(ValueError, match="PARTITION"):
        partition_keys(("M&M",), definitions, windows, family)


def test_special_session_and_nontrading_window_boundary() -> None:
    sunday = datetime(2026, 10, 4, tzinfo=NSE).astimezone(UTC)
    window = standard_window(sunday, 6)
    assert window.end.astimezone(NSE).weekday() == 6
    data = prices((100,) * 30)
    shifted = Series.model_validate(
        data.model_dump()
        | {
            "bars": data.bars[1:],
            "sessions": (Session(start=START + FIVE, end=data.to_exclusive),),
        }
    )
    # Explicit dated special sessions are supported by close-only strategies, not OR15.
    result = execute(shifted, registry()[1], Settings(), tariff())
    assert result.trades == () and result.exposure_seconds == 0


def test_receipt_missing_exit_and_inconsistent_identifier(tmp_path: Path) -> None:
    record = receipt(tmp_path)
    command = Path(record["command_receipt"])
    value = json.loads(command.read_bytes())
    value.pop("exit_code")
    command.write_text(json.dumps(value))
    record["command_sha256"] = hashlib.sha256(command.read_bytes()).hexdigest()
    with pytest.raises(ValueError, match="COMMAND_OR_EXIT"):
        RECEIPTS.verify_receipts([record], ["focused"], scope="FRESH", require_command=True)
