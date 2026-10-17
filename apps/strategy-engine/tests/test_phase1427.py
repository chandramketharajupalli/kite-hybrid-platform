"""Bounded RC integration over unchanged synthetic trading and evidence contracts."""

import json
import os
import subprocess
import sys
from decimal import Decimal
from pathlib import Path
from typing import Any

import pytest
from pydantic import TypeAdapter
from test_phase1419 import fixture
from test_phase1421 import report as analytics_report
from test_phase1424 import observations_fixture, source_fixture
from test_phase1426 import build_reports as prior_reports
from test_phase1426 import recover, setup_authority

from strategy_engine.phase1415.hardening import ROOT, frozen_policy
from strategy_engine.phase1415.strategies import registry
from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1419 import scheduler
from strategy_engine.phase1423.quality import accepted_fills, attribute, fixture_reference
from strategy_engine.phase1424.analytics import exposure
from strategy_engine.phase1425.reliability import session_check
from strategy_engine.phase1427.assessment import (
    dependencies,
    identity,
    source_archive,
    source_inventory,
)


def serial(value: Any) -> Any:
    return json.loads(TypeAdapter(Any).dump_json(value))


def scenario(name: str, inputs: Any, expected: Any, actual: Any) -> dict[str, Any]:
    body = dict(
        scenario=name,
        scope="SYNTHETIC_FIXTURE",
        inputs=serial(inputs),
        expected=expected,
        actual=actual,
    )
    return body | {
        "input_fingerprint": sha(canonical(body["inputs"])),
        "fingerprint": sha(canonical(body)),
    }


def build_reports(directory: Path) -> dict[str, Any]:
    previous = prior_reports(directory / "prior-contracts")
    args = fixture()
    normal = scheduler.run(*args, directory / "normal")
    assert normal["complete"] and not normal["account"]["positions"]
    check = session_check(directory / "normal", normal, args[1].base.capital or Decimal(0))
    assert scheduler.run(*args, directory / "normal", verify_only=True) == normal
    source = source_fixture()
    fills = accepted_fills(source, sha(canonical(source)))
    references = [fixture_reference(f, f.price) for f in fills[:-1]]
    observations = observations_fixture(source)[1:]
    quality = attribute(fills, references)
    partial = exposure(source, sha(canonical(source)), observations)
    analytics = analytics_report(source)
    assert quality["portfolio"]["status"] == "PARTIAL"
    assert quality["portfolio"]["signed_total"] is None
    assert partial["status"] == "NOT_COMPUTABLE" and partial["exposure_adjusted_return"] is None
    a, state, head, approval = setup_authority(directory / "explicit-approval")
    authorized = recover(a, state, head, approval)
    historical_inputs = dict(
        fixture_source_sha256=sha(
            (ROOT / "apps/strategy-engine/tests/test_phase1426.py").read_bytes()
        ),
        inherited_report_fingerprint=previous["fingerprint"],
        contract="Frozen deterministic fixture definitions and external journals",
    )
    rows = [
        scenario(
            "A",
            args,
            {"complete": True, "accounting": "PASS"},
            {"result": normal, "accounting": check},
        ),
        scenario(
            "B",
            historical_inputs | {"fixture": "test_phase1420.fixture"},
            {"sessions": 2, "closing_cash": "9997.83041920"},
            previous["normal_continuity"],
        ),
        scenario(
            "C",
            historical_inputs | {"fixture": "test_phase1425.halted_multi"},
            {"halted": True, "sessions": 1, "unresolved_SBIN": 10},
            previous["halted_continuity"],
        ),
        scenario(
            "D",
            {
                "accepted_events": previous["accepted_events"],
                "truth_fingerprint": previous["assurance"]["truth_fingerprint"],
            },
            {"timely": 10, "missed": 0},
            previous["assurance"],
        ),
        scenario(
            "E",
            {"approval": approval, "witness": serial(state), "head": head},
            {"consumed_before_mutation": True, "halt_clearance": False},
            authorized,
        ),
        scenario(
            "F",
            historical_inputs | {"fixture": "test_surviving_witness_rejects_rollback"},
            {"rejected": 5},
            previous["recovery_decisions"][:5],
        ),
        scenario(
            "G",
            historical_inputs | {"fixture": "test_complete_authority_rollback_counterexample"},
            {"universal_prevention": False},
            previous["complete_rollback"],
        ),
        scenario(
            "H",
            {"source": source, "references": references, "observations": observations},
            {"quality": "PARTIAL", "exposure": "NOT_COMPUTABLE", "unavailable": None},
            {"quality": quality, "exposure": partial, "analytics": analytics},
        ),
    ]
    body = dict(
        version="Phase1427Scenarios.v1",
        scope="SYNTHETIC_FIXTURE",
        scenarios=rows,
        historical_monitoring=previous["historical"],
        deliberate_missing_alert=previous["missing_alert"],
        one_use=previous["competing_consumers"],
        replay=previous["replay_rejection"],
        genuine_qualified=0,
        operational="NO_GO",
        real_kite_requests=0,
    )
    return body | {"fingerprint": sha(canonical(body))}


@pytest.fixture(scope="module")
def integrated(tmp_path_factory: pytest.TempPathFactory) -> dict[str, Any]:
    return build_reports(tmp_path_factory.mktemp("rc-integrated"))


