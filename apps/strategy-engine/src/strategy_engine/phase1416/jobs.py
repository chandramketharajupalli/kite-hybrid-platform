"""Single-worker offline family jobs with content-bound atomic symbol checkpoints."""

import csv
import os
import time
from datetime import datetime
from pathlib import Path
from typing import Any

from pydantic import Field

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import Frozen, Hash, Instant, digest
from strategy_engine.phase14.engine import Settings
from strategy_engine.phase1415.hardening import (
    ROOT,
    FamilyBinding,
    bind_result,
    dependencies,
    family_binding,
    frozen_policy,
    verify_family,
)
from strategy_engine.phase1415.research import Result, evaluate
from strategy_engine.phase1415.strategies import registry
from strategy_engine.phase1416.imports import (
    Imported,
    Input,
    canonical,
    external,
    publish,
    read_json,
    sha,
)

LOCAL = tuple(
    "apps/strategy-engine/src/strategy_engine/phase1416/" + p
    for p in ("__init__.py", "imports.py", "jobs.py")
)


def software() -> dict[str, str]:
    actual = {p.name for p in (ROOT / Path(LOCAL[0]).parent).glob("*.py")}
    if actual != {Path(p).name for p in LOCAL}:
        raise ValueError("SOFTWARE_INVENTORY_MISMATCH")
    inventory = {"apps/strategy-engine/src/" + d.path: d.sha256 for d in dependencies()}
    inventory.update({p: sha((ROOT / p).read_bytes()) for p in LOCAL})
    return dict(sorted(inventory.items()))


class Selection(Frozen):
    symbol: str
    months: int = Field(ge=6, le=12)
    record: Input


class Job(Frozen):
    schema_version: str = "Phase1416Job.v1"
    family: FamilyBinding
    datasets: tuple[Selection, ...] = ()
    software_inventory: dict[str, Hash]
    settings: Settings
    costs: IntradayCostSchedule
    max_seconds: int = Field(default=3600, ge=1, le=3600)
    workers: int = Field(default=1, ge=1, le=1)
    as_of: Instant

    @property
    def run_id(self) -> str:
        return digest(self.model_dump_json())


def plan(
    end: datetime,
    settings: Settings,
    costs: IntradayCostSchedule,
    datasets: tuple[Selection, ...],
    *,
    as_of: datetime,
) -> Job:
    return Job(
        family=family_binding(end, settings, costs),
        datasets=datasets,
        software_inventory=software(),
        settings=settings,
        costs=costs,
        as_of=as_of,
    )


def validate(job: Job, now: datetime) -> dict[tuple[str, int], Selection]:
    if job.schema_version != "Phase1416Job.v1" or job.software_inventory != software():
        raise ValueError("REPLAY_REJECTED:SOFTWARE_OR_SCHEMA")
    verify_family(job.family, job.settings, job.costs, frozen_policy())
    if now < job.as_of or job.as_of < job.family.windows[-1].end:
        raise ValueError("INVALID_JOB_TIME")
    selected: dict[tuple[str, int], Selection] = {}
    classes: set[str] = set()
    for item in job.datasets:
        key = item.symbol, item.months
        if key in selected or item.symbol not in job.family.symbols or item.months not in (6, 12):
            raise ValueError("REPLAY_REJECTED:DATASET_INVENTORY")
        imported = load(item, job, now)
        if imported.status == "READY":
            classes.add(imported.source_class)
        selected[key] = item
    if len(classes) > 1:
        raise ValueError("SYNTHETIC_AND_GENUINE_RUNS_MUST_BE_SEPARATE")
    return selected


def load(item: Selection, job: Job, now: datetime) -> Imported:
    raw, value = read_json(Path(item.record.path), 32 * 1024 * 1024)
    if sha(raw) != item.record.sha256:
        raise ValueError("REPLAY_REJECTED:DATASET_FINGERPRINT")
    data = Imported.model_validate(value)
    window = next(w for w in job.family.windows if w.months == item.months)
    if (data.symbol, data.start, data.end) != (item.symbol, window.start, window.end):
        raise ValueError("REPLAY_REJECTED:DATASET_IDENTITY")
    if now >= data.retention_until:
        raise ValueError("RETENTION_EXPIRED")
    if data.status == "READY":
        if data.dataset is None or data.reasons or data.source_class == "UNVERIFIED_LOCAL_DATA":
            raise ValueError("INCONSISTENT_ACCEPTANCE")
        if data.dataset.series.synthetic != (data.source_class == "SYNTHETIC_FIXTURE"):
            raise ValueError("PROVENANCE_CONFLICT")
    elif data.dataset is not None:
        raise ValueError("QUARANTINE_CONTAINS_PERFORMANCE_INPUT")
    return data


