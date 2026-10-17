"""Fail-closed ingestion around existing calendar, candle and research contracts.

Fingerprints bind content, not publisher identity. Clocks and synthetic evidence
are supplied by the caller. No network, credentials, files or environment ports.
"""

import hashlib
import json
from datetime import datetime
from decimal import Decimal
from typing import Any, Literal

from pydantic import Field, ValidationError

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import Frozen, Hash, Instant, digest
from strategy_engine.phase14.engine import FIVE, Candle, Series, Settings
from strategy_engine.phase1415.hardening import frozen_policy
from strategy_engine.phase1415.research import Dataset, ResearchWindow, evaluate
from strategy_engine.phase1415.strategies import entry_signal, registry
from strategy_engine.phase1419.scheduler import Calendar
from strategy_engine.phase1428.resources import load


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ValueError(code)


def canonical(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def fingerprint(value: Frozen) -> str:
    return sha(canonical(value.model_dump(mode="json")))


def checked_time(at: datetime) -> None:
    # Reuse the UTC transport contract rather than accept naive/host-local clocks.
    Clock(at=at)


class Clock(Frozen):
    at: Instant


class Boundary(Frozen):
    version: Literal["Phase1439MarketDataAcquisitionBoundary.v1"] = (
        "Phase1439MarketDataAcquisitionBoundary.v1"
    )
    instrument_master: Literal["NOT_AUTHORIZED"] = "NOT_AUTHORIZED"
    historical_candles: Literal["NOT_AUTHORIZED"] = "NOT_AUTHORIZED"
    validation: Literal["SYNTHETIC_ONLY"] = "SYNTHETIC_ONLY"
    retention: Literal["MEMORY_ONLY_SYNTHETIC"] = "MEMORY_ONLY_SYNTHETIC"
    research: Literal["SYNTHETIC_ONLY"] = "SYNTHETIC_ONLY"

    def acquire(self, operation: str) -> None:
        # Deliberately no credential or transport arguments/callbacks to invoke.
        raise ValueError("ACQUISITION_NOT_AUTHORIZED:" + operation)


class Instrument(Frozen):
    exchange: Literal["NSE"] = "NSE"
    symbol: str = Field(min_length=1)
    token: str = Field(pattern=r"^[1-9][0-9]{0,9}$")


class Master(Frozen):
    version: Literal["Phase1439InstrumentMasterBatch.v1"] = "Phase1439InstrumentMasterBatch.v1"
    source_type: Literal["SYNTHETIC"] = "SYNTHETIC"
    authorization: Literal["OFFLINE_SYNTHETIC_ONLY"] = "OFFLINE_SYNTHETIC_ONLY"
    batch_id: str = Field(min_length=1, max_length=128)
    observed_at: Instant
    expires_at: Instant
    source_digest: Hash
    records: tuple[Instrument, ...] = Field(max_length=1115)


def parse(raw: bytes, expected: str, model: type[Master]) -> Master:
    require(len(raw) <= 2 * 1024 * 1024 and sha(raw) == expected, "SOURCE_UNAVAILABLE")

    def unique(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        require(len(dict(pairs)) == len(pairs), "SOURCE_UNAVAILABLE:DUPLICATE_JSON_KEY")
        return dict(pairs)

    try:
        return model.model_validate(json.loads(raw, object_pairs_hook=unique))
    except (ValidationError, json.JSONDecodeError) as exc:
        raise ValueError("SOURCE_UNAVAILABLE:SCHEMA") from exc


def master(raw: bytes, expected: str, now: datetime, previous: Master | None = None) -> Master:
    checked_time(now)
    result = parse(raw, expected, Master)
    require(result.observed_at <= now < result.expires_at, "MAPPING_STALE")
    symbols = tuple(r.symbol for r in result.records)
    tokens = tuple(r.token for r in result.records)
    require(len(set(symbols)) == len(symbols), "DUPLICATE_SYMBOL")
    require(len(set(tokens)) == len(tokens), "TOKEN_REUSE_CONFLICT")
    require(symbols == tuple(load()["symbols"]), "MAPPING_UNAVAILABLE:CANONICAL_ORDER_COVERAGE")
    require(result.source_digest == sha(canonical(
        [r.model_dump(mode="json") for r in result.records])), "SOURCE_UNAVAILABLE")
    if previous is not None:
        # Revalidate supplied instances, including ones built using model_copy.
        old_raw = canonical(previous.model_dump(mode="json"))
        previous = master(old_raw, sha(old_raw), previous.observed_at)
        require(previous.observed_at < result.observed_at, "MAPPING_STALE")
        old = {r.token: r.symbol for r in previous.records}
        require(all(old.get(r.token, r.symbol) == r.symbol for r in result.records),
                "TOKEN_REUSE_CONFLICT")
    return result


class CandleBatch(Frozen):
    version: Literal["Phase1439HistoricalCandleBatch.v1"] = "Phase1439HistoricalCandleBatch.v1"
    source_type: Literal["SYNTHETIC"] = "SYNTHETIC"
    batch_id: str = Field(min_length=1, max_length=128)
    source_batch_id: str = Field(min_length=1)
    mapping_generation: Hash
    source_digest: Hash
    observed_at: Instant
    series: Series
    calendar: Calendar
    calendar_verified: bool
    corporate_actions_verified: bool
    listing_suspension_verified: bool


def candles(batch: CandleBatch, mapping: Master, now: datetime) -> CandleBatch:
    checked_time(now)
    mapping = Master.model_validate(mapping.model_dump())
    # Full canonical/expiry validation also protects against caller-crafted models.
    raw = canonical(mapping.model_dump(mode="json"))
    mapping = master(raw, sha(raw), now)
    try:
        batch = CandleBatch.model_validate(batch.model_dump())
    except ValidationError as exc:
        raise ValueError("INVALID_CANDLE") from exc
    series = batch.series
    require(series.synthetic and series.source == "SYNTHETIC", "ACQUISITION_NOT_AUTHORIZED")
    require(batch.mapping_generation == fingerprint(mapping)
            and series.master_hash == fingerprint(mapping)
            and batch.source_batch_id == mapping.batch_id, "MAPPING_UNAVAILABLE")
    require(any(r.symbol == series.symbol and r.token == series.instrument_token
                for r in mapping.records), "MAPPING_UNAVAILABLE")
    require(mapping.observed_at <= batch.observed_at <= now
            and series.as_of == batch.observed_at, "INVALID_CANDLE:AVAILABILITY")
    require(all(b.end <= batch.observed_at for b in series.bars), "INVALID_CANDLE:FUTURE")
    require(batch.source_digest == series.source_hash
            == sha(canonical([b.model_dump(mode="json") for b in series.bars])),
            "SOURCE_UNAVAILABLE")
    require(series.calendar_hash == batch.calendar.fingerprint
            and series.sessions == batch.calendar.sessions
            and series.non_trading_days == batch.calendar.holidays, "CALENDAR_UNVERIFIED")
    require(not series.quality()[1], "INVALID_CANDLE:OUT_OF_SESSION")
    return batch


def completeness(batch: CandleBatch, mapping: Master, now: datetime) -> dict[str, Any]:
    try:
        batch = candles(batch, mapping, now)
    except ValueError as exc:
        return {"version": "Phase1439HistoricalCompletenessReport.v1", "status": "INVALID",
                "error": str(exc), "scope": "SYNTHETIC", "genuine_performance": None}
    missing, extra = batch.series.quality()
    expected = sum(int((s.end - s.start) / FIVE) for s in batch.series.sessions)
    status = "COMPLETE"
    if not batch.calendar_verified or not batch.listing_suspension_verified:
        status = "CALENDAR_UNVERIFIED"
    elif not batch.corporate_actions_verified or not batch.series.corporate_actions_reviewed:
        status = "CORPORATE_ACTION_UNVERIFIED"
    elif not batch.series.bars:
        status = "DATA_UNAVAILABLE"
    elif missing:
        status = "PARTIAL"
    return dict(version="Phase1439HistoricalCompletenessReport.v1", status=status,
                scope="SYNTHETIC", expected=expected, observed=len(batch.series.bars),
                missing=[t.isoformat() for t in missing], extras=[t.isoformat() for t in extra],
                duplicate_count=0, invalid_count=0, calendar_verified=batch.calendar_verified,
                corporate_actions_verified=batch.corporate_actions_verified,
                listing_suspension_verified=batch.listing_suspension_verified,
                coverage=str(Decimal(len(batch.series.bars)) / expected) if expected else None,
                genuine_performance=None)


class Lifecycle(Frozen):
    version: Literal["Phase1439TransientDataSession.v1"] = "Phase1439TransientDataSession.v1"
    identity: str = Field(min_length=1)
    source: Literal["SYNTHETIC"] = "SYNTHETIC"
    purpose: Literal["SYNTHETIC_RESEARCH"] = "SYNTHETIC_RESEARCH"
    starts_at: Instant
    expires_at: Instant
    retention_policy: Hash
    storage: Literal["PROHIBITED"] = "PROHIBITED"


class Checkpoint(Frozen):
    version: Literal["Phase1439IngestionCheckpoint.v1"] = "Phase1439IngestionCheckpoint.v1"
    batch_id: str
    source: Hash
    mapping: Hash
    candles: Hash
    lifecycle: Hash
    retention_policy: Hash
    state: Literal["VALIDATED_SYNTHETIC"] = "VALIDATED_SYNTHETIC"
    accepted: int = Field(ge=0)
    rejected: Literal[0] = 0


class Transient:
    """In-memory idempotency within one lifetime; supplied replay is not durable authority."""

    def __init__(self, policy: Lifecycle) -> None:
        self.policy = Lifecycle.model_validate(policy.model_dump())
        self._seen: dict[str, str] = {}
        self._closed = False

    def active(self, now: datetime) -> None:
        checked_time(now)
        if now >= self.policy.expires_at:
            self.dispose()
        require(not self._closed and self.policy.starts_at <= now < self.policy.expires_at,
                "RETENTION_NOT_AUTHORIZED:EXPIRED")

    def persist(self) -> None:
        raise ValueError("RETENTION_NOT_AUTHORIZED")

    def accept(self, batch: CandleBatch, mapping: Master, now: datetime,
               replay: dict[str, Any] | None = None) -> dict[str, Any]:
        self.active(now)
        batch = candles(batch, mapping, now)
        report = completeness(batch, mapping, now)
        require(report["status"] == "COMPLETE", "MISSING_CANDLE:" + report["status"])
        point = Checkpoint(batch_id=batch.batch_id, source=batch.source_digest,
                           mapping=batch.mapping_generation, candles=fingerprint(batch),
                           lifecycle=fingerprint(self.policy),
                           retention_policy=self.policy.retention_policy,
                           accepted=len(batch.series.bars))
        body = point.model_dump(mode="json")
        replay_id = sha(canonical(body))
        envelope: dict[str, Any] = {"checkpoint": body, "fingerprint": replay_id}
        if replay is not None:
            require(replay == envelope, "CHECKPOINT_INVALID")
        require(batch.batch_id not in self._seen, "REPLAY_CONFLICT")
        self._seen[batch.batch_id] = replay_id
        return envelope

    def dispose(self) -> dict[str, Any]:
        count = len(self._seen)
        self._seen.clear()
        self._closed = True
        return dict(lifecycle=fingerprint(self.policy), discarded_checkpoints=count,
                    closed=True, persistent_files_created=0,
                    limitation="MEMORY_REFERENCES_RELEASED_NOT_SECURE_ERASURE")


def available_signals(bars: tuple[Candle, ...], now: datetime) -> dict[str, bool]:
    checked_time(now)
    checked = tuple(Candle.model_validate(b.model_dump()) for b in bars)
    require(tuple(b.start for b in checked) == tuple(sorted({b.start for b in checked})),
            "INVALID_CANDLE:ORDER")
    completed = tuple(b for b in checked if b.end <= now)
    return {d.identifier: entry_signal(completed, d) for d in registry()}


def research(batch: CandleBatch, mapping: Master, now: datetime, window: ResearchWindow,
             costs: IntradayCostSchedule, session: Transient) -> dict[str, Any]:
    session.active(now)
    batch = candles(batch, mapping, now)
    require(completeness(batch, mapping, now)["status"] == "COMPLETE", "MISSING_CANDLE")
    require(batch.batch_id in session._seen, "CHECKPOINT_INVALID:NOT_ACCEPTED")
    # Recompute all bindings; the same ID may not be reused with different content.
    expected = Checkpoint(batch_id=batch.batch_id, source=batch.source_digest,
                          mapping=batch.mapping_generation, candles=fingerprint(batch),
                          lifecycle=fingerprint(session.policy),
                          retention_policy=session.policy.retention_policy,
                          accepted=len(batch.series.bars))
    require(session._seen[batch.batch_id] == fingerprint(expected), "REPLAY_CONFLICT")
    dataset = Dataset(series=batch.series, dataset_sha256=digest(batch.series.model_dump_json()),
                      acquired_at=batch.observed_at, calendar_version=batch.calendar.version,
                      calendar_source="SYNTHETIC", calendar_verified=True,
                      corporate_action_evidence="SYNTHETIC_NO_ACTIONS", missing_sessions=0,
                      duplicate_candles=0, out_of_order=0, quarantined=False, mapping_verified=True)
    results = [evaluate(batch.series.symbol, d, window, dataset, Settings(), costs,
                        frozen_policy()).model_dump(mode="json") for d in registry()]
    require(all(not r["qualified"] for r in results), "SYNTHETIC_QUALIFICATION_FORBIDDEN")
    return dict(scope="SYNTHETIC_REPRESENTATIVE_SYMBOL", results=results,
                genuine_performance=None, genuine_qualified=0, real_kite_requests=0,
                operational="NO_GO")


def request_plan(operation: Literal["INSTRUMENT_MASTER", "HISTORICAL_CANDLES"],
                 start: datetime, end: datetime) -> dict[str, Any]:
    checked_time(start)
    checked_time(end)
    require(start < end, "REQUEST_BUDGET_UNVERIFIED:WINDOW")
    require(operation in ("INSTRUMENT_MASTER", "HISTORICAL_CANDLES"),
            "ACQUISITION_NOT_AUTHORIZED:ENDPOINT")
    return dict(version="Phase1439AcquisitionApproval.v1", operation=operation,
                endpoint="GET /instruments/NSE" if operation == "INSTRUMENT_MASTER"
                else "GET /instruments/historical/{instrument_token}/5minute",
                endpoint_evidence="CANDIDATE_REQUIRES_APPROVED_DOCUMENTATION",
                authorization="NOT_AUTHORIZED", symbol_count=1115,
                universe=load()["manifest"]["resources"]["universe"]["sha256"],
                from_inclusive=start.isoformat(), to_exclusive=end.isoformat(),
                requested_windows_months=[6, 12], request_budget="REQUEST_BUDGET_UNVERIFIED",
                estimated_requests=None, rate_limit=None, permitted_requests=0,
                retry_policy="NO_EXECUTION", backoff=None, credential_custody="UNVERIFIED",
                authorization_expiry=None, retention_authority="UNVERIFIED",
                Gate_A="NOT_AUTHORIZED", Gate_B="NOT_AUTHORIZED")
