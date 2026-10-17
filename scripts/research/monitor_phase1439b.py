"""Windows read-only process-tree observations; execution is explicitly invoked."""

import ctypes
import json
import subprocess
import time
from ctypes import wintypes as w
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


class Entry(ctypes.Structure):
    _fields_ = [("size", w.DWORD), ("usage", w.DWORD), ("pid", w.DWORD),
                ("heap", ctypes.c_size_t), ("module", w.DWORD), ("threads", w.DWORD),
                ("parent", w.DWORD), ("priority", w.LONG), ("flags", w.DWORD),
                ("exe", w.WCHAR * 260)]


class Memory(ctypes.Structure):
    _fields_ = [("cb", w.DWORD), ("faults", w.DWORD),
                *[(n, ctypes.c_size_t) for n in (
                    "peak", "working", "peak_paged", "paged", "peak_nonpaged",
                    "nonpaged", "pagefile", "peak_pagefile")]]


def stamp() -> str:
    return datetime.now(UTC).isoformat()


def ticks(value: w.FILETIME) -> int:
    return (value.dwHighDateTime << 32) + value.dwLowDateTime


class Monitor:
    def __init__(self, root_pid: int, log: Path) -> None:
        self.root_pid = root_pid
        self.log = log
        self.handles: dict[int, Any] = {}
        self.records: dict[int, dict[str, Any]] = {}
        self.errors: list[dict[str, Any]] = []
        self.k = ctypes.WinDLL("kernel32", use_last_error=True)
        self.ps = ctypes.WinDLL("psapi", use_last_error=True)
        self.k.CreateToolhelp32Snapshot.argtypes = [w.DWORD, w.DWORD]
        self.k.CreateToolhelp32Snapshot.restype = w.HANDLE
        for name in ("Process32FirstW", "Process32NextW"):
            fn = getattr(self.k, name)
            fn.argtypes = [w.HANDLE, ctypes.POINTER(Entry)]
            fn.restype = w.BOOL
        self.k.OpenProcess.argtypes = [w.DWORD, w.BOOL, w.DWORD]
        self.k.OpenProcess.restype = w.HANDLE
        self.k.CloseHandle.argtypes = [w.HANDLE]
        self.k.GetProcessTimes.argtypes = [w.HANDLE, *([ctypes.POINTER(w.FILETIME)] * 4)]
        self.k.GetProcessTimes.restype = w.BOOL
        self.k.QueryFullProcessImageNameW.argtypes = [w.HANDLE, w.DWORD, w.LPWSTR,
                                                    ctypes.POINTER(w.DWORD)]
        self.k.GetExitCodeProcess.argtypes = [w.HANDLE, ctypes.POINTER(w.DWORD)]
        self.ps.GetProcessMemoryInfo.argtypes = [w.HANDLE, ctypes.POINTER(Memory), w.DWORD]
        self.ps.GetProcessMemoryInfo.restype = w.BOOL
        self.k.TerminateProcess.argtypes = [w.HANDLE, w.UINT]

    def discover(self) -> None:
        handle = self.k.CreateToolhelp32Snapshot(2, 0)
        if handle == ctypes.c_void_p(-1).value:
            raise OSError(ctypes.get_last_error(), "Process snapshot failed")
        entry = Entry()
        entry.size = ctypes.sizeof(entry)
        found: dict[int, tuple[int, str]] = {}
        try:
            ok = self.k.Process32FirstW(handle, ctypes.byref(entry))
            while ok:
                found[entry.pid] = (entry.parent, entry.exe)
                ok = self.k.Process32NextW(handle, ctypes.byref(entry))
        finally:
            self.k.CloseHandle(handle)
        selected = {self.root_pid, *self.handles}
        while True:
            children = {pid for pid, (parent, _) in found.items() if parent in selected}
            if children <= selected:
                break
            selected |= children
        for pid in sorted(selected - self.handles.keys()):
            if pid not in found:
                continue
            handle = self.k.OpenProcess(0x1000 | 0x400 | 0x10 | 1, False, pid)
            if not handle:
                self.errors.append({"pid": pid, "operation": "OpenProcess",
                                        "error": ctypes.get_last_error(), "at": stamp()})
                continue
            self.handles[pid] = handle
            self.records[pid] = {"pid": pid, "parent_pid": found[pid][0], "exe": found[pid][1],
                                     "first_observed": stamp(), "samples": 0}

    def sample(self) -> None:
        self.discover()
        for pid, handle in self.handles.items():
            ts = [w.FILETIME() for _ in range(4)]
            if not self.k.GetProcessTimes(handle, *[ctypes.byref(t) for t in ts]):
                self.errors.append({"pid": pid, "operation": "GetProcessTimes",
                                        "error": ctypes.get_last_error()})
                continue
            record = self.records[pid]
            creation = ticks(ts[0])
            if record.get("creation_filetime", creation) != creation:
                raise ValueError("PID_IDENTITY_MISMATCH")
            record["creation_filetime"] = creation
            size = w.DWORD(32768)
            image = ctypes.create_unicode_buffer(size.value)
            if self.k.QueryFullProcessImageNameW(handle, 0, image, ctypes.byref(size)):
                record["image"] = image.value
            memory = Memory()
            memory.cb = ctypes.sizeof(memory)
            memory_ok = self.ps.GetProcessMemoryInfo(handle, ctypes.byref(memory), memory.cb)
            cpu = (ticks(ts[2]) + ticks(ts[3])) / 10_000_000
            at = time.monotonic()
            previous = record.get("last_monotonic")
            utilization = None if previous is None else (
                100 * (cpu - record["cpu_seconds"]) / max(at - previous, 1e-9))
            code = w.DWORD()
            self.k.GetExitCodeProcess(handle, ctypes.byref(code))
            record.update(cpu_seconds=cpu, last_monotonic=at, exit_code=code.value,
                          exit_filetime=ticks(ts[1]), last_observed=stamp(),
                          samples=record["samples"] + 1)
            if memory_ok:
                record["peak_working_set_bytes"] = max(
                    record.get("peak_working_set_bytes", 0), memory.peak)
            event = {"pid": pid, "creation_filetime": creation, "at": stamp(), "cpu_seconds": cpu,
                         "cpu_percent_one_core": utilization, "exit_code": code.value,
                         "working_set_bytes": memory.working if memory_ok else None,
                         "os_peak_working_set_bytes": memory.peak if memory_ok else None}
            with self.log.open("a", encoding="utf-8") as stream:
                stream.write(json.dumps(event, sort_keys=True) + "\n")

    def close(self) -> None:
        for handle in self.handles.values():
            self.k.CloseHandle(handle)