@pytest.mark.parametrize("name", list("ABCDEFGH"))
def test_integrated_scenarios(integrated: dict[str, Any], name: str) -> None:
    row = next(r for r in integrated["scenarios"] if r["scenario"] == name)
    assert row["input_fingerprint"] == sha(canonical(row["inputs"]))
    actual = row["actual"]
    if name == "A":
        assert actual["result"]["complete"] and actual["accounting"]["invariants"] == "PASS"
    elif name == "B":
        assert len(actual["sessions"]) == 2 and actual["state"]["cash"] == "9997.83041920"
        assert actual["sessions"][0]["closing_state"] == actual["sessions"][1]["starting_state"]
    elif name == "C":
        assert actual["state"]["halted"] and len(actual["sessions"]) == 1
        assert actual["state"]["positions"]["SBIN"]["quantity"] == 10
    elif name == "D":
        assert actual["timely_monitored_alerts"] == 10 and actual["missed_critical_alerts"] == 0
        assert (
            not actual["false_positives"]
            and not actual["duplicate_alerts"]
            and not actual["late_alerts"]
        )
    elif name == "E":
        assert actual["consumed"] and actual["state"]["paper_halt"] and not actual["halt_clearance"]
    elif name == "F":
        assert len(actual) == 5 and all(
            r["result"] == "REJECTED_WITH_SURVIVING_WITNESS" for r in actual
        )
    elif name == "G":
        assert actual == "REUSE_POSSIBLE_WHEN_ALL_AUTHORITY_AND_WITNESSES_ROLL_BACK"
    else:
        assert actual["quality"]["portfolio"]["signed_total"] is None
        assert actual["exposure"]["capital_time"] is None


def test_preserved_monitoring_and_denial(integrated: dict[str, Any]) -> None:
    old = integrated["historical_monitoring"]
    assert (old["true_positives"], old["false_negatives"], old["false_positives"]) == (2, 8, 3)
    assert len(old["duplicate_alerts"]) == len(old["late_alerts"]) == 1
    assert not integrated["deliberate_missing_alert"]["certification_eligible"]
    assert integrated["one_use"] == {"accepted": 1, "rejected": 1}


def test_source_graph_and_qualification() -> None:
    result = source_inventory()
    assert not result["findings"] and not result["broker_endpoints"]
    assert not result["credential_access_references"]
    assert "strategy_engine.config" not in result["graph"]
    assert len(registry()) == 6 and frozen_policy().family_size == 13380


def test_installed_dependency_integrity() -> None:
    result = dependencies()
    assert not result["lock_mismatches"] and not result["missing_locked"]
    assert all(r["status"] == "VERIFIED_INSTALLED_RECORD" for r in result["records"])
    assert all(r["satisfied"] for r in result["constraints"])


def test_deterministic_source_archive(tmp_path: Path) -> None:
    inventory = source_inventory()["inventory"]
    assert source_archive(tmp_path / "a", inventory) == source_archive(tmp_path / "b", inventory)


@pytest.mark.parametrize("fault", ["repository", "traversal", "hash"])
def test_output_and_source_boundaries(tmp_path: Path, fault: str) -> None:
    entries = source_inventory()["inventory"]
    if fault == "repository":
        with pytest.raises(ValueError):
            source_archive(ROOT / "forbidden-rc-output", entries)
    else:
        key = next(iter(entries))
        bad = {"../outside.py": "0" * 64} if fault == "traversal" else {key: "0" * 64}
        with pytest.raises(ValueError):
            source_archive(tmp_path / fault, bad)


def test_identity_binding() -> None:
    names = (
        "software",
        "dependencies",
        "strategies",
        "qualification",
        "risk",
        "execution",
        "monitoring",
        "authority",
        "evidence_payload",
        "build_environment",
        "test_receipts",
    )
    bindings = dict.fromkeys(names, "a" * 64) | {"git_sha": "b" * 40}
    first = identity(bindings)
    assert first == identity(bindings)
    assert first != identity(bindings | {"test_receipts": "c" * 64})
    with pytest.raises(ValueError):
        identity({"git_sha": "b" * 40})


def test_process_guard(tmp_path: Path) -> None:
    code = """import json,sys
from pathlib import Path
def guard(event,args):
    if event.startswith('socket.') or event in {'subprocess.Popen','os.system'}:
        raise PermissionError('OFFLINE_PROCESS_GUARD')
    if event=='open' and isinstance(args[0],(str,bytes)):
        name=str(args[0]).lower()
        if name.endswith('.env') or 'kite-credentials' in name:
            raise PermissionError('CREDENTIAL_ACCESS_DENIED')
sys.addaudithook(guard)
import socket,subprocess
for action in (lambda:socket.socket(),lambda:subprocess.Popen(['not-started']),lambda:open('.env')):
    try:action()
    except PermissionError:pass
    else:raise AssertionError('GUARD_FAILED')
sys.path.insert(0,sys.argv[1])
from test_phase1427 import build_reports
r=build_reports(Path(sys.argv[2]))
print(json.dumps({'scenario_count':len(r['scenarios']),'fingerprint':r['fingerprint'],'guard':'PASS','production_egress':'PRODUCTION_EGRESS_NOT_VERIFIED'}))
"""
    result = subprocess.run(
        [
            sys.executable,
            "-B",
            "-c",
            code,
            str(ROOT / "apps/strategy-engine/tests"),
            str(tmp_path / "guarded"),
        ],
        capture_output=True,
        env=os.environ | {"PYTHONDONTWRITEBYTECODE": "1"},
    )
    assert result.returncode == 0, result.stderr.decode()
    assert json.loads(result.stdout)["scenario_count"] == 8


def test_deterministic_scenario_replay(integrated: dict[str, Any], tmp_path: Path) -> None:
    assert build_reports(tmp_path / "replay") == integrated
