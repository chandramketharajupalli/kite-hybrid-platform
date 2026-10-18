"""Pure policy evaluation. Real authorization is unconditionally unavailable."""

import datetime as dt
import hashlib
import json
from collections.abc import Mapping
from dataclasses import dataclass
from typing import Literal

CONTRACT = "Phase1444EHConsoleHostAuthorization.v1"
BASELINE = "913a1014a0ffd5fa14fc9d228ffb3070b1d33d0f"
SUPERVISOR = "a70a068cf71b8da035d5d15c764ef8721b73ce265fd3eaa4b05ffa973de76597"
IMAGE = "C:/Windows/System32/conhost.exe"
IMAGE_SHA = "c86146188b78e484f32cc29e2331689659943b47689a85570342044f499938ac"
SIGNER = "CN=Microsoft Windows, O=Microsoft Corporation, L=Redmond, S=Washington, C=US"
FIELDS = frozenset(
    {
        "contract",
        "status",
        "git_baseline",
        "supervisor_fingerprint",
        "executable_path",
        "executable_sha256",
        "catalog_signer",
        "catalog_evidence_reference",
        "catalog_evidence_sha256",
        "required_role",
        "command_identity",
        "creation_policy",
        "parent_policy",
        "job_policy",
        "console_policy",
        "owner_identity",
        "authorization_identity",
        "approval_evidence",
        "approval_timestamp",
        "expiry",
        "revocation_status",
        "replay_identifier",
        "payload_fingerprint",
    }
)


def fingerprint(raw: Mapping[str, object]) -> str:
    body = {key: value for key, value in raw.items() if key != "payload_fingerprint"}
    return hashlib.sha256(
        json.dumps(
            body, sort_keys=True, separators=(",", ":"), ensure_ascii=True, allow_nan=False
        ).encode()
    ).hexdigest()


def validate_envelope(raw: Mapping[str, object]) -> dict[str, str | None]:
    if set(raw) != FIELDS or any(
        value is not None and not isinstance(value, str) for value in raw.values()
    ):
        raise ValueError("SCHEMA")
    if raw["contract"] != CONTRACT or raw["status"] not in ("NOT_AUTHORIZED", "TEST_ONLY"):
        raise ValueError("SCHEMA")
    if raw["payload_fingerprint"] != fingerprint(raw):
        raise ValueError("PAYLOAD_FINGERPRINT")
    return {key: value if isinstance(value, str) else None for key, value in raw.items()}


@dataclass(frozen=True)
class Verdict:
    test_accepted: bool
    reason: str

    @property
    def execution_authorized(self) -> Literal[False]:
        return False


def evaluate_authorization(raw: Mapping[str, object]) -> Verdict:
    """No trust root exists. Structurally valid input cannot change this boundary."""
    try:
        validate_envelope(raw)
    except ValueError as error:
        return Verdict(False, str(error))
    return Verdict(False, "OWNER_AUTHENTICATION_NOT_ESTABLISHED")


@dataclass(frozen=True)
class SyntheticContext:
    now: dt.datetime
    command_identity: str
    catalog_reference: str
    catalog_fingerprint: str
    process_pid: int
    process_creation: int
    parent_pid: int
    parent_creation: int
    job_identity: str
    console_association: str
    consumed_decisions: frozenset[str] = frozenset()
    consumed_nonces: frozenset[str] = frozenset()
    conflicting_decisions: frozenset[str] = frozenset()


def image_state(exited: bool, error: int | None, known: bool) -> str:
    if exited:
        return "PROCESS_EXITED"
    if error == 5:
        return "PROCESS_ACCESS_DENIED"
    if error is not None:
        return "PROCESS_IMAGE_QUERY_FAILED"
    return "PROCESS_IDENTITY_VERIFIED" if known else "PROCESS_IDENTITY_UNVERIFIED"


def utc_timestamp(value: str | None) -> dt.datetime:
    if value is None:
        raise ValueError("TIME")
    result = dt.datetime.fromisoformat(value)
    if result.utcoffset() != dt.timedelta(0):
        raise ValueError("TIME")
    return result


