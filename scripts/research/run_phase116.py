"""Freeze first, then evaluate only DEVELOPMENT_REUSE_ONLY; no acquisition or TEST CLI."""

import argparse
import json
from pathlib import Path
from typing import Any, Literal

from run_phase114 import ROOT, load, local, pin

from strategy_engine.backtest.corpus import slice_corpus
from strategy_engine.backtest.dataset import digest
from strategy_engine.backtest.engine import Result, canonical_json
from strategy_engine.research.candidate_evaluation import ReuseReport, describe, run_reuse
from strategy_engine.research.candidates import REGISTRATION, CandidateSpec
from strategy_engine.research.walkforward import WalkForwardReport

PARENT = "ae497b05d9fed7475c3d30f0f2bdc04c975c8c3c7a9057ad4c924175417de110"
DIAGNOSTIC = "f2b20e24137c9453a40f7296fa6e5f70451b5e94873364b976aa910d4959664c"
CORPUS = "702631ba2f5ef2959cdfa687f87959e9347818c5e772a2dd266b058c30ad280c"
BOUNDARIES = ("2026-02-02", "2026-03-01", "2026-04-01", "2026-05-01", "2026-06-01", "2026-07-01")


def canonical(value: Any) -> str:
    return json.dumps(
        value, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
    )


def evidence() -> tuple[dict[str, Any], WalkForwardReport]:
    registration = json.loads((ROOT / "research/phase-11.5/hypotheses-v1.json").read_text())
    body = registration["registration"]
    doc = (ROOT / "docs/operations/phase-11.5-hypotheses.md").read_text(encoding="utf-8")
    parent = WalkForwardReport.model_validate_json(
        (ROOT / "research/phase-11.4/development-report.json").read_text()
    )
    checks = (
        digest(canonical(body)) == registration["registration_fingerprint"] == REGISTRATION,
        digest(doc) == body["hypothesis_document_fingerprint"],
        doc.split("## H1 ")[1].split("## H2 ")[0].strip() == body["hypotheses"][0]["registration"],
        doc.split("## H2 ")[1].split("## Frozen comparison")[0].strip()
        == body["hypotheses"][1]["registration"],
        doc.split("## Frozen comparison and rejection rules")[1].strip()
        == body["comparison_protocol"],
        parent.fingerprint == PARENT == body["phase114_report_fingerprint"],
        parent.specification.corpus_fingerprint == CORPUS == body["parent_corpus_fingerprint"],
        digest((ROOT / f"research/phase-11.5/{DIAGNOSTIC}.json").read_text())
        == DIAGNOSTIC
        == body["analysis_fingerprint"],
    )
    if not all(checks):
        raise ValueError("REGISTRATION_OR_PARENT_MISMATCH_STOP")
    return body, parent


def sources() -> dict[str, str]:
    base = ROOT / "apps/strategy-engine/src/strategy_engine"
    files = list((base / "backtest").glob("*.py"))
    files += [
        base / "research" / name
        for name in (
            "features.py",
            "strategy.py",
            "costs.py",
            "experiments.py",
            "walkforward.py",
            "opening_features.py",
            "diagnostics.py",
            "candidate_features.py",
            "candidates.py",
            "candidate_evaluation.py",
        )
    ]
    files += [
        ROOT / "scripts/research/run_phase114.py",
        Path(__file__),
        ROOT / "docs/operations/phase-11.6-implementation-plan.md",
    ]
    return {
        p.relative_to(ROOT).as_posix(): digest(p.read_text(encoding="utf-8")) for p in sorted(files)
    }


def freeze_body() -> dict[str, Any]:
    registration, parent = evidence()
    hashes = sources()
    comparators = {s.parameters.kind: s for s in parent.specification.strategies}
    candidates = []
    for hypothesis, family in (("H1", "EMA_CROSS"), ("H2", "VWAP_CROSS")):
        candidate = CandidateSpec(
            hypothesis=hypothesis,
            implementation_fingerprint=digest(
                hypothesis + "\n" + canonical(hashes) + "\n" + REGISTRATION
            ),
            comparator=comparators[family],
        )
        candidates.append(candidate.model_dump(mode="json"))
    return {
        "schema_version": "FrozenHypothesisImplementation.G1",
        "usage": "DEVELOPMENT_REUSE_ONLY",
        "registration_fingerprint": REGISTRATION,
        "parent_corpus_fingerprint": CORPUS,
        "parent_report_fingerprint": PARENT,
        "diagnostic_fingerprint": DIAGNOSTIC,
        "source_fingerprints": hashes,
        "candidates": candidates,
        "configuration": parent.specification.config.model_dump(mode="json"),
        "cost_version": registration["cost_version"],
        "slippage_version": "adverse-bps-v1",
        "month_boundaries_local": BOUNDARIES,
        "test_state": "SEALED_NOT_EVALUATED",
        "selection": "NONE",
        "success_criteria": registration["comparison_protocol"],
        "criteria_application": "PROSPECTIVE_ONLY_NOT_SCORED_ON_REUSE",
        "part_b": "NO_ACQUISITION_OR_CONFIRMATION_AUTHORIZATION",
    }


