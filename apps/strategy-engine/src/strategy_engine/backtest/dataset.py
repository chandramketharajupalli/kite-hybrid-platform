"""Versioned, immutable Java historical -> Python research boundary."""
import hashlib
import json
import re
from datetime import UTC, date, datetime, time, timedelta
from decimal import Decimal
from typing import Annotated, Any, Literal
from uuid import UUID
from zoneinfo import ZoneInfo

from pydantic import BaseModel, BeforeValidator, ConfigDict, Field, model_validator

NSE = ZoneInfo("Asia/Kolkata")
MINUTE = timedelta(minutes=1)


def decimal_text(value: Decimal) -> str:
    text = format(value, "f")
    return text.rstrip("0").rstrip(".") if "." in text else text


def parse_decimal(value: object) -> Decimal:
    if isinstance(value, Decimal):
        if not value.is_finite() or value.adjusted() > 17:
            raise ValueError("INVALID_DECIMAL")
        exponent = value.as_tuple().exponent
        if not isinstance(exponent, int) or exponent < -10:
            raise ValueError("INVALID_DECIMAL")
        value = decimal_text(value)
    if not isinstance(value, str) or not re.fullmatch(
        r"(?:0|[1-9][0-9]{0,17})(?:\.[0-9]{1,10})?", value
    ):
        raise ValueError("INVALID_DECIMAL")
    return Decimal(value)


def parse_instant(value: object) -> datetime:
    if isinstance(value, datetime):
        if value.tzinfo != UTC:
            raise ValueError("UTC_REQUIRED")
        return value
    if not isinstance(value, str) or not re.fullmatch(
        r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,6})?Z", value
    ):
        raise ValueError("UTC_REQUIRED")
    return datetime.fromisoformat(value)


def instant_text(value: datetime) -> str:
    return value.isoformat().replace("+00:00", "Z")


def parse_time(value: object) -> time:
    if isinstance(value, time):
        if value.second or value.microsecond or value.tzinfo:
            raise ValueError("MINUTE_TIME_REQUIRED")
        return value
    if not isinstance(value, str) or not re.fullmatch(r"[0-9]{2}:[0-9]{2}", value):
        raise ValueError("MINUTE_TIME_REQUIRED")
    return time.fromisoformat(value)


def parse_date(value: object) -> date:
    if type(value) is date:
        return value
    if not isinstance(value, str) or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", value):
        raise ValueError("ISO_DATE_REQUIRED")
    return date.fromisoformat(value)


Money = Annotated[Decimal, BeforeValidator(parse_decimal)]
Instant = Annotated[datetime, BeforeValidator(parse_instant)]
LocalMinute = Annotated[time, BeforeValidator(parse_time)]
TradingDate = Annotated[date, BeforeValidator(parse_date)]
Hash = Annotated[str, Field(pattern=r"^[a-f0-9]{64}$")]
Label = Annotated[str, Field(pattern=r"^[A-Za-z0-9_.-]{1,80}$")]
Quantity = Annotated[int, Field(strict=True, ge=1, le=2_147_483_647)]


