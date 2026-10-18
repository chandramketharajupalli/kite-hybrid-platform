"""Bounded synthetic commands in owned jobs; retained handles establish identity."""

import ctypes as C
import hashlib
import json
import msvcrt
import os
import subprocess
import time
from ctypes import wintypes as W
from pathlib import Path
from typing import Any

from strategy_engine.phase1444ee import supervisor as native

CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-F/generation-1")
ALLOWLIST_SHA256 = "8da54faebd03a9bb1fa83073ded7ae98bec693c7d3263b3be366d062e7c29555"
QUERY_RIGHTS = 0x101000  # SYNCHRONIZE | PROCESS_QUERY_LIMITED_INFORMATION
PROCESS_ID = native.api("GetProcessId", [W.HANDLE], W.DWORD)


class StartupEx(C.Structure):
    _fields_ = [("startup", native.SI), ("attributes", C.c_void_p)]


init_attributes = native.api(
    "InitializeProcThreadAttributeList", [C.c_void_p, W.DWORD, W.DWORD, C.POINTER(C.c_size_t)]
)
update_attributes = native.api(
    "UpdateProcThreadAttribute",
    [C.c_void_p, W.DWORD, C.c_size_t, C.c_void_p, C.c_size_t, C.c_void_p, C.c_void_p],
)
delete_attributes = native.api("DeleteProcThreadAttributeList", [C.c_void_p], None)
create_process = native.api(
    "CreateProcessW",
    [
        W.LPCWSTR,
        W.LPWSTR,
        C.c_void_p,
        C.c_void_p,
        W.BOOL,
        W.DWORD,
        C.c_void_p,
        W.LPCWSTR,
        C.c_void_p,
        C.POINTER(native.PI),
    ],
)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path: Path, value: Any) -> None:
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(value, sort_keys=True, indent=2, allow_nan=False) + "\n")


def exited(handle: Any) -> bool:
    result = native.wait(handle, 0)
    if result == 0xFFFFFFFF:
        raise C.WinError(C.get_last_error())
    if result not in (0, 258):
        raise ValueError("UNEXPECTED_WAIT_STATE")
    return bool(result == 0)


def image_identity(handle: Any, pins: dict[str, str], fault: str | None) -> dict[str, Any]:
    before = exited(handle)
    code: int | None = None
    path: str | None = None
    raw: str | None = None
    if not before:
        size = W.DWORD(32768)
        buffer = C.create_unicode_buffer(size.value)
        injected = {"access-denied": 5, "image31": 31, "invalid-handle": 6, "query-rights": 5}
        if fault in injected:
            code = injected[fault]
        elif fault != "missing-image":
            if native.image(handle, 0, buffer, C.byref(size)):
                if not 0 < size.value < 32768 or not buffer.value:
                    raise ValueError("INVALID_IMAGE_BUFFER")
                path = str(Path(buffer.value).resolve())
                raw = digest(Path(path))
            else:
                code = C.get_last_error()
    after = exited(handle)
    matches = bool(path and pins.get(path.casefold()) == raw and fault != "wrong-image")
    return {
        "api": "QueryFullProcessImageNameW",
        "handle": int(handle),
        "state": native.classify_query(after, code, path, matches),
        "winerror": code,
        "image_path": path,
        "image_sha256": raw,
        "identity_verified": matches and code is None,
        "exited_before_query": before,
        "exited_after_query": after,
        "injected": fault,
    }


def same_identity(pid: int, creation: int, observed_pid: int, observed_creation: int) -> bool:
    return pid > 0 and pid == observed_pid and creation == observed_creation


