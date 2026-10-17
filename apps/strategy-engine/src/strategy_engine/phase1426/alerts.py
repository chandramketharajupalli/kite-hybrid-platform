"""Monitored event-to-alert emission separated from independent truth evaluation."""

from datetime import date
from pathlib import Path
from typing import Any, Literal

from pydantic import model_validator

from strategy_engine.backtest.dataset import NSE, Frozen, Hash, Instant
from strategy_engine.phase1416.imports import canonical, external, sha
from strategy_engine.phase1420.multi import alert
from strategy_engine.phase1424.analytics import (
    CATEGORIES,
    MONITORING_POLICY,
    Truth,
    monitoring,
    seal,
)
from strategy_engine.phase1425.reliability import require
from strategy_engine.phase1426.authority import append, chain, ownership

ALERT_POLICY = {
    "version": "Phase1426CriticalAlerts.v1",
    "scope": "SYNTHETIC_FIXTURE",
    "mandatory_timely_fraction": "1",
    "allowed_missed": 0,
    "max_incidents": 64,
    "max_alerts": 256,
    "matching": MONITORING_POLICY,
    "binding": "incident_id+incident_evidence+accepted_event+run+session+category",
    "paper_halt": "STICKY_ON_CRITICAL_INCIDENT",
    "external_delivery": False,
}


class AcceptedEvent(Frozen):
    version: Literal["Phase1426AcceptedIncident.v1"] = "Phase1426AcceptedIncident.v1"
    scope: Literal["SYNTHETIC_FIXTURE"] = "SYNTHETIC_FIXTURE"
    incident_id: Hash
    run_id: Hash
    session_date: date
    category: str
    occurred_at: Instant
    accepted_at: Instant
    incident_evidence: Hash
    fingerprint: Hash

    @model_validator(mode="after")
    def valid(self) -> "AcceptedEvent":
        require(
            self.category in CATEGORIES and self.accepted_at >= self.occurred_at,
            "INVALID_ACCEPTED_INCIDENT",
        )
        require(self.accepted_at.astimezone(NSE).date() == self.session_date, "INCIDENT_SESSION")
        require(
            self.fingerprint
            == sha(canonical(self.model_dump(mode="json", exclude={"fingerprint"}))),
            "ACCEPTED_INCIDENT_HASH",
        )
        return self


def emitted(event: AcceptedEvent) -> dict[str, Any]:
    """No Truth input: the monitored path acts only on accepted incident events."""
    row = alert(
        event.run_id,
        event.session_date.isoformat(),
        event.accepted_at.isoformat(),
        event.category,
        event.incident_evidence,
        "CRITICAL",
    )
    row.pop("alert_id")
    row.update(
        incident_id=event.incident_id,
        incident_evidence=event.incident_evidence,
        accepted_event_fingerprint=event.fingerprint,
        emitter_version=ALERT_POLICY["version"],
    )
    return row | {"alert_id": sha(canonical(row))}


class AlertLedger:
    def __init__(self, directory: Path):
        self.directory = external(directory)

    def snapshot(self, expected_head: str) -> list[dict[str, Any]]:
        rows, _ = chain(self.directory / "records", expected_head)
        require(len(rows) <= 64, "INCIDENT_BUDGET")
        seen = set()
        for row in rows:
            event = AcceptedEvent.model_validate(row["event"])
            require(event.incident_id not in seen, "DUPLICATE_INCIDENT_RECORD")
            require(
                row["alert"] == emitted(event) and row["paper_halt"] is True,
                "ALERT_RECORD_MISMATCH",
            )
            seen.add(event.incident_id)
        return rows

    def accept(self, event: AcceptedEvent, expected_head: str, crash: str | None = None) -> str:
        with ownership(self.directory):
            rows = self.snapshot(expected_head)
            for row in rows:
                if row["event"]["incident_id"] == event.incident_id:
                    require(row["event"] == event.model_dump(mode="json"), "INCIDENT_CONFLICT")
                    return expected_head
            require(len(rows) < 64, "INCIDENT_BUDGET")
            if crash == "before_record":
                raise InterruptedError(crash)
            head = append(
                self.directory / "records",
                {
                    "event": event.model_dump(mode="json"),
                    "alert": emitted(event),
                    "paper_halt": True,
                },
            )
            if crash == "after_record":
                raise InterruptedError(crash)
            return head


