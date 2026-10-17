"""Evidence gates around the existing calendar, ingestion and research contracts."""

import json
from collections import Counter
from datetime import date, datetime, timedelta
from decimal import Decimal
from typing import Any, Final, Literal

from pydantic import Field, ValidationError

from strategy_engine.backtest.costs import IntradayCostSchedule
from strategy_engine.backtest.dataset import NSE, Frozen, Hash, Instant, Money
from strategy_engine.phase14.engine import FIVE, Candle, Session
from strategy_engine.phase1415.execution import execution_fingerprint
from strategy_engine.phase1415.research import ResearchWindow
from strategy_engine.phase1419.scheduler import Calendar
from strategy_engine.phase1428.resources import load
from strategy_engine.phase1439 import pipeline as p

POLICY: Final = "Phase1440CorporateActionAdjustment.v1:NO_LOCAL_ADJUSTMENT"
CAUSAL = "2078a10ecad7df0faef638e3e6e37eb4808c4a8c33af5fb81883b427d0ed5925"


class Day(Frozen):
    day: date
    kind: Literal["VERIFIED_SESSION", "VERIFIED_NON_TRADING_DAY", "SPECIAL_SESSION_VERIFIED"]
    description: str = Field(min_length=1)
    source: Hash
    available_at: Instant


class Period(Frozen):
    instrument: p.Instrument
    generation: Hash
    source: Hash
    observed_at: Instant
    start: Instant
    end: Instant


class Identity(Frozen):
    version: Literal["Phase1440InstrumentIdentityLedger.v1"] = (
        "Phase1440InstrumentIdentityLedger.v1"
    )
    source_type: Literal["SYNTHETIC"] = "SYNTHETIC"
    periods: tuple[Period, ...]
    complete: bool


class Action(Frozen):
    exchange: Literal["NSE"] = "NSE"
    symbol: str
    kind: Literal["SPLIT", "BONUS", "DIVIDEND", "RIGHTS", "MERGER", "DEMERGER",
                  "SYMBOL_CHANGE", "DELISTING", "SUSPENSION", "IDENTITY_CHANGE"]
    announced_at: Instant | None
    effective_at: Instant
    available_at: Instant
    ratio: Money | None = None
    amount: Money | None = None
    source: str = Field(min_length=1)
    observed_at: Instant
    source_hash: Hash
    policy: Literal["Phase1440CorporateActionAdjustment.v1:NO_LOCAL_ADJUSTMENT"] = POLICY


class Actions(Frozen):
    version: Literal["Phase1440CorporateActionEvidence.v1"] = "Phase1440CorporateActionEvidence.v1"
    source_type: Literal["SYNTHETIC"] = "SYNTHETIC"
    symbol: str
    coverage_start: Instant
    coverage_end: Instant
    available_at: Instant
    complete: bool
    source: str = Field(min_length=1)
    source_hash: Hash
    events: tuple[Action, ...] = ()


class Scope(Frozen):
    version: Literal["Phase1440ResearchDataset.v1"] = "Phase1440ResearchDataset.v1"
    dataset_id: str = Field(min_length=1)
    source_type: Literal["SYNTHETIC", "GENUINE"] = "SYNTHETIC"
    exchange: Literal["NSE"] = "NSE"
    symbol: str
    window: ResearchWindow
    calendar: Calendar
    days: tuple[Day, ...]
    calendar_verified: bool
    listing_start: Instant
    listing_end: Instant
    listing_evidence: Hash
    listing_available_at: Instant
    suspensions: tuple[Session, ...] = ()
    suspension_evidence: Hash
    suspension_available_at: Instant
    suspension_verified: bool
    identity: Identity
    actions: Actions
    lifecycle: p.Lifecycle
    as_of: Instant
    adjustment: Literal["RAW_UNADJUSTED", "ADJUSTED_VERIFIED", "ADJUSTMENT_REQUIRED",
                        "ADJUSTMENT_UNVERIFIED", "NOT_APPLICABLE"] = "RAW_UNADJUSTED"
    adjustment_policy: Literal["Phase1440CorporateActionAdjustment.v1:NO_LOCAL_ADJUSTMENT"] = POLICY


