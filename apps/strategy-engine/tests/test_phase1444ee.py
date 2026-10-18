"""Disposable Windows process tests; no guest or original database tests."""

import ast
import json
import subprocess
import sys
import uuid
from pathlib import Path
from typing import Any

import pytest
from strategy_engine.phase1444ee import supervisor as s

ROOT = Path(__file__).resolve().parents[3]
EXE = ROOT / "apps/strategy-engine/.venv/Scripts/python.exe"
PINS = s.allowed_images(EXE, Path(sys._base_executable))
RUN_ID = uuid.uuid4().hex


def receipt_label(name: str) -> str:
    return "test-" + RUN_ID + "-" + name.replace("_", "-")


def launch(name: str, code: str, budget: float = 3, fault: str | None = None) -> dict[str, Any]:
    return s.run(
        receipt_label(name),
        [str(EXE), "-B", "-c", code],
        ROOT,
        budget,
        PINS,
        purpose="TEST_ONLY",
        fault=fault,
    )


@pytest.mark.parametrize(
    "name,code,expected",
    [
        ("normal", "import time;time.sleep(.3)", 0),
        ("nonzero", "import time;time.sleep(.3);raise SystemExit(7)", 7),
        (
            "childcrash",
            "import subprocess,sys,time; p=subprocess.Popen([sys.executable,'-"
            "B','-c','raise SystemExit(9)']);p.wait();time.sleep(.3)",
            0,
        ),
        ("parentcrash", "import time,os;time.sleep(.3);os._exit(8)", 8),
        (
            "shortchild",
            "import subprocess,sys,time;p=subprocess.Popen([sys.executable,'-B"
            "','-c','pass']);p.wait();time.sleep(.3)",
            0,
        ),
        (
            "longchild",
            "import subprocess,sys;p=subprocess.Popen([sys.executable,'-B','-c"
            "','import time;time.sleep(.4)']);p.wait()",
            0,
        ),
        (
            "descendant",
            "import subprocess,sys;p=subprocess.Popen([sys.executable,'-B','-c"
            "',\"import subprocess,sys;p=subprocess.Popen([sys.executable,'-B',"
            "'-c','import time;time.sleep(.4)']);p.wait()\"]);p.wait()",
            0,
        ),
    ],
)
def test_process_lifecycle(name: str, code: str, expected: int) -> None:
    receipt = launch(name, code)
    assert receipt["status"] == "COMPLETE"
    assert receipt["exit_code"] == expected
    assert receipt["assignment_before_resume"]
    assert receipt["job_empty_after_cleanup"]


@pytest.mark.parametrize(
    "fault",
    [
        "image31",
        "access_denied",
        "missing_image",
        "invalid_handle",
        "executable_mismatch",
        "creation_mismatch",
        "assignment_failure",
        "job_closed",
    ],
)
def test_failure_retains_receipt_and_cleanup(fault: str) -> None:
    receipt = launch(fault, "import time;time.sleep(5)", fault=fault)
    assert receipt["status"] == "SUPERVISION_FAILED"
    assert receipt["errors"]
    assert receipt["exit_code"] != 259
    stored = json.loads((s.CUSTODY / (receipt_label(fault) + ".command.json")).read_bytes())
    assert stored == receipt
    if fault == "image31":
        assert any(e.get("winerror") == 31 for e in receipt["errors"])
    if fault == "assignment_failure":
        assert not receipt["assignment_before_resume"]


def test_timeout_and_unrelated_sentinel() -> None:
    sentinel = subprocess.Popen([sys._base_executable, "-B", "-c", "import time;time.sleep(15)"])
    try:
        receipt = launch(
            "timeout",
            "import subprocess,sys,time;subprocess.Popen([sys.executable,'-B',"
            "'-c','import time;time.sleep(10)']);time.sleep(10)",
            0.8,
        )
        assert receipt["timed_out"] and receipt["exit_code"] == 124
        assert receipt["job_empty_after_cleanup"] and sentinel.poll() is None
        assert len(receipt["processes"]) >= 2
        (s.CUSTODY / ("sentinel-" + RUN_ID + ".json")).write_text(
            json.dumps(
                {
                    "unrelated_sentinel_survived": True,
                    "owned_job_empty": True,
                    "sentinel_cleanup": "retained_owned_Popen_handle",
                }
            )
            + "\n"
        )
    finally:
        sentinel.terminate()
        sentinel.wait(timeout=5)


@pytest.mark.parametrize(
    "exited,error,path,match,state",
    [
        (True, 31, None, False, "PROCESS_EXITED"),
        (False, 31, None, False, "PROCESS_IMAGE_QUERY_FAILED"),
        (False, 5, None, False, "PROCESS_ACCESS_DENIED"),
        (False, None, None, False, "PROCESS_IDENTITY_UNVERIFIED"),
        (False, None, "TEST_ONLY", True, "PROCESS_IDENTITY_VERIFIED"),
    ],
)
def test_query_state(
    exited: bool, error: int | None, path: str | None, match: bool, state: str
) -> None:
    assert s.classify_query(exited, error, path, match) == state


def test_parent_pid_reuse_rejected() -> None:
    child = {"parent_pid": 5, "creation": 100}
    assert not s.validate_lineage(child, {"pid": 5, "creation": 101, "exit_time": 0})
    assert not s.validate_lineage(child, {"pid": 5, "creation": 1, "exit_time": 99})
    assert not s.validate_lineage(child, None)


def test_pid_reuse_not_identity() -> None:
    assert s.classify_query(False, None, "TEST_ONLY", False) == "PROCESS_IDENTITY_UNVERIFIED"


def test_no_false_retry_promotion() -> None:
    assert s.classify_query(False, 31, "TEST_ONLY", True) == "PROCESS_IMAGE_QUERY_FAILED"
    assert s.classify_query(True, 31, "TEST_ONLY", True) == "PROCESS_EXITED"


def test_no_authorization_from_test_context() -> None:
    with pytest.raises(ValueError, match="NOT_AUTHORIZED"):
        s.run("real", [str(EXE)], ROOT, 1, PINS, purpose="REAL")


def test_receipt_overwrite_rejected() -> None:
    launch("once", "import time;time.sleep(.2)")
    with pytest.raises(ValueError, match="RECEIPT_EXISTS"):
        launch("once", "pass")


def test_static_no_pid_only_termination() -> None:
    text = Path(s.__file__).read_text()
    assert "os.kill(" not in text and "taskkill" not in text
    assert "terminatejob(job," in text


def test_static_network_credential_database_broker_exclusion() -> None:
    tree = ast.parse(Path(s.__file__).read_bytes())
    modules = {n.names[0].name for n in ast.walk(tree) if isinstance(n, ast.Import)}
    assert modules.isdisjoint({"socket", "requests", "httpx", "sqlite3", "kiteconnect"})
    assert all("phase1444c" not in name for name in modules)
    assert "PYTHONPATH" not in s.run.__code__.co_consts


def test_no_guest_or_series_authority() -> None:
    assert "NOT_AUTHORIZED" in s.run.__code__.co_consts
