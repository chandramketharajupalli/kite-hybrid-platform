"""Inactive integration predicates; no native supervisor import or execution capability."""

import datetime as dt
import hashlib
import json
from collections.abc import Mapping
from dataclasses import dataclass
from typing import Literal

BASELINE = "ab7984794edb2f6be0cb6db90cbbd71a9486aaf0"
CONTRACT = "Phase1444EIPolicySupervisorIntegration.v1"
POLICY_SHA = "c417ba7802c2d08362c0e3d83aad57d109c4a07dbc99e030d9748adf4adbc043"
SUPERVISOR_SHA = "a70a068cf71b8da035d5d15c764ef8721b73ce265fd3eaa4b05ffa973de76597"
IMAGE = "C:/Windows/System32/conhost.exe"
IMAGE_SHA = "c86146188b78e484f32cc29e2331689659943b47689a85570342044f499938ac"
TEST_IDS = (
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_lifecycle[nonzero-7]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_lifecycle[early-0]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_lifecycle[child-0]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_lifecycle[descendant-0]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_lifecycle[parent-crash-8]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_lifecycle[child-crash-0]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[access-denied]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[image31]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[missing-image]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[wrong-image]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[invalid-handle]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[query-rights]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[assignment-failure]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[membership-failure]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[pid-reuse]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[handle-mismatch]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_fault[creation-mismatch]",
    "apps/strategy-engine/tests/test_phase1444ef.py::test_live_timeout_sentinel_and_exited_query",
)
LIMITS = {
    "processes": 4,
    "children": 3,
    "wall_seconds": 5,
    "cpu_seconds": 10,
    "memory_bytes": 268435456,
    "output_bytes": 8388608,
    "write_bytes": 33554432,
    "retries": 0,
}
BINDINGS = {
    "contract": CONTRACT,
    "baseline": BASELINE,
    "policy_sha256": POLICY_SHA,
    "supervisor_sha256": SUPERVISOR_SHA,
    "image": IMAGE,
    "image_sha256": IMAGE_SHA,
    "role": "CONSOLE_HOST_FOR_SYNTHETIC_EXECUTION",
    "operation": "PREPARATION_ONLY",
    "relationship_policy": "HANDLE_PID_CREATION_PARENT_CONSOLE",
    "job_policy": "OWNED_JOB_ASSIGN_BEFORE_RESUME",
}
FIELDS = frozenset(BINDINGS) | {
    "status",
    "command_identity",
    "envelope_fingerprint",
    "trust_root",
    "revocation_evidence",
    "replay_reference",
    "approval_timestamp",
    "expiry",
    "limits",
    "cleanup_authority",
    "owner",
    "authentication",
    "test_ids",
    "fingerprint",
}


def fingerprint(value: Mapping[str, object]) -> str:
    return hashlib.sha256(
        json.dumps(
            {k: v for k, v in value.items() if k != "fingerprint"},
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=True,
            allow_nan=False,
        ).encode()
    ).hexdigest()


@dataclass(frozen=True)
class Verdict:
    fixture_accepted: bool
    reason: str

    @property
    def execution_authorized(self) -> Literal[False]:
        return False


def real_authorization(packet: Mapping[str, object]) -> Verdict:
    """No trust mechanism exists; this function never delegates to a supervisor."""
    return Verdict(False, "OWNER_TRUST_NOT_ESTABLISHED")


@dataclass(frozen=True)
class Context:
    now: dt.datetime
    command_identity: str
    envelope_fingerprint: str
    pid: int
    creation: int
    parent_pid: int
    parent_creation: int
    job: str
    console: str
    consumed: frozenset[str] = frozenset()
    conflicts: frozenset[str] = frozenset()


def evaluate_fixture(
    packet: Mapping[str, object], observation: Mapping[str, object], context: Context
) -> Verdict:
    """Pure TEST_ONLY model, not authentication, durable replay or OS enforcement."""

    def reject(reason: str) -> Verdict:
        return Verdict(False, reason)

    if set(packet) != FIELDS:
        return reject("SCHEMA")
    try:
        if packet["fingerprint"] != fingerprint(packet):
            return reject("FINGERPRINT")
    except (TypeError, ValueError):
        return reject("SERIALIZATION")
    if packet["status"] != "TEST_ONLY" or observation.get("fixture") != "TEST_ONLY":
        return reject("TEST_ONLY_REQUIRED")
    for key, value in BINDINGS.items():
        if packet[key] != value:
            return reject("BINDING_" + key)
    for key in (
        "owner",
        "authentication",
        "trust_root",
        "revocation_evidence",
        "cleanup_authority",
    ):
        if packet[key] != "TEST_ONLY":
            return reject("AUTHORITY_" + key)
    if not context.command_identity or packet["command_identity"] != context.command_identity:
        return reject("COMMAND")
    if (
        not context.envelope_fingerprint
        or packet["envelope_fingerprint"] != context.envelope_fingerprint
    ):
        return reject("ENVELOPE")
    if packet["test_ids"] != list(TEST_IDS):
        return reject("TEST_INVENTORY")
    nonce = packet["replay_reference"]
    if not isinstance(nonce, str) or not nonce:
        return reject("REPLAY_REFERENCE")
    if nonce in context.consumed:
        return reject("REPLAYED")
    if nonce in context.conflicts:
        return reject("CONFLICT")
    try:
        start, end = packet["approval_timestamp"], packet["expiry"]
        if not isinstance(start, str) or not isinstance(end, str):
            return reject("TIME")
        a, b = dt.datetime.fromisoformat(start), dt.datetime.fromisoformat(end)
        if any(x.utcoffset() != dt.timedelta(0) for x in (a, b, context.now)):
            return reject("TIME")
        if not a <= context.now < b or b - a > dt.timedelta(hours=168):
            return reject("TIME")
    except ValueError:
        return reject("TIME")
    limits = packet["limits"]
    if not isinstance(limits, dict) or set(limits) != set(LIMITS):
        return reject("LIMITS")
    for key, maximum in LIMITS.items():
        limit, actual = limits[key], observation.get(key)
        if type(limit) is not int or limit != maximum:
            return reject("LIMIT_" + key)
        if type(actual) is not int or not 0 <= actual <= limit:
            return reject("EXCEEDED_" + key)
    for key, expected in {
        "pid": context.pid,
        "handle_pid": context.pid,
        "creation": context.creation,
        "handle_creation": context.creation,
        "parent_pid": context.parent_pid,
        "parent_creation": context.parent_creation,
    }.items():
        observed_number = observation.get(key)
        if type(observed_number) is not int or expected <= 0 or observed_number != expected:
            return reject("IDENTITY_" + key)
    if context.creation < context.parent_creation:
        return reject("PARENT_LIFETIME")
    for key, expected_value in {
        "image": IMAGE,
        "image_sha256": IMAGE_SHA,
        "job": context.job,
        "console": context.console,
    }.items():
        if not expected_value or observation.get(key) != expected_value:
            return reject("OBSERVATION_" + key)
    if any(
        observation.get(key) is not True
        for key in ("job_owned", "job_member", "assigned_before_resume", "image_verified")
    ):
        return reject("OWNERSHIP")
    return Verdict(True, "TEST_ONLY_ACCEPTED_NO_EXECUTION")
