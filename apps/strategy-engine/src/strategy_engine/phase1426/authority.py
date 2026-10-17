"""Append-only synthetic witness outside the declared local rollback directory."""

import os
from collections.abc import Callable, Iterator
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path
from typing import Any, Literal

from pydantic import Field, model_validator

from strategy_engine.backtest.dataset import Frozen, Hash, Instant
from strategy_engine.phase1416.imports import canonical, external, publish, read_json, sha
from strategy_engine.phase1424.analytics import seal
from strategy_engine.phase1425.reliability import require

ZERO = "0" * 64
LIMIT = 10000
AUTHORITY_POLICY = {
    "version": "Phase1426Authority.v1",
    "scope": "SYNTHETIC_FIXTURE",
    "consume_before_recovery": True,
    "automatic_retry": False,
    "halt_clearance": False,
    "witness_survival": "OUTSIDE_LOCAL_ROLLBACK_SUBTREE",
    "independent_custody": False,
    "universal_rollback_prevention": False,
}


def chain(directory: Path, expected: str | None = None) -> tuple[list[dict[str, Any]], str]:
    directory = external(directory)
    paths = sorted(directory.glob("*"))
    require(len(paths) <= LIMIT, "LEDGER_BUDGET")
    require(
        [p.name for p in paths] == [f"{i:06}.json" for i in range(len(paths))],
        "MISSING_OR_AMBIGUOUS_RECORD",
    )
    rows = []
    previous = ZERO
    for i, path in enumerate(paths):
        raw, row = read_json(path, 1024 * 1024)
        require(
            isinstance(row, dict)
            and set(row) == {"sequence", "previous", "payload", "fingerprint"},
            "CORRUPT_RECORD",
        )
        require(
            row == seal(row) and row["sequence"] == i and row["previous"] == previous,
            "CORRUPT_CHAIN",
        )
        require(raw == canonical(row), "NONCANONICAL_RECORD")
        rows.append(row["payload"])
        previous = sha(raw)
    require(expected is None or expected == previous, "TRUSTED_HEAD_MISMATCH")
    return rows, previous


def append(directory: Path, payload: dict[str, Any]) -> str:
    rows, head = chain(directory)
    require(len(rows) < LIMIT, "LEDGER_BUDGET")
    raw = canonical(seal({"sequence": len(rows), "previous": head, "payload": payload}))
    require(len(raw) <= 1024 * 1024, "RECORD_BUDGET")
    publish(directory / f"{len(rows):06}.json", raw)
    return sha(raw)


@contextmanager
def ownership(directory: Path) -> Iterator[None]:
    directory = external(directory)
    directory.mkdir(parents=True, exist_ok=True)
    lock = directory / "owner.lock"
    try:
        fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError as exc:
        raise ValueError("AMBIGUOUS_OR_COMPETING_OWNER") from exc
    try:
        yield
    finally:
        os.close(fd)
        lock.unlink()


class Witness(Frozen):
    version: Literal["Phase1426Witness.v1"] = "Phase1426Witness.v1"
    run_id: Hash
    session_sequence: int = Field(ge=0, strict=True)
    highest_event_sequence: int = Field(ge=0, strict=True)
    journal_head: Hash
    authority_epoch: int = Field(ge=0, strict=True)
    paper_halt: bool = Field(strict=True)
    last_accepted_recovery_action: str


class Approval(Frozen):
    version: Literal["Phase1426Recovery.v1"]
    recovery_request_id: Hash
    run_id: Hash
    incident_id: Hash
    authority_identity: str
    authorized_action: Literal["RESUME_SYNTHETIC_REPLAY"]
    authorization_scope: Literal["SYNTHETIC_RECOVERY_ONLY"]
    issued_timestamp: Instant
    expiry_timestamp: Instant
    evidence_fingerprint: Hash
    current_trusted_head_fingerprint: Hash
    approval_fingerprint: Hash
    one_use_consumption_identity: Hash

    @model_validator(mode="after")
    def valid(self) -> "Approval":
        require(self.authority_identity.startswith("synthetic:"), "SYNTHETIC_IDENTITY_REQUIRED")
        require(self.issued_timestamp < self.expiry_timestamp, "INVALID_AUTHORITY_INTERVAL")
        require(
            sha(canonical(self.model_dump(mode="json", exclude={"approval_fingerprint"})))
            == self.approval_fingerprint,
            "CORRUPT_APPROVAL",
        )
        return self


