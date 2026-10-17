"""Original-archive provenance is distinct from installed RECORD consistency."""

from pathlib import Path
from typing import Any

from strategy_engine.phase1416.imports import sha


def classify(
    record: dict[str, Any], locked_version: str | None,
    trusted_archive_hashes: list[str], archives: tuple[Path, ...],
) -> dict[str, Any]:
    version_match = record["version"] == locked_version
    original = [dict(path=str(p), sha256=sha(p.read_bytes())) for p in sorted(archives)]
    matches = [a for a in original if "sha256:" + a["sha256"] in trusted_archive_hashes]
    record_ok = record["status"] == "VERIFIED_INSTALLED_RECORD"
    if version_match and record_ok and matches:
        status = "ARCHIVE_HASH_VERIFIED"
    elif version_match and record_ok:
        status = "INSTALLED_RECORD_VERIFIED"
    elif version_match:
        status = "LOCK_VERSION_ONLY"
    else:
        status = "UNVERIFIED"
    return dict(
        name=record["name"], version=record["version"], installed_record=record["status"],
        installed_files_fingerprint=record["files_fingerprint"], lock_version_match=version_match,
        lock_archive_hashes=trusted_archive_hashes, local_archives=original,
        archive_matches=matches, classification=status,
        missing_evidence=None if matches else "ORIGINAL_ARCHIVE_MATCHING_TRUSTED_LOCK_DIGEST",
    )
