"""Offline Phase 12 registration, export verification, study freeze and baseline replay.

No authentication, network, database, candidate evaluation or TEST command.
Registration consumes the separately reviewed local broker reference snapshot.
"""
import argparse
import csv
import hashlib
import json
import sys
from datetime import date
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker
from run_phase114 import ROOT, bounded, local, pin, unique
from run_phase116 import verify_freeze

from strategy_engine.backtest.corpus import Corpus, calendar_subset
from strategy_engine.backtest.dataset import Calendar, Dataset, Day, bar_fingerprint, digest
from strategy_engine.backtest.engine import Result, canonical_json
from strategy_engine.research.development import DevelopmentWalkForwardSpec
from strategy_engine.research.multi_instrument import (
    MultiInstrumentBaselineStudySpec,
    evaluate_study,
)
from strategy_engine.research.universe import (
    InstrumentIdentity,
    MemberCertification,
    MultiInstrumentCorpusManifest,
    MultiInstrumentResearchCorpus,
    ResearchUniverseSpec,
    SourceEvidence,
    cash_instrument_id,
)
from strategy_engine.research.walkforward import WalkForwardSpec

OUT = ROOT / "research/phase-12.0"
LOCAL = ROOT / "data/phase120"
SYMBOLS = ("HDFCBANK", "ICICIBANK", "LT", "RELIANCE", "SBIN")
CALENDAR_PIN = "22c28c857547dbc81d7796a4b083edb84b0239bbf76cc9b4cc9fcfd04eb77f3f"
FREEZE_PIN = "56a1bd05bbbec36f639450f2f52c491c8c5436c16094f86bcf0c2818d4f63656"
NOTES = {
    "HDFCBANK": "Issuer financial calendar: dividend record date 2026-06-19. Full-window "
    "split/bonus/merger/symbol/identity review incomplete; absence not established.",
    "ICICIBANK": "2026-04-18 issuer filing recommends INR 12 dividend. Recommendation "
    "does not establish in-window ex-date or complete absence of other corporate actions.",
    "LT": "FY2026 results recommend INR 38 dividend and describe a Realty Undertaking "
    "scheme with appointed date 2026-04-01, subject to conditions. Continuity impact unresolved.",
    "RELIANCE": "2025-26 annual report describes 2024 bonus, outside the window. This "
    "does not establish complete February-June 2026 action/identity continuity.",
    "SBIN": "Issuer notice records INR 17.35 dividend, record date 2026-05-16 and payment "
    "2026-06-04. Retained Phase 11 bar integrity is separate from a complete action review.",
}
SOURCES = {
    "HDFCBANK": "https://www.hdfc.bank.in/about-us/corporate-governance/financial-calendar",
    "ICICIBANK": "https://nsearchives.nseindia.com/corporate/ICICI2022_18042026145407_NSEBSE_18042026.pdf",
    "LT": "https://investors.larsentoubro.com/upload/Quarterly/FY2026QuarterlyLTResultMarch2026.pdf",
    "RELIANCE": "https://www.ril.com/reports/RIL-Integrated-Annual-Report-2025-26.pdf",
    "SBIN": "https://nsearchives.nseindia.com/corporate/"
    "SBIN_08052026154613_BSE_NSE_DividendRecordDate_08052026.pdf",
}


def canonical(value: object) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True,
                      allow_nan=False)


def calendar() -> tuple[Calendar, tuple[Calendar, ...]]:
    source = json.loads(bounded(ROOT / "research/phase-11.4/calendar.json", 256_000),
                        object_pairs_hook=unique)
    days = tuple(Day.model_validate(d) for d in source["days"])
    # Calendar v1 bounds each segment at 31 days. Use the retained first shard as
    # a validated prototype, then independently hash the original whole calendar.
    prototype = Dataset.from_json(bounded(ROOT / "data/phase114-sbin/2026-02-02.json",
                                           2_000_000)).calendar
    if (prototype.version, prototype.source) != (source["version"], source["source"]):
        raise ValueError("CALENDAR_SOURCE_MISMATCH")
    # calendar_subset enforces <=31 days, so global hash follows its Java text convention.
    def fingerprint(selected: tuple[Day, ...]) -> str:
        text = source["version"] + "\n" + source["source"] + "\n"
        for day in selected:
            sessions = ", ".join(f"Session[open={s.open:%H:%M}, close={s.close:%H:%M}]"
                                 for s in day.sessions)
            text += f"{day.date}=Day[status={day.status}, sessions=[{sessions}]]\n"
        return digest(text)
    if fingerprint(days) != CALENDAR_PIN:
        raise ValueError("PHASE114_CALENDAR_CHANGED")
    months = tuple(calendar_subset(prototype, tuple(d for d in days if d.date.month == month))
                   for month in range(2, 7))
    return prototype, months