def checkpoint_rows(path: Path, job: Job, symbol: str) -> list[Result]:
    _, value = read_json(path, 32 * 1024 * 1024)
    if value["run_id"] != job.run_id or value["sha256"] != sha(canonical(value["rows"])):
        raise ValueError("REPLAY_REJECTED:CHECKPOINT_HASH")
    rows = [Result.model_validate(r) for r in value["rows"]]
    expected = [(symbol, w.months, d.identifier) for w in job.family.windows for d in registry()]
    if [(r.symbol, r.window.months, r.strategy_id) for r in rows] != expected:
        raise ValueError("REPLAY_REJECTED:INCOMPLETE_PARTITION")
    if any(bind_result(r, job.family) != r for r in rows):
        raise ValueError("REPLAY_REJECTED:POLICY_BINDING")
    return rows


def execute(job: Job, directory: Path, *, now: datetime) -> dict[str, Any]:
    directory = external(directory)
    selected = validate(job, now)
    directory.mkdir(parents=True, exist_ok=True)
    lock = directory / "run.lock"
    fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    os.close(fd)
    started = time.monotonic()
    try:
        publish(directory / "job-plan.json", job.model_dump_json().encode())
        used = sum(p.stat().st_size for p in directory.rglob("*") if p.is_file())
        if used > 256 * 1024 * 1024:
            raise ValueError("JOB_DISK_LIMIT")
        for symbol in job.family.symbols:
            if time.monotonic() - started > job.max_seconds:
                raise ValueError("EXECUTION_TIME_LIMIT")
            path = directory / "checkpoints" / (digest(symbol) + ".json")
            if path.exists():
                checkpoint_rows(path, job, symbol)
                continue
            rows: list[Result] = []
            states = ["PLANNED"]
            for window in job.family.windows:
                item = selected.get((symbol, window.months))
                data = load(item, job, now) if item else None
                states.append(data.status if data else "DATA_UNAVAILABLE")
                if data and data.status == "READY":
                    states.append("RUNNING")
                for definition in registry():
                    result = evaluate(
                        symbol,
                        definition,
                        window,
                        data.dataset if data else None,
                        job.settings,
                        job.costs,
                        frozen_policy(),
                    )
                    if data and data.status == "DATA_QUARANTINED":
                        result = result.model_copy(
                            update=dict(data_status="QUARANTINED", reasons=data.reasons)
                        )
                    rows.append(bind_result(result, job.family))
            values = [r.model_dump(mode="json") for r in rows]
            payload = canonical(
                dict(
                    run_id=job.run_id,
                    rows=values,
                    sha256=sha(canonical(values)),
                    states=states + ["CHECKPOINTED"],
                )
            )
            used += len(payload)
            # Reserve half the budget for atomic report publication and diagnostics.
            if len(payload) > 32 * 1024 * 1024 or used > 128 * 1024 * 1024:
                raise ValueError("JOB_DISK_LIMIT")
            publish(path, payload)
        return report(job, directory, selected, now)
    except Exception as exc:
        # Additive diagnostic, never a completion marker. Preserve earlier failures.
        failure = dict(
            state="REPLAY_REJECTED" if "REPLAY_REJECTED" in str(exc) else "FAILED",
            error=type(exc).__name__,
            reason=str(exc)[:200],
        )
        publish(directory / ("failure-" + sha(canonical(failure)) + ".json"), canonical(failure))
        raise
    finally:
        lock.unlink()