def run(command: list[str], cwd: Path, env: dict[str, str], prefix: Path,
        budget: float, interval: float = 0.1) -> dict[str, Any]:
    """One explicit command, streaming receipts; never retries a failed command."""
    start = stamp()
    clock = time.monotonic()
    timed_out = False
    with prefix.with_suffix(".stdout").open("xb") as out, \
            prefix.with_suffix(".stderr").open("xb") as err:
        process = subprocess.Popen(command, cwd=cwd, env=env, stdout=out, stderr=err)
        monitor = Monitor(process.pid, prefix.with_suffix(".samples.jsonl"))
        try:
            while True:
                monitor.sample()
                active = any(r.get("exit_code") == 259 for r in monitor.records.values())
                if process.poll() is not None and not active:
                    break
                if time.monotonic() - clock >= budget:
                    timed_out = True
                    for handle in monitor.handles.values():
                        monitor.k.TerminateProcess(handle, 124)
                    process.wait(timeout=10)
                    time.sleep(interval)
                    monitor.sample()
                    break
                time.sleep(interval)
            result = {"version": "Phase1439BWorkerResourceEvidence.v1", "command": command,
                          "cwd": str(cwd), "start_utc": start, "end_utc": stamp(),
                          "seconds": time.monotonic() - clock, "budget_seconds": budget,
                          "launcher_pid": process.pid, "exit_code": process.poll(),
                          "timeout": timed_out, "sampling_interval": interval,
                          "processes": list(monitor.records.values()), "errors": monitor.errors,
                          "limitation": "Children living entirely between snapshots can be missed; "
                          "OS peak working set observed before exit may miss final-interval growth; "
                          "CPU percent is relative to one logical core; no OS egress certification"}
        finally:
            monitor.close()
    prefix.with_suffix(".command.json").write_text(
        json.dumps(result, sort_keys=True), encoding="utf-8")
    return result
