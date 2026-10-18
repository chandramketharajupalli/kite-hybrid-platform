"""Local diagnostic supervisor. Assign suspended launch to owned Windows job first."""

import ctypes as C
import datetime
import hashlib
import json
import msvcrt
import os
import subprocess
import time
from ctypes import wintypes as W
from pathlib import Path
from typing import Any

K = C.WinDLL("kernel32", use_last_error=True)
P = C.WinDLL("psapi", use_last_error=True)
H = W.HANDLE


class SI(C.Structure):
    _fields_ = (
        [("cb", W.DWORD), ("lpReserved", W.LPWSTR), ("lpDesktop", W.LPWSTR), ("lpTitle", W.LPWSTR)]
        + [
            (n, W.DWORD)
            for n in [
                "dwX",
                "dwY",
                "dwXSize",
                "dwYSize",
                "dwXCountChars",
                "dwYCountChars",
                "dwFillAttribute",
                "dwFlags",
            ]
        ]
        + [
            ("wShowWindow", W.WORD),
            ("cbReserved2", W.WORD),
            ("lpReserved2", C.POINTER(W.BYTE)),
            ("hStdInput", H),
            ("hStdOutput", H),
            ("hStdError", H),
        ]
    )


class PI(C.Structure):
    _fields_ = [("hProcess", H), ("hThread", H), ("dwProcessId", W.DWORD), ("dwThreadId", W.DWORD)]


class IDS(C.Structure):
    _fields_ = [("assigned", W.DWORD), ("count", W.DWORD), ("ids", C.c_size_t * 1024)]


class PE(C.Structure):
    _fields_ = [
        ("dwSize", W.DWORD),
        ("cntUsage", W.DWORD),
        ("th32ProcessID", W.DWORD),
        ("th32DefaultHeapID", C.c_size_t),
        ("th32ModuleID", W.DWORD),
        ("cntThreads", W.DWORD),
        ("th32ParentProcessID", W.DWORD),
        ("pcPriClassBase", W.LONG),
        ("dwFlags", W.DWORD),
        ("szExeFile", W.WCHAR * 260),
    ]


class MEM(C.Structure):
    _fields_ = [("cb", W.DWORD), ("faults", W.DWORD)] + [
        (n, C.c_size_t) for n in ["peak", "working", "qpp", "qp", "qpnp", "qnp", "page", "peakpage"]
    ]


def api(name: str, args: list[Any], result: Any = W.BOOL) -> Any:
    f = getattr(K, name)
    f.argtypes = args
    f.restype = result
    return f


createjob = api("CreateJobObjectW", [C.c_void_p, W.LPCWSTR], H)
assign = api("AssignProcessToJobObject", [H, H])
queryjob = api("QueryInformationJobObject", [H, C.c_int, C.c_void_p, W.DWORD, C.c_void_p])
injob = api("IsProcessInJob", [H, H, C.POINTER(W.BOOL)])
terminatejob = api("TerminateJobObject", [H, W.UINT])
close = api("CloseHandle", [H])
resume = api("ResumeThread", [H], W.DWORD)
create = api(
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
        C.POINTER(SI),
        C.POINTER(PI),
    ],
)
times = api("GetProcessTimes", [H] + [C.POINTER(W.FILETIME)] * 4)
image = api("QueryFullProcessImageNameW", [H, W.DWORD, W.LPWSTR, C.POINTER(W.DWORD)])
exitcode = api("GetExitCodeProcess", [H, C.POINTER(W.DWORD)])
openprocess = api("OpenProcess", [W.DWORD, W.BOOL, W.DWORD], H)
snapshot = api("CreateToolhelp32Snapshot", [W.DWORD, W.DWORD], H)
first = api("Process32FirstW", [H, C.POINTER(PE)])
nextproc = api("Process32NextW", [H, C.POINTER(PE)])
P.GetProcessMemoryInfo.argtypes = [H, C.POINTER(MEM), W.DWORD]
P.GetProcessMemoryInfo.restype = W.BOOL


def check(ok: Any) -> None:
    if not ok:
        raise C.WinError(C.get_last_error())


def info(handle: Any) -> dict[str, Any]:
    ts = [W.FILETIME() for _ in range(4)]
    check(times(handle, *(C.byref(t) for t in ts)))
    vals = [(t.dwHighDateTime << 32) | t.dwLowDateTime for t in ts]
    code = W.DWORD()
    check(exitcode(handle, C.byref(code)))
    return {
        "creation": vals[0],
        "exit_time": vals[1],
        "cpu_seconds": (vals[2] + vals[3]) / 1e7,
        "exit_code": code.value,
    }


