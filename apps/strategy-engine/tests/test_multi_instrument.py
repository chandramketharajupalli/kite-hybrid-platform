"""Synthetic safety/identity checks; never real prices or broker connectivity."""
import ast
from datetime import date, timedelta
from decimal import ROUND_UP, Context, Decimal, localcontext
from pathlib import Path

import pytest
from test_research import settings
from test_walkforward import fixture

from strategy_engine.backtest.corpus import Corpus, calendar_subset, slice_corpus
from strategy_engine.backtest.dataset import Bar, Dataset, Day, bar_fingerprint, digest
from strategy_engine.backtest.engine import canonical_json
from strategy_engine.research.development import (
    DevelopmentReport,
    DevelopmentWalkForwardSpec,
    evaluate_development,
)
from strategy_engine.research.experiments import Window
from strategy_engine.research.multi_instrument import (
    MinimumSamples,
    MultiInstrumentBaselineReport,
    MultiInstrumentBaselineStudySpec,
    category,
    evaluate_study,
)
from strategy_engine.research.strategy import BaselineSpec
from strategy_engine.research.universe import (
    InstrumentIdentity,
    MemberCertification,
    MultiInstrumentCorpusManifest,
    MultiInstrumentResearchCorpus,
    ResearchUniverseSpec,
    SourceEvidence,
    cash_instrument_id,
)
from strategy_engine.research.walkforward import Fold


def synthetic(symbol: str) -> Corpus:
    original = fixture()
    shift = timedelta(days=-150)
    values = original.model_dump()
    values["from_inclusive"] += shift
    values["to_exclusive"] += shift
    for shard in values["shards"]:
        shard["symbol"] = symbol
        shard["instrument_id"] = cash_instrument_id(symbol)
        for key in ("from_inclusive", "to_exclusive", "decision_cutoff", "dataset_cutoff"):
            shard[key] += shift
        for bar in shard["bars"]:
            bar["start"] += shift
        cal = original.shards[0].calendar
        days = tuple(Day.model_validate({**d, "date": d["date"] + shift})
                     for d in shard["calendar"]["days"])
        calendar = calendar_subset(cal, days)
        shard["calendar"] = calendar.model_dump()
        for provenance in shard["provenance"]:
            provenance["calendar_fingerprint"] = calendar.fingerprint
        shard["content_fingerprint"] = bar_fingerprint(shard["instrument_id"],
            tuple(Bar.model_validate(b) for b in shard["bars"]))
    for cal_values, cal in zip(values["calendars"], original.calendars, strict=True):
        days = tuple(Day.model_validate({**d, "date": d["date"] + shift})
                     for d in cal_values["days"])
        cal_values.update(calendar_subset(cal, days).model_dump())
    shards = tuple(Dataset.model_validate(s) for s in values["shards"])
    values["content_fingerprint"] = bar_fingerprint(cash_instrument_id(symbol),
        tuple(b for s in shards for b in s.bars))
    return Corpus.model_validate(values)


def identity(symbol: str, token: str) -> InstrumentIdentity:
    return InstrumentIdentity(instrument_id=cash_instrument_id(symbol), symbol=symbol,
                              broker_id=token, reference_fingerprint="a" * 64)


