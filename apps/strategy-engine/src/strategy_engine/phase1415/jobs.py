"""Bounded sequential offline jobs with deterministic, verified per-pair checkpoints."""

import csv
import hashlib
import json
import os
from collections.abc import Callable, Generator, Iterator
from pathlib import Path

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import digest
from strategy_engine.phase14.engine import Policy, Settings
from strategy_engine.phase14.universe import normalize
from strategy_engine.phase1415.hardening import (
    SCHEMA,
    FamilyBinding,
    bind_result,
    frozen_policy,
    partition_keys,
    verify_family,
    verify_identity,
)
from strategy_engine.phase1415.research import Dataset, ResearchWindow, Result, evaluate
from strategy_engine.phase1415.strategies import Definition, registry, validate_registry

Loader = Callable[[str, int], Dataset | None]


def run(
    universe: list[dict[str, str]],
    definitions: tuple[Definition, ...],
    windows: tuple[ResearchWindow, ...],
    loader: Loader,
    settings: Settings,
    costs: IntradayCostSchedule,
    directory: Path,
    policy: Policy | None = None,
    *,
    family: FamilyBinding | None = None,
) -> Generator[Result, None, None]:
    """One worker, one series at a time. Trusted local checkpoint custody, not anti-rollback."""
    members, _ = normalize(universe)
    validate_registry(definitions)
    if not windows or len({w.months for w in windows}) != len(windows):
        raise ValueError("DUPLICATE_OR_EMPTY_WINDOWS")
    policy = frozen_policy(policy)
    family = verify_family(family, settings, costs, policy)
    expected_keys = partition_keys(
        tuple(m["symbol"] for m in members), definitions, windows, family
    )
    config = json.dumps(
        dict(
            engine="phase1415-session-executor-v1",
            schema=SCHEMA,
            family=family.model_dump(mode="json"),
            run_id=family.run_id,
            software_hash=family.software_hash,
            dependency_inventory=[d.model_dump() for d in family.dependency_inventory],
            expected_keys=expected_keys,
            universe=members,
            definitions=[d.model_dump(mode="json") for d in definitions],
            windows=[w.model_dump(mode="json") for w in windows],
            settings=settings.model_dump(mode="json"),
            costs=costs.model_dump(mode="json"),
            policy=policy.model_dump(mode="json"),
        ),
        sort_keys=True,
        separators=(",", ":"),
    )
    directory.mkdir(parents=True, exist_ok=True)
    lock = directory / "job.lock"
    # Crash leaves a stale lock: operator must inspect and explicitly remove OWNED lock.
    fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    os.close(fd)
    try:
        identity = directory / "configuration.json"
        if identity.exists():
            if identity.read_text(encoding="utf-8") != config:
                raise ValueError("CHECKPOINT_CONFIGURATION_CONFLICT")
        else:
            with identity.open("x", encoding="utf-8", newline="\n") as stream:
                stream.write(config)
                stream.flush()
                os.fsync(stream.fileno())
        for member in members:
            symbol = member["symbol"]
            for window in windows:
                dataset = loader(symbol, window.months)
                source = dataset.model_dump_json() if dataset else "UNAVAILABLE"
                for definition in definitions:
                    key = digest(
                        config + symbol + window.model_dump_json() + definition.fingerprint
                    )
                    path = directory / (key + ".json")
                    binding = digest(source)
                    if path.exists():
                        record = json.loads(path.read_bytes())
                        if (
                            record["input"] != binding
                            or digest(record["result"]) != record["sha256"]
                        ):
                            raise ValueError("CHECKPOINT_DATA_OR_CONTENT_CONFLICT")
                        result = Result.model_validate_json(record["result"])
                        if (
                            result.family_run_id != family.run_id
                            or result.hypothesis_family_size != family.hypotheses
                            or result.policy_hash != family.policy_hash
                            or result.strategy_id != definition.identifier
                            or result.strategy_version != definition.version
                            or result.dataset_hash != (dataset.dataset_sha256 if dataset else None)
                            or result.symbol != symbol
                            or result.strategy_hash != definition.fingerprint
                            or result.window != window
                        ):
                            raise ValueError("CHECKPOINT_IDENTITY_CONFLICT")
                    else:
                        result = evaluate(
                            symbol, definition, window, dataset, settings, costs, policy
                        )
                        result = bind_result(result, family)
                        payload = result.model_dump_json()
                        encoded = json.dumps(
                            dict(input=binding, result=payload, sha256=digest(payload)),
                            sort_keys=True,
                            separators=(",", ":"),
                        )
                        temporary = path.with_suffix(".pending")
                        with temporary.open("x", encoding="utf-8", newline="\n") as stream:
                            stream.write(encoded)
                            stream.flush()
                            os.fsync(stream.fileno())
                        os.replace(temporary, path)
                    yield result
    finally:
        lock.unlink()


