"""Synthetic compatibility evidence, never a Series replacement or adjustment engine."""

from datetime import datetime
from typing import Any, Literal

from pydantic import Field

from strategy_engine.backtest.dataset import Frozen, Hash, Instant
from strategy_engine.phase14.engine import Series
from strategy_engine.phase1439 import pipeline as p
from strategy_engine.phase1440.datasets import Action

SCENARIOS = ("NONE", "SPLIT", "BONUS", "DIVIDEND", "RIGHTS", "MERGER", "DEMERGER",
             "SYMBOL_CHANGE", "DELISTING", "SUSPENSION", "TOKEN_REPLACEMENT",
             "PROVIDER_ADJUSTED", "LOCALLY_ADJUSTED", "MIXED")


class Period(Frozen):
    security: str = Field(min_length=1)
    exchange: Literal["NSE"] = "NSE"
    symbol: str = Field(min_length=1)
    token: str = Field(pattern=r"^[1-9][0-9]{0,9}$")
    generation: Hash
    source: Hash
    start: Instant
    end: Instant
    observed: Instant
    available: Instant
    valid_until: Instant
    reason: Literal["INITIAL", "TOKEN_REPLACEMENT", "SYMBOL_CHANGE", "DELISTING",
                    "SUSPENSION", "MERGER", "DEMERGER"]
    transition: Hash | None = None


class Timeline(Frozen):
    version: Literal["Phase1441InstrumentIdentityTimeline.v1"] = (
        "Phase1441InstrumentIdentityTimeline.v1"
    )
    source_type: Literal["SYNTHETIC"] = "SYNTHETIC"
    security: str = Field(min_length=1)
    start: Instant
    end: Instant
    periods: tuple[Period, ...]
    availability: Literal["POINT_IN_TIME", "RETROSPECTIVELY_NORMALIZED"] = "POINT_IN_TIME"


def timeline(value: Timeline, as_of: datetime, registry: tuple[Period, ...] = ()) -> str:
    value = Timeline.model_validate(value.model_dump())
    p.require(bool(value.periods) and value.start < value.end <= as_of, "IDENTITY_WINDOW")
    previous = value.start
    for i, item in enumerate(value.periods):
        p.require(item.security == value.security, "SECURITY_MIX")
        p.require(item.start == previous and item.start < item.end <= value.end,
                  "IDENTITY_GAP_OR_OVERLAP")
        p.require(item.observed <= item.available <= as_of, "FUTURE_MAPPING_EVIDENCE")
        p.require(item.valid_until >= item.end, "STALE_MAPPING")
        if value.availability == "POINT_IN_TIME":
            p.require(item.available <= item.start, "RETROACTIVE_MAPPING")
        if i == 0:
            p.require(item.reason == "INITIAL", "INITIAL_IDENTITY")
        else:
            before = value.periods[i - 1]
            p.require(item.transition is not None and item.reason != "INITIAL",
                      "MISSING_TRANSITION_EVIDENCE")
            p.require(item.generation != before.generation, "UNCHANGED_MAPPING_GENERATION")
            if item.symbol != before.symbol:
                p.require(item.reason == "SYMBOL_CHANGE", "UNSUPPORTED_SYMBOL_TRANSITION")
            if item.token != before.token:
                p.require(item.reason in {"TOKEN_REPLACEMENT", "SYMBOL_CHANGE"},
                          "UNSUPPORTED_TOKEN_TRANSITION")
        for other in (*registry, *value.periods[:i]):
            if other.exchange == item.exchange and other.token == item.token:
                p.require(other.security == item.security, "TOKEN_REUSE_CONFLICT")
        previous = item.end
    p.require(previous == value.end, "IDENTITY_GAP_OR_OVERLAP")
    return p.fingerprint(value)


class Candidate(Frozen):
    version: Literal["Phase1441ResearchSeriesV2Candidate.v1"] = (
        "Phase1441ResearchSeriesV2Candidate.v1"
    )
    status: Literal["PROPOSED_NOT_ADOPTED"] = "PROPOSED_NOT_ADOPTED"
    identity: Timeline
    raw_segments: tuple[Hash, ...]
    source: Hash
    calendar: Hash
    actions_fingerprint: Hash
    actions: tuple[Action, ...] = ()
    action_source_complete: bool
    action_source_available: Instant
    basis: Literal["RAW_UNADJUSTED", "PROVIDER_ADJUSTED", "LOCALLY_ADJUSTED",
                   "ADJUSTMENT_UNVERIFIED", "MIXED"] = "RAW_UNADJUSTED"
    methodology: Literal["Phase1441AdjustmentMethodologyCandidate.v1:NOT_AUTHORIZED"] = (
        "Phase1441AdjustmentMethodologyCandidate.v1:NOT_AUTHORIZED"
    )
    retention: Hash
    window: Hash
    migration: Literal["NOT_AUTHORIZED"] = "NOT_AUTHORIZED"