def composition() -> MultiInstrumentResearchCorpus:
    corpora = (synthetic("HDFCBANK"), synthetic("SBIN"))
    first = corpora[0]
    identities = (identity("HDFCBANK", "1"), identity("SBIN", "2"))
    universe = ResearchUniverseSpec(
        universe_version="synthetic-v1", eligibility_date=date(2025, 6, 30),
        selection_evidence_date=date(2025, 9, 30), registered_on=date(2026, 10, 7),
        inclusion_criteria="synthetic only", exclusion_criteria="fail whole universe",
        rationale="tests only", source_evidence=(SourceEvidence(
            url="https://example.invalid/synthetic", version="synthetic-only",
            available_by=date(2025, 9, 30), observation_date=date(2025, 6, 30), claim="fixture"),),
        members=identities, common_window=Window(start=first.from_inclusive,
                                                end=first.to_exclusive),
        calendar_fingerprint="c" * 64,
        common_calendar_fingerprint=digest("\n".join(c.fingerprint for c in first.calendars)))
    evidence = tuple(MemberCertification(
        identity=i, status="CERTIFIED", expected_sessions=4, expected_bars=32,
        actual_sessions=4, actual_bars=32, gaps=0, duplicates=0, conflicts=0,
        unexpected_timestamps=0, first_timestamp=c.bars[0].start, last_timestamp=c.bars[-1].start,
        corpus_fingerprint=c.fingerprint, content_fingerprint=c.content_fingerprint,
        corporate_action_status="REVIEWED", corporate_action_note="synthetic fixture only",
        provenance=("synthetic",)) for i, c in zip(identities, corpora, strict=True))
    return MultiInstrumentResearchCorpus(
        manifest=MultiInstrumentCorpusManifest(universe=universe, members=evidence),
        members=corpora)


def definition(corpus: Corpus) -> DevelopmentWalkForwardSpec:
    parameters = (
        {"kind": "EMA_CROSS", "quantity": 1, "fast": 9, "slow": 21},
        {"kind": "VWAP_CROSS", "quantity": 1},
        {"kind": "OPENING_RANGE", "quantity": 1, "opening_bars": 15},
        {"kind": "RSI_RECOVERY", "quantity": 1, "lookback": 14,
         "oversold": "30", "exit_threshold": "50"})
    start = corpus.from_inclusive
    return DevelopmentWalkForwardSpec(
        research_generation="synthetic-v1", corpus_fingerprint=corpus.fingerprint,
        implementation_fingerprint="e" * 64,
        common_window=Window(start=start, end=corpus.to_exclusive),
        folds=tuple(Fold(train=Window(start=start, end=start + timedelta(days=n)),
                         validation=Window(start=start + timedelta(days=n),
                                           end=start + timedelta(days=n + 1))) for n in (1, 2, 3)),
        strategies=tuple(BaselineSpec.model_validate({"version": "v1",
            "implementation_fingerprint": "b" * 64, "parameters": p}) for p in parameters),
        config=settings(corpus.shards[0]))


def study(corpus: MultiInstrumentResearchCorpus) -> MultiInstrumentBaselineStudySpec:
    return MultiInstrumentBaselineStudySpec(
        study_version="synthetic-v1", universe_fingerprint=corpus.manifest.universe.fingerprint,
        aggregate_corpus_fingerprint=corpus.fingerprint,
        specifications=tuple(definition(c) for c in corpus.members))


def test_platform_id_matches_existing_java_sbin_identity() -> None:
    assert cash_instrument_id("SBIN") == "050f94dd-e639-364f-97a6-595594de6543"


def test_canonical_universe_and_aggregate_order_roundtrip_and_member_sensitivity() -> None:
    corpus = composition()
    values = corpus.model_dump()
    values["members"] = tuple(reversed(values["members"]))
    values["manifest"]["members"] = tuple(reversed(values["manifest"]["members"]))
    values["manifest"]["universe"]["members"] = tuple(
        reversed(values["manifest"]["universe"]["members"]))
    assert MultiInstrumentResearchCorpus.model_validate(values).fingerprint == corpus.fingerprint
    universe = corpus.manifest.universe
    restored = ResearchUniverseSpec.model_validate_json(canonical_json(universe))
    assert restored == universe and restored.fingerprint == universe.fingerprint
    values = universe.model_dump()
    values["members"][0]["broker_id"] = "3"
    assert ResearchUniverseSpec.model_validate(values).fingerprint != universe.fingerprint
    values = universe.model_dump()
    values["members"] = (identity("LT", "3").model_dump(), values["members"][1])
    assert ResearchUniverseSpec.model_validate(values).fingerprint != universe.fingerprint