def members(job: Any) -> list[int]:
    ids = IDS()
    check(queryjob(job, 3, C.byref(ids), C.sizeof(ids), None))
    assert ids.count <= 1024
    return list(ids.ids[: ids.count])


def parents() -> dict[int, int]:
    snap = snapshot(2, 0)
    check(snap and snap != C.c_void_p(-1).value)
    entry = PE()
    entry.dwSize = C.sizeof(entry)
    out = {}
    ok = first(snap, C.byref(entry))
    while ok:
        out[entry.th32ProcessID] = entry.th32ParentProcessID
        ok = nextproc(snap, C.byref(entry))
    close(snap)
    return out


def utc() -> str:
    return datetime.datetime.now(datetime.UTC).isoformat()


class BasicLimits(C.Structure):
    _fields_ = [
        ("process_time", C.c_int64),
        ("job_time", C.c_int64),
        ("flags", W.DWORD),
        ("min_working", C.c_size_t),
        ("max_working", C.c_size_t),
        ("active_limit", W.DWORD),
        ("affinity", C.c_size_t),
        ("priority", W.DWORD),
        ("scheduling", W.DWORD),
    ]


class ExtendedLimits(C.Structure):
    _fields_ = [
        ("basic", BasicLimits),
        ("io", C.c_uint64 * 6),
        ("process_memory", C.c_size_t),
        ("job_memory", C.c_size_t),
        ("peak_process", C.c_size_t),
        ("peak_job", C.c_size_t),
    ]


setjob = api("SetInformationJobObject", [H, C.c_int, C.c_void_p, W.DWORD])
terminateprocess = api("TerminateProcess", [H, W.UINT])
wait = api("WaitForSingleObject", [H, W.DWORD], W.DWORD)
CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-E/generation-1")


def classify_query(
    exited: bool, error: int | None, path: str | None, identity_matches: bool
) -> str:
    """Exit is lifecycle evidence, never automatic identity verification."""
    if exited:
        return "PROCESS_EXITED"
    if error == 5:
        return "PROCESS_ACCESS_DENIED"
    if error is not None:
        return "PROCESS_IMAGE_QUERY_FAILED"
    if not path or not identity_matches:
        return "PROCESS_IDENTITY_UNVERIFIED"
    return "PROCESS_IDENTITY_VERIFIED"


def validate_lineage(child: dict[str, Any], parent: dict[str, Any] | None) -> bool:
    return bool(
        parent
        and child["parent_pid"] == parent["pid"]
        and parent["creation"] <= child["creation"]
        and (not parent["exit_time"] or child["creation"] <= parent["exit_time"])
    )


def allowed_images(executable: Path, base_executable: Path) -> dict[str, str]:
    result = {}
    for path in (
        executable,
        base_executable,
        Path(os.environ["SystemRoot"]) / "System32/conhost.exe",
    ):
        result[str(path.resolve()).casefold()] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result


def inspect_image(handle: Any, pins: dict[str, str], fault: str | None = None) -> dict[str, Any]:
    error: int | None = None
    path: str | None = None
    digest: str | None = None
    exited = wait(handle, 0) == 0
    if not exited:
        size = W.DWORD(32768)
        buffer = C.create_unicode_buffer(size.value)
        if fault in ("image31", "access_denied", "invalid_handle"):
            error = {"image31": 31, "access_denied": 5, "invalid_handle": 6}[fault]
        elif fault != "missing_image":
            if image(handle, 0, buffer, C.byref(size)):
                path = str(Path(buffer.value).resolve())
                digest = hashlib.sha256(Path(path).read_bytes()).hexdigest()
            else:
                error = C.get_last_error()
        exited = wait(handle, 0) == 0
    matched = bool(path and pins.get(path.casefold()) == digest)
    if fault == "executable_mismatch":
        matched = False
    state = classify_query(exited, error, path, matched)
    return {
        "state": state,
        "winerror": error,
        "image_path": path,
        "image_sha256": digest,
        "identity_verified": matched and error is None,
        "exited": exited,
        "fault_injected": fault,
    }


