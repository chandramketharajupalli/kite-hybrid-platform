"""Explicit chronological comparisons. No parameter selection, optimizer or default TEST access."""
from typing import Annotated, Literal

from pydantic import Field, model_validator

from strategy_engine.backtest.costs import Charges, IntradayCostSchedule
from strategy_engine.backtest.dataset import (
    Dataset,
    Frozen,
    Hash,
    Instant,
    Label,
    bar_fingerprint,
    digest,
)
from strategy_engine.backtest.engine import Config, Result, canonical_json, run
from strategy_engine.research.costs import audit_costs
from strategy_engine.research.strategy import BaselineSpec, BaselineStrategy


class Window(Frozen):
    start: Instant
    end: Instant

    @model_validator(mode="after")
    def valid(self) -> "Window":
        if self.start >= self.end or any(t.second or t.microsecond for t in (self.start, self.end)):
            raise ValueError("INVALID_PARTITION_WINDOW")
        return self


class Partitions(Frozen):
    train: Window
    validation: Window
    test: Window
    warmup: Literal["COLD_PER_PARTITION_AND_SESSION"]

    @model_validator(mode="after")
    def valid(self) -> "Partitions":
        if (not self.train.end <= self.validation.start
                or not self.validation.end <= self.test.start):
            raise ValueError("PARTITIONS_OVERLAP_OR_UNORDERED")
        return self


class ExperimentSpec(Frozen):
    schema_version: Literal["ResearchExperiment.v1"] = "ResearchExperiment.v1"
    experiment_id: Label
    dataset_artifact_fingerprint: Hash
    dataset_content_fingerprint: Hash
    instrument_id: str
    interval: Literal["MINUTE"]
    strategy: BaselineSpec
    config: Config
    partitions: Partitions
    slippage_model_version: Literal["adverse-bps-v1"]
    feature_version: Literal["session-decimal40-v1"]
    engine_version: Literal["intraday-next-open-v1", "intraday-next-open-v2-costs"]
    cost_model_version: Label

    @model_validator(mode="after")
    def valid(self) -> "ExperimentSpec":
        calibrated = isinstance(self.config.costs, IntradayCostSchedule)
        expected_engine = "intraday-next-open-v2-costs" if calibrated else "intraday-next-open-v1"
        expected_cost = (self.config.costs.version if isinstance(self.config.costs,
                                                                IntradayCostSchedule)
                         else "configured-fixed-bps-v1")
        if self.engine_version != expected_engine or self.cost_model_version != expected_cost:
            raise ValueError("EXPERIMENT_MODEL_VERSION_MISMATCH")
        return self

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


class Evaluation(Frozen):
    partition: Literal["TRAIN", "VALIDATION", "TEST"]
    result: Result
    cost_breakdown: tuple[Charges, ...]


class ExperimentReport(Frozen):
    schema_version: Literal["ResearchExperimentReport.v1"] = "ResearchExperimentReport.v1"
    specification: ExperimentSpec
    evaluations: tuple[Evaluation, ...]
    selection_fingerprint: Hash | None

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


def _validate(dataset: Dataset, spec: ExperimentSpec) -> tuple[Dataset, ExperimentSpec]:
    dataset = Dataset.model_validate(dataset.model_dump())
    spec = ExperimentSpec.model_validate(spec.model_dump())
    if (digest(canonical_json(dataset)) != spec.dataset_artifact_fingerprint
            or dataset.content_fingerprint != spec.dataset_content_fingerprint
            or dataset.instrument_id != spec.instrument_id or dataset.interval != spec.interval):
        raise ValueError("EXPERIMENT_DATASET_MISMATCH")
    for window in (spec.partitions.train, spec.partitions.validation, spec.partitions.test):
        if window.start < dataset.from_inclusive or window.end > dataset.to_exclusive:
            raise ValueError("PARTITION_OUTSIDE_DATASET")
    return dataset, spec


def partition_dataset(dataset: Dataset, window: Window) -> Dataset:
    bars = tuple(b for b in dataset.bars if window.start <= b.start < window.end)
    values = dataset.model_dump()
    values.update(from_inclusive=window.start, to_exclusive=window.end,
                  decision_cutoff=window.end, bars=bars,
                  content_fingerprint=bar_fingerprint(dataset.instrument_id, bars))
    # Retain original calendar/provenance; canonical validation rejects partial sessions.
    return Dataset.model_validate(values)