@pytest.mark.parametrize("field", ["instrument_id", "symbol", "broker_id"])
def test_duplicate_or_inconsistent_identity_denied(field: str) -> None:
    values = composition().manifest.universe.model_dump()
    values["members"][1][field] = values["members"][0][field]
    with pytest.raises(ValueError):
        ResearchUniverseSpec.model_validate(values)


@pytest.mark.parametrize("status", ["NOT_CERTIFIED", "DATA_GAP", "CONFLICT",
                                    "CORPORATE_ACTION_UNRESOLVED", "INSUFFICIENT_REFERENCE"])
def test_uncertified_member_denies_whole_universe(status: str) -> None:
    values = composition().model_dump()
    values["manifest"]["members"][0]["status"] = status
    manifest = MultiInstrumentCorpusManifest.model_validate(values["manifest"])
    assert manifest.status == "NOT_CERTIFIED" and manifest.aggregate_corpus_fingerprint is None
    with pytest.raises(ValueError, match="UNIVERSE_NOT_CERTIFIED"):
        MultiInstrumentResearchCorpus.model_validate(values)


def test_future_eligibility_common_calendar_window_and_false_certificate_rejected() -> None:
    corpus = composition()
    values = corpus.manifest.universe.model_dump()
    values["source_evidence"][0]["available_by"] = date(2026, 6, 30)
    with pytest.raises(ValueError, match="NOT_PREPERIOD"):
        ResearchUniverseSpec.model_validate(values)
    values = corpus.model_dump()
    values["manifest"]["universe"]["common_window"]["end"] -= timedelta(days=1)
    with pytest.raises(ValueError, match="COMMON_WINDOW_MISMATCH"):
        MultiInstrumentResearchCorpus.model_validate(values)
    values = corpus.model_dump()
    values["manifest"]["universe"]["common_calendar_fingerprint"] = "0" * 64
    with pytest.raises(ValueError, match="COMMON_CALENDAR_PIN_MISMATCH"):
        MultiInstrumentResearchCorpus.model_validate(values)
    values = corpus.model_dump()
    values["manifest"]["members"][0]["gaps"] = 1
    with pytest.raises(ValueError, match="MEMBER_NOT_CERTIFIED"):
        MultiInstrumentResearchCorpus.model_validate(values)


@pytest.mark.parametrize("candidate", ["H1", "H2", "EMA_DIRECTIONAL_PERSISTENCE_G1",
    "VWAP_ONE_BAR_CONFIRMATION_G1",
    "17d249536d96a469442011454c42a99787a50d08966831b51938ac59964b7f04",
    "ccb5ebe8ad5fce034a30ddb6f8d4272d18b8c1136c7caac7698fd75b64bd6b67"])
def test_frozen_candidates_are_explicitly_quarantined(candidate: str) -> None:
    values = definition(synthetic("SBIN")).model_dump()
    values["strategies"][0]["implementation_fingerprint"] = candidate
    with pytest.raises(ValueError, match="H1_H2_QUARANTINED"):
        DevelopmentWalkForwardSpec.model_validate(values)


def test_development_only_no_dummy_test_no_july_no_partial_report() -> None:
    corpus = synthetic("SBIN")
    spec = definition(corpus)
    restored = DevelopmentWalkForwardSpec.model_validate_json(canonical_json(spec))
    assert restored.fingerprint == spec.fingerprint
    values = spec.model_dump()
    values["final_test"] = values["common_window"]
    with pytest.raises(ValueError):
        DevelopmentWalkForwardSpec.model_validate(values)
    values = spec.model_dump()
    values["common_window"]["end"] += timedelta(days=180)
    values["folds"][-1]["validation"]["end"] = values["common_window"]["end"]
    with pytest.raises(ValueError, match="JULY_OR_LATER_DENIED"):
        DevelopmentWalkForwardSpec.model_validate(values)
    report = evaluate_development(corpus, spec)
    assert len(report.evaluations) == 24
    assert {e.partition for e in report.evaluations} == {"TRAIN", "VALIDATION"}
    values = report.model_dump()
    values["evaluations"] = values["evaluations"][:-1]
    with pytest.raises(ValueError, match="INCOMPLETE_DEVELOPMENT_REPORT"):
        DevelopmentReport.model_validate(values)