class Record(Frozen):
    candle: Candle
    token: str = Field(pattern=r"^[1-9][0-9]{0,9}$")
    mapping_generation: Hash
    source_batch: str = Field(min_length=1)
    available_at: Instant
    interval: Literal["5minute"] = "5minute"


def expected(scope: Scope) -> dict[str, Any]:
    """Explicit supplied sessions only; no weekday-derived exchange authority."""
    scope = Scope.model_validate(scope.model_dump())
    p.require(scope.calendar_verified and scope.suspension_verified
              and scope.suspension_available_at <= scope.window.start, "CALENDAR_UNVERIFIED")
    p.require(scope.window.start <= scope.listing_start < scope.listing_end <= scope.window.end
              and scope.listing_available_at <= scope.listing_start, "CALENDAR_UNVERIFIED")
    first = scope.window.start.astimezone(NSE).date()
    last = (scope.window.end - timedelta(microseconds=1)).astimezone(NSE).date()
    dates = {first + timedelta(days=i) for i in range((last - first).days + 1)}
    days = {d.day: d for d in scope.days}
    p.require(set(days) == dates and len(days) == len(scope.days), "CALENDAR_UNVERIFIED")
    sessions = {s.start.astimezone(NSE).date(): s for s in scope.calendar.sessions}
    p.require(set(sessions) | set(scope.calendar.holidays) == dates, "CALENDAR_UNVERIFIED")
    p.require(all(s.start >= scope.window.start and s.end <= scope.window.end
                  for s in scope.calendar.sessions), "CALENDAR_UNVERIFIED")
    for d in scope.days:
        p.require(d.available_at <= scope.window.start, "CALENDAR_UNVERIFIED:FUTURE_EVIDENCE")
        if d.day in sessions:
            s = sessions[d.day]
            p.require(d.kind != "VERIFIED_NON_TRADING_DAY", "CALENDAR_UNVERIFIED")
            if d.kind == "VERIFIED_SESSION":
                start, end = s.start.astimezone(NSE), s.end.astimezone(NSE)
                p.require((start.hour, start.minute, end.hour, end.minute) == (9, 15, 15, 30),
                          "CALENDAR_UNVERIFIED:SPECIAL_REQUIRED")
        else:
            p.require(d.kind == "VERIFIED_NON_TRADING_DAY", "CALENDAR_UNVERIFIED")
    for i, pause in enumerate(scope.suspensions):
        p.require(scope.listing_start <= pause.start < pause.end <= scope.listing_end
                  and (i == 0 or scope.suspensions[i - 1].end <= pause.start),
                  "CALENDAR_UNVERIFIED:SUSPENSION")
    slots = []
    per_session = []
    for session in scope.calendar.sessions:
        starts = []
        at = session.start
        while at < session.end:
            if scope.listing_start <= at and at + FIVE <= scope.listing_end:
                if not any(at < s.end and at + FIVE > s.start for s in scope.suspensions):
                    starts.append(at.isoformat())
            at += FIVE
        slots.extend(starts)
        per_session.append({"identity": p.fingerprint(session), "open": session.start.isoformat(),
                            "close": session.end.isoformat(), "expected": starts,
                            "kind": days[session.start.astimezone(NSE).date()].kind})
    return {"version": "Phase1440ExpectedCandleLedger.v1", "exchange": "NSE",
            "symbol": scope.symbol, "window": p.fingerprint(scope.window), "interval": "5minute",
            "timestamp_boundary": "OPEN_INCLUSIVE_CLOSE_EXCLUSIVE", "timestamps": slots,
            "sessions": per_session, "calendar": scope.calendar.fingerprint,
            "listing": [scope.listing_start.isoformat(), scope.listing_end.isoformat()],
            "suspensions": [s.model_dump(mode="json") for s in scope.suspensions],
            "source": "SYNTHETIC", "evidence": p.sha(p.canonical([d.model_dump(mode="json")
                                                                  for d in scope.days]))}


