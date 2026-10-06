"""Bounded composition of unchanged v1 exports; no provider, database or file access."""
from datetime import timedelta
from typing import Literal

from pydantic import model_validator

from strategy_engine.backtest.dataset import (
    NSE,
    Bar,
    Calendar,
    Dataset,
    Day,
    Frozen,
    Hash,
    Instant,
    bar_fingerprint,
    digest,
)


class CorpusCalendar(Frozen):
    days: tuple[Day, ...]


class Corpus(Frozen):
    """Certified full range only. Missing dates/bars cannot become a silent subset."""

    schema_version: Literal["HistoricalCorpus.v1"] = "HistoricalCorpus.v1"
    from_inclusive: Instant
    to_exclusive: Instant
    calendars: tuple[Calendar, ...]
    shards: tuple[Dataset, ...]
    content_fingerprint: Hash

    @model_validator(mode="after")
    def valid(self) -> "Corpus":
        if not timedelta(0) < self.to_exclusive - self.from_inclusive <= timedelta(days=366):
            raise ValueError("CORPUS_RANGE_INVALID")
        if any(t.second or t.microsecond for t in (self.from_inclusive, self.to_exclusive)):
            raise ValueError("CORPUS_ALIGNMENT_INVALID")
        if not 1 <= len(self.shards) <= 366 or not 1 <= len(self.calendars) <= 12:
            raise ValueError("CORPUS_SIZE_INVALID")
        days = self.calendar.days
        first = self.from_inclusive.astimezone(NSE).date()
        last = (self.to_exclusive - timedelta(microseconds=1)).astimezone(NSE).date()
        expected_dates = tuple(first + timedelta(days=n) for n in range((last - first).days + 1))
        if tuple(d.date for d in days) != expected_dates:
            raise ValueError("CORPUS_CALENDAR_COVERAGE_INVALID")
        if any(d.status == "UNKNOWN_SESSION" for d in days):
            raise ValueError("CORPUS_UNKNOWN_SESSION")
        first_shard = self.shards[0]
        seen_days: dict[object, Day] = {}
        previous = self.from_inclusive
        for shard in self.shards:
            Dataset.model_validate(shard.model_dump())
            if any(getattr(shard, key) != getattr(first_shard, key) for key in (
                "instrument_id", "symbol", "exchange", "segment", "lot_size", "interval",
                "timestamp_semantics", "adjustment_policy",
            )):
                raise ValueError("CORPUS_IDENTITY_MISMATCH")
            if shard.from_inclusive < previous or shard.to_exclusive > self.to_exclusive:
                raise ValueError("CORPUS_SHARDS_OVERLAP_OR_OUTSIDE")
            previous = shard.to_exclusive
            active_dates = {b.start.astimezone(NSE).date() for b in shard.bars}
            for day in shard.calendar.days:
                if day.date in active_dates:
                    if day.date in seen_days:
                        raise ValueError("CORPUS_DUPLICATE_SESSION")
                    seen_days[day.date] = day
        expected_days = {d.date: d for d in days if d.status == "EXPECTED_SESSION"}
        if seen_days != expected_days:
            raise ValueError("CORPUS_SESSION_COVERAGE_INVALID")
        bars = self.bars
        if (len(bars) > 150_000
                or bar_fingerprint(self.instrument_id, bars) != self.content_fingerprint):
            raise ValueError("CORPUS_CONTENT_MISMATCH")
        return self

    @property
    def calendar(self) -> CorpusCalendar:
        return CorpusCalendar(days=tuple(d for cal in self.calendars for d in cal.days))

    @property
    def bars(self) -> tuple[Bar, ...]:
        return tuple(bar for shard in self.shards for bar in shard.bars)

    @property
    def instrument_id(self) -> str:
        return self.shards[0].instrument_id

    @property
    def interval(self) -> str:
        return "MINUTE"

    @property
    def lot_size(self) -> int:
        return self.shards[0].lot_size

    @property
    def dataset_cutoff(self) -> Instant:
        return max(shard.dataset_cutoff for shard in self.shards)

    @property
    def decision_cutoff(self) -> Instant:
        return self.to_exclusive

    @property
    def fingerprint(self) -> str:
        # Operational timestamps/reference refreshes do not change content identity.
        text = "historical-corpus-v1\n" + self.instrument_id + "\n"
        text += self.shards[0].symbol + "|NSE|CASH|MINUTE|" + str(self.lot_size) + "\n"
        text += self.from_inclusive.isoformat() + "\n" + self.to_exclusive.isoformat() + "\n"
        text += self.content_fingerprint + "\n"
        text += "\n".join(c.fingerprint for c in self.calendars) + "\n"
        text += "\n".join(s.content_fingerprint for s in self.shards) + "\n"
        text += "\n".join(sorted({
            p.source + "|" + p.source_version for s in self.shards for p in s.provenance
        })) + "\nINTERVAL_START\nUNSPECIFIED_NO_LOCAL_ADJUSTMENTS\n"
        return digest(text)


def calendar_subset(calendar: Calendar, days: tuple[Day, ...]) -> Calendar:
    text = calendar.version + "\n" + calendar.source + "\n"
    for day in days:
        sessions = ", ".join(
            f"Session[open={s.open:%H:%M}, close={s.close:%H:%M}]" for s in day.sessions
        )
        text += f"{day.date}=Day[status={day.status}, sessions=[{sessions}]]\n"
    return Calendar(version=calendar.version, source=calendar.source,
                    days=days, fingerprint=digest(text))


def slice_corpus(corpus: Corpus, start: Instant, end: Instant) -> Corpus:
    """Whole-session/shard boundaries only. No convenience access to unpinned latest data."""
    if not corpus.from_inclusive <= start < end <= corpus.to_exclusive:
        raise ValueError("PARTITION_OUTSIDE_CORPUS")
    shards = tuple(s for s in corpus.shards if start <= s.from_inclusive and s.to_exclusive <= end)
    first = start.astimezone(NSE).date()
    last = (end - timedelta(microseconds=1)).astimezone(NSE).date()
    calendars = tuple(calendar_subset(c, tuple(d for d in c.days if first <= d.date <= last))
                      for c in corpus.calendars if any(first <= d.date <= last for d in c.days))
    bars = tuple(b for s in shards for b in s.bars)
    return Corpus(from_inclusive=start, to_exclusive=end, calendars=calendars, shards=shards,
                  content_fingerprint=bar_fingerprint(corpus.instrument_id, bars))