def assess(candidate: Candidate, as_of: datetime) -> dict[str, Any]:
    candidate = Candidate.model_validate(candidate.model_dump())
    identity_hash = timeline(candidate.identity, as_of)
    p.require(len(candidate.raw_segments) == len(candidate.identity.periods), "SEGMENT_BINDING")
    p.require(candidate.action_source_complete, "ACTION_SOURCE_INCOMPLETE")
    p.require(candidate.action_source_available <= as_of, "FUTURE_ACTION_SOURCE")
    p.require(candidate.actions_fingerprint == p.sha(p.canonical(
        [a.model_dump(mode="json") for a in candidate.actions])), "ACTION_HASH")
    if candidate.identity.availability == "POINT_IN_TIME":
        p.require(candidate.action_source_available <= candidate.identity.start,
                  "RETROSPECTIVE_LABEL_REQUIRED")
    for action in candidate.actions:
        p.require(action.symbol in {x.symbol for x in candidate.identity.periods},
                  "ACTION_SECURITY")
        p.require(action.observed_at <= action.available_at <= as_of, "FUTURE_ACTION_EVIDENCE")
        p.require(action.announced_at is None or action.announced_at <= action.available_at,
                  "ANNOUNCEMENT_AVAILABILITY")
        p.require(candidate.identity.start <= action.effective_at < candidate.identity.end,
                  "ACTION_WINDOW")
        if candidate.identity.availability == "POINT_IN_TIME":
            p.require(action.available_at <= action.effective_at, "RETROSPECTIVE_LABEL_REQUIRED")
        if action.kind in {"SPLIT", "BONUS"}:
            p.require(action.ratio is not None and action.ratio > 0, "ACTION_RATIO")
        if action.kind == "DIVIDEND":
            p.require(action.amount is not None and action.amount >= 0, "ACTION_AMOUNT")
    supported = (len(candidate.identity.periods) == 1 and not candidate.actions
                 and candidate.basis == "RAW_UNADJUSTED"
                 and candidate.identity.availability == "POINT_IN_TIME")
    return {"candidate": p.fingerprint(candidate), "identity": identity_hash,
            "status": "PROPOSED_NOT_ADOPTED", "migration": "NOT_AUTHORIZED",
            "legacy": "ELIGIBLE_FOR_EXISTING_GATES" if supported else "UNSUPPORTED_CONVERSION",
            "availability": candidate.identity.availability,
            "adjustment": "NOT_AUTHORIZED", "genuine_performance": None,
            "genuine_qualified_pairs": 0, "real_kite_requests": 0}


def legacy_check(candidate: Candidate, series: Series, as_of: datetime) -> str:
    """Validate unchanged v1 bytes; never manufacture a v1 series from a timeline."""
    result = assess(candidate, as_of)
    p.require(result["legacy"] == "ELIGIBLE_FOR_EXISTING_GATES", "UNSUPPORTED_SERIES_V1")
    series = Series.model_validate(series.model_dump())
    item = candidate.identity.periods[0]
    p.require(series.synthetic and series.corporate_actions_reviewed, "LEGACY_EVIDENCE")
    p.require((series.symbol, series.instrument_token) == (item.symbol, item.token),
              "IDENTITY_BINDING")
    p.require(series.source_hash == candidate.source and series.master_hash == item.generation
              and series.calendar_hash == candidate.calendar, "SOURCE_MAPPING_BINDING")
    p.require(series.from_inclusive == candidate.identity.start
              and series.to_exclusive == candidate.identity.end, "WINDOW_BINDING")
    p.require(candidate.raw_segments == (p.fingerprint(series),), "SEGMENT_HASH")
    p.require(not any(series.quality()), "LEGACY_COMPLETENESS")
    return p.fingerprint(series)


def family(candidate: Candidate) -> str:
    """Conservative comparability: any source or methodology difference separates families."""
    return p.sha(p.canonical({"version": "Phase1441ResearchComparabilityPolicy.v1",
                              "family": "CANDIDATE_SERIES_V2",
                              "candidate": candidate.model_dump(mode="json")}))


def migration_approval(packet: dict[str, Any]) -> None:
    p.require(False, "METHODOLOGY_NOT_AUTHORIZED")


def matrix() -> list[dict[str, str]]:
    return [{"scenario": name,
             "evidence": "complete action source, identity, source basis and availability",
             "legacy": "EXISTING_GATES_ONLY" if name == "NONE" else "BLOCKED_PENDING_POLICY",
             "candidate": "PROPOSED_NOT_ADOPTED",
             "adjustment": "NO_TRANSFORM" if name == "NONE" else "NOT_AUTHORIZED",
             "comparability": "SEPARATE_IF_IDENTITY_BASIS_OR_AVAILABILITY_DIFFERS"}
            for name in SCENARIOS]
