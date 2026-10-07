"""Read-only fixed-universe identity and fail-closed composition of v1 corpora."""
import hashlib
import struct
from datetime import date
from typing import Annotated, Literal
from uuid import UUID

from pydantic import Field, field_validator, model_validator

from strategy_engine.backtest.corpus import Corpus
from strategy_engine.backtest.dataset import NSE, Frozen, Hash, Instant, Label, digest
from strategy_engine.backtest.engine import canonical_json
from strategy_engine.research.experiments import Window

Count = Annotated[int, Field(strict=True, ge=0)]
Status = Literal[
    "CERTIFIED", "NOT_CERTIFIED", "INSUFFICIENT_REFERENCE",
    "CORPORATE_ACTION_UNRESOLVED", "DATA_GAP", "CONFLICT",
]


def cash_instrument_id(symbol: str) -> str:
    """Existing Java length-prefixed InstrumentIdentity v1; never token-derived."""
    fields = ("instrument-id:v1", "NSE", symbol, "CASH", "CASH", "", "")
    raw = b"".join(struct.pack(">I", len(s.encode())) + s.encode() for s in fields)
    return str(UUID(bytes=hashlib.md5(raw, usedforsecurity=False).digest(), version=3))


class InstrumentIdentity(Frozen):
    instrument_id: str
    symbol: Annotated[str, Field(pattern=r"^[A-Z0-9&-]{1,32}$")]
    exchange: Literal["NSE"] = "NSE"
    segment: Literal["CASH"] = "CASH"
    instrument_type: Literal["CASH"] = "CASH"
    broker: Literal["ZERODHA"] = "ZERODHA"
    broker_id: Annotated[str, Field(pattern=r"^[1-9][0-9]{0,9}$")]
    lot_size: Literal[1] = 1
    reference_fingerprint: Hash

    @model_validator(mode="after")
    def valid(self) -> "InstrumentIdentity":
        if self.instrument_id != cash_instrument_id(self.symbol):
            raise ValueError("PLATFORM_INSTRUMENT_ID_MISMATCH")
        if int(self.broker_id) > 0xffff_ffff:
            raise ValueError("INVALID_BROKER_ID")
        return self


class SourceEvidence(Frozen):
    url: Annotated[str, Field(min_length=8, max_length=2048)]
    version: Annotated[str, Field(min_length=1, max_length=512)]
    available_by: date
    observation_date: date
    claim: Annotated[str, Field(min_length=1, max_length=2048)]


class ResearchUniverseSpec(Frozen):
    schema_version: Literal["ResearchUniverse.v1"] = "ResearchUniverse.v1"
    universe_version: Label
    eligibility_date: date
    selection_evidence_date: date
    registered_on: date
    inclusion_criteria: str
    exclusion_criteria: str
    rationale: str
    source_evidence: tuple[SourceEvidence, ...]
    members: tuple[InstrumentIdentity, ...]
    common_window: Window
    calendar_fingerprint: Hash
    common_calendar_fingerprint: Hash
    interval: Literal["MINUTE"] = "MINUTE"

    @field_validator("members")
    @classmethod
    def ordered(cls, value: tuple[InstrumentIdentity, ...]) -> tuple[InstrumentIdentity, ...]:
        return tuple(sorted(value, key=lambda m: (m.exchange, m.symbol, m.instrument_id)))

    @field_validator("source_evidence")
    @classmethod
    def ordered_sources(cls, value: tuple[SourceEvidence, ...]) -> tuple[SourceEvidence, ...]:
        return tuple(sorted(value, key=canonical_json))

    @model_validator(mode="after")
    def valid(self) -> "ResearchUniverseSpec":
        if not 2 <= len(self.members) <= 10:
            raise ValueError("UNIVERSE_SIZE_INVALID")
        for key in ("instrument_id", "symbol", "broker_id"):
            if len({getattr(m, key) for m in self.members}) != len(self.members):
                raise ValueError("DUPLICATE_UNIVERSE_INSTRUMENT")
        start = self.common_window.start.astimezone(NSE).date()
        if not self.eligibility_date <= self.selection_evidence_date < start:
            raise ValueError("UNIVERSE_EVIDENCE_NOT_PREPERIOD")
        if not self.source_evidence or any(
            e.available_by > self.selection_evidence_date
            or e.observation_date > self.eligibility_date for e in self.source_evidence
        ):
            raise ValueError("UNIVERSE_EVIDENCE_NOT_PREPERIOD")
        if self.registered_on < self.selection_evidence_date:
            raise ValueError("REGISTRATION_DATE_INVALID")
        # Phase 12 has no July or later partition, regardless of symbol.
        if self.common_window.end.astimezone(NSE).date() > date(2026, 7, 1):
            raise ValueError("PHASE120_JULY_OR_LATER_DENIED")
        return self

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