def evaluate_synthetic(
    raw: Mapping[str, object], observation: Mapping[str, object], context: SyntheticContext
) -> Verdict:
    """Fixture-only predicates. No process API, consumption, side effect or real acceptance."""

    def reject(reason: str) -> Verdict:
        return Verdict(False, reason)

    try:
        envelope = validate_envelope(raw)
    except ValueError as error:
        return reject(str(error))
    if envelope["status"] != "TEST_ONLY" or envelope["approval_evidence"] != "TEST_ONLY":
        return reject("MISSING_SYNTHETIC_AUTHORIZATION")
    if envelope["owner_identity"] != "TEST_ONLY_OWNER" or observation.get("fixture") != "TEST_ONLY":
        return reject("SYNTHETIC_CONTEXT_REQUIRED")
    bindings = {
        "git_baseline": BASELINE,
        "supervisor_fingerprint": SUPERVISOR,
        "executable_path": IMAGE,
        "executable_sha256": IMAGE_SHA,
        "catalog_signer": SIGNER,
        "catalog_evidence_reference": context.catalog_reference,
        "catalog_evidence_sha256": context.catalog_fingerprint,
        "required_role": "CONSOLE_HOST_FOR_SYNTHETIC_EXECUTION",
        "command_identity": context.command_identity,
        "creation_policy": "HANDLE_PID_CREATION_MATCH",
        "parent_policy": "ROOT_PID_CREATION_LIFETIME",
        "job_policy": "RETAINED_OWNED_JOB",
        "console_policy": "EXPLICIT_ASSOCIATION_REQUIRED",
    }
    for key, expected in bindings.items():
        if not expected or envelope[key] != expected:
            return reject("BINDING_" + key)
    if (
        observation.get("executable_path") != IMAGE
        or observation.get("executable_sha256") != IMAGE_SHA
    ):
        return reject("OBSERVED_IMAGE_BINDING")
    if observation.get("catalog_verified") is not True:
        return reject("CATALOG_UNVERIFIED")
    if envelope["revocation_status"] != "TEST_ONLY_CURRENT":
        return reject("REVOCATION_UNVERIFIED_OR_REVOKED")
    try:
        start = utc_timestamp(envelope["approval_timestamp"])
        expiry = utc_timestamp(envelope["expiry"])
        revocation = observation.get("revocation_checked_at")
        checked = utc_timestamp(revocation if isinstance(revocation, str) else None)
        if context.now.utcoffset() != dt.timedelta(0):
            return reject("TIME")
        if not start <= context.now < expiry or expiry - start > dt.timedelta(hours=168):
            return reject("TIME")
        if not dt.timedelta(0) <= context.now - checked <= dt.timedelta(hours=1):
            return reject("STALE_REVOCATION")
    except (ValueError, TypeError):
        return reject("TIME")
    identity, nonce = envelope["authorization_identity"], envelope["replay_identifier"]
    if not identity or not nonce:
        return reject("MISSING_DECISION_IDENTITY")
    if identity in context.consumed_decisions or nonce in context.consumed_nonces:
        return reject("REPLAYED")
    if identity in context.conflicting_decisions:
        return reject("CONFLICTING")
    for key, expected_number in {
        "pid": context.process_pid,
        "handle_pid": context.process_pid,
        "creation": context.process_creation,
        "handle_creation": context.process_creation,
        "parent_pid": context.parent_pid,
        "parent_creation": context.parent_creation,
    }.items():
        value = observation.get(key)
        if type(value) is not int or expected_number <= 0 or value != expected_number:
            return reject("PROCESS_BINDING_" + key)
    if context.process_creation < context.parent_creation:
        return reject("PARENT_LIFETIME")
    parent_exit = observation.get("parent_exit")
    if type(parent_exit) is not int or (
        parent_exit != 0 and parent_exit < context.process_creation
    ):
        return reject("PARENT_LIFETIME")
    if (
        not context.console_association
        or observation.get("console_association") != context.console_association
    ):
        return reject("CONSOLE_ASSOCIATION")
    if not context.job_identity or observation.get("job_identity") != context.job_identity:
        return reject("JOB_IDENTITY")
    if observation.get("job_member") is not True or observation.get("job_owned") is not True:
        return reject("JOB_OWNERSHIP")
    image_error = observation.get("image_error")
    if image_error is not None and type(image_error) is not int:
        return reject("INVALID_IMAGE_ERROR")
    if type(observation.get("exited")) is not bool:
        return reject("UNKNOWN_LIFECYCLE")
    state = image_state(
        observation["exited"] is True, image_error, observation.get("image_verified") is True
    )
    if state != "PROCESS_IDENTITY_VERIFIED":
        return reject(state)
    return Verdict(True, "TEST_ONLY_BINDING_ACCEPTED_NO_EXECUTION_AUTHORITY")