def run(label: str, command_name: str, *, fault: str | None = None) -> dict[str, Any]:
    """Execute only a frozen diagnostic command. This grants no governance authority."""
    if not label or not label.replace("-", "").isalnum():
        raise ValueError("LABEL")
    for path in (CUSTODY, *CUSTODY.parents):
        if path.lstat().st_file_attributes & 0x400:
            raise ValueError("REPARSE_CUSTODY")
    allow_path = CUSTODY / "command-allowlist.json"
    if digest(allow_path) != ALLOWLIST_SHA256:
        raise ValueError("COMMAND_ALLOWLIST_NOT_FROZEN")
    policy = json.loads(allow_path.read_bytes())
    if command_name not in policy["commands"] or fault not in policy["faults"]:
        raise ValueError("NOT_AUTHORIZED")
    spec = policy["commands"][command_name]
    command = spec["argv"]
    budget = spec["budget_seconds"]
    pins = policy["pins"]
    if not 0 < budget <= 300 or command[1:4] != ["-I", "-S", "-B"]:
        raise ValueError("COMMAND_POLICY")
    if pins.get(str(Path(command[0]).resolve()).casefold()) != digest(Path(command[0])):
        raise ValueError("EXECUTABLE_PIN")
    for name, expected in spec.get("source_hashes", {}).items():
        if digest(Path(name)) != expected:
            raise ValueError("SOURCE_PIN")
    target = CUSTODY / (label + ".command.json")
    if any(
        (CUSTODY / (label + suffix)).exists() for suffix in (".command.json", ".stdout", ".stderr")
    ):
        raise ValueError("RECEIPT_EXISTS")
    env = {key: os.environ[key] for key in ("SystemRoot", "WINDIR") if key in os.environ}
    env.update(
        TEMP=str(CUSTODY), TMP=str(CUSTODY), PYTHONNOUSERSITE="1", PYTHONDONTWRITEBYTECODE="1"
    )
    block = C.create_unicode_buffer("\0".join(k + "=" + v for k, v in sorted(env.items())) + "\0\0")
    record: dict[str, Any] = {
        "contract": "Phase1444EFProcessSupervisor.v1",
        "purpose": "TEST_ONLY",
        "label": label,
        "command_name": command_name,
        "command": command,
        "command_allowlist_sha256": ALLOWLIST_SHA256,
        "budget_seconds": budget,
        "start_utc": native.utc(),
        "launcher_pid": os.getpid(),
        "errors": [],
        "environment_names": sorted(env),
        "fault_injected": fault,
        "samples": [],
        "coverage_gaps": [],
        "assignment_before_resume": False,
        "timed_out": False,
        "job_empty_after_cleanup": False,
        "status": "SUPERVISION_FAILED",
        "sampling_seconds": 0.02,
        "inherited_handles": "EXPLICIT_STDIN_STDOUT_STDERR_ONLY",
        "limitations": [
            "Polling can miss short-lived children.",
            "Synthetic commands do not establish OS network or guest containment.",
        ],
    }
    pi = native.PI()
    job: Any = None
    handles: dict[int, Any] = {}
    processes: dict[int, dict[str, Any]] = {}
    assigned = False
    started = time.monotonic()
    try:
        current = native.api("GetCurrentProcess", [], W.HANDLE)()
        record["launcher_creation"] = native.info(current)["creation"]
        job = native.createjob(None, None)
        native.check(job)
        limits = native.ExtendedLimits()
        limits.basic.flags = 0x2000
        native.check(native.setjob(job, 9, C.byref(limits), C.sizeof(limits)))
        record["job_identity"] = {
            "handle": int(job),
            "owner_pid": os.getpid(),
            "created_before_launch": True,
            "kill_on_close": True,
        }
        with (
            (CUSTODY / (label + ".stdout")).open("xb") as out,
            (CUSTODY / (label + ".stderr")).open("xb") as err,
            open(os.devnull, "rb") as inp,
        ):
            streams = [msvcrt.get_osfhandle(f.fileno()) for f in (inp, out, err)]
            size = C.c_size_t()
            init_attributes(None, 1, 0, C.byref(size))
            attributes = C.create_string_buffer(size.value)
            native.check(init_attributes(attributes, 1, 0, C.byref(size)))
            inherited = (W.HANDLE * 3)(*streams)
            try:
                for handle in streams:
                    os.set_handle_inheritable(handle, True)
                native.check(
                    update_attributes(
                        attributes, 0, 0x20002, inherited, C.sizeof(inherited), None, None
                    )
                )
                startup = StartupEx()
                startup.startup.cb = C.sizeof(startup)
                startup.startup.dwFlags = 0x100
                startup.startup.hStdInput, startup.startup.hStdOutput, startup.startup.hStdError = (
                    streams
                )
                startup.attributes = C.cast(attributes, C.c_void_p)
                native.check(
                    create_process(
                        command[0],
                        C.create_unicode_buffer(subprocess.list2cmdline(command)),
                        None,
                        None,
                        True,
                        0x4 | 0x400 | 0x08000000 | 0x80000,
                        block,
                        str(CUSTODY),
                        C.byref(startup),
                        C.byref(pi),
                    )
                )
            finally:
                for handle in streams:
                    os.set_handle_inheritable(handle, False)
                delete_attributes(attributes)
            root = native.info(pi.hProcess)
            root.update(
                pid=int(pi.dwProcessId),
                parent_pid=os.getpid(),
                handle=int(pi.hProcess),
                handle_origin="CreateProcessW",
                requested_access="CREATE_PROCESS_RETURNED_HANDLE",
                peak_observed_working_set=0,
                parent_identity_verified=True,
            )
            handles[int(pi.dwProcessId)] = pi.hProcess
            processes[int(pi.dwProcessId)] = root
            record.update(root_pid=int(pi.dwProcessId), root_creation=root["creation"])
            root.update(image_identity(pi.hProcess, pins, fault))
            if not root["identity_verified"]:
                record["errors"].append(dict(root))
                raise ValueError("ROOT_IDENTITY_UNVERIFIED")
            if fault == "assignment-failure":
                raise ValueError("INJECTED_ASSIGNMENT_FAILURE")
            native.check(native.assign(job, pi.hProcess))
            assigned = True
            member = W.BOOL()
            native.check(native.injob(pi.hProcess, job, C.byref(member)))
            if not member.value or fault == "membership-failure":
                raise ValueError("JOB_MEMBERSHIP_UNVERIFIED")
            root["job_member"] = True
            record["assignment_before_resume"] = True
            native.check(native.resume(pi.hThread) != 0xFFFFFFFF)
            native.close(pi.hThread)
            pi.hThread = None
            while True:
                ids = native.members(job)
                ancestry = native.parents()
                for pid in ids:
                    if pid not in handles:
                        handle = native.openprocess(QUERY_RIGHTS, False, pid)
                        if not handle:
                            record["errors"].append(
                                {
                                    "api": "OpenProcess",
                                    "pid": pid,
                                    "rights": QUERY_RIGHTS,
                                    "winerror": C.get_last_error(),
                                }
                            )
                            raise ValueError("CHILD_IDENTITY_UNVERIFIED")
                        handles[pid] = handle
                        child = native.info(handle)
                        child.update(
                            pid=pid,
                            parent_pid=ancestry.get(pid),
                            handle=int(handle),
                            handle_origin="OpenProcess",
                            requested_access=QUERY_RIGHTS,
                            peak_observed_working_set=0,
                        )
                        processes[pid] = child
                        member = W.BOOL()
                        native.check(native.injob(handle, job, C.byref(member)))
                        child["job_member"] = bool(member.value)
                        child.update(image_identity(handle, pins, None))
                        if not member.value or not child["identity_verified"]:
                            record["errors"].append(dict(child))
                            raise ValueError("CHILD_IDENTITY_UNVERIFIED")
                    entry, handle = processes[pid], handles[pid]
                    now = native.info(handle)
                    actual_pid = int(PROCESS_ID(handle))
                    if fault in ("pid-reuse", "handle-mismatch"):
                        actual_pid += 1
                    creation = now["creation"] + (1 if fault == "creation-mismatch" else 0)
                    if not same_identity(pid, entry["creation"], actual_pid, creation):
                        raise ValueError("HANDLE_PROCESS_IDENTITY_MISMATCH")
                    memory = native.MEM()
                    memory.cb = C.sizeof(memory)
                    if native.P.GetProcessMemoryInfo(handle, C.byref(memory), memory.cb):
                        entry["peak_observed_working_set"] = max(
                            entry["peak_observed_working_set"], memory.working
                        )
                    else:
                        entry["memory_query_unavailable"] = C.get_last_error()
                    record["samples"].append(
                        {
                            "pid": pid,
                            "creation": now["creation"],
                            "cpu_seconds": now["cpu_seconds"],
                            "elapsed": time.monotonic() - started,
                        }
                    )
                if not ids:
                    break
                if time.monotonic() - started >= budget:
                    native.check(native.terminatejob(job, 124))
                    record["timed_out"] = True
                    break
                time.sleep(0.02)
            record["status"] = "COMPLETE"
    except Exception as exc:
        record["errors"].append(
            {
                "type": type(exc).__name__,
                "winerror": getattr(exc, "winerror", None),
                "reason": str(exc) if isinstance(exc, ValueError) else "NATIVE_API_FAILURE",
            }
        )
    finally:
        try:
            if job and assigned:
                if native.members(job):
                    native.check(native.terminatejob(job, 124 if record["timed_out"] else 125))
                deadline = time.monotonic() + 10
                while native.members(job) and time.monotonic() < deadline:
                    time.sleep(0.02)
                record["job_empty_after_cleanup"] = not native.members(job)
                if not record["job_empty_after_cleanup"]:
                    raise ValueError("OWNED_JOB_CLEANUP_INCOMPLETE")
            elif pi.hProcess:
                native.check(native.terminateprocess(pi.hProcess, 125))
                record["job_empty_after_cleanup"] = bool(job and not native.members(job))
            if pi.hProcess:
                if native.wait(pi.hProcess, 10000) != 0:
                    raise ValueError("ROOT_EXIT_UNVERIFIED")
                record["exit_code"] = native.info(pi.hProcess)["exit_code"]
            for pid, handle in handles.items():
                processes[pid].update(native.info(handle))
            for pid, entry in processes.items():
                if pid != record.get("root_pid"):
                    entry["parent_identity_verified"] = native.validate_lineage(
                        entry, processes.get(entry["parent_pid"])
                    )
            if record["status"] == "COMPLETE" and not all(
                entry.get("parent_identity_verified") for entry in processes.values()
            ):
                raise ValueError("PARENT_LIFETIME_UNVERIFIED")
        except Exception as exc:
            record["status"] = "SUPERVISION_FAILED"
            record["errors"].append(
                {
                    "phase": "cleanup",
                    "type": type(exc).__name__,
                    "winerror": getattr(exc, "winerror", None),
                    "reason": str(exc),
                }
            )
        for handle in handles.values():
            if handle != pi.hProcess:
                native.close(handle)
        if pi.hThread:
            native.close(pi.hThread)
        if pi.hProcess:
            native.close(pi.hProcess)
        if job:
            native.close(job)
        record["processes"] = list(processes.values())
        record["tree_identity_verified"] = bool(processes) and all(
            p.get("identity_verified") and p.get("job_member") and p.get("parent_identity_verified")
            for p in processes.values()
        )
        record["end_utc"] = native.utc()
        record["duration_seconds"] = time.monotonic() - started
        save(target, record)
    return record