def load_member(folder: Path, member: InstrumentIdentity, *, allow_phase114_parent: bool = False
                ) -> Corpus:
    manifest = json.loads(bounded(folder / "manifest.json", 256_000), object_pairs_hook=unique)
    if (manifest["schema"] != "HistoricalCorpusManifest.v1"
            or manifest["quality"] != "COMPLETE_CANONICAL_SESSIONS"
            or manifest["calendarFingerprint"] != CALENDAR_PIN):
        raise ValueError("EXPORT_MANIFEST_NOT_CERTIFIED")
    if allow_phase114_parent:
        parent = json.loads(bounded(ROOT / "research/phase-11.4/manifest.json", 256_000),
                            object_pairs_hook=unique)
        if (member.symbol != "SBIN" or folder != ROOT / "data/phase114-sbin"
                or manifest != parent):
            raise ValueError("PHASE114_REUSE_PARENT_MISMATCH")
    _, calendars = calendar()
    expected = {f"{day.date}.json" for cal in calendars for day in cal.days
                if day.status == "EXPECTED_SESSION"}
    entries = [e for e in manifest["sessions"] if e["file"] in expected]
    if len(entries) != len(expected) or {e["file"] for e in entries} != expected:
        raise ValueError("EXPORT_SESSION_COVERAGE_INVALID")
    if not allow_phase114_parent and len(manifest["sessions"]) != len(expected):
        raise ValueError("OUTSIDE_WINDOW_EXPORT_DENIED")
    schema = json.loads((ROOT / "contracts/schemas/v1/HistoricalResearchDataset.v1.schema.json")
                        .read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    shards = []
    for entry in sorted(entries, key=lambda e: e["file"]):
        # Only calendar-derived Feb-Jun filenames are ever opened. No July price read.
        raw = bounded(folder / entry["file"], 2_000_000)
        if hashlib.sha256(raw.encode()).hexdigest() != entry["artifactFingerprint"]:
            raise ValueError("EXPORT_ARTIFACT_PIN_MISMATCH")
        validator.validate(json.loads(raw, object_pairs_hook=unique))
        shard = Dataset.from_json(raw)
        if (shard.instrument_id != member.instrument_id or shard.symbol != member.symbol
                or shard.exchange != "NSE" or shard.segment != "CASH" or shard.lot_size != 1
                or any(p.source != "KITE" for p in shard.provenance)
                or shard.content_fingerprint != entry["contentFingerprint"]
                or len(shard.bars) != entry["bars"]):
            raise ValueError("EXPORT_IDENTITY_OR_CONTENT_MISMATCH")
        shards.append(shard)
    corpus = Corpus(from_inclusive=local("2026-02-02"), to_exclusive=local("2026-07-01"),
                    calendars=calendars, shards=tuple(shards),
                    content_fingerprint=bar_fingerprint(member.instrument_id,
                        tuple(b for s in shards for b in s.bars)))
    if not allow_phase114_parent and corpus.content_fingerprint != manifest["contentFingerprint"]:
        raise ValueError("EXPORT_MANIFEST_CONTENT_PIN_MISMATCH")
    return corpus


def register() -> None:
    reference = ROOT / "data/phase120-reference/instruments.csv"
    if reference.stat().st_size > 40_000_000:
        raise ValueError("REFERENCE_TOO_LARGE")
    reference_hash = hashlib.sha256(reference.read_bytes()).hexdigest()
    with reference.open(encoding="utf-8", newline="") as stream:
        rows = [r for r in csv.DictReader(stream) if r["exchange"] == "NSE"
                and r["segment"] == "NSE" and r["instrument_type"] == "EQ"
                and r["tradingsymbol"] in SYMBOLS]
    if len(rows) != 5 or {r["tradingsymbol"] for r in rows} != set(SYMBOLS):
        raise ValueError("REFERENCE_MEMBERS_INCOMPLETE")
    members = tuple(InstrumentIdentity(instrument_id=cash_instrument_id(r["tradingsymbol"]),
        symbol=r["tradingsymbol"], broker_id=r["instrument_token"],
        lot_size=int(r["lot_size"]), reference_fingerprint=reference_hash) for r in rows)
    _, calendars = calendar()
    # Identity for the ordered shared bounded calendar segments, not a second calendar definition.
    common_pin = digest("\n".join(c.fingerprint for c in calendars))
    universe = ResearchUniverseSpec.model_validate({
        "universe_version": "phase120-generation1", "eligibility_date": "2025-06-30",
        "selection_evidence_date": "2025-09-30", "registered_on": "2026-10-07",
        "inclusion_criteria": "Fixed purposive NSE cash sample; June 2025 Nifty50 membership "
        "as dated liquidity proxy; no strategy performance or future-volume selection.",
        "exclusion_criteria": "No replacements: any missing/ambiguous identity, split/bonus or "
        "unresolved continuity/data evidence makes the whole universe NOT_CERTIFIED.",
        "rationale": "Three banks including SBIN control, construction and energy; infrastructure "
        "coverage only, not unbiased population inference or independent temporal trials.",
        "source_evidence": (SourceEvidence(
            url="https://niftyindices.com/docs/default-source/indices/nifty-50/"
            "nifty-50-whitepaper_2025.pdf", version="Series4-Sep2025-Exhibit10-page8",
            available_by=date(2025, 9, 30), observation_date=date(2025, 6, 30),
            claim="Exhibit10 identifies all five selected companies as members through June 2025."
        ),), "members": members,
        "common_window": {"start": local("2026-02-02"), "end": local("2026-07-01")},
        "calendar_fingerprint": CALENDAR_PIN, "common_calendar_fingerprint": common_pin})
    OUT.mkdir(parents=True, exist_ok=True)
    pin(OUT / "universe.json", canonical_json(universe))
    quality = []
    for member in universe.members:
        values = dict(identity=member, status="CORPORATE_ACTION_UNRESOLVED",
            expected_sessions=99, expected_bars=37125, corporate_action_status="UNRESOLVED",
            corporate_action_note=NOTES[member.symbol], provenance=(SOURCES[member.symbol],))
        if member.symbol == "SBIN":
            corpus = load_member(ROOT / "data/phase114-sbin", member, allow_phase114_parent=True)
            values.update(actual_sessions=len(corpus.shards), actual_bars=len(corpus.bars),
                gaps=0, duplicates=0, conflicts=0, unexpected_timestamps=0,
                first_timestamp=corpus.bars[0].start, last_timestamp=corpus.bars[-1].start,
                corpus_fingerprint=corpus.fingerprint,
                content_fingerprint=corpus.content_fingerprint)
        quality.append(MemberCertification.model_validate(values))
    manifest = MultiInstrumentCorpusManifest(universe=universe, members=tuple(quality))
    pin(OUT / "corpus-manifest.json", canonical_json(manifest))
    plan = {"schema_version": "Phase120AcquisitionPlan.v1",
        "universe_fingerprint": universe.fingerprint, "calendar_fingerprint": CALENDAR_PIN,
        "common_window": universe.common_window.model_dump(mode="json"),
        "state": "BLOCKED_CORPORATE_ACTION_REVIEW", "executable_historical_budget": 0,
        "conditional_historical_maximum": 495, "conditional_profile_maximum": 1,
        "reference_maximum": 1, "request_spacing_seconds": 1, "retry_maximum": 0,
        "members": [{"identity": m.model_dump(mode="json"), "expected_sessions": 99,
            "expected_bars": 37125, "maximum_historical_gets": 99,
            "chunks": [{"date": str(d.date), "open": "09:15", "close": "15:30",
                        "expected_bars": 375} for c in calendars for d in c.days
                       if d.status == "EXPECTED_SESSION"]} for m in universe.members]}
    pin(OUT / "acquisition-plan.json", canonical(plan))
    evidence = {"schema_version": "Phase120Readiness.v1",
        "primary_status": "MULTI_INSTRUMENT_CORPUS_NOT_CERTIFIED",
        "universe_fingerprint": universe.fingerprint,
        "manifest_fingerprint": manifest.fingerprint, "aggregate_corpus_fingerprint": None,
        "acquisition_plan_fingerprint": digest(canonical(plan)),
        "historical_gets": 0, "profile_gets": 0, "reference_gets": 1,
        "reference_source": "https://api.kite.trade/instruments",
        "reference_access_date": "2026-10-07", "reference_fingerprint": reference_hash,
        "certified_members": 0, "declared_members": 5, "baseline_evaluations": 0,
        "replay": "OFFLINE_SBIN_EXPORT_VERIFICATION_ONLY_NO_ACQUISITION_REPLAY",
        "h1_h2": "FROZEN_NOT_EVALUATED_CROSS_INSTRUMENT", "sbin_july": "SEALED",
        "selection": "NONE", "corpus_blocker": "Complete corporate-action and identity "
        "continuity evidence not established for every member. NSE dynamic pages returned no "
        "rows; bounded dated API requests were unavailable via the web research tool.",
        "safety": {k: 0 for k in ("real_order_mutations", "account_trading_reads", "websocket",
            "development_db_access", "development_db_mutations", "token_access", "token_mutations",
            "halt_resume", "execution_arm", "execute_calls", "live_paper_execution")}}
    pin(OUT / "readiness.json", canonical(evidence))
    print(canonical(evidence))


def sources() -> str:
    paths = [Path(__file__)] + [ROOT / "apps/strategy-engine/src/strategy_engine/research" / name
        for name in ("universe.py", "development.py", "multi_instrument.py")]
    return digest(canonical({p.relative_to(ROOT).as_posix(): digest(p.read_text(encoding="utf-8"))
                             for p in sorted(paths)}))


def verified() -> MultiInstrumentResearchCorpus:
    universe = ResearchUniverseSpec.model_validate_json(bounded(OUT / "universe.json", 256_000))
    manifest = MultiInstrumentCorpusManifest.model_validate_json(
        bounded(OUT / "corpus-manifest.json", 256_000))
    plan = json.loads(bounded(OUT / "acquisition-plan.json", 256_000), object_pairs_hook=unique)
    if universe.fingerprint != plan["universe_fingerprint"]:
        raise ValueError("FROZEN_UNIVERSE_PLAN_MISMATCH")
    if universe != manifest.universe or manifest.status != "CERTIFIED":
        raise ValueError("UNIVERSE_NOT_CERTIFIED_NO_STRATEGY_EVALUATION")
    return MultiInstrumentResearchCorpus(manifest=manifest, members=tuple(
        load_member(LOCAL / m.symbol, m) for m in universe.members))


def study_spec(corpus: MultiInstrumentResearchCorpus) -> MultiInstrumentBaselineStudySpec:
    parent = WalkForwardSpec.model_validate_json(
        bounded(ROOT / "research/phase-11.4/walkforward-spec.json", 256_000))
    return MultiInstrumentBaselineStudySpec(
        study_version="phase120-generation1",
        universe_fingerprint=corpus.manifest.universe.fingerprint,
        aggregate_corpus_fingerprint=corpus.fingerprint,
        specifications=tuple(DevelopmentWalkForwardSpec(
            research_generation="phase120-generation1", corpus_fingerprint=c.fingerprint,
            implementation_fingerprint=sources(),
            common_window=corpus.manifest.universe.common_window,
            folds=parent.folds, strategies=parent.strategies, config=parent.config)
            for c in corpus.members))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("register", "verify", "freeze", "evaluate"))
    args = parser.parse_args()
    freeze_path = ROOT / "research/phase-11.6/implementation-freeze-g1.json"
    if hashlib.sha256(freeze_path.read_bytes()).hexdigest() != FREEZE_PIN:
        raise ValueError("H1_H2_FREEZE_FILE_CHANGED")
    verify_freeze(freeze_path)
    if args.mode == "register":
        register()
        return
    corpus = verified()
    if args.mode == "verify":
        print(corpus.fingerprint)
        return
    spec = study_spec(corpus)
    if args.mode == "freeze":
        pin(OUT / "study-spec.json", canonical_json(spec))
        print(spec.fingerprint)
        return
    frozen = MultiInstrumentBaselineStudySpec.model_validate_json(
        bounded(OUT / "study-spec.json", 256_000))
    if frozen != spec:
        raise ValueError("STUDY_SOURCE_OR_SPEC_CHANGED")
    result_folder = LOCAL / "results"
    result_folder.mkdir(parents=True, exist_ok=True)
    def retain(result: Result) -> None:
        pin(result_folder / f"{result.fingerprint}.json", canonical_json(result))
    report = evaluate_study(corpus, frozen, retain)
    pin(OUT / "baseline-report.json", canonical_json(report))
    print(canonical({"study_fingerprint": spec.fingerprint,
                     "report_fingerprint": report.fingerprint}))


if __name__ == "__main__":
    try:
        main()
    except ValueError as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1) from None
