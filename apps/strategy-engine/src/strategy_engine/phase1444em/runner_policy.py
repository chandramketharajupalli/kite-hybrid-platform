"""Pure fixture policy; no runner or real execution authority exists here."""

from collections.abc import Mapping
from typing import Literal

IDENTITY_FIELDS = (
    "baseline", "executable_path", "executable_hash", "command", "working_directory",
    "job", "pid", "creation_time", "parent_pid", "parent_creation_time",
    "evidence_destination", "cleanup_authority",
)
REQUIRED_FLAGS = (
    "assigned_before_resume", "retained_handle", "child_owned", "filesystem_enforced",
    "network_enforced", "credential_isolated", "owner_authenticated",
)
FORBIDDEN_FLAGS = (
    "filesystem_write", "sealed_access", "repository_write", "credential_inheritance",
    "network_egress", "dns_egress", "proxy_inheritance", "revoked", "replayed",
)
BUDGET_FIELDS = ("processes", "children", "wall_seconds", "cpu_seconds", "memory_bytes",
                 "output_bytes", "write_bytes", "retries")


def real_execution_authorized() -> Literal[False]:
    """Fixture acceptance cannot issue a lease or activate any supervisor."""
    return False


def classify_query(exited: bool, error: int | None) -> str:
    if exited:
        return "PROCESS_EXITED"
    if error == 5:
        return "PROCESS_ACCESS_DENIED"
    if error is not None:
        return "PROCESS_IMAGE_QUERY_FAILED"
    return "PROCESS_IDENTITY_UNVERIFIED"


def evaluate_fixture(
    observed: Mapping[str, object],
    expected: Mapping[str, object],
    limits: Mapping[str, int],
    now: int,
) -> tuple[str, ...]:
    """Check supplied synthetic facts only; do not verify external facts or authorize execution."""
    errors: list[str] = []
    if observed.get("domain") != "SYNTHETIC_ONLY":
        errors.append("SYNTHETIC_DOMAIN_REQUIRED")
    if observed.get("operation") != "PURE_POLICY_MODEL":
        errors.append("OPERATION_REJECTED")
    for field in IDENTITY_FIELDS:
        value = observed.get(field)
        if value is None or value == "" or value != expected.get(field):
            errors.append("IDENTITY_" + field)
    for field in REQUIRED_FLAGS:
        if observed.get(field) is not True:
            errors.append("MISSING_" + field)
    for field in FORBIDDEN_FLAGS:
        if observed.get(field) is not False:
            errors.append("FORBIDDEN_" + field)
    start, end = observed.get("not_before"), observed.get("expires")
    if (
        type(now) is not int or type(start) is not int or type(end) is not int
        or not isinstance(start, int) or not isinstance(end, int)
        or not start <= now < end
    ):
        errors.append("TIME_REJECTED")
    for field in BUDGET_FIELDS:
        maximum, usage = limits.get(field), observed.get(field)
        minimum = 0 if field in ("children", "retries") else 1
        if (
            type(maximum) is not int or type(usage) is not int
            or not isinstance(maximum, int) or not isinstance(usage, int)
            or maximum < minimum or usage < 0 or usage > maximum
        ):
            errors.append("BUDGET_" + field)
    return tuple(errors)
