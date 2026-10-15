"""Frozen-policy qualification views and complete-family deterministic publication."""

import csv
import json
from datetime import datetime
from decimal import Decimal
from pathlib import Path
from typing import Any

from strategy_engine.backtest.dataset import Frozen, Hash, digest
from strategy_engine.phase14.engine import qualify
from strategy_engine.phase1415.hardening import ROOT, FamilyBinding, bind_result, frozen_policy
from strategy_engine.phase1415.research import Dataset, Result
from strategy_engine.phase1416.imports import canonical, external, publish, sha
from strategy_engine.phase1416.jobs import Job, checkpoint_rows, execute, load, software

LOCAL = tuple(
    "apps/strategy-engine/src/strategy_engine/phase1417/" + n
    for n in ("__init__.py", "qualification.py", "paper.py")
)


def inventory() -> dict[str, str]:
    if {p.name for p in (ROOT / Path(LOCAL[0]).parent).glob("*.py")} != {
        Path(p).name for p in LOCAL
    }:
        raise ValueError("SOFTWARE_INVENTORY_CONFLICT")
    return dict(sorted((software() | {p: sha((ROOT / p).read_bytes()) for p in LOCAL}).items()))


def software_hash() -> str:
    return sha(canonical(inventory()))


def primary(result: Result) -> str:
    if result.data_status == "UNAVAILABLE":
        return "DATA_UNAVAILABLE"
    if result.data_status == "QUARANTINED":
        return "DATA_QUARANTINED"
    if result.synthetic:
        return "INSUFFICIENT_EVIDENCE"
    mapping = (
        ("OUT_OF_SAMPLE_NOT_AVAILABLE", "REJECTED_OUT_OF_SAMPLE"),
        ("INSUFFICIENT_OOS_TRADES", "REJECTED_TRADE_COUNT"),
        ("OOS_WIN_RATE_BELOW_THRESHOLD", "REJECTED_WIN_RATE"),
        ("NON_POSITIVE_NET_EXPECTANCY", "REJECTED_NET_PROFIT"),
        ("DRAWDOWN_LIMIT", "REJECTED_DRAWDOWN"),
        ("MARKED_DRAWDOWN_LIMIT", "REJECTED_DRAWDOWN"),
        ("MULTIPLE_TESTING_UNCERTAINTY", "REJECTED_CONFIDENCE"),
        ("UNSTABLE_OR_INSUFFICIENT_WALK_FORWARD", "REJECTED_OUT_OF_SAMPLE"),
    )
    for reason, status in mapping:
        if reason in result.reasons:
            return status
    if result.reasons:
        return "REJECTED_DATA_QUALITY"
    return "QUALIFIED" if result.qualified and result.qualification else "INSUFFICIENT_EVIDENCE"


class Row(Frozen):
    schema_version: str = "Phase1417Qualification.v1"
    run_id: Hash
    software_hash: Hash
    calendar_hash: Hash | None
    corporate_action_policy: str | None
    risk_policy_hash: Hash
    capital: Decimal
    result: Result

    @property
    def fingerprint(self) -> str:
        return digest(self.model_dump_json())

    @property
    def status(self) -> str:
        return primary(self.result)

    def numerical_gate(self) -> bool:
        r = self.result
        if (
            self.schema_version != "Phase1417Qualification.v1"
            or self.software_hash != software_hash()
            or r.coverage != 1
            or not self.calendar_hash
            or r.policy_hash != digest(frozen_policy().model_dump_json())
            or r.hypothesis_family_size != 13380
            or not r.family_run_id
            or not r.dataset_hash
            or not r.holdout
            or not r.qualification
            or r.data_status not in ("SYNTHETIC", "LOCAL_VERIFIED")
        ):
            return False
        q = qualify(r.trades, self.capital, r.window.holdout_start, r.window.folds, frozen_policy())
        return (
            q == r.qualification
            and q.qualified
            and not any(reason != "SYNTHETIC_NOT_HISTORICAL_PERFORMANCE" for reason in r.reasons)
        )

    def record(self) -> dict[str, Any]:
        r, h = self.result, self.result.holdout
        b = h.basic if h else None
        return dict(
            symbol=r.symbol,
            strategy_id=r.strategy_id,
            strategy_version=r.strategy_version,
            months=r.window.months,
            window=r.window.model_dump(mode="json"),
            dataset_status=r.data_status,
            synthetic=r.synthetic,
            status=self.status,
            trade_count=b.trades if b else None,
            wins=b.wins if b else None,
            losses=b.losses if b else None,
            breakevens=b.breakeven if b else None,
            win_rate=str(b.win_rate) if b else None,
            confidence_interval=list(h.confidence_95) if h else None,
            adjusted_wilson_lower=r.qualification.adjusted_wilson_lower
            if r.qualification
            else None,
            net=str(b.net) if b else None,
            expectancy=str(b.expectancy) if b else None,
            profit_factor=str(h.profit_factor) if h and h.profit_factor is not None else None,
            drawdown=str(b.drawdown) if b else None,
            training=r.training.model_dump(mode="json") if r.training else None,
            validation=r.validation.model_dump(mode="json") if r.validation else None,
            holdout=h.model_dump(mode="json") if h else None,
            reasons=list(r.reasons),
            policy_hash=r.policy_hash,
            strategy_hash=r.strategy_hash,
            dataset_hash=r.dataset_hash,
            calendar_hash=self.calendar_hash,
            software_hash=self.software_hash,
            corporate_action_policy=self.corporate_action_policy,
            risk_policy_hash=self.risk_policy_hash,
            run_id=self.run_id,
            qualification_hash=self.fingerprint,
        )


