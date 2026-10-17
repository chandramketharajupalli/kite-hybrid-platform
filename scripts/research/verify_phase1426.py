"""Read-only seal verification with isolated external synthetic replays; run with -B."""

import importlib.util
import json
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1426.alerts import ALERT_POLICY
from strategy_engine.phase1426.authority import AUTHORITY_POLICY

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = ROOT / "research/phase-14.26/generation-1"


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", "--no-optional-locks", *args], cwd=ROOT)


def verify() -> dict[str, Any]:
    envelope = json.loads((DIRECTORY / "evidence-manifest.json").read_bytes())
    m = envelope["manifest"]
    assert sha(canonical(m)) == envelope["fingerprint"], "MANIFEST_HASH"
    assert git("branch", "--show-current").strip() == b"develop"
    for ref in ("HEAD", "origin/develop"):
        assert git("rev-parse", ref).decode().strip() == m["baseline"]
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    tracked = set(git("ls-files").decode().splitlines())
    new = set(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
    assert tracked == set(m["baseline_hashes"]) and len(tracked) == 1338
    assert new == set(m["new_files"]) | set(m["preservation_exceptions"])
    assert set(m["artifacts"]) == set(m["new_files"]) - {
        "research/phase-14.26/generation-1/evidence-manifest.json"
    }
    for n, h in (
        m["baseline_hashes"] | m["artifacts"] | m["preservation_exceptions"]
    ).items():
        assert sha((ROOT / n).read_bytes()) == h, n
    for n in m["new_files"]:
        raw = (ROOT / n).read_bytes()
        assert b"\r" not in raw and raw.endswith(b"\n"), n
        assert all(x.rstrip(b" \t") == x for x in raw.splitlines()), n
        assert git("hash-object", n) == git("hash-object", "--no-filters", n), n
    assert sha(canonical(ALERT_POLICY)) == m["alert_policy_fingerprint"]
    assert sha(canonical(AUTHORITY_POLICY)) == m["authority_policy_fingerprint"]
    assert sha(canonical(m["software_inventory"])) == m["software_fingerprint"]
    for n, h in m["software_inventory"].items():
        assert sha((ROOT / n).read_bytes()) == h, n
    for n, h in m["external_evidence"].items():
        assert sha(Path(n).read_bytes()) == h, n
    tests = {}
    for entry in m["junit"]:
        root = ET.fromstring(Path(entry["path"]).read_bytes())
        cases = list(root.iter("testcase"))
        assert len(cases) == entry["tests"] > 0
        assert all(
            int(s.attrib[k]) == 0
            for s in root.iter("testsuite")
            for k in ("failures", "errors", "skipped")
        )
        assert not any(list(root.iter(k)) for k in ("failure", "error", "skipped"))
        tests[entry["id"]] = len(cases)
    for name in m["required_commands"]:
        selected = [c for c in m["commands"] if c["id"] == name]
        assert len(selected) == 1 and selected[0]["exit_code"] == 0
    for command in m["commands"]:
        path = Path(command["path"])
        receipt = json.loads(path.read_bytes())
        assert receipt["exit_code"] == command["exit_code"]
        assert receipt["protected_changed"] == []
        for ext in ("stdout", "stderr"):
            assert (
                sha(path.with_suffix("." + ext).read_bytes())
                == receipt[ext + "_sha256"]
            )
    recorded = json.loads((DIRECTORY / "synthetic-alert-authority.json").read_bytes())
    assert sha(canonical(recorded)) == m["report_fingerprint"]
    assert (
        sha(canonical({k: v for k, v in recorded.items() if k != "fingerprint"}))
        == recorded["fingerprint"]
    )
    current = recorded["assurance"]
    assert current["frozen_incident_count"] == current["timely_monitored_alerts"] == 10
    assert current["missed_critical_alerts"] == current["false_positives"] == 0
    assert not current["late_alerts"] and not current["duplicate_alerts"]
    assert current["certification_eligible"] and current["paper_halt_required"]
    assert not recorded["missing_alert"]["certification_eligible"]
    assert recorded["missing_alert"]["missed_critical_alerts"] == 1
    old = recorded["historical"]
    assert (old["true_positives"], old["false_positives"], old["false_negatives"]) == (
        2,
        3,
        8,
    )
    assert len(old["duplicate_alerts"]) == len(old["late_alerts"]) == 1
    matched = {r["incident_id"] for r in current["matches"]}
    assert set(old["missed_critical"]) <= matched
    assert recorded["accounting"]["accounting"]["invariants"] == "PASS"
    assert recorded["halted_continuity"]["state"]["halted"]
    assert len(recorded["halted_continuity"]["sessions"]) == 1
    assert recorded["competing_consumers"] == {"accepted": 1, "rejected": 1}
    assert recorded["recovery"]["state"]["paper_halt"]
    assert recorded["operational"] == "NO_GO" and recorded["genuine_qualified"] == 0
    sys.path.insert(0, str(ROOT / "apps/strategy-engine/tests"))
    from test_phase1426 import build_reports

    with tempfile.TemporaryDirectory(prefix="phase1426-replay-") as temp:
        assert build_reports(Path(temp)) == recorded, "REPLAY_DIFFERENCE"
    spec = importlib.util.spec_from_file_location(
        "preserved1426", ROOT / "scripts/research/verify_phase1415d.py"
    )
    assert spec and spec.loader
    old_verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(old_verifier)
    protected = old_verifier.protected(m["baseline"])
    old_verifier.reports()
    scans = old_verifier.scan(sorted(tracked | new))
    assert not git("diff", "--name-only") and not git("diff", "--cached", "--name-only")
    return {
        "verification": "PASS",
        "classification": m["classification"],
        "baseline": m["baseline"],
        "manifest": envelope["fingerprint"],
        "report": m["report_fingerprint"],
        "protected": len(tracked),
        "protected_details": protected,
        "tests": tests,
        "scans": scans,
        "new_files": len(m["new_files"]),
        "real_kite_requests": 0,
        "Gate_A": "NOT_AUTHORIZED",
        "Gate_B": "NOT_AUTHORIZED",
        "operational": "NO_GO",
    }


if __name__ == "__main__":
    print(json.dumps(verify(), sort_keys=True))
