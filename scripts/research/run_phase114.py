"""Explicit offline verification/development runner. No final TEST command or network capability."""
import argparse
import hashlib
import json
import re
import time
from datetime import UTC, date, datetime
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

from strategy_engine.backtest.corpus import Corpus, calendar_subset
from strategy_engine.backtest.dataset import NSE, Calendar, Dataset, Day
from strategy_engine.backtest.engine import Config, canonical_json
from strategy_engine.research.costs import nse_intraday_snapshot
from strategy_engine.research.strategy import BaselineSpec
from strategy_engine.research.walkforward import WalkForwardSpec, evaluate_walk_forward

ROOT = Path(__file__).resolve().parents[2]


def bounded(path: Path, maximum: int) -> str:
    if path.stat().st_size > maximum:
        raise ValueError("ARTIFACT_SIZE_EXCEEDED")
    return path.read_text(encoding="utf-8-sig")


def unique(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def load(folder: Path) -> Corpus:
    manifest = json.loads(bounded(folder / "manifest.json", 256_000), object_pairs_hook=unique)
    if (manifest["schema"] != "HistoricalCorpusManifest.v1"
            or manifest["quality"] != "COMPLETE_CANONICAL_SESSIONS"
            or not 1 <= len(manifest["sessions"]) <= 366):
        raise ValueError("MANIFEST_NOT_CERTIFIED")
    schema = json.loads((ROOT / "contracts/schemas/v1/HistoricalResearchDataset.v1.schema.json")
                        .read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    shards = []
    for entry in manifest["sessions"]:
        if not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}\.json", entry["file"]):
            raise ValueError("INVALID_SHARD_PATH")
        raw = bounded(folder / entry["file"], 2_000_000)
        if hashlib.sha256(raw.encode("utf-8")).hexdigest() != entry["artifactFingerprint"]:
            raise ValueError("ARTIFACT_FINGERPRINT_MISMATCH")
        validator.validate(json.loads(raw, object_pairs_hook=unique))
        data = Dataset.from_json(raw)
        if (data.instrument_id != "050f94dd-e639-364f-97a6-595594de6543"
                or data.symbol != "SBIN" or data.exchange != "NSE"
                or any(p.source != "KITE" for p in data.provenance)):
            raise ValueError("PHASE114_INSTRUMENT_SOURCE_MISMATCH")
        if (data.content_fingerprint != entry["contentFingerprint"]
                or len(data.bars) != entry["bars"]):
            raise ValueError("MANIFEST_CONTENT_MISMATCH")
        shards.append(data)
    calendar = json.loads(bounded(ROOT / "research/phase-11.4/calendar.json", 256_000),
                          object_pairs_hook=unique)
    days = tuple(Day.model_validate(day) for day in calendar["days"])
    # Construct bounded canonical calendars without bypassing Calendar validation.
    prototype = shards[0].calendar
    if (prototype.version != calendar["version"] or prototype.source != calendar["source"]):
        raise ValueError("CALENDAR_SOURCE_MISMATCH")
    calendars: list[Calendar] = []
    for month in range(2, 8):
        month_days = tuple(d for d in days if d.date.month == month)
        calendars.append(calendar_subset(prototype, month_days))
    text = calendar["version"] + "\n" + calendar["source"] + "\n"
    for day in days:
        sessions = ", ".join(f"Session[open={s.open:%H:%M}, close={s.close:%H:%M}]"
                             for s in day.sessions)
        text += f"{day.date}=Day[status={day.status}, sessions=[{sessions}]]\n"
    if hashlib.sha256(text.encode()).hexdigest() != manifest["calendarFingerprint"]:
        raise ValueError("CORPUS_CALENDAR_PIN_MISMATCH")
    return Corpus(from_inclusive=local("2026-02-02"), to_exclusive=local("2026-08-01"),
                  calendars=tuple(calendars), shards=tuple(shards),
                  content_fingerprint=manifest["contentFingerprint"])


def local(value: str) -> datetime:
    return datetime.fromisoformat(value).replace(tzinfo=NSE).astimezone(UTC)


def specification(corpus: Corpus) -> WalkForwardSpec:
    source = ROOT / "apps/strategy-engine/src/strategy_engine"
    implementation = hashlib.sha256(b"".join(
        (source / name).read_bytes() for name in (
            "research/features.py", "research/strategy.py", "backtest/engine.py",
            "backtest/costs.py", "research/costs.py", "backtest/corpus.py",
            "research/walkforward.py"))).hexdigest()
    parameters = (
        {"kind": "EMA_CROSS", "quantity": 1, "fast": 9, "slow": 21},
        {"kind": "VWAP_CROSS", "quantity": 1},
        {"kind": "OPENING_RANGE", "quantity": 1, "opening_bars": 15},
        {"kind": "RSI_RECOVERY", "quantity": 1, "lookback": 14,
         "oversold": "30", "exit_threshold": "50"})
    strategies = tuple(BaselineSpec.model_validate({
        "version": "v1", "implementation_fingerprint": implementation, "parameters": p})
        for p in parameters)
    return WalkForwardSpec.model_validate({
        "research_generation": "phase114-generation1", "corpus_fingerprint": corpus.fingerprint,
        "folds": [{"train": {"start": local("2026-02-02"), "end": local(start)},
                    "validation": {"start": local(start), "end": local(end)}}
               for start, end in (("2026-04-01", "2026-05-01"),
                                  ("2026-05-01", "2026-06-01"),
                                  ("2026-06-01", "2026-07-01"))],
        "final_test": {"start": local("2026-07-01"), "end": local("2026-08-01")},
        "strategies": strategies, "config": Config.model_validate({
            "initial_cash": "100000", "entry_start": "09:15", "last_entry_time": "14:45",
            "forced_exit_time": "15:15", "slippage": {"bps": "5"},
            "costs": nse_intraday_snapshot(fixed_as_of=date(2026, 10, 6))})})


def pin(path: Path, text: str) -> None:
    if path.exists():
        if path.read_text(encoding="utf-8") != text:
            raise ValueError("PINNED_RESEARCH_ARTIFACT_CONFLICT")
    else:
        path.write_text(text, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--development", action="store_true")
    mode.add_argument("--replay-spec", type=Path,
                      help="Replay a reviewed pinned development spec with its original identity")
    args = parser.parse_args()
    started = time.perf_counter()
    corpus = load(args.folder)
    print(json.dumps({"stage": "VERIFIED", "sessions": len(corpus.shards), "bars": len(corpus.bars),
                          "corpusFingerprint": corpus.fingerprint,
                          "contentFingerprint": corpus.content_fingerprint,
                          "test": "SEALED_NOT_EVALUATED"}), flush=True)
    if args.development or args.replay_spec:
        spec = (WalkForwardSpec.model_validate(json.loads(bounded(args.replay_spec, 256_000),
                                                         object_pairs_hook=unique))
                if args.replay_spec else specification(corpus))
        if not args.replay_spec:
            pin(args.folder / "walkforward-spec.json", canonical_json(spec))
        report = evaluate_walk_forward(corpus, spec)
        pin(args.folder / "development-report.json", canonical_json(report))
        print(json.dumps({"stage": "DEVELOPMENT", "evaluations": len(report.evaluations),
                          "fingerprint": report.fingerprint,
                          "test": report.test_state}), flush=True)
    print(json.dumps({"stage": "OPERATIONAL", "elapsedSeconds": time.perf_counter() - started}))


if __name__ == "__main__":
    main()
