"""Lossless universe audit: physical CSV line numbers, first occurrence retained."""

import csv
import hashlib
from pathlib import Path


def records(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != ["symbol", "exchange", "enabled"]:
            raise ValueError("UNIVERSE_COLUMNS")
        rows = list(reader)
    if any(None in r or any(v is None for v in r.values()) for r in rows):
        raise ValueError("MALFORMED_UNIVERSE")
    return rows


def normalize(rows: list[dict[str, str]]) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    seen: set[tuple[str, str]] = set()
    unique, removed = [], []
    for line, row in enumerate(rows, 2):
        if (
            not row["symbol"].strip()
            or row["exchange"].strip().casefold() != "nse"
            or row["enabled"].strip().casefold() != "true"
        ):
            raise ValueError("INVALID_OR_DISABLED_UNIVERSE_MEMBER")
        key = row["exchange"].strip().casefold(), row["symbol"].strip().casefold()
        if key in seen:
            removed.append({"source_row": str(line), "symbol": row["symbol"]})
        else:
            seen.add(key)
            unique.append(dict(row))
    return unique, removed


def audit(directory: Path) -> dict[str, object]:
    source = directory / "universe(1).csv"
    canonical = directory / "phase14_unique_universe.csv"
    duplicates = directory / "phase14_removed_duplicates.csv"
    rows = records(source)
    unique, removed = normalize(rows)
    with duplicates.open(encoding="utf-8-sig", newline="") as stream:
        supplied = list(csv.DictReader(stream))
    if unique != records(canonical) or removed != supplied:
        raise ValueError("UNIVERSE_RECONCILIATION_FAILED")
    if (len(rows), len(unique), len(removed)) != (1140, 1115, 25):
        raise ValueError("AUTHORITATIVE_COUNTS_MISMATCH")
    return {
        "source_rows": len(rows),
        "unique_rows": len(unique),
        "removed_rows": len(removed),
        "comparison": "trim+casefold(exchange,symbol); preserve source text and first order",
        "source_row_convention": "physical CSV line, header=1",
        "enabled": True,
        "hashes": {
            p.name: hashlib.sha256(p.read_bytes()).hexdigest()
            for p in (source, canonical, duplicates)
        },
        "removed": removed,
    }