def assure(
    independent_truth: dict[str, Any],
    expected_truth: str,
    events: tuple[AcceptedEvent, ...],
    alerts: list[dict[str, Any]],
    expected_alert_hash: str,
    *,
    run_id: str,
    session_date: str,
    monitoring_policy: str,
    paper_halt: bool,
) -> dict[str, Any]:
    require(len(events) <= 64 and len(alerts) <= 256, "ASSURANCE_BUDGET")
    truth = Truth.model_validate(independent_truth)
    require(truth.fingerprint == expected_truth, "TRUSTED_TRUTH_MISMATCH")
    require(monitoring_policy == truth.policy_hash, "MONITORING_POLICY_MISMATCH")
    require(0 < len(truth.incidents) <= 64, "INCIDENT_BUDGET")
    # Validate every alert even when it cannot satisfy an obligation.
    monitoring(None, alerts, expected_alert_hash)
    accepted: dict[str, AcceptedEvent] = {}
    for candidate in events:
        require(
            candidate.incident_id not in accepted or accepted[candidate.incident_id] == candidate,
            "INCIDENT_CONFLICT",
        )
        accepted[candidate.incident_id] = candidate
    obligations, matches, missed = [], [], []
    duplicates: set[str] = set()
    late: set[str] = set()
    for incident in truth.incidents:
        require(
            incident.run_id == run_id and incident.session_date.isoformat() == session_date,
            "ASSURANCE_IDENTITY",
        )
        if incident.severity != "CRITICAL":
            continue
        evidence = sha(canonical(incident.model_dump(mode="json")))
        obligation = seal(
            {
                "version": "Phase1426Obligation.v1",
                "incident_id": incident.incident_id,
                "run_id": run_id,
                "session_date": session_date,
                "incident_evidence": evidence,
                "expected_alert": incident.expected_alert,
                "deadline": incident.deadline.isoformat(),
                "severity": "CRITICAL",
                "truth_fingerprint": expected_truth,
                "paper_halt_required": True,
            }
        )
        obligations.append(obligation)
        event = accepted.get(incident.incident_id)
        if event:
            require(
                event.incident_evidence == evidence
                and event.run_id == run_id
                and event.session_date == incident.session_date
                and event.category == incident.category
                and event.occurred_at == incident.start,
                "ACCEPTED_EVENT_BINDING",
            )
        eligible = [
            a
            for a in alerts
            if event is not None
            and a.get("incident_id") == incident.incident_id
            and a.get("incident_evidence") == evidence
            and a.get("accepted_event_fingerprint") == event.fingerprint
        ]
        # Reuse the frozen matcher on an identity-bound projection without changing deadlines.
        projected = truth.model_dump(mode="json", exclude={"fingerprint"})
        projected["incidents"] = [incident.model_dump(mode="json")]
        projected["fingerprint"] = sha(canonical(projected))
        measured = monitoring(projected, eligible, sha(canonical(eligible)))
        matches.extend(measured["matches"])
        missed.extend(measured["missed_critical"])
        duplicates.update(measured["duplicate_alerts"])
        late.update(measured["late_alerts"])
    require(bool(obligations), "NO_MANDATORY_INCIDENTS")
    require(len({m["alert_id"] for m in matches}) == len(matches), "DUPLICATE_SATISFACTION")
    return {
        "scope": "SYNTHETIC_FIXTURE",
        "policy_fingerprint": sha(canonical(ALERT_POLICY)),
        "truth_fingerprint": expected_truth,
        "alert_fingerprint": expected_alert_hash,
        "obligations": obligations,
        "matches": matches,
        "missing_obligations": missed,
        "frozen_incident_count": len(obligations),
        "timely_monitored_alerts": len(matches),
        "missed_critical_alerts": len(missed),
        "false_positives": len(alerts) - len(matches),
        "late_alerts": sorted(late),
        "duplicate_alerts": sorted(duplicates),
        "alert_obligation_coverage": f"{len(matches)}/{len(obligations)}",
        "assurance_check_coverage": f"{len(obligations)}/{len(obligations)}",
        "evaluator_missing_alert_detection_count": len(missed),
        "critical_coverage_status": "COMPLETE" if not missed else "INCOMPLETE",
        "certification_eligible": not missed,
        "paper_halt_required": paper_halt or bool(obligations),
        "new_entries_allowed": False,
        "halt_clearance": False,
        "external_delivery": False,
        "operational": "NO_GO",
        "genuine_qualified": 0,
    }