class Authority:
    def __init__(self, local: Path, witness: Path, trusted_approvals: dict[str, str]):
        self.local, self.witness = external(local), external(witness)
        require(
            self.local != self.witness
            and self.local not in self.witness.parents
            and self.witness not in self.local.parents,
            "WITNESS_NOT_SEPARATE",
        )
        self.trusted_approvals = dict(trusted_approvals)

    def initialize(self, state: Witness) -> str:
        with ownership(self.witness):
            require(
                not (self.witness / "records").exists() and not self.local.exists(),
                "AUTHORITY_ALREADY_INITIALIZED",
            )
            self.local.mkdir(parents=True)
            return append(
                self.witness / "records",
                {"state": state.model_dump(mode="json"), "spent": [], "status": "READY"},
            )

    def current(self, expected_head: str) -> dict[str, Any]:
        rows, _ = chain(self.witness / "records", expected_head)
        require(bool(rows), "WITNESS_MISSING")
        previous: Witness | None = None
        spent: list[dict[str, Any]] = []
        for row in rows:
            state = Witness.model_validate(row["state"])
            if previous:
                self.progress(previous, state)
                require(row["spent"][: len(spent)] == spent, "CONSUMPTION_ROLLBACK")
            previous, spent = state, row["spent"]
        require(
            len({r["request"] for r in spent}) == len(spent)
            and len({r["identity"] for r in spent}) == len(spent),
            "DUPLICATE_CONSUMPTION",
        )
        files = sorted(self.local.glob("*.json"))
        require(
            {p.name for p in files} == {r["identity"] + ".json" for r in spent}
            and not list(self.local.glob("*.pending")),
            "MISSING_OR_AMBIGUOUS_CONSUMPTION",
        )
        for record in spent:
            raw, value = read_json(self.local / (record["identity"] + ".json"))
            require(value == record and raw == canonical(record), "CONSUMPTION_CORRUPTED")
        return rows[-1]

    @staticmethod
    def progress(previous: Witness, new: Witness) -> None:
        require(
            previous.run_id == new.run_id
            and new.session_sequence >= previous.session_sequence
            and new.highest_event_sequence >= previous.highest_event_sequence
            and new.authority_epoch >= previous.authority_epoch,
            "WITNESS_ROLLBACK",
        )
        require(not previous.paper_halt or new.paper_halt, "HALT_CLEARANCE_FORBIDDEN")
        if new.highest_event_sequence == previous.highest_event_sequence:
            require(new.journal_head == previous.journal_head, "JOURNAL_HEAD_WITHOUT_PROGRESS")

    def verify(self, expected_head: str, observed: Witness) -> dict[str, Any]:
        current = self.current(expected_head)
        require(current["state"] == observed.model_dump(mode="json"), "OBSERVED_STATE_MISMATCH")
        return current

    def recover(
        self,
        raw_approval: dict[str, Any] | None,
        *,
        expected_head: str,
        observed: Witness,
        run_id: str,
        incident_id: str,
        evidence: str,
        now: datetime,
        action: Callable[[Witness], Witness],
        crash: str | None = None,
    ) -> dict[str, Any]:
        """No retry or repair path; every callback requires a durable consumption first."""
        require(raw_approval is not None, "MISSING_APPROVAL")
        approval = Approval.model_validate(raw_approval)
        with ownership(self.witness):
            row = self.verify(expected_head, observed)
            require(row["status"] != "CONSUMED", "AMBIGUOUS_RECOVERY_NO_RETRY")
            require(
                self.trusted_approvals.get(approval.recovery_request_id)
                == approval.approval_fingerprint,
                "UNTRUSTED_APPROVAL",
            )
            require(
                approval.run_id == run_id == observed.run_id
                and approval.incident_id == incident_id,
                "AUTHORITY_IDENTITY_MISMATCH",
            )
            require(approval.evidence_fingerprint == evidence, "AUTHORITY_EVIDENCE_MISMATCH")
            require(approval.current_trusted_head_fingerprint == expected_head, "STALE_APPROVAL")
            require(
                now.tzinfo is not None
                and approval.issued_timestamp <= now < approval.expiry_timestamp,
                "EXPIRED_APPROVAL",
            )
            spent = row["spent"]
            require(
                not any(
                    r["request"] == approval.recovery_request_id
                    or r["identity"] == approval.one_use_consumption_identity
                    or r["approval"] == approval.approval_fingerprint
                    for r in spent
                ),
                "APPROVAL_ALREADY_USED",
            )
            if crash == "before_consumption":
                raise InterruptedError(crash)
            record = {
                "request": approval.recovery_request_id,
                "identity": approval.one_use_consumption_identity,
                "approval": approval.approval_fingerprint,
                "trusted_head": expected_head,
                "evidence": evidence,
            }
            publish(
                self.local / (approval.one_use_consumption_identity + ".json"), canonical(record)
            )
            if crash == "after_local_consumption":
                raise InterruptedError(crash)
            consumed = observed.model_copy(
                update={
                    "authority_epoch": observed.authority_epoch + 1,
                    "last_accepted_recovery_action": "CONSUMED:"
                    + approval.one_use_consumption_identity,
                }
            )
            spent = spent + [record]
            append(
                self.witness / "records",
                {"state": consumed.model_dump(mode="json"), "spent": spent, "status": "CONSUMED"},
            )
            if crash == "after_consumption":
                raise InterruptedError(crash)
            new = action(consumed)
            self.progress(consumed, new)
            require(
                new.authority_epoch == consumed.authority_epoch
                and new.last_accepted_recovery_action == approval.authorized_action,
                "RECOVERY_RESULT_BINDING",
            )
            if crash in {"during_recovery", "ambiguous_acknowledgment"}:
                raise InterruptedError(crash)
            head = append(
                self.witness / "records",
                {"state": new.model_dump(mode="json"), "spent": spent, "status": "COMPLETED"},
            )
            return {
                "scope": "SYNTHETIC_FIXTURE",
                "head": head,
                "state": new.model_dump(mode="json"),
                "approval": approval.approval_fingerprint,
                "consumed": record["identity"],
                "halt_clearance": False,
                "external_delivery": False,
            }
