"""Synthetic disposable processes only; original replay tests are never imported."""

import ast
import json
import os
import subprocess
import uuid
from pathlib import Path
from typing import Any

import pytest
from strategy_engine.phase1444ee import supervisor as native
from strategy_engine.phase1444ef import supervisor as s

RUN = uuid.uuid4().hex


def run(name: str, fault: str | None = None) -> dict[str, Any]:
    return s.run("test-" + RUN + "-" + (fault or name), name, fault=fault)


@pytest.mark.parametrize(
    "error,state",
    [
        (5, "PROCESS_ACCESS_DENIED"),
        (31, "PROCESS_IMAGE_QUERY_FAILED"),
        (6, "PROCESS_IMAGE_QUERY_FAILED"),
    ],
)
def test_error_classification(error: int, state: str) -> None:
    assert native.classify_query(False, error, "TEST_ONLY", True) == state
    assert native.classify_query(True, error, None, False) == "PROCESS_EXITED"


@pytest.mark.parametrize("pid,creation", [(6, 100), (5, 101), (0, 100)])
def test_identity_mismatch(pid: int, creation: int) -> None:
    assert not s.same_identity(5, 100, pid, creation)


def test_parent_reuse() -> None:
    child = {"parent_pid": 5, "creation": 100}
    assert not native.validate_lineage(child, {"pid": 5, "creation": 101, "exit_time": 0})
    assert not native.validate_lineage(child, {"pid": 5, "creation": 1, "exit_time": 99})
    assert not native.validate_lineage(child, None)


@pytest.mark.parametrize("path,match", [(None, False), ("TEST_ONLY", False)])
def test_unknown_identity(path: str | None, match: bool) -> None:
    assert native.classify_query(False, None, path, match) == "PROCESS_IDENTITY_UNVERIFIED"


def test_bad_command() -> None:
    with pytest.raises(ValueError, match="NOT_AUTHORIZED"):
        s.run("unapproved", "NOT_APPROVED")


def test_static_boundaries() -> None:
    source = Path(s.__file__).read_text()
    tree = ast.parse(source)
    imports = {n.names[0].name for n in ast.walk(tree) if isinstance(n, ast.Import)}
    assert imports.isdisjoint({"socket", "requests", "httpx", "sqlite3", "kiteconnect"})
    assert "os.kill(" not in source and "taskkill" not in source
    assert "native.terminatejob(job," in source
    assert "native.terminateprocess(pi.hProcess," in source
    assert "0x20002" in source  # Explicit inherited handle list.
    assert "native.run(" not in source
    assert "phase1444c" not in source and "SeriesV2" not in source
    assert s.QUERY_RIGHTS == 0x101000


@pytest.mark.parametrize(
    "name,expected",
    [
        ("normal", 0),
        ("nonzero", 7),
        ("early", 0),
        ("child", 0),
        ("descendant", 0),
        ("parent-crash", 8),
        ("child-crash", 0),
    ],
)
def test_live_lifecycle(name: str, expected: int) -> None:
    receipt = run(name)
    assert receipt["status"] == "COMPLETE", receipt
    assert receipt["exit_code"] == expected
    assert receipt["assignment_before_resume"] and receipt["job_empty_after_cleanup"]
    assert receipt["tree_identity_verified"]
    assert not receipt["errors"]
    assert set(receipt["environment_names"]).issubset(
        {"SystemRoot", "WINDIR", "TEMP", "TMP", "PYTHONNOUSERSITE", "PYTHONDONTWRITEBYTECODE"}
    )
    if name in ("child", "descendant"):
        assert len(receipt["processes"]) >= (3 if name == "descendant" else 2)


@pytest.mark.parametrize(
    "fault",
    [
        "access-denied",
        "image31",
        "missing-image",
        "wrong-image",
        "invalid-handle",
        "query-rights",
        "assignment-failure",
        "membership-failure",
        "pid-reuse",
        "handle-mismatch",
        "creation-mismatch",
    ],
)
def test_live_fault(fault: str) -> None:
    receipt = run("hold", fault)
    assert receipt["status"] == "SUPERVISION_FAILED"
    assert receipt["errors"] and receipt["exit_code"] != 259
    assert receipt["job_empty_after_cleanup"]
    assert not any(e.get("phase") == "cleanup" for e in receipt["errors"])
    assert json.loads((s.CUSTODY / (receipt["label"] + ".command.json")).read_bytes()) == receipt
    if fault in ("access-denied", "query-rights"):
        assert any(e.get("winerror") == 5 for e in receipt["errors"])


def test_live_timeout_sentinel_and_exited_query() -> None:
    policy = json.loads((s.CUSTODY / "command-allowlist.json").read_bytes())
    env = {k: os.environ[k] for k in ("SystemRoot", "WINDIR") if k in os.environ}
    with subprocess.Popen(
        policy["commands"]["sentinel"]["argv"],
        env=env,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        close_fds=True,
    ) as sentinel:
        try:
            handle = vars(sentinel)["_handle"]
            identity = s.image_identity(handle, policy["pins"], None)
            assert identity["identity_verified"]
            receipt = run("timeout")
            assert receipt["status"] == "COMPLETE", receipt
            assert receipt["timed_out"] and receipt["exit_code"] == 124
            assert receipt["job_empty_after_cleanup"] and sentinel.poll() is None
            assert len(receipt["processes"]) >= 2
            s.save(
                s.CUSTODY / ("sentinel-" + RUN + ".json"),
                {
                    "identity": identity,
                    "pid": sentinel.pid,
                    "survived": True,
                    "creation": native.info(handle)["creation"],
                    "cleanup": "RETAINED_OWNED_POPEN_HANDLE",
                },
            )
        finally:
            sentinel.terminate()
            sentinel.wait(timeout=5)
        observed = s.image_identity(handle, policy["pins"], None)
        assert observed["state"] == "PROCESS_EXITED" and not observed["identity_verified"]
