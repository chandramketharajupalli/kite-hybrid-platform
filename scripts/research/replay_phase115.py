"""Offline exact development replay into a fingerprint-checked local result cache."""

import argparse
import json
from pathlib import Path

from run_phase114 import ROOT, load, pin
from strategy_engine.backtest.corpus import slice_corpus
from strategy_engine.backtest.dataset import digest
from strategy_engine.backtest.engine import Result, canonical_json, run
from strategy_engine.research.strategy import BaselineStrategy
from strategy_engine.research.walkforward import WalkForwardReport

PARENT = "ae497b05d9fed7475c3d30f0f2bdc04c975c8c3c7a9057ad4c924175417de110"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("cache", type=Path)
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
    corpus = load(args.folder)  # July: integrity/schema/completeness only.
    spec = parent.specification
    if corpus.fingerprint != spec.corpus_fingerprint:
        raise ValueError("PARENT_CORPUS_MISMATCH")
    development = slice_corpus(corpus, spec.folds[0].train.start, spec.final_test.start)
    del corpus
    args.cache.mkdir(parents=True, exist_ok=True)
    strategies = {digest(canonical_json(s)): s for s in spec.strategies}
    for evaluation in parent.evaluations:
        if evaluation.window.end > spec.final_test.start:
            raise ValueError("SEALED_TEST_BOUNDARY")
        target = args.cache / (evaluation.result_fingerprint + ".json")
        if target.exists():
            values = json.loads(target.read_text())
            values["config"] = spec.config
            result = Result.model_validate(values)
        else:
            subset = slice_corpus(
                development, evaluation.window.start, evaluation.window.end
            )
            strategy = strategies[evaluation.strategy_identity]
            result = run(
                subset,
                spec.config,
                strategy.engine_spec(),
                BaselineStrategy(specification=strategy, config=spec.config),
            )
        if (
            result.fingerprint != evaluation.result_fingerprint
            or result.metrics != evaluation.metrics
        ):
            raise ValueError("PHASE114_REPLAY_DIVERGED")
        pin(target, canonical_json(result))
        print(
            json.dumps(
                {
                    "fold": evaluation.fold_number,
                    "partition": evaluation.partition,
                    "strategy": result.strategy.name,
                    "replay": "EXACT_MATCH",
                    "test": "SEALED_NOT_EVALUATED",
                }
            ),
            flush=True,
        )


if __name__ == "__main__":
    main()
