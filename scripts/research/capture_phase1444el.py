"""Encoding-safe passive capture preparation; process launch is unavailable."""

import hashlib
import json
from pathlib import Path
from typing import Literal

CUSTODY = Path("D:/Yogendra/offline-validation-evidence/phase-14.44E-L/generation-1")
PYTHON = "D:/Yogendra/kite-hybrid-platform/apps/strategy-engine/.venv/Scripts/python.exe"
MODULES = (
    "apps/strategy-engine/tests/test_phase1444ej.py",
    "scripts/research/test_phase1444ej.py",
)
COLLECTION_ARGUMENTS = (
    "-B", "-m", "pytest", "--import-mode=importlib", "-p", "no:cacheprovider",
    "--collect-only", "-q", *MODULES,
)


def launch_authorized() -> Literal[False]:
    return False


def launch() -> None:
    raise RuntimeError("PROCESS_CONTAINMENT_UNVERIFIED_NO_LAUNCH")


def inspect_source(raw: bytes) -> dict[str, object]:
    text = raw.decode("utf-8", errors="strict")
    compile(text, "<supplied-source>", "exec")
    return {
        "sha256": hashlib.sha256(raw).hexdigest(),
        "utf8": True,
        "syntax": "PASS",
        "ascii_only": raw.isascii(),
        "executed": False,
    }


def command_matches(executable: str, arguments: tuple[str, ...], digest: str, pin: str) -> bool:
    """Identity match is preparation evidence only; it never enables launch."""
    return (
        executable == PYTHON
        and arguments == COLLECTION_ARGUMENTS
        and len(pin) == 64
        and all(character in "0123456789abcdef" for character in pin)
        and digest == pin
    )


def retain_supplied_streams(stdout: bytes, stderr: bytes, exit_code: int) -> None:
    """Retain supplied bytes only, without asserting process identity or execution."""
    if type(exit_code) is not int:
        raise ValueError("EXIT_CODE")
    for path in (CUSTODY, *CUSTODY.parents):
        if not path.exists() or path.lstat().st_file_attributes & 0x400:
            raise ValueError("UNSAFE_CUSTODY")
    names = ("supplied.stdout", "supplied.stderr", "supplied-receipt.json")
    if any((CUSTODY / name).exists() for name in names):
        raise ValueError("RECEIPT_EXISTS")
    with (CUSTODY / names[0]).open("xb") as stream:
        stream.write(stdout)
    with (CUSTODY / names[1]).open("xb") as stream:
        stream.write(stderr)
    record = {
        "provenance": "SUPPLIED_BYTES_NOT_VERIFIED_EXECUTION",
        "exit_code_supplied": exit_code,
        "stdout_sha256": hashlib.sha256(stdout).hexdigest(),
        "stderr_sha256": hashlib.sha256(stderr).hexdigest(),
        "process_identity": "UNVERIFIED",
        "timeout": "NOT_RUN",
        "launch_authorized": False,
    }
    with (CUSTODY / names[2]).open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(record, sort_keys=True, separators=(",", ":")) + "\n")


if __name__ == "__main__":
    launch()