def row(
    result: Result,
    family: FamilyBinding,
    data: Dataset | None,
    run_id: str,
    risk_hash: str,
    capital: Decimal,
    fingerprint: str | None = None,
) -> Row:
    if bind_result(result, family) != result:
        raise ValueError("UNBOUND_QUALIFICATION_RESULT")
    if result.dataset_hash != (data.dataset_sha256 if data else None):
        raise ValueError("DATASET_BINDING_CONFLICT")
    return Row(
        run_id=run_id,
        software_hash=fingerprint or software_hash(),
        calendar_hash=data.series.calendar_hash if data else None,
        corporate_action_policy=data.series.adjustment if data else None,
        risk_policy_hash=risk_hash,
        capital=capital,
        result=result,
    )


def run(job: Job, directory: Path, risk_hash: str, *, now: datetime) -> dict[str, Any]:
    directory = external(directory)
    binding = dict(
        schema="Phase1417Run.v1",
        job=job.model_dump(mode="json"),
        dependencies=inventory(),
        risk_policy_hash=risk_hash,
    )
    run_id = sha(canonical(binding))
    publish(directory / "qualification-plan.json", canonical(binding))
    execute(job, directory / "research", now=now)
    summary_path = directory / "qualification-summary.json"
    if summary_path.exists():
        summary = json.loads(summary_path.read_bytes())
        if summary["run_id"] != run_id or summary["rows"] != 13380:
            raise ValueError("QUALIFICATION_REPLAY_REJECTED")
        for name, expected in summary["reports"].items():
            if sha((directory / name).read_bytes()) != expected:
                raise ValueError("QUALIFICATION_REPORT_TAMPERED")
        return dict(summary)
    pending = directory / "qualification.pending"
    pending.mkdir()
    selected = {(s.symbol, s.months): s for s in job.datasets}
    counts: dict[str, int] = {}
    reasons: dict[str, int] = {}
    count = size = 0
    fingerprint = software_hash()
    with (
        (pending / "qualification-ledger.json").open("x", encoding="utf8", newline="\n") as j,
        (pending / "qualification-ledger.csv").open("x", encoding="utf8", newline="") as c,
    ):
        j.write("[")
        writer = None
        for symbol in job.family.symbols:
            results = checkpoint_rows(
                directory / "research/checkpoints" / (digest(symbol) + ".json"), job, symbol
            )
            for months in (6, 12):
                item = selected.get((symbol, months))
                data = load(item, job, now).dataset if item else None
                for result in (r for r in results if r.window.months == months):
                    record = row(
                        result,
                        job.family,
                        data,
                        run_id,
                        risk_hash,
                        job.settings.capital,
                        fingerprint,
                    ).record()
                    encoded = canonical(record).decode()
                    size += len(encoded)
                    if size > 100 * 1024 * 1024:
                        raise ValueError("QUALIFICATION_OUTPUT_LIMIT")
                    if writer is None:
                        writer = csv.DictWriter(c, list(record), lineterminator="\n")
                        writer.writeheader()
                    writer.writerow(
                        {
                            k: canonical(v).decode() if isinstance(v, (dict, list)) else v
                            for k, v in record.items()
                        }
                    )
                    j.write(("," if count else "") + encoded)
                    count += 1
                    counts[record["status"]] = counts.get(record["status"], 0) + 1
                    for reason in result.reasons:
                        reasons[reason] = reasons.get(reason, 0) + 1
        j.write("]\n")
    if count != 13380:
        raise ValueError("INCOMPLETE_QUALIFICATION_FAMILY")
    publish(
        pending / "walk-forward-report.json",
        canonical(
            dict(
                method="FROZEN_PARAMETERS_50_20_30_THREE_HOLDOUT_FOLDS",
                windows=[w.model_dump(mode="json") for w in job.family.windows],
                policy=frozen_policy().model_dump(mode="json"),
                policy_hash=job.family.policy_hash,
                caveat=(
                    "Correlated trades; Wilson is the frozen research method, "
                    "not a 70% true-rate guarantee"
                ),
            )
        ),
    )
    publish(pending / "rejection-reasons.json", canonical(reasons))
    hashes = {p.name: sha(p.read_bytes()) for p in sorted(pending.iterdir())}
    for p in sorted(pending.iterdir()):
        publish(directory / p.name, p.read_bytes())
    summary = dict(
        run_id=run_id,
        rows=count,
        windows={"6": 6690, "12": 6690},
        statuses=counts,
        reports=hashes,
        software_hash=software_hash(),
        policy_hash=job.family.policy_hash,
        qualified=counts.get("QUALIFIED", 0),
        complete=True,
    )
    publish(summary_path, canonical(summary))
    return summary