def report(
    job: Job, directory: Path, selected: dict[tuple[str, int], Selection], now: datetime
) -> dict[str, Any]:
    """Publish full-family reports only after verifying every symbol checkpoint."""
    completed = directory / "run-summary.json"
    if completed.exists():
        _, summary = read_json(completed)
        if summary["run_id"] != job.run_id or summary["rows"] != 13380:
            raise ValueError("REPLAY_REJECTED:COMPLETION")
        for name, expected in summary["reports"].items():
            if sha((directory / name).read_bytes()) != expected:
                raise ValueError("REPLAY_REJECTED:REPORT")
        return dict(summary)
    output = directory / "reports.pending"
    output.mkdir()  # Interrupted report publication requires explicit operator review.
    fields = [
        "symbol",
        "strategy",
        "months",
        "data_status",
        "trade_count",
        "wins",
        "losses",
        "breakevens",
        "win_rate",
        "gross",
        "net",
        "costs",
        "profit_factor",
        "drawdown",
        "average_holding_seconds",
        "coverage",
        "out_of_sample",
        "qualified",
        "reasons",
        "dataset_hash",
        "run_id",
    ]
    counts: dict[str, int] = {}
    qualified = 0
    with (
        (output / "stock-strategy-results.csv").open("x", encoding="utf8", newline="") as c,
        (output / "stock-strategy-results.json").open("x", encoding="utf8", newline="\n") as j,
        (output / "rejection-report.jsonl").open("x", encoding="utf8", newline="\n") as rejected,
    ):
        writer = csv.DictWriter(c, fields, lineterminator="\n")
        writer.writeheader()
        j.write("[")
        number = 0
        for symbol in job.family.symbols:
            rows = checkpoint_rows(
                directory / "checkpoints" / (digest(symbol) + ".json"), job, symbol
            )
            for r in rows:
                q = r.holdout
                basic = q.basic if q else None
                row = dict(
                    symbol=symbol,
                    strategy=r.strategy_id,
                    months=r.window.months,
                    data_status=r.data_status,
                    trade_count=basic.trades if basic else None,
                    wins=basic.wins if basic else None,
                    losses=basic.losses if basic else None,
                    breakevens=basic.breakeven if basic else None,
                    win_rate=str(basic.win_rate) if basic else None,
                    gross=str(q.gross) if q else None,
                    net=str(basic.net) if basic else None,
                    costs=str(basic.costs) if basic else None,
                    profit_factor=str(q.profit_factor) if q and q.profit_factor else None,
                    drawdown=str(basic.drawdown) if basic else None,
                    average_holding_seconds=str(q.average_holding_seconds)
                    if q and q.average_holding_seconds is not None
                    else None,
                    coverage=str(r.coverage) if r.coverage is not None else None,
                    out_of_sample=bool(q),
                    qualified=r.qualified,
                    reasons=list(r.reasons),
                    dataset_hash=r.dataset_hash,
                    run_id=job.run_id,
                )
                writer.writerow(row)
                j.write(("," if number else "") + canonical(row).decode())
                if not r.qualified:
                    rejected.write(canonical(row).decode() + "\n")
                qualified += int(r.qualified)
                counts[r.data_status] = counts.get(r.data_status, 0) + 1
                number += 1
        j.write("]\n")
    ledger = []
    for item in selected.values():
        data = load(item, job, now)
        ledger.append(data.model_dump(mode="json", exclude={"dataset"}))
    for name, value in {
        "import-ledger.json": ledger,
        "dataset-quality-report.json": ledger,
        "coverage-report.json": dict(
            symbols=1115, windows={"6": 6690, "12": 6690}, statuses=counts
        ),
        "qualification-report.json": dict(
            qualified=qualified, family_size=13380, policy_hash=job.family.policy_hash
        ),
        "checkpoint-ledger.json": dict(
            run_id=job.run_id,
            symbols=list(job.family.symbols),
            partitions=1115,
            state="CHECKPOINTED",
        ),
    }.items():
        publish(output / name, canonical(value))
    hashes = {p.name: sha(p.read_bytes()) for p in sorted(output.iterdir())}
    for p in sorted(output.iterdir()):
        publish(directory / p.name, p.read_bytes())
    summary = dict(
        state="COMPLETED",
        run_id=job.run_id,
        rows=number,
        symbols=1115,
        windows={"6": 6690, "12": 6690},
        statuses=counts,
        qualified=qualified,
        reports=hashes,
        software_hash=sha(canonical(job.software_inventory)),
        family_run_id=job.family.run_id,
        policy_hash=job.family.policy_hash,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
    )
    publish(completed, canonical(summary))
    return summary
