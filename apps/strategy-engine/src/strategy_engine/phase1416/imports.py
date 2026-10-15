"""Bounded imports of the existing Java export contract into isolated research storage."""

import hashlib
import json
import os
from datetime import datetime
from pathlib import Path
from typing import Any, Literal

from pydantic import Field

from strategy_engine.backtest.dataset import Calendar, Frozen, Hash, Instant, digest
from strategy_engine.phase14.imports import FiveMinuteExport, compose
from strategy_engine.phase14.universe import records
from strategy_engine.phase1415.hardening import ROOT, UNIVERSE
from strategy_engine.phase1415.research import Dataset

SourceClass = Literal["SYNTHETIC_FIXTURE", "AUTHORIZED_LOCAL_EXPORT", "UNVERIFIED_LOCAL_DATA"]


def canonical(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def unique(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def read_json(path: Path, limit: int = 4 * 1024 * 1024) -> tuple[bytes, Any]:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > limit:
        raise ValueError("INPUT_FILE_LIMIT_OR_TYPE")
    with path.open("rb") as stream:
        raw = stream.read(limit + 1)
    if len(raw) > limit:
        raise ValueError("INPUT_FILE_LIMIT")
    return raw, json.loads(raw.decode("utf-8"), object_pairs_hook=unique)


def external(path: Path) -> Path:
    resolved = path.resolve()
    if resolved == ROOT or ROOT in resolved.parents:
        raise ValueError("RESEARCH_STORAGE_MUST_BE_EXTERNAL")
    return resolved


def publish(path: Path, raw: bytes) -> None:
    """Exclusive claim; interrupted pending files fail closed, never appear accepted."""
    if path.exists():
        if path.read_bytes() != raw:
            raise ValueError("CONTENT_CONFLICT")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    pending = path.with_suffix(path.suffix + ".pending")
    with pending.open("xb") as stream:
        stream.write(raw)
        stream.flush()
        os.fsync(stream.fileno())
    os.rename(pending, path)


class Input(Frozen):
    path: str = Field(min_length=1)
    sha256: Hash


class Request(Frozen):
    schema_version: Literal["Phase1416Import.v1"] = "Phase1416Import.v1"
    symbol: str
    instrument_id: str
    instrument_token: str
    source: str = Field(min_length=1)
    source_timestamp: Instant
    source_class: SourceClass
    exports: tuple[Input, ...] = Field(min_length=1, max_length=32)
    calendars: tuple[Calendar, ...] = Field(min_length=1, max_length=32)
    start: Instant
    end: Instant
    master_hash: Hash
    permission: Input | None = None
    calendar_evidence: Input | None = None
    corporate_action_evidence: Input | None = None
    mapping_evidence: Input | None = None
    retention_until: Instant
    source_timezone: Literal["UTC_INTERVAL_START"] = "UTC_INTERVAL_START"


class Imported(Frozen):
    schema_version: Literal["Phase1416Dataset.v1"] = "Phase1416Dataset.v1"
    request_hash: Hash
    source_class: SourceClass
    status: Literal["READY", "DATA_QUARANTINED"]
    reasons: tuple[str, ...]
    raw_hashes: tuple[Hash, ...]
    calendar_hashes: tuple[Hash, ...]
    retention_until: Instant
    symbol: str
    start: Instant
    end: Instant
    dataset: Dataset | None


def evidence(ref: Input | None, expected: dict[str, Any]) -> bool:
    if ref is None:
        return False
    raw, value = read_json(Path(ref.path), 65536)
    return (
        sha(raw) == ref.sha256
        and isinstance(value, dict)
        and all(value.get(k) == v for k, v in expected.items())
        and isinstance(value.get("reviewer"), str)
        and bool(value["reviewer"].strip())
    )


def import_local(request: Request, storage: Path, *, now: datetime) -> Imported:
    storage = external(storage)
    if now.tzinfo is None or not request.start < request.end <= request.source_timestamp <= now:
        raise ValueError("INVALID_IMPORT_WINDOW")
    if now >= request.retention_until:
        raise ValueError("RETENTION_EXPIRED")
    if request.symbol not in {r["symbol"] for r in records(UNIVERSE)}:
        raise ValueError("NON_CANONICAL_SYMBOL")
    if len({x.path for x in request.exports}) != len(request.exports):
        raise ValueError("DUPLICATE_INPUT")
    if len({x.sha256 for x in request.exports}) != len(request.exports):
        raise ValueError("DUPLICATE_INPUT")
    if sum(len(c.days) for c in request.calendars) > 400:
        raise ValueError("CALENDAR_RESOURCE_LIMIT")
    chunks: list[FiveMinuteExport] = []
    size = count = 0
    for item in request.exports:
        raw, value = read_json(Path(item.path))
        size += len(raw)
        if size > 32 * 1024 * 1024:
            raise ValueError("IMPORT_RESOURCE_LIMIT")
        if sha(raw) != item.sha256:
            raise ValueError("SOURCE_FINGERPRINT_MISMATCH")
        chunk = FiveMinuteExport.model_validate(value)
        count += len(chunk.bars)
        if count > 30000:
            raise ValueError("CANDLE_RESOURCE_LIMIT")
        if (chunk.symbol, chunk.instrument_id, chunk.instrument_token) != (
            request.symbol,
            request.instrument_id,
            request.instrument_token,
        ) or any(
            p.source != request.source or p.reference_fingerprint != request.master_hash
            for p in chunk.provenance
        ):
            raise ValueError("IDENTITY_OR_PROVENANCE_MISMATCH")
        if chunk.dataset_cutoff > request.source_timestamp:
            raise ValueError("FUTURE_SOURCE")
        chunks.append(chunk)
    synthetic = request.source_class == "SYNTHETIC_FIXTURE"
    classification: SourceClass = request.source_class
    reasons: list[str] = []
    scope = dict(
        symbol=request.symbol,
        instrument_id=request.instrument_id,
        instrument_token=request.instrument_token,
        start=request.start.isoformat(),
        end=request.end.isoformat(),
    )
    if not synthetic:
        if "SYNTHETIC" in request.source.upper() or any(
            "synthetic" in c.calendar.source.lower() for c in chunks
        ):
            reasons.append("SYNTHETIC_SOURCE_CANNOT_BE_PROMOTED")
        permitted = evidence(
            request.permission,
            dict(
                **scope,
                source=request.source,
                purpose="OFFLINE_RESEARCH",
                raw_hashes=[x.sha256 for x in request.exports],
                retention_until=request.retention_until.isoformat(),
            ),
        )
        if classification != "AUTHORIZED_LOCAL_EXPORT" or not permitted:
            classification = "UNVERIFIED_LOCAL_DATA"
            reasons.append("SOURCE_PERMISSION_UNVERIFIED")
        for ref, expected, reason in (
            (
                request.calendar_evidence,
                dict(
                    calendar_hashes=[c.fingerprint for c in request.calendars], authoritative=True
                ),
                "CALENDAR_INCOMPLETE",
            ),
            (
                request.corporate_action_evidence,
                dict(**scope, policy="PROVIDER_UNSPECIFIED_NO_LOCAL_ADJUSTMENT", reviewed=True),
                "CORPORATE_ACTION_UNCERTAINTY",
            ),
            (
                request.mapping_evidence,
                dict(
                    **scope,
                    master_hash=request.master_hash,
                    resolution="RESOLVED",
                    exchange="NSE",
                    candidates=1,
                ),
                "MAPPING_UNVERIFIED",
            ),
        ):
            if not evidence(ref, expected):
                reasons.append(reason)
    series = compose(
        tuple(chunks),
        request.start,
        request.end,
        request.calendars,
        request.master_hash,
        synthetic=synthetic,
        corporate_actions_reviewed=not reasons,
    )
    missing, unexpected = series.quality()
    if missing or unexpected or not series.sessions:
        reasons.append("INCOMPLETE_MANDATORY_SESSIONS")
    data = (
        None
        if reasons
        else Dataset(
            series=series,
            dataset_sha256=digest(series.model_dump_json()),
            acquired_at=request.source_timestamp,
            calendar_version="COMPOSED_PINNED_CALENDARS",
            calendar_source=request.source,
            calendar_verified=True,
            corporate_action_evidence="SYNTHETIC_FIXTURE"
            if synthetic
            else request.corporate_action_evidence.sha256
            if request.corporate_action_evidence
            else "UNRESOLVED",
            missing_sessions=0,
            duplicate_candles=0,
            out_of_order=0,
            quarantined=False,
            mapping_verified=True,
        )
    )
    result = Imported(
        request_hash=digest(request.model_dump_json()),
        source_class=classification,
        status="DATA_QUARANTINED" if reasons else "READY",
        reasons=tuple(reasons),
        raw_hashes=tuple(x.sha256 for x in request.exports),
        calendar_hashes=tuple(c.fingerprint for c in request.calendars),
        retention_until=request.retention_until,
        symbol=request.symbol,
        start=request.start,
        end=request.end,
        dataset=data,
    )
    payload = result.model_dump_json().encode()
    # Quarantine records contain hashes/reasons only, never market candles.
    if (
        storage.exists()
        and sum(p.stat().st_size for p in storage.rglob("*") if p.is_file()) + len(payload)
        > 256 * 1024 * 1024
    ):
        raise ValueError("STORE_RESOURCE_LIMIT")
    publish(storage / (sha(payload) + ".json"), payload)
    return result