class Frozen(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")


class Bar(Frozen):
    start: Instant
    open: Money
    high: Money
    low: Money
    close: Money
    volume: Annotated[int, Field(strict=True, ge=0, le=9_223_372_036_854_775_807)]
    open_interest: Money | None

    @model_validator(mode="after")
    def valid(self) -> "Bar":
        if self.start.second or self.start.microsecond or not 1990 <= self.start.year <= 2100:
            raise ValueError("INVALID_BAR_TIME")
        if not (0 < self.low <= min(self.open, self.close)
                <= max(self.open, self.close) <= self.high):
            raise ValueError("INVALID_OHLC")
        return self

    @property
    def end(self) -> datetime:
        return self.start + MINUTE


class Session(Frozen):
    open: LocalMinute
    close: LocalMinute

    @model_validator(mode="after")
    def valid(self) -> "Session":
        if self.open >= self.close:
            raise ValueError("INVALID_SESSION")
        return self


class Day(Frozen):
    date: TradingDate
    status: Literal["EXPECTED_SESSION", "NON_TRADING_DAY", "UNKNOWN_SESSION"]
    sessions: tuple[Session, ...]


class Calendar(Frozen):
    version: Label
    source: Annotated[str, Field(min_length=1, max_length=512)]
    fingerprint: Hash
    days: tuple[Day, ...]

    @model_validator(mode="after")
    def valid(self) -> "Calendar":
        if not self.source.strip() or any(ord(c) < 32 for c in self.source):
            raise ValueError("INVALID_PROVENANCE")
        if len(self.days) > 32 or tuple(sorted({d.date for d in self.days})) != tuple(
            d.date for d in self.days
        ):
            raise ValueError("INVALID_CALENDAR_ORDER")
        text = self.version + "\n" + self.source + "\n"
        for day in self.days:
            if (day.status == "EXPECTED_SESSION") != bool(day.sessions):
                raise ValueError("INVALID_CALENDAR")
            sessions = ", ".join(
                f"Session[open={s.open:%H:%M}, close={s.close:%H:%M}]" for s in day.sessions
            )
            text += f"{day.date}=Day[status={day.status}, sessions=[{sessions}]]\n"
        if digest(text) != self.fingerprint:
            raise ValueError("CALENDAR_FINGERPRINT_MISMATCH")
        return self


class Provenance(Frozen):
    source: Label
    source_version: Label
    reference_fingerprint: Hash
    calendar_fingerprint: Hash


def digest(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def bar_fingerprint(instrument: str, bars: tuple[Bar, ...]) -> str:
    lines = ["historical-bars-v1\n"]
    for bar in bars:
        fields = [instrument, "MINUTE", instant_text(bar.start)]
        fields += [decimal_text(v) for v in (bar.open, bar.high, bar.low, bar.close)]
        fields += [str(bar.volume), "ABSENT" if bar.open_interest is None
                   else decimal_text(bar.open_interest)]
        lines.append("|".join(fields) + "\n")
    return digest("".join(lines))


class Dataset(Frozen):
    schema_version: Literal["HistoricalResearchDataset.v1"]
    instrument_id: str
    exchange: Literal["NSE"]
    symbol: Annotated[str, Field(pattern=r"^[A-Z0-9&_.-]{1,128}$")]
    segment: Literal["CASH"]
    lot_size: Quantity
    interval: Literal["MINUTE"]
    timestamp_semantics: Literal["INTERVAL_START"]
    adjustment_policy: Literal["UNSPECIFIED_NO_LOCAL_ADJUSTMENTS"]
    from_inclusive: Instant
    to_exclusive: Instant
    decision_cutoff: Instant
    dataset_cutoff: Instant
    content_fingerprint: Hash
    calendar: Calendar
    provenance: tuple[Provenance, ...]
    bars: tuple[Bar, ...]

    @model_validator(mode="after")
    def valid(self) -> "Dataset":
        if str(UUID(self.instrument_id)) != self.instrument_id:
            raise ValueError("INVALID_INSTRUMENT_ID")
        if not timedelta(0) < self.to_exclusive - self.from_inclusive <= timedelta(days=31):
            raise ValueError("INVALID_WINDOW")
        if any(t.second or t.microsecond for t in (self.from_inclusive, self.to_exclusive)):
            raise ValueError("INVALID_WINDOW_ALIGNMENT")
        if self.decision_cutoff < self.to_exclusive or not self.provenance:
            raise ValueError("INCOMPLETE_DATASET")
        if len(self.bars) > 44_640 or not self.bars:
            raise ValueError("INVALID_DATASET_SIZE")
        if len(self.provenance) > 44_640 or any(
            p.calendar_fingerprint != self.calendar.fingerprint for p in self.provenance
        ):
            raise ValueError("PROVENANCE_CALENDAR_MISMATCH")
        provenance_keys = tuple(
            (p.source, p.source_version, p.reference_fingerprint, p.calendar_fingerprint)
            for p in self.provenance
        )
        if tuple(sorted(set(provenance_keys))) != provenance_keys:
            raise ValueError("PROVENANCE_MUST_BE_UNIQUE_AND_SORTED")
        if bar_fingerprint(self.instrument_id, self.bars) != self.content_fingerprint:
            raise ValueError("DATASET_FINGERPRINT_MISMATCH")
        days = {d.date: d for d in self.calendar.days}
        cursor = self.from_inclusive.astimezone(NSE).date()
        last = (self.to_exclusive - timedelta(microseconds=1)).astimezone(NSE).date()
        expected: list[datetime] = []
        while cursor <= last:
            day = days.get(cursor)
            if day is None or day.status == "UNKNOWN_SESSION":
                raise ValueError("UNKNOWN_SESSION")
            if day.status == "EXPECTED_SESSION":
                if len(day.sessions) != 1:
                    raise ValueError("SPLIT_SESSION_UNSUPPORTED")
                session = day.sessions[0]
                start = datetime.combine(cursor, session.open, NSE).astimezone(UTC)
                end = datetime.combine(cursor, session.close, NSE).astimezone(UTC)
                if start < self.from_inclusive or end > self.to_exclusive:
                    raise ValueError("PARTIAL_SESSION_UNSUPPORTED")
                while start < end:
                    expected.append(start)
                    start += MINUTE
            cursor += timedelta(days=1)
        if tuple(expected) != tuple(b.start for b in self.bars):
            raise ValueError("INCOMPLETE_OR_UNORDERED_SESSION")
        return self

    @classmethod
    def from_json(cls, raw: str) -> "Dataset":
        if len(raw) > 32_000_000:
            raise ValueError("DATASET_TOO_LARGE")

        def unique_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
            result: dict[str, Any] = {}
            for key, value in pairs:
                if key in result:
                    raise ValueError("DUPLICATE_JSON_KEY")
                result[key] = value
            return result

        return cls.model_validate(json.loads(raw, object_pairs_hook=unique_pairs))