def test_parameter_variation_and_unsafe_copy_fail_before_any_result() -> None:
    corpus = composition()
    spec = study(corpus)
    values = spec.model_dump()
    values["specifications"][1]["strategies"][0]["parameters"]["fast"] = 8
    with pytest.raises(ValueError):
        MultiInstrumentBaselineStudySpec.model_validate(values)
    values = spec.model_dump()
    for member in values["specifications"]:
        member["strategies"][0]["parameters"]["fast"] = 8
    with pytest.raises(ValueError, match="FIXED_BASELINES_REQUIRED"):
        MultiInstrumentBaselineStudySpec.model_validate(values)
    unsafe = spec.model_copy(update={"universe_fingerprint": "0" * 64})
    retained: list[object] = []
    with pytest.raises(ValueError, match="STUDY_CORPUS_MISMATCH"):
        evaluate_study(corpus, unsafe, retained.append)
    assert not retained


def test_study_reproduces_and_report_tampering_or_missing_member_denied() -> None:
    corpus = composition()
    spec = study(corpus)
    left = evaluate_study(corpus, spec)
    right = evaluate_study(corpus, spec)
    assert left.fingerprint == right.fingerprint
    assert sum(len(r.evaluations) for r in left.per_instrument_results) == 48
    assert len(left.robustness_summaries) == 12
    assert all(s.instruments == 2 for s in left.robustness_summaries)
    assert left.selection == "NONE"
    assert MultiInstrumentBaselineReport.model_validate_json(canonical_json(left)) == left
    values = left.model_dump()
    values["robustness_summaries"][0]["positive_net"] += 1
    with pytest.raises(ValueError, match="SUMMARY_MISMATCH"):
        MultiInstrumentBaselineReport.model_validate(values)
    values = left.model_dump()
    values["per_instrument_results"] = values["per_instrument_results"][:-1]
    with pytest.raises(ValueError, match="MEMBER_SET_MISMATCH"):
        MultiInstrumentBaselineReport.model_validate(values)


def test_sample_threshold_precedes_profit_category() -> None:
    corpus = synthetic("SBIN")
    row = evaluate_development(corpus, definition(corpus)).evaluations[0]
    for count, sessions, raw, net, expected in (
        (2, 2, 10, 5, "INSUFFICIENT_SAMPLE"), (20, 4, 10, 5, "INSUFFICIENT_SAMPLE"),
        (20, 5, 10, 5, "POSITIVE_NET"), (20, 5, 10, -1, "POSITIVE_GROSS_COST_ERODED"),
        (20, 5, -1, -2, "NEGATIVE_GROSS"), (20, 5, 0, -1, "ZERO_GROSS"),
    ):
        changed = row.model_copy(update={"traded_sessions": sessions, "raw_gross": Decimal(raw),
            "metrics": row.metrics.model_copy(update={
                "trade_count": count, "net_pnl": Decimal(net)})})
        assert category(changed, MinimumSamples()) == expected


def test_report_identity_is_independent_of_callers_decimal_context() -> None:
    corpus = composition()
    spec = study(corpus)
    original = evaluate_study(corpus, spec)
    with localcontext(Context(prec=6, rounding=ROUND_UP)):
        repeated = evaluate_study(corpus, spec)
    assert repeated.fingerprint == original.fingerprint


