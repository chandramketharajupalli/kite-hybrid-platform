"""Reproducible full-universe UNAVAILABLE report; never creates market candles."""

import argparse
import csv
import hashlib
import json
from datetime import UTC, datetime
from pathlib import Path

from strategy_engine.backtest.dataset import NSE, digest
from strategy_engine.phase14.engine import Policy
from strategy_engine.phase14.universe import normalize, records
from strategy_engine.phase1415.research import Result, standard_window
from strategy_engine.phase1415.strategies import registry


def generate(universe: Path, end: datetime, output: Path) -> dict[str, object]:
    members, duplicates = normalize(records(universe))
    if len(members) != 1115 or duplicates:
        raise ValueError("CANONICAL_1115_UNIVERSE_REQUIRED")
    definitions = registry()
    windows = (standard_window(end, 6), standard_window(end, 12))
    policy = Policy(family_size=13380)
    columns = [
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
        "policy_hash",
    ]
    metadata = dict(
        schema_version="Phase1415UnavailableMatrix.v1",
        note="Rows are UNAVAILABLE, not synthetic or genuine performance. Planned windows only.",
        columns=columns,
        registry=[d.model_dump(mode="json") for d in definitions],
        strategy_hashes={d.identifier: d.fingerprint for d in definitions},
        windows=[w.model_dump(mode="json") for w in windows],
        policy=policy.model_dump(mode="json"),
        policy_hash=digest(policy.model_dump_json()),
        universe_sha256=hashlib.sha256(universe.read_bytes()).hexdigest(),
        coverage={"symbols": 1115, "available_datasets": 0, "MASTER_UNAVAILABLE": 1115},
        walk_forward={"status": "OUT_OF_SAMPLE_NOT_AVAILABLE", "results": None},
        qualification={"qualified": 0, "unavailable": 13380},
        source={"dataset": None, "calendar": None, "corporate_actions": None},
        mode="BACKTEST_ONLY",
        costs=None,
    )
    output.mkdir(parents=True, exist_ok=True)
    count = 0
    with (
        (output / "pair-results.csv").open("x", encoding="utf-8", newline="") as c,
        (output / "pair-results.json").open("x", encoding="utf-8", newline="\n") as j,
    ):
        writer = csv.writer(c, lineterminator="\n")
        writer.writerow(columns)
        j.write(json.dumps(metadata, sort_keys=True, separators=(",", ":"))[:-1] + ',"rows":[\n')
        for member in members:
            for window in windows:
                for definition in definitions:
                    result = Result(
                        symbol=member["symbol"],
                        strategy_id=definition.identifier,
                        strategy_version=definition.version,
                        strategy_hash=definition.fingerprint,
                        window=window,
                        policy_hash=digest(policy.model_dump_json()),
                        dataset_hash=None,
                        synthetic=None,
                        data_status="UNAVAILABLE",
                        reasons=(
                            "NO_SOURCED_HISTORY_OR_RESOLVED_MAPPING",
                            "OUT_OF_SAMPLE_NOT_AVAILABLE",
                        ),
                    )
                    row = [
                        result.exchange,
                        result.symbol,
                        result.strategy_id,
                        result.strategy_version,
                        window.months,
                        result.data_status,
                        None,
                        None,
                        None,
                        None,
                        False,
                        ";".join(result.reasons),
                        result.strategy_hash,
                        None,
                        result.policy_hash,
                    ]
                    writer.writerow(row)
                    if count:
                        j.write(",\n")
                    j.write(json.dumps(row, ensure_ascii=True, separators=(",", ":")))
                    count += 1
        j.write("\n]}\n")
    hashes = {}
    for name in ["pair-results.csv", "pair-results.json"]:
        with (output / name).open("rb") as stream:
            hashes[name] = hashlib.file_digest(stream, "sha256").hexdigest()
    return dict(rows=count, six_month=6690, twelve_month=6690, qualified=0, sha256=hashes)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--universe", required=True, type=Path)
    parser.add_argument(
        "--end", required=True, help="Planned IST midnight YYYY-MM-DD, not acquisition"
    )
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    end = datetime.strptime(args.end, "%Y-%m-%d").replace(tzinfo=NSE).astimezone(UTC)
    print(json.dumps(generate(args.universe, end, args.output), sort_keys=True))


if __name__ == "__main__":
    main()
