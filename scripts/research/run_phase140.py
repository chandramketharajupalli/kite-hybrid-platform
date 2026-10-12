"""Offline Phase 14 resource audit and reproducible reports. Never acquires market data."""

import csv
import hashlib
import json
from datetime import UTC, date, datetime, timedelta
from decimal import Decimal
from pathlib import Path

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.phase14.engine import (
    Candle,
    Policy,
    Series,
    Session,
    Settings,
    Strategy,
    simulate,
)
from strategy_engine.phase14.imports import FiveMinuteExport
from strategy_engine.phase14.reporting import PairReport, Window, evaluate_pairs, export_csv
from strategy_engine.phase14.universe import audit, records

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "research/phase-14.0/generation-1"
UNIQUE_HASH = "4345207d985672c34e79347eafb13475ed8c7fe5f440914b25ac0999465c3729"
DUPLICATE_HASH = "ae6d0c8a363959b6e6b9a5f62e1487b1caf77321dd2be981ba08136c40488300"
EXPECTED = {
    "universe(1).csv": "bec552ae3c23c0f22b8980cb0c933b300de53d475efab07c4ddd22de3c9a02ca",
    "phase14_unique_universe.csv": UNIQUE_HASH,
    "phase14_removed_duplicates.csv": DUPLICATE_HASH,
}


