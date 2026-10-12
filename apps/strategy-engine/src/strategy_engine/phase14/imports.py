"""Validate pinned Java exports, then compose an offline five-minute research series."""

from datetime import UTC, date, datetime
from typing import Literal

from pydantic import Field, model_validator

from strategy_engine.backtest.dataset import (
    NSE,
    Calendar,
    Day,
    Frozen,
    Hash,
    Instant,
    decimal_text,
    digest,
    instant_text,
)
from strategy_engine.phase14.engine import Candle, Series, Session


class ExportProvenance(Frozen):
    source: str
    source_version: str
    reference_fingerprint: Hash
    calendar_fingerprint: Hash
    source_instrument_token: str
    reference_as_of: Instant


class FiveMinuteExport(Frozen):
    schema_version: Literal["HistoricalResearchDataset.five-minute.v1"]
    instrument_id: str
    instrument_token: str
    exchange: Literal["NSE"]
    symbol: str
    segment: Literal["CASH"]
    lot_size: int = Field(ge=1)
    interval: Literal["FIVE_MINUTE"]
    timestamp_semantics: Literal["INTERVAL_START"]
    adjustment_policy: Literal["UNSPECIFIED_NO_LOCAL_ADJUSTMENTS"]
    from_inclusive: Instant
    to_exclusive: Instant
    decision_cutoff: Instant
    dataset_cutoff: Instant
    content_fingerprint: Hash
    calendar: Calendar
    provenance: tuple[ExportProvenance, ...]
    bars: tuple[Candle, ...]

    @model_validator(mode="after")
    def valid(self) -> "FiveMinuteExport":
        if (
            self.schema_version != "HistoricalResearchDataset.five-minute.v1"
            or self.exchange != "NSE"
            or self.segment != "CASH"
            or self.interval != "FIVE_MINUTE"
            or self.timestamp_semantics != "INTERVAL_START"
            or self.adjustment_policy != "UNSPECIFIED_NO_LOCAL_ADJUSTMENTS"
            or not self.from_inclusive < self.to_exclusive <= self.decision_cutoff
            or self.decision_cutoff > self.dataset_cutoff
            or not self.provenance
        ):
            raise ValueError("INVALID_EXPORT_CONTRACT")
        text = "historical-bars-v1\n"
        for bar in self.bars:
            fields = [self.instrument_id, self.interval, instant_text(bar.start)]
            fields += [decimal_text(v) for v in (bar.open, bar.high, bar.low, bar.close)]
            fields += [
                str(bar.volume),
                "ABSENT" if bar.open_interest is None else decimal_text(bar.open_interest),
            ]
            text += "|".join(fields) + "\n"
        if digest(text) != self.content_fingerprint:
            raise ValueError("EXPORT_CONTENT_HASH_MISMATCH")
        if any(
            p.source_instrument_token != self.instrument_token
            or p.calendar_fingerprint != self.calendar.fingerprint
            or p.reference_as_of > self.dataset_cutoff
            for p in self.provenance
        ):
            raise ValueError("EXPORT_MAPPING_OR_CALENDAR_MISMATCH")
        return self


def compose(
    exports: tuple[FiveMinuteExport, ...],
    start: datetime,
    end: datetime,
    calendars: tuple[Calendar, ...],
    master_hash: str,
    *,
    synthetic: bool,
    corporate_actions_reviewed: bool,
) -> Series:
    """Compose explicit dated calendars (<=32 days each) including non-trading dates.

    Hashes provide consistency, not publisher authenticity or signatures.
    """
    if not exports or not calendars:
        raise ValueError("PINNED_CHUNK_CALENDARS_REQUIRED")
    first = exports[0]
    sessions = []
    closed = set()
    bars: list[Candle] = []
    days: dict[date, Day] = {}
    previous = start
    for chunk in exports:
        if (
            chunk.instrument_id != first.instrument_id
            or chunk.symbol != first.symbol
            or chunk.instrument_token != first.instrument_token
            or chunk.from_inclusive < previous
            or chunk.to_exclusive > end
        ):
            raise ValueError("EXPORT_IDENTITY_ORDER_OR_OVERLAP")
        previous = chunk.to_exclusive
        bars.extend(chunk.bars)
    for cal in (*calendars, *(chunk.calendar for chunk in exports)):
        for day in cal.days:
            if day.date in days and days[day.date] != day:
                raise ValueError("CALENDAR_CONFLICT")
            if day.date in days:
                continue
            days[day.date] = day
            if day.status == "UNKNOWN_SESSION":
                raise ValueError("UNKNOWN_CALENDAR")
            if day.status == "NON_TRADING_DAY":
                closed.add(day.date)
            for session in day.sessions:
                sessions.append(
                    Session(
                        start=datetime.combine(day.date, session.open, NSE).astimezone(UTC),
                        end=datetime.combine(day.date, session.close, NSE).astimezone(UTC),
                    )
                )
    return Series(
        symbol=first.symbol,
        instrument_token=first.instrument_token,
        source="JAVA_PINNED_EXPORT",
        synthetic=synthetic,
        source_hash=digest("\n".join(e.model_dump_json() for e in exports)),
        master_hash=master_hash,
        calendar_hash=digest("\n".join(c.fingerprint for c in calendars)),
        as_of=min(e.dataset_cutoff for e in exports),
        from_inclusive=start,
        to_exclusive=end,
        corporate_actions_reviewed=corporate_actions_reviewed,
        sessions=tuple(sorted(sessions, key=lambda s: s.start)),
        non_trading_days=tuple(sorted(closed)),
        bars=tuple(bars),
    )