def identity(scope: Scope) -> None:
    p.require(scope.identity.complete and bool(scope.identity.periods),
              "INSTRUMENT_IDENTITY_UNVERIFIED")
    periods = scope.identity.periods
    for i, period in enumerate(periods):
        p.require(period.observed_at <= period.start < period.end
                  and period.observed_at <= scope.as_of, "INSTRUMENT_IDENTITY_UNVERIFIED")
        for other in periods[:i]:
            a, b = period.instrument, other.instrument
            same = (a.exchange, a.symbol) == (b.exchange, b.symbol)
            overlap = max(period.start, other.start) < min(period.end, other.end)
            p.require(not (same and overlap), "INSTRUMENT_IDENTITY_UNVERIFIED:OVERLAP")
            p.require(not (a.token == b.token and not same),
                      "INSTRUMENT_IDENTITY_UNVERIFIED:TOKEN_REUSE")


def actions(scope: Scope) -> str:
    evidence = scope.actions
    p.require(evidence.complete and evidence.symbol == scope.symbol
              and evidence.coverage_start <= scope.window.start
              and evidence.coverage_end >= scope.window.end
              and evidence.available_at <= scope.as_of, "CORPORATE_ACTION_UNVERIFIED")
    seen = set()
    for action in evidence.events:
        fp = p.fingerprint(action)
        p.require(fp not in seen and action.symbol == scope.symbol
                  and evidence.coverage_start <= action.effective_at < evidence.coverage_end
                  and action.observed_at <= action.available_at <= action.effective_at
                  and (action.announced_at is None or action.announced_at <= action.available_at),
                  "CORPORATE_ACTION_UNVERIFIED")
        p.require(action.kind not in ("SPLIT", "BONUS")
                  or (action.ratio is not None and action.ratio > 0),
                  "CORPORATE_ACTION_UNVERIFIED:RATIO")
        p.require(action.kind not in ("DIVIDEND", "RIGHTS")
                  or (action.amount is not None and action.amount > 0),
                  "CORPORATE_ACTION_UNVERIFIED:AMOUNT")
        seen.add(fp)
    if evidence.events:
        return "ADJUSTMENT_REQUIRED"
    p.require(scope.adjustment in ("RAW_UNADJUSTED", "NOT_APPLICABLE"), "ADJUSTMENT_UNVERIFIED")
    return "NOT_APPLICABLE"