class MemberCertification(Frozen):
    identity: InstrumentIdentity
    status: Status
    expected_sessions: Count
    expected_bars: Count
    actual_sessions: Count | None = None
    actual_bars: Count | None = None
    gaps: Count | None = None
    duplicates: Count | None = None
    conflicts: Count | None = None
    unexpected_timestamps: Count | None = None
    excluded_sessions: tuple[date, ...] = ()
    first_timestamp: Instant | None = None
    last_timestamp: Instant | None = None
    corpus_fingerprint: Hash | None = None
    content_fingerprint: Hash | None = None
    corporate_action_status: Literal["REVIEWED", "UNRESOLVED"]
    corporate_action_note: Annotated[str, Field(min_length=1)]
    provenance: tuple[str, ...]

    @model_validator(mode="after")
    def valid(self) -> "MemberCertification":
        if self.status == "CERTIFIED" and (
            self.corporate_action_status != "REVIEWED"
            or self.actual_sessions != self.expected_sessions
            or self.actual_bars != self.expected_bars
            or not self.actual_sessions or not self.actual_bars
            or (self.gaps, self.duplicates, self.conflicts, self.unexpected_timestamps)
            != (0, 0, 0, 0)
            or self.excluded_sessions or not self.corpus_fingerprint or not self.content_fingerprint
            or self.first_timestamp is None or self.last_timestamp is None or not self.provenance
        ):
            raise ValueError("MEMBER_NOT_CERTIFIED")
        return self


class MultiInstrumentCorpusManifest(Frozen):
    schema_version: Literal["MultiInstrumentCorpusManifest.v1"] = "MultiInstrumentCorpusManifest.v1"
    universe: ResearchUniverseSpec
    members: tuple[MemberCertification, ...]

    @field_validator("members")
    @classmethod
    def ordered(cls, value: tuple[MemberCertification, ...]) -> tuple[MemberCertification, ...]:
        return tuple(sorted(value, key=lambda m: (m.identity.exchange, m.identity.symbol,
                                                  m.identity.instrument_id)))

    @model_validator(mode="after")
    def valid(self) -> "MultiInstrumentCorpusManifest":
        if tuple(m.identity for m in self.members) != self.universe.members:
            raise ValueError("MANIFEST_MEMBER_SET_MISMATCH")
        if len({(m.expected_sessions, m.expected_bars) for m in self.members}) != 1:
            raise ValueError("MANIFEST_EXPECTATIONS_DIFFER")
        return self

    @property
    def status(self) -> str:
        complete = all(m.status == "CERTIFIED" for m in self.members)
        return "CERTIFIED" if complete else "NOT_CERTIFIED"

    @property
    def fingerprint(self) -> str:
        # Failed manifests have an evidence identity, not a certified aggregate corpus.
        return digest(canonical_json(self))

    @property
    def aggregate_corpus_fingerprint(self) -> str | None:
        return self.fingerprint if self.status == "CERTIFIED" else None


class MultiInstrumentResearchCorpus(Frozen):
    manifest: MultiInstrumentCorpusManifest
    members: tuple[Corpus, ...]

    @field_validator("members")
    @classmethod
    def ordered(cls, value: tuple[Corpus, ...]) -> tuple[Corpus, ...]:
        return tuple(sorted(value, key=lambda c: (c.shards[0].symbol, c.instrument_id)))

    @model_validator(mode="after")
    def valid(self) -> "MultiInstrumentResearchCorpus":
        manifest = MultiInstrumentCorpusManifest.model_validate(self.manifest.model_dump())
        if manifest.status != "CERTIFIED":
            raise ValueError("UNIVERSE_NOT_CERTIFIED")
        if tuple(c.instrument_id for c in self.members) != tuple(
            m.identity.instrument_id for m in manifest.members
        ):
            raise ValueError("CORPUS_MEMBER_SET_MISMATCH")
        for corpus, evidence in zip(self.members, manifest.members, strict=True):
            Corpus.model_validate(corpus.model_dump())
            universe = manifest.universe
            if (corpus.from_inclusive, corpus.to_exclusive) != (
                universe.common_window.start, universe.common_window.end
            ):
                raise ValueError("COMMON_WINDOW_MISMATCH")
            if corpus.calendars != self.members[0].calendars:
                raise ValueError("SHARED_CALENDAR_MISMATCH")
            common_pin = digest("\n".join(c.fingerprint for c in corpus.calendars))
            if common_pin != universe.common_calendar_fingerprint:
                raise ValueError("COMMON_CALENDAR_PIN_MISMATCH")
            bars = corpus.bars
            if (corpus.fingerprint != evidence.corpus_fingerprint
                    or corpus.content_fingerprint != evidence.content_fingerprint
                    or len(bars) != evidence.actual_bars
                    or sum(d.status == "EXPECTED_SESSION" for d in corpus.calendar.days)
                    != evidence.actual_sessions
                    or bars[0].start != evidence.first_timestamp
                    or bars[-1].start != evidence.last_timestamp
                    or corpus.shards[0].symbol != evidence.identity.symbol):
                raise ValueError("MEMBER_EVIDENCE_MISMATCH")
        return self

    @property
    def fingerprint(self) -> str:
        return self.manifest.fingerprint