def verify_freeze(path: Path) -> tuple[str, dict[str, Any]]:
    retained = json.loads(path.read_text())
    body = freeze_body()
    identity = digest(canonical(body))
    if retained["freeze_fingerprint"] != identity or canonical(retained["freeze"]) != canonical(
        body
    ):
        raise ValueError("G1_FROZEN_IMPLEMENTATION_CHANGED_STOP_NEW_GENERATION_REQUIRED")
    return identity, body


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("freeze", "reuse"))
    parser.add_argument("--folder", type=Path, default=ROOT / "data/phase114-sbin")
    parser.add_argument("--cache", type=Path, default=ROOT / "data/phase116-reuse")
    parser.add_argument("--output", type=Path, default=ROOT / "research/phase-11.6")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    freeze_path = args.output / "implementation-freeze-g1.json"
    if args.mode == "freeze":
        body = freeze_body()
        identity = digest(canonical(body))
        pin(freeze_path, canonical({"freeze_fingerprint": identity, "freeze": body}))
        print(
            canonical(
                {
                    "usage": "DEVELOPMENT_REUSE_ONLY",
                    "freeze_fingerprint": identity,
                    "candidate_evaluations": 0,
                }
            ),
            flush=True,
        )
        return
    identity, body = verify_freeze(freeze_path)
    _, parent = evidence()
    if json.loads((args.folder / "manifest.json").read_text()) != json.loads(
        (ROOT / "research/phase-11.4/manifest.json").read_text()
    ):
        raise ValueError("MANIFEST_PIN_MISMATCH")
    corpus = load(args.folder)  # Original July integrity/schema/completeness only.
    if corpus.fingerprint != CORPUS:
        raise ValueError("CORPUS_PIN_MISMATCH")
    development = slice_corpus(corpus, local(BOUNDARIES[0]), local(BOUNDARIES[-1]))
    del corpus
    args.cache.mkdir(parents=True, exist_ok=True)
    config = parent.specification.config
    candidates = tuple(CandidateSpec.model_validate(c) for c in body["candidates"])
    rows = []
    roles: tuple[Literal["CANDIDATE", "COMPARATOR"], ...] = ("COMPARATOR", "CANDIDATE")
    for start, end in zip(BOUNDARIES, BOUNDARIES[1:], strict=False):
        subset = slice_corpus(development, local(start), local(end))
        for candidate in candidates:
            for role in roles:
                verify_freeze(freeze_path)  # Every member remains tied to the pre-run freeze.
                key = digest(identity + "\n" + start + "\n" + candidate.hypothesis + "\n" + role)
                target = args.cache / (key + ".json")
                if target.exists():
                    cached = json.loads(target.read_text())
                    values = cached["result"]
                    values["config"] = config
                    result = Result.model_validate(values)
                    if (
                        cached["usage"] != "DEVELOPMENT_REUSE_ONLY"
                        or cached["freeze_fingerprint"] != identity
                        or cached["result_fingerprint"] != result.fingerprint
                    ):
                        raise ValueError("CACHED_REUSE_RESULT_CONFLICT")
                else:
                    result = run_reuse(subset, config, candidate, role)
                if role == "COMPARATOR" and start >= "2026-04-01":
                    original = next(
                        e
                        for e in parent.evaluations
                        if e.partition == "VALIDATION"
                        and e.window.start == local(start)
                        and e.strategy_identity == digest(canonical_json(candidate.comparator))
                    )
                    if result.fingerprint != original.result_fingerprint:
                        raise ValueError("BASELINE_COMPARATOR_REGRESSION_STOP")
                payload = {
                    "usage": "DEVELOPMENT_REUSE_ONLY",
                    "freeze_fingerprint": identity,
                    "result_fingerprint": result.fingerprint,
                    "result": json.loads(canonical_json(result)),
                }
                pin(target, canonical(payload))
                rows.append(describe(subset, result, candidate, role))
                print(
                    canonical(
                        {
                            "usage": "DEVELOPMENT_REUSE_ONLY",
                            "month": start[:7],
                            "hypothesis": candidate.hypothesis,
                            "role": role,
                            "status": "RECORDED",
                            "test": "SEALED_NOT_EVALUATED",
                        }
                    ),
                    flush=True,
                )
    verify_freeze(freeze_path)
    report = ReuseReport(
        freeze_fingerprint=identity,
        registration_fingerprint=REGISTRATION,
        parent_corpus_fingerprint=CORPUS,
        parent_report_fingerprint=PARENT,
        diagnostic_fingerprint=DIAGNOSTIC,
        implementation_fingerprints=tuple(c.implementation_fingerprint for c in candidates),
        cost_version=body["cost_version"],
        evaluations=tuple(rows),
    )
    text = canonical_json(report)
    if ReuseReport.model_validate_json(text).fingerprint != report.fingerprint:
        raise ValueError("REUSE_REPORT_ROUND_TRIP_MISMATCH")
    pin(args.output / (report.fingerprint + ".json"), text)
    print(
        canonical(
            {
                "usage": "DEVELOPMENT_REUSE_ONLY",
                "report_fingerprint": report.fingerprint,
                "evaluations": len(rows),
                "confirmation": "NOT_ASSESSED",
            }
        ),
        flush=True,
    )


if __name__ == "__main__":
    main()