def _evaluate(dataset: Dataset, spec: ExperimentSpec,
              name: Literal["TRAIN", "VALIDATION", "TEST"], window: Window) -> Evaluation:
    subset = partition_dataset(dataset, window)
    strategy = BaselineStrategy(specification=spec.strategy, config=spec.config)
    result = run(subset, spec.config, spec.strategy.engine_spec(), strategy)
    return Evaluation(partition=name, result=result, cost_breakdown=audit_costs(result))


def evaluate_development(dataset: Dataset, spec: ExperimentSpec) -> ExperimentReport:
    dataset, spec = _validate(dataset, spec)
    return ExperimentReport(specification=spec, selection_fingerprint=None, evaluations=(
        _evaluate(dataset, spec, "TRAIN", spec.partitions.train),
        _evaluate(dataset, spec, "VALIDATION", spec.partitions.validation)))


class FrozenSelection(Frozen):
    schema_version: Literal["ResearchSelection.v1"] = "ResearchSelection.v1"
    selected_experiment_fingerprint: Hash
    development_report_fingerprints: tuple[Hash, ...]
    trial_count: Annotated[int, Field(strict=True, ge=1, le=16)]
    selection_criterion: Label

    @model_validator(mode="after")
    def valid(self) -> "FrozenSelection":
        if type(self.trial_count) is not int or self.trial_count != len(
            self.development_report_fingerprints
        ) or not 1 <= self.trial_count <= 16:
            raise ValueError("INVALID_TRIAL_RECORD")
        if len(set(self.development_report_fingerprints)) != self.trial_count:
            raise ValueError("DUPLICATE_TRIAL_RECORD")
        return self

    @property
    def fingerprint(self) -> str:
        return digest(canonical_json(self))


def freeze_selection(reports: tuple[ExperimentReport, ...], selected_id: str,
                     criterion: str) -> FrozenSelection:
    """Record an explicit human/research choice; never rank by P&L or read TEST."""
    if not 1 <= len(reports) <= 16:
        raise ValueError("INVALID_TRIAL_RECORD")
    ordered = sorted(reports, key=lambda report: report.specification.experiment_id)
    if len({r.specification.experiment_id for r in ordered}) != len(ordered):
        raise ValueError("DUPLICATE_TRIAL_RECORD")
    for report in ordered:
        if tuple(e.partition for e in report.evaluations) != ("TRAIN", "VALIDATION"):
            raise ValueError("TEST_MUST_NOT_SELECT_PARAMETERS")
    selected = [r for r in ordered if r.specification.experiment_id == selected_id]
    if len(selected) != 1:
        raise ValueError("SELECTED_EXPERIMENT_NOT_IN_TRIALS")
    return FrozenSelection(selected_experiment_fingerprint=selected[0].specification.fingerprint,
                           development_report_fingerprints=tuple(r.fingerprint for r in ordered),
                           trial_count=len(ordered), selection_criterion=criterion)


def evaluate_final_test(dataset: Dataset, spec: ExperimentSpec,
                        selection: FrozenSelection) -> ExperimentReport:
    dataset, spec = _validate(dataset, spec)
    selection = FrozenSelection.model_validate(selection.model_dump())
    if selection.selected_experiment_fingerprint != spec.fingerprint:
        raise ValueError("FROZEN_SELECTION_MISMATCH")
    return ExperimentReport(specification=spec, selection_fingerprint=selection.fingerprint,
                            evaluations=(_evaluate(dataset, spec, "TEST", spec.partitions.test),))


def compare_development(dataset: Dataset, specs: tuple[ExperimentSpec, ...]
                        ) -> tuple[ExperimentReport, ...]:
    if not 1 <= len(specs) <= 16 or len({s.experiment_id for s in specs}) != len(specs):
        raise ValueError("BOUNDED_UNIQUE_EXPERIMENTS_REQUIRED")
    first = specs[0]
    for spec in specs:
        if (spec.dataset_artifact_fingerprint != first.dataset_artifact_fingerprint
                or spec.partitions != first.partitions or spec.config != first.config):
            raise ValueError("INCOMPARABLE_EXPERIMENT_CONDITIONS")
    ordered = sorted(specs, key=lambda s: s.experiment_id)
    return tuple(evaluate_development(dataset, spec) for spec in ordered)