def parse(raw: bytes, digest: str) -> list[Any]:
    p.require(len(raw) <= 16 * 1024 * 1024 and p.sha(raw) == digest, "SOURCE_HASH_MISMATCH")

    def unique(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        p.require(len(dict(pairs)) == len(pairs), "DUPLICATE_JSON_KEY")
        return dict(pairs)

    value = json.loads(raw, object_pairs_hook=unique)
    p.require(isinstance(value, list) and len(value) <= 100000, "INVALID_SOURCE")
    return list(value)


def missing_runs(missing: list[datetime]) -> list[dict[str, Any]]:
    runs: list[dict[str, Any]] = []
    for at in missing:
        if runs and at == datetime.fromisoformat(runs[-1]["last"]) + FIVE:
            runs[-1].update(last=at.isoformat(), count=runs[-1]["count"] + 1)
        else:
            runs.append({"first": at.isoformat(), "last": at.isoformat(), "count": 1})
    return runs


def assess(raw: bytes, digest: str, scope: Scope) -> dict[str, Any]:
    """No storage or transport. Failed records remain counted and diagnosed."""
    base: dict[str, Any] = {"version": "Phase1440CandleCompleteness.v1", "symbol": scope.symbol,
                            "source": digest, "genuine_performance": None}
    try:
        scope = Scope.model_validate(scope.model_dump())
        if scope.source_type != "SYNTHETIC":
            return {**base, "status": "GENUINE_DATA_UNAVAILABLE"}
        p.require(scope.symbol in load()["symbols"], "INSTRUMENT_IDENTITY_UNVERIFIED")
        p.Transient(scope.lifecycle).active(scope.as_of)
        p.require(scope.as_of >= scope.window.end, "DATASET_INVALID:FUTURE_WINDOW")
        ledger = expected(scope)
        identity(scope)
        for timestamp in ledger["timestamps"]:
            at = datetime.fromisoformat(timestamp)
            p.require(sum(period.instrument.symbol == scope.symbol
                          and period.start <= at and at + FIVE <= period.end
                          for period in scope.identity.periods) == 1,
                      "INSTRUMENT_IDENTITY_UNVERIFIED:COVERAGE")
        adjustment = actions(scope)
        values = parse(raw, digest)
        records: list[Record] = []
        observed_starts = []
        invalid = []
        for i, value in enumerate(values):
            try:
                observed_starts.append(p.Clock(at=value["candle"]["start"]).at)
            except (TypeError, KeyError, ValueError):
                pass  # The full record error is retained below, not silently accepted.
            try:
                record = Record.model_validate(value)
                p.require(record.candle.end <= record.available_at <= scope.as_of,
                          "CAUSAL_AVAILABILITY")
                matches = [s for s in scope.identity.periods
                           if s.instrument.symbol == scope.symbol
                           and s.instrument.token == record.token
                           and s.generation == record.mapping_generation
                           and s.start <= record.candle.start and record.candle.end <= s.end]
                p.require(len(matches) == 1, "INSTRUMENT_IDENTITY_UNVERIFIED")
                p.require(record.source_batch == scope.dataset_id, "SOURCE_BATCH_MISMATCH")
                records.append(record)
            except (ValueError, ValidationError) as exc:
                invalid.append({"index": i, "reason": str(exc).splitlines()[0]})
        starts = [r.candle.start for r in records]
        counter = Counter(observed_starts)
        duplicates = sorted(t for t, n in counter.items() if n > 1)
        expected_set = {datetime.fromisoformat(t) for t in ledger["timestamps"]}
        actual = set(starts)
        valid = actual & expected_set
        missing = sorted(expected_set - actual)
        extras = sorted(set(observed_starts) - expected_set)
        per_session = []
        for session in ledger["sessions"]:
            wanted = {datetime.fromisoformat(t) for t in session["expected"]}
            per_session.append({"identity": session["identity"], "expected": len(wanted),
                                "valid": len(wanted & valid),
                                "missing": [t.isoformat() for t in sorted(wanted - actual)]})
        status = "SYNTHETIC_DATASET_CERTIFIED"
        if invalid or duplicates or extras or observed_starts != sorted(observed_starts):
            status = "DATASET_INVALID"
        elif adjustment == "ADJUSTMENT_REQUIRED":
            status = "ADJUSTMENT_UNVERIFIED"
        elif missing or not expected_set:
            status = "SYNTHETIC_DATASET_PARTIAL"
        return {**base, "status": status, "expected": len(expected_set), "observed": len(values),
                "observed_intervals": len(set(observed_starts)),
                "valid": len(valid), "invalid": invalid, "invalid_count": len(invalid),
                "duplicates": [t.isoformat() for t in duplicates],
                "duplicate_count": sum(n - 1 for n in counter.values()),
                "out_of_session": [t.isoformat() for t in extras],
                "missing": [t.isoformat() for t in missing], "missing_count": len(missing),
                "first_missing": missing[0].isoformat() if missing else None,
                "last_missing": missing[-1].isoformat() if missing else None,
                "missing_runs": missing_runs(missing), "per_session": per_session,
                "coverage_percent": str(Decimal(100) * len(valid) / len(expected_set))
                if expected_set else None, "adjustment": adjustment,
                "ledger": ledger, "ledger_fingerprint": p.sha(p.canonical(ledger)),
                "scope_fingerprint": p.fingerprint(scope)}
    except (ValueError, ValidationError) as exc:
        code = str(exc).splitlines()[0]
        known = ("CALENDAR_UNVERIFIED", "INSTRUMENT_IDENTITY_UNVERIFIED",
                 "CORPORATE_ACTION_UNVERIFIED", "ADJUSTMENT_UNVERIFIED", "RETENTION_NOT_AUTHORIZED")
        return {**base, "status": next((s for s in known if code.startswith(s)), "DATASET_INVALID"),
                "reason": code}


def receipt(raw: bytes, digest: str, scope: Scope, software: str) -> dict[str, Any]:
    result = assess(raw, digest, scope)
    p.require(result["status"] == "SYNTHETIC_DATASET_CERTIFIED", result["status"])
    p.require(len(software) == 64 and all(c in "0123456789abcdef" for c in software),
              "SOFTWARE_FINGERPRINT_INVALID")
    p.require(execution_fingerprint() == CAUSAL, "CAUSAL_POLICY_CHANGED")
    body = {"version": "Phase1440DatasetCertificationReceipt.v1", "dataset_id": scope.dataset_id,
            "dataset": p.fingerprint(scope), "source": digest,
            "calendar": scope.calendar.fingerprint, "mapping": p.fingerprint(scope.identity),
            "corporate_actions": p.fingerprint(scope.actions),
            "adjustment": p.sha(POLICY.encode()), "retention": p.fingerprint(scope.lifecycle),
            "completeness": p.sha(p.canonical(result)), "decision": result["status"],
            "certified_at": scope.as_of.isoformat(), "software": software, "causal": CAUSAL,
            "universe": load()["manifest"]["resources"]["universe"]["sha256"]}
    return {"receipt": body, "fingerprint": p.sha(p.canonical(body))}


class Certification:
    """Explicit in-memory lifecycle, no serialization or restoration side effects."""

    def __init__(self) -> None:
        self.seen: dict[str, str] = {}

    def accept(self, raw: bytes, digest: str, scope: Scope, software: str,
               replay: dict[str, Any] | None = None) -> dict[str, Any]:
        result = receipt(raw, digest, scope, software)
        p.require(replay is None or replay == result, "REPLAY_CONFLICT")
        p.require(scope.dataset_id not in self.seen, "REPLAY_CONFLICT")
        self.seen[scope.dataset_id] = result["fingerprint"]
        return result

    def persist(self) -> None:
        raise ValueError("RETENTION_NOT_AUTHORIZED")


def integrate(raw: bytes, digest: str, scope: Scope, software: str, batch: p.CandleBatch,
              mapping: p.Master, costs: IntradayCostSchedule) -> dict[str, Any]:
    certified = receipt(raw, digest, scope, software)
    records = [Record.model_validate(v) for v in parse(raw, digest)]
    p.require(tuple(r.candle for r in records) == batch.series.bars
              and all(r.token == batch.series.instrument_token
                      and r.mapping_generation == batch.mapping_generation for r in records)
              and batch.calendar == scope.calendar and batch.series.symbol == scope.symbol
              and batch.series.from_inclusive == scope.window.start
              and batch.series.to_exclusive == scope.window.end, "INTEGRATION_BINDING")
    session = p.Transient(scope.lifecycle)
    session.accept(batch, mapping, scope.as_of)
    result = p.research(batch, mapping, scope.as_of, scope.window, costs, session)
    return {"certification": certified, "research": result, "cleanup": session.dispose(),
            "scope": "REPRESENTATIVE_ONE_SYMBOL_INTEGRATION", "full_universe_end_to_end": False}


def dataset_report(reports: list[dict[str, Any]]) -> dict[str, Any]:
    symbols = [r["symbol"] for r in reports]
    p.require(len(symbols) == len(set(symbols)), "DUPLICATE_SYMBOL")
    complete = bool(reports) and all(r["status"] == "SYNTHETIC_DATASET_CERTIFIED" for r in reports)
    status = "SYNTHETIC_DATASET_CERTIFIED" if complete else "SYNTHETIC_DATASET_PARTIAL"
    if any(r["status"] == "DATASET_INVALID" for r in reports):
        status = "DATASET_INVALID"
    expected_count = sum(r.get("expected", 0) for r in reports)
    valid = sum(r.get("valid", 0) for r in reports)
    return {"status": status, "expected": expected_count, "valid": valid,
            "coverage_percent": str(Decimal(100) * valid / expected_count)
            if expected_count else None,
            "missing": sum(r.get("missing_count", 0) for r in reports),
            "duplicates": sum(r.get("duplicate_count", 0) for r in reports),
            "invalid": sum(r.get("invalid_count", 0) for r in reports),
            "symbol_reports": sorted(reports, key=lambda r: r["symbol"]),
            "symbol_count": len(symbols), "canonical_symbol_count": len(load()["symbols"]),
            "full_universe_coverage": set(symbols) == set(load()["symbols"]),
            "genuine_performance": None, "genuine_qualified_pairs": 0}