def save(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def run() -> None:
    inputs = ROOT / "input/phase14"
    result = audit(inputs)
    if result["hashes"] != EXPECTED:
        raise ValueError("USER_SUPPLIED_HASH_MISMATCH")
    canonical = (inputs / "phase14_unique_universe.csv").read_bytes()
    destination = ROOT / "config/universes/phase14_unique_universe.csv"
    if destination.exists() and destination.read_bytes() != canonical:
        raise ValueError("EXISTING_CANONICAL_COPY_DIFFERS")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(canonical)
    result["project_copy_sha256"] = hashlib.sha256(canonical).hexdigest()
    result["existing_universe_sha256"] = hashlib.sha256(
        (ROOT / "universe.csv").read_bytes()
    ).hexdigest()
    save(OUT / "universe-audit.json", result)
    members = records(destination)
    with (OUT / "instrument-resolution.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(
            [
                "exchange",
                "symbol",
                "enabled",
                "status",
                "instrument_token",
                "snapshot_as_of",
                "snapshot_sha256",
                "reason",
            ]
        )
        for row in members:
            writer.writerow(
                [
                    row["exchange"],
                    row["symbol"],
                    "true",
                    "UNRESOLVED",
                    "",
                    "",
                    "",
                    "NO_DATED_MASTER_SUPPLIED_NO_LOOKUP_AUTHORIZED",
                ]
            )
    save(
        OUT / "instrument-resolution-summary.json",
        {
            "RESOLVED": 0,
            "UNRESOLVED": 1115,
            "AMBIGUOUS": 0,
            "INACTIVE": 0,
            "NON_EQUITY": 0,
            "snapshot": None,
            "actual_resolution_attempts": 0,
            "no_symbol_removed": True,
        },
    )
    costs = IntradayCostSchedule(
        version="synthetic-v1",
        source_version="SYNTHETIC_NOT_BROKER_TARIFF",
        effective_from=date(2025, 1, 1),
        effective_to=date(2027, 1, 1),
        basis="HISTORICAL",
        as_of=None,
        scope="NSE_CASH_SHARES_RESIDENT_RETAIL_FULL_CASH",
        rounding="EXACT_ACCRUAL_NOT_CONTRACT_NOTE",
        stt_basis="FLAT_ROUND_TRIP_AVERAGE",
        brokerage_rate=Decimal("0.0003"),
        brokerage_cap=Decimal(20),
        stt_rate=Decimal("0.00025"),
        exchange_rate=Decimal("0.00003"),
        ipft_rate=Decimal("0.000001"),
        sebi_rate=Decimal("0.000001"),
        gst_rate=Decimal("0.18"),
        stamp_rate=Decimal("0.00003"),
    )
    settings, strategy, policy = Settings(), Strategy(), Policy()
    end = datetime(2026, 10, 12, tzinfo=UTC)
    bounds = tuple(datetime(2026, month, 12, tzinfo=UTC) for month in (7, 8, 9, 10))
    windows = (
        Window(months=6, end=end, oos_start=bounds[0], fold_boundaries=bounds),
        Window(months=12, end=end, oos_start=bounds[0], fold_boundaries=bounds),
    )
    reports = evaluate_pairs(members, (strategy,), windows, {}, settings, costs, policy)
    export_csv(reports, OUT / "stock-strategy-results.csv")
    save(OUT / "stock-strategy-results.json", [r.model_dump(mode="json") for r in reports])
    save(
        OUT / "predeclared-policy.json",
        {
            "strategy": strategy.model_dump(mode="json"),
            "settings": settings.model_dump(mode="json"),
            "policy": policy.model_dump(mode="json"),
            "windows": [w.model_dump(mode="json") for w in windows],
            "fixture_costs_only_not_a_real_tariff": costs.model_dump(mode="json"),
        },
    )
    for name, schema in (
        ("Phase14PairReport.v1", PairReport.model_json_schema()),
        ("HistoricalResearchDataset.five-minute.v1", FiveMinuteExport.model_json_schema()),
    ):
        schema["$schema"] = "https://json-schema.org/draft/2020-12/schema"
        schema["$id"] = "https://kite-hybrid.local/schemas/v1/" + name + ".schema.json"
        save(ROOT / "contracts/schemas/v1" / (name + ".schema.json"), schema)
    save(
        OUT / "csv-export-schema.json",
        {
            "version": "Phase14PairCSV.v1",
            "encoding": "UTF-8",
            "delimiter": ",",
            "header": list(
                next(csv.reader((OUT / "stock-strategy-results.csv").read_text().splitlines()))
            ),
            "absent_performance": "empty cell, never zero",
            "decimal_format": "plain decimal string",
            "row_identity": ["exchange", "symbol", "strategy_hash", "months"],
            "details": "JSON includes exact window, policy, every trade and all folds",
        },
    )
    start = datetime(2026, 10, 5, 3, 45, tzinfo=UTC)
    prices = (*range(120, 99, -1), 115, 115, 115, 115)
    bars = tuple(
        Candle(
            start=start + timedelta(minutes=5 * i),
            open=Decimal(p),
            high=Decimal(p) + Decimal("0.1"),
            low=Decimal(p) - Decimal("0.1"),
            close=Decimal(p),
            volume=10000,
            open_interest=None,
        )
        for i, p in enumerate(prices)
    )
    series = Series(
        symbol="SYNTHETIC&ONLY",
        instrument_token="123",
        source="SYNTHETIC_FIXTURE",
        synthetic=True,
        source_hash="a" * 64,
        master_hash="b" * 64,
        calendar_hash="c" * 64,
        from_inclusive=start,
        to_exclusive=bars[-1].end,
        as_of=bars[-1].end,
        corporate_actions_reviewed=True,
        sessions=(Session(start=start, end=bars[-1].end),),
        bars=bars,
    )
    save(
        OUT / "synthetic-example.json",
        {
            "label": "SIMULATED_FIXTURE_NOT_HISTORICAL_PERFORMANCE_NOT_SIX_MONTH_DATA",
            "input": series.model_dump(mode="json"),
            "result": simulate(series, strategy, settings, costs).model_dump(mode="json"),
        },
    )
    estimates = []
    for months, first in ((6, date(2026, 4, 12)), (12, date(2025, 10, 12))):
        days = (date(2026, 10, 12) - first).days
        weekdays = sum((first + timedelta(days=n)).weekday() < 5 for n in range(days))
        estimates.append(
            {
                "months": months,
                "first": first.isoformat(),
                "end_exclusive": "2026-10-12",
                "calendar_days": days,
                "weekday_illustration_not_exchange_calendar": weekdays,
                "weekday_requests": weekdays * 1115,
                "calendar_day_upper_bound_requests": days * 1115,
                "weekday_minimum_pacing_seconds_at_1rps": weekdays * 1115 - 1,
                "exact_authorized_requests": 0,
                "exact_real_plan_requests": None,
            }
        )
    save(
        OUT / "dry-run-estimates.json",
        {
            "universe": 1115,
            "interval": "5minute",
            "concurrency": 1,
            "configured_requests_per_second": 1,
            "documented_historical_limit_per_second": 3,
            "automatic_retry_budget": 0,
            "account_access_verified": False,
            "estimates": estimates,
            "assumptions": (
                "one request per confirmed session, all symbols hypothetically resolved; "
                "no holidays inferred"
            ),
        },
    )
    print(
        "OFFLINE_ONLY: 1140 source, 25 removed, 1115 unique; "
        "2230 missing-history pair rows; zero broker requests"
    )


if __name__ == "__main__":
    run()