def export(
    results: Iterator[Result],
    directory: Path,
    *,
    family: FamilyBinding,
    expected_keys: tuple[tuple[str, str, int], ...],
) -> dict[str, object]:
    """CSV and JSONL stream; JSONL retains all metrics/trades/folds; absent metrics are null."""
    verify_identity(family)
    if not expected_keys or len(set(expected_keys)) != len(expected_keys):
        raise ValueError("INVALID_PARTITION_INVENTORY")
    full_keys = {
        (s, d.identifier, w.months)
        for s in family.symbols
        for w in family.windows
        for d in registry()
    }
    if not set(expected_keys) <= full_keys:
        raise ValueError("PARTITION_OUTSIDE_FAMILY")
    expected_set = set(expected_keys)
    seen: set[tuple[str, str, int]] = set()
    directory.mkdir(parents=True, exist_ok=True)
    fields = [
        "exchange",
        "symbol",
        "strategy_id",
        "strategy_version",
        "months",
        "data_status",
        "trades",
        "win_rate",
        "net",
        "drawdown",
        "qualified",
        "reasons",
        "strategy_hash",
        "dataset_hash",
    ]
    count = 0
    windows: dict[int, int] = {}
    with (
        (directory / "pairs.csv").open("x", encoding="utf-8", newline="") as c,
        (directory / "pairs.jsonl").open("x", encoding="utf-8", newline="\n") as j,
    ):
        writer = csv.DictWriter(c, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        for result in results:
            key = (result.symbol, result.strategy_id, result.window.months)
            if key not in expected_set or key in seen:
                raise ValueError("DUPLICATE_OR_UNEXPECTED_PARTITION_RESULT")
            if (
                result.family_run_id != family.run_id
                or result.hypothesis_family_size != family.hypotheses
                or bind_result(result, family) != result
            ):
                raise ValueError("UNBOUND_PARTITION_RESULT")
            seen.add(key)
            q = result.holdout.basic if result.holdout else None
            writer.writerow(
                dict(
                    exchange=result.exchange,
                    symbol=result.symbol,
                    strategy_id=result.strategy_id,
                    strategy_version=result.strategy_version,
                    months=result.window.months,
                    data_status=result.data_status,
                    trades=q.trades if q else "",
                    win_rate=q.win_rate if q else "",
                    net=q.net if q else "",
                    drawdown=q.drawdown if q else "",
                    qualified=result.qualified,
                    reasons=";".join(result.reasons),
                    strategy_hash=result.strategy_hash,
                    dataset_hash=result.dataset_hash or "",
                )
            )
            j.write(result.model_dump_json() + "\n")
            count += 1
            windows[result.window.months] = windows.get(result.window.months, 0) + 1
    if seen != set(expected_keys):
        raise ValueError("INCOMPLETE_RESULT_PARTITION")
    hashes = {}
    for name in ["pairs.csv", "pairs.jsonl"]:
        with (directory / name).open("rb") as stream:
            hashes[name] = hashlib.file_digest(stream, "sha256").hexdigest()
    completion = dict(
        schema="Phase1415Partition.v1",
        rows=count,
        windows=windows,
        sha256=hashes,
        run_id=family.run_id,
        scope="FULL_FAMILY" if seen == full_keys else "PARTITION",
        complete=True,
    )
    with (directory / "partition-manifest.json").open(
        "x", encoding="utf-8", newline="\n"
    ) as stream:
        json.dump(completion, stream, sort_keys=True, separators=(",", ":"))
        stream.flush()
        os.fsync(stream.fileno())
    return completion