def test_future_partition_and_other_instrument_do_not_change_earlier_fills() -> None:
    corpus = synthetic("SBIN")
    first = evaluate_development(corpus, definition(corpus))
    values = corpus.model_dump()
    for bar in values["shards"][-1]["bars"]:
        for key in ("open", "high", "low", "close"):
            bar[key] += 100
    changed_shard = values["shards"][-1]
    changed_shard["content_fingerprint"] = bar_fingerprint(corpus.instrument_id,
        tuple(Bar.model_validate(b) for b in changed_shard["bars"]))
    shards = tuple(Dataset.model_validate(s) for s in values["shards"])
    values["content_fingerprint"] = bar_fingerprint(corpus.instrument_id,
        tuple(b for s in shards for b in s.bars))
    changed = Corpus.model_validate(values)
    later = evaluate_development(changed, definition(changed))
    assert [(r.metrics, r.result_fingerprint) for r in first.evaluations[:-4]] == [
        (r.metrics, r.result_fingerprint) for r in later.evaluations[:-4]]
    short = slice_corpus(corpus, corpus.from_inclusive, corpus.to_exclusive - timedelta(days=1))
    assert len(short.shards) == 3


def test_member_content_change_changes_aggregate_but_not_other_instrument_results() -> None:
    corpus = composition()
    original = evaluate_study(corpus, study(corpus))
    values = corpus.model_dump()
    member = values["members"][0]
    for shard in member["shards"]:
        for bar in shard["bars"]:
            for key in ("open", "high", "low", "close"):
                bar[key] += 20
        shard["content_fingerprint"] = bar_fingerprint(corpus.members[0].instrument_id,
            tuple(Bar.model_validate(b) for b in shard["bars"]))
    shards = tuple(Dataset.model_validate(s) for s in member["shards"])
    member["content_fingerprint"] = bar_fingerprint(corpus.members[0].instrument_id,
        tuple(b for s in shards for b in s.bars))
    updated = Corpus.model_validate(member)
    certificate = values["manifest"]["members"][0]
    certificate["content_fingerprint"] = updated.content_fingerprint
    certificate["corpus_fingerprint"] = updated.fingerprint
    changed = MultiInstrumentResearchCorpus.model_validate(values)
    assert changed.fingerprint != corpus.fingerprint
    other = evaluate_study(changed, study(changed))
    assert original.per_instrument_results[1] == other.per_instrument_results[1]
    assert original.fingerprint != other.fingerprint
    assert [r.result_fingerprint for r in original.per_instrument_results[0].evaluations] != [
        r.result_fingerprint for r in other.per_instrument_results[0].evaluations]


def test_missing_member_and_changed_artifact_cannot_be_relabelled_complete() -> None:
    corpus = composition()
    values = corpus.model_dump()
    values["members"] = values["members"][:-1]
    with pytest.raises(ValueError, match="MEMBER_SET_MISMATCH"):
        MultiInstrumentResearchCorpus.model_validate(values)
    values = corpus.model_dump()
    values["manifest"]["members"][0]["corpus_fingerprint"] = "0" * 64
    with pytest.raises(ValueError, match="MEMBER_EVIDENCE_MISMATCH"):
        MultiInstrumentResearchCorpus.model_validate(values)


def test_new_research_has_no_candidate_execution_or_network_imports() -> None:
    root = Path(__file__).resolve().parents[1] / "src/strategy_engine/research"
    allowed = {"collections", "datetime", "decimal", "hashlib", "struct", "statistics", "typing",
               "uuid", "pydantic", "strategy_engine"}
    for name in ("universe.py", "development.py", "multi_instrument.py"):
        tree = ast.parse((root / name).read_text())
        for node in ast.walk(tree):
            imports: list[str] = []
            if isinstance(node, ast.Import):
                imports = [a.name for a in node.names]
            elif isinstance(node, ast.ImportFrom):
                imports = [node.module or ""]
            assert all(i.split(".")[0] in allowed for i in imports)
            assert not any("candidate" in i or "transport" in i for i in imports)
            if isinstance(node, ast.Call):
                called = (node.func.attr if isinstance(node.func, ast.Attribute)
                          else node.func.id if isinstance(node.func, ast.Name) else "")
                assert called not in {"execute", "resume", "authorize", "uuid4", "uuid1",
                    "KiteOrderAdapter", "OperatorExecutionService", "OrderExecutionGateway",
                    "RuntimeExecutionArming", "RiskService"}
