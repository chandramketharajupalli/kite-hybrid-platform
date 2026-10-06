"""Offline development-only diagnostic report; requires exact Phase 11.4 replay cache."""

import argparse
import json
from pathlib import Path

from replay_phase115 import PARENT
from run_phase114 import ROOT, load, pin
from strategy_engine.backtest.corpus import slice_corpus
from strategy_engine.backtest.dataset import digest
from strategy_engine.backtest.engine import Result, canonical_json
from strategy_engine.research.diagnostics import END, START, DiagnosticReport, analyze
from strategy_engine.research.walkforward import WalkForwardReport


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("cache", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    retained = ROOT / "research/phase-11.4"
    if json.loads((args.folder / "manifest.json").read_text()) != json.loads(
        (retained / "manifest.json").read_text()
    ):
        raise ValueError("MANIFEST_PIN_MISMATCH")
    parent = WalkForwardReport.model_validate_json(
        (retained / "development-report.json").read_text()
    )
    if parent.fingerprint != PARENT:
        raise ValueError("PARENT_REPORT_MISMATCH")
    corpus = load(args.folder)  # July is integrity-checked only.
    if corpus.fingerprint != parent.specification.corpus_fingerprint:
        raise ValueError("CORPUS_PIN_MISMATCH")
    development = slice_corpus(corpus, START, END)
    del corpus
    results = []
    for evaluation in parent.evaluations:
        values = json.loads(
            (args.cache / (evaluation.result_fingerprint + ".json")).read_text()
        )
        values["config"] = parent.specification.config
        result = Result.model_validate(values)
        if result.fingerprint != evaluation.result_fingerprint:
            raise ValueError("REPLAY_CACHE_PIN_MISMATCH")
        results.append(result)
    source = ROOT / "apps/strategy-engine/src/strategy_engine/research"
    implementation = digest(
        "\n".join(
            p.read_text(encoding="utf-8")
            for p in (
                source / "diagnostics.py",
                source / "opening_features.py",
                Path(__file__),
            )
        )
    )
    plan = digest(
        (ROOT / "docs/operations/phase-11.5-analysis-plan.md").read_text(
            encoding="utf-8"
        )
    )
    report = analyze(development, parent, tuple(results), plan, implementation)
    text = canonical_json(report)
    restored = DiagnosticReport.model_validate_json(text)
    if restored.fingerprint != report.fingerprint:
        raise ValueError("DIAGNOSTIC_ROUND_TRIP_MISMATCH")
    args.output.mkdir(parents=True, exist_ok=True)
    pin(args.output / (report.fingerprint + ".json"), text)
    print(
        json.dumps(
            {
                "report_fingerprint": report.fingerprint,
                "development_sessions": len(report.sessions),
                "evaluations": len(report.evaluations),
                "test": report.test_state,
            }
        ),
        flush=True,
    )


if __name__ == "__main__":
    main()