def run(
    label: str,
    command: list[str],
    cwd: Path,
    budget: float,
    pins: dict[str, str],
    *,
    purpose: str,
    fault: str | None = None,
) -> dict[str, Any]:
    """Only disposable diagnostic workloads. No authorization of guest or trading actions."""
    if purpose != "TEST_ONLY" or not 0 < budget <= 300:
        raise ValueError("NOT_AUTHORIZED")
    if not label.replace("-", "").isalnum():
        raise ValueError("LABEL")
    if str(Path(command[0]).resolve()).casefold() not in pins:
        raise ValueError("UNPINNED_EXECUTABLE")
    for p in (CUSTODY, *CUSTODY.parents):
        if p.lstat().st_file_attributes & 0x400:
            raise ValueError("REPARSE_CUSTODY")
    destination = CUSTODY / (label + ".command.json")
    if destination.exists():
        raise ValueError("RECEIPT_EXISTS")
    env = {
        k: os.environ[k]
        for k in ["SystemRoot", "WINDIR", "COMSPEC", "PATH", "PATHEXT"]
        if k in os.environ
    }
    env.update(
        PYTHONDONTWRITEBYTECODE="1", PYTHONNOUSERSITE="1", TEMP=str(CUSTODY), TMP=str(CUSTODY)
    )
    block = C.create_unicode_buffer("\0".join(k + "=" + v for k, v in sorted(env.items())) + "\0\0")
    record: dict[str, Any] = {
        "version": "Phase1444EEProcessSupervisor.v1",
        "purpose": purpose,
        "command": command,
        "cwd": str(cwd),
        "budget_seconds": budget,
        "start_utc": utc(),
        "launcher_pid": os.getpid(),
        "sampling_seconds": 0.05,
        "fault": fault,
        "errors": [],
        "coverage_gaps": [],
        "transitions": [],
        "timed_out": False,
        "assignment_before_resume": False,
        "exit_code": None,
        "status": "PARTIAL",
        "processes": [],
        "samples": [],
        "environment_names": sorted(env),
        "limitations": [
            "Polling may miss short-lived children; no perfect child coverage.",
            "Job supervision does not establish guest/network/credential containment.",
        ],
    }
    owned: dict[int, dict[str, Any]] = {}
    handles: dict[int, Any] = {}
    job: Any = None
    pi = PI()
    assigned = False
    started = time.monotonic()
    try:
        current = api("GetCurrentProcess", [], H)()
        record["launcher_creation"] = info(current)["creation"]
        job = createjob(None, None)
        check(job)
        limits = ExtendedLimits()
        limits.basic.flags = 0x2000  # KILL_ON_JOB_CLOSE; breakaway is not enabled.
        check(setjob(job, 9, C.byref(limits), C.sizeof(limits)))
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
            stdhandles = [msvcrt.get_osfhandle(f.fileno()) for f in (inp, out, err)]
            for handle in stdhandles:
                os.set_handle_inheritable(handle, True)
            try:
                si = SI()
                si.cb = C.sizeof(si)
                si.dwFlags = 0x100
                si.hStdInput, si.hStdOutput, si.hStdError = stdhandles
                check(
                    create(
                        command[0],
                        C.create_unicode_buffer(subprocess.list2cmdline(command)),
                        None,
                        None,
                        True,
                        0x4 | 0x400 | 0x08000000,
                        block,
                        str(cwd),
                        C.byref(si),
                        C.byref(pi),
                    )
                )
            finally:
                for handle in stdhandles:
                    os.set_handle_inheritable(handle, False)
            root = info(pi.hProcess)
            record.update(root_pid=pi.dwProcessId, root_creation=root["creation"])
            root_image = inspect_image(pi.hProcess, pins)
            if not root_image["identity_verified"]:
                raise ValueError("ROOT_IMAGE_IDENTITY_UNVERIFIED")
            record["pinned_root"] = root_image
            record["transitions"].append("CREATED_SUSPENDED_PINNED")
            if fault == "assignment_failure":
                raise ValueError("INJECTED_ASSIGNMENT_FAILURE")
            check(assign(job, pi.hProcess))
            assigned = True
            record["assignment_before_resume"] = True
            record["transitions"].append("ASSIGNED")
            check(resume(pi.hThread) != 0xFFFFFFFF)
            close(pi.hThread)
            pi.hThread = None
            record["transitions"].append("RUNNING")
            if fault == "job_closed":
                close(job)
                job = None
                raise ValueError("INJECTED_JOB_CLOSED_KILL_ON_CLOSE")
            while True:
                ids = members(job)
                ancestry = parents()
                for pid in ids:
                    if pid not in owned:
                        handle = openprocess(0x100410, False, pid)
                        if not handle:
                            code = C.get_last_error()
                            record["coverage_gaps"].append(
                                {
                                    "pid": pid,
                                    "winerror": code,
                                    "state": "PROCESS_IDENTITY_UNVERIFIED",
                                }
                            )
                            if pid in members(job):
                                raise ValueError("ACTIVE_CHILD_INACCESSIBLE")
                            continue
                        handles[pid] = handle
                        member = W.BOOL()
                        check(injob(handle, job, C.byref(member)))
                        check(member.value)
                        entry = info(handle)
                        entry.update(
                            pid=pid,
                            parent_pid=ancestry.get(pid),
                            job_member=True,
                            peak_observed_working_set=0,
                        )
                        owned[pid] = entry
                        observed_image = inspect_image(handle, pins, fault)
                        entry.update(observed_image)
                        if not observed_image["identity_verified"]:
                            if observed_image["state"] == "PROCESS_EXITED":
                                record["coverage_gaps"].append({"pid": pid, **observed_image})
                                continue
                            record["errors"].append(
                                {"pid": pid, "creation": entry["creation"], **observed_image}
                            )
                            raise ValueError("ACTIVE_PROCESS_IDENTITY_UNVERIFIED")
                    entry = owned[pid]
                    handle = handles[pid]
                    now = info(handle)
                    expected_creation = entry["creation"] + (
                        1 if fault == "creation_mismatch" else 0
                    )
                    if now["creation"] != expected_creation:
                        raise ValueError("CREATION_TIME_MISMATCH")
                    memory = MEM()
                    memory.cb = C.sizeof(memory)
                    if P.GetProcessMemoryInfo(handle, C.byref(memory), memory.cb):
                        entry["peak_observed_working_set"] = max(
                            entry["peak_observed_working_set"], memory.working
                        )
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
                    check(terminatejob(job, 124))
                    record["timed_out"] = True
                    record["transitions"].append("OWNED_JOB_TIMEOUT")
                    break
                time.sleep(0.05)
            record["status"] = "COMPLETE"
    except Exception as exc:
        record["errors"].append(
            {
                "type": type(exc).__name__,
                "winerror": getattr(exc, "winerror", None),
                "reason": str(exc) if isinstance(exc, ValueError) else "NATIVE_API_FAILURE",
            }
        )
        record["status"] = "SUPERVISION_FAILED"
    finally:
        try:
            if job and assigned:
                # The retained job was created here before the suspended root was assigned.
                # Cleanup never resolves a PID into termination authority.
                if members(job):
                    check(terminatejob(job, 124 if record["timed_out"] else 125))
                deadline = time.monotonic() + 10
                while members(job) and time.monotonic() < deadline:
                    time.sleep(0.05)
                record["job_empty_after_cleanup"] = not members(job)
            elif pi.hProcess and not assigned:
                check(terminateprocess(pi.hProcess, 125))
            if pi.hProcess:
                wait(pi.hProcess, 10000)
                record["exit_code"] = info(pi.hProcess)["exit_code"]
        except Exception as exc:
            record["status"] = "SUPERVISION_FAILED"
            record["errors"].append(
                {
                    "phase": "cleanup",
                    "type": type(exc).__name__,
                    "winerror": getattr(exc, "winerror", None),
                }
            )
        for pid, entry in owned.items():
            try:
                entry.update(info(handles[pid]))
                entry["parent_identity_verified"] = pid == record.get(
                    "root_pid"
                ) or validate_lineage(entry, owned.get(entry["parent_pid"]))
            except OSError as exc:
                entry["final_observation_error"] = getattr(exc, "winerror", None)
            close(handles[pid])
        if pi.hThread:
            close(pi.hThread)
        if pi.hProcess:
            close(pi.hProcess)
        if job:
            close(job)
        record["processes"] = list(owned.values())
        record["tree_identity_verified"] = bool(owned) and all(
            p.get("identity_verified") and p.get("parent_identity_verified") for p in owned.values()
        )
        record["end_utc"] = utc()
        record["duration_seconds"] = time.monotonic() - started
        record["transitions"].append("CLEANED")
        destination.write_text(
            json.dumps(record, sort_keys=True, indent=2) + "\n", encoding="utf-8"
        )
    return record
