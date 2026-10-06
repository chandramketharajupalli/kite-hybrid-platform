"""Predeclared expanding windows; development evaluation cannot evaluate final TEST."""
from typing import Literal

from pydantic import field_validator, model_validator

from strategy_engine.backtest.corpus import Corpus, slice_corpus
from strategy_engine.backtest.dataset import Frozen, Hash, Label, digest
from strategy_engine.backtest.engine import Config, Metrics, canonical_json, run
from strategy_engine.research.experiments import Window
from strategy_engine.research.strategy import BaselineSpec, BaselineStrategy


class Fold(Frozen):
    train: Window
    validation: Window

    @model_validator(mode="after")
    def valid(self) -> "Fold":
        if self.train.end != self.validation.start:
            raise ValueError("FOLD_NOT_CHRONOLOGICAL")
        return self


class WalkForwardSpec(Frozen):
    schema_version: Literal["WalkForward.v1"] = "WalkForward.v1"
    research_generation: Label
    corpus_fingerprint: Hash
    policy: Literal["EXPANDING"] = "EXPANDING"
    warmup: Literal["COLD_PER_PARTITION_AND_SESSION"] = "COLD_PER_PARTITION_AND_SESSION"
    folds: tuple[Fold, ...]
    final_test: Window
    strategies: tuple[BaselineSpec, ...]
    config: Config
    engine_version: Literal["intraday-next-open-v3-session-corpus"] = (
        "intraday-next-open-v3-session-corpus"
    )
    feature_version: Literal["session-decimal40-v1"] = "session-decimal40-v1"
    slippage_version: Literal["adverse-bps-v1"] = "adverse-bps-v1"

    @field_validator("config", mode="before")
    @classmethod
    def serialized_minute_times(cls, value: object) -> object:
        # Pydantic serializes time as HH:MM:SS; Config accepts minute precision.
        # Accept only its exact zero-second representation, never round a time.
        if isinstance(value, dict):
            value = dict(value)
            for key in ("entry_start", "last_entry_time", "forced_exit_time"):
                item = value.get(key)
                if (isinstance(item, str) and len(item) == 8 and item[2] == ":"
                        and item.endswith(":00") and item[:2].isascii()
                        and item[:2].isdigit() and item[3:5].isascii()
                        and item[3:5].isdigit()):
                    value[key] = item[:5]
        return value

    @model_validator(mode="after")
    def valid(self) -> "WalkForwardSpec":
        if not 1 <= len(self.folds) <= 12 or not 1 <= len(self.strategies) <= 16:
            raise ValueError("WALK_FORWARD_BUDGET_INVALID")
        if len({canonical_json(s) for s in self.strategies}) != len(self.strategies):
            raise ValueError("DUPLICATE_STRATEGY_TRIAL")
        previous = None
        for fold in self.folds:
            if fold.train.start != self.folds[0].train.start:
                raise ValueError("EXPANDING_START_CHANGED")
            if previous is not None and fold.train.end != previous.validation.end:
                raise ValueError("FOLDS_NOT_EXPANDING_OR_VALIDATION_OVERLAP")
            previous = fold
        if self.folds[-1].validation.end != self.final_test.start:
            raise ValueError("TEST_MUST_FOLLOW_DEVELOPMENT")
        return self

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


class FoldEvaluation(Frozen):
    fold_identity: Hash
    strategy_identity: Hash
    fold_number: int
    partition: Literal["TRAIN", "VALIDATION"]
    window: Window
    result_fingerprint: Hash
    metrics: Metrics


class WalkForwardReport(Frozen):
    specification: WalkForwardSpec
    evaluations: tuple[FoldEvaluation, ...]
    test_state: Literal["SEALED_NOT_EVALUATED"] = "SEALED_NOT_EVALUATED"
    selection: Literal["NONE"] = "NONE"

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


def evaluate_walk_forward(corpus: Corpus, spec: WalkForwardSpec) -> WalkForwardReport:
    corpus = Corpus.model_validate(corpus.model_dump())
    spec = WalkForwardSpec.model_validate(spec.model_dump())
    if corpus.fingerprint != spec.corpus_fingerprint:
        raise ValueError("WALK_FORWARD_CORPUS_MISMATCH")
    if (spec.folds[0].train.start != corpus.from_inclusive
            or spec.final_test.end != corpus.to_exclusive):
        raise ValueError("WALK_FORWARD_RANGE_MISMATCH")
    # Validate holdout coverage, but do not invoke any strategy or expose holdout metrics.
    slice_corpus(corpus, spec.final_test.start, spec.final_test.end)
    evaluations = []
    strategies = sorted(spec.strategies, key=canonical_json)
    for number, fold in enumerate(spec.folds, 1):
        partitions: tuple[tuple[Literal["TRAIN", "VALIDATION"], Window], ...] = (
            ("TRAIN", fold.train), ("VALIDATION", fold.validation))
        for name, window in partitions:
            subset = slice_corpus(corpus, window.start, window.end)
            for strategy in strategies:
                identity = digest(canonical_json(strategy))
                fold_identity = digest(spec.fingerprint + "\n" + canonical_json(fold)
                                       + "\n" + identity)
                result = run(subset, spec.config, strategy.engine_spec(),
                             BaselineStrategy(specification=strategy, config=spec.config))
                evaluations.append(FoldEvaluation(
                    fold_identity=fold_identity, strategy_identity=identity, fold_number=number,
                    partition=name, window=window, result_fingerprint=result.fingerprint,
                    metrics=result.metrics))
    return WalkForwardReport(specification=spec, evaluations=tuple(evaluations))
