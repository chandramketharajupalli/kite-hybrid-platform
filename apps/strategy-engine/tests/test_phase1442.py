"""Synthetic governance decisions never authorize an active migration."""

from datetime import UTC, datetime, timedelta
from typing import Any

import pytest

from strategy_engine.phase1439 import pipeline as p
from strategy_engine.phase1442 import governance as g

NOW = datetime(2024, 1, 2, tzinfo=UTC)


def proposal() -> g.Proposal:
    return g.Proposal(
        baseline="1" * 40,
        candidate=p.sha(b"CANDIDATE"),
        adjustment=p.sha(b"ADJUSTMENT"),
        identity=p.sha(b"IDENTITY"),
        comparability=p.sha(b"COMPARABILITY"),
        rollback=p.sha(b"ROLLBACK"),
        permitted_files=("apps/strategy-engine/SYNTHETIC_CANDIDATE.py",),
    )


def decision() -> g.Approval:
    value = proposal()
    return g.Approval(
        status="APPROVED",
        source="SYNTHETIC_TEST",
        governance_fingerprint=p.fingerprint(value),
        baseline=value.baseline,
        owner="SYNTHETIC_OWNER",
        decision_evidence=p.sha(b"SYNTHETIC_EVIDENCE"),
        decided_at=NOW - timedelta(days=1),
        expires_at=NOW + timedelta(days=1),
        approved_contract=g.CONTRACT,
        adjustment=value.adjustment,
        identity=value.identity,
        comparability=value.comparability,
        rollback=value.rollback,
        rollback_authority="SYNTHETIC_AUTHORITY",
        implementation_scope=value.scope,
        permitted_files=value.permitted_files,
        tests=value.tests,
    )


def test_valid_synthetic_decision_still_cannot_execute() -> None:
    value = decision()
    assert g.verify_synthetic_decision(value, proposal(), p.fingerprint(value), NOW).endswith(
        "EXECUTION_NOT_AUTHORIZED"
    )
    with pytest.raises(ValueError, match="MIGRATION_NOT_AUTHORIZED"):
        g.execute_migration()


def test_pending_has_no_owner_decision() -> None:
    value = g.pending(proposal())
    assert value.status == "NOT_AUTHORIZED" and value.owner is None
    assert value.decided_at is None and value.expires_at is None
    assert value.decision_evidence is None
    with pytest.raises(ValueError):
        g.verify_synthetic_decision(value, proposal(), p.fingerprint(value), NOW)


@pytest.mark.parametrize(
    "field,replacement",
    [
        ("owner", None),
        ("owner", " "),
        ("decision_evidence", None),
        ("expires_at", NOW),
        ("expires_at", None),
        ("decided_at", NOW + timedelta(days=1)),
        ("baseline", "2" * 40),
        ("approved_contract", "SeriesUnknown"),
        ("adjustment", p.sha(b"WRONG")),
        ("identity", p.sha(b"WRONG")),
        ("comparability", p.sha(b"WRONG")),
        ("rollback", p.sha(b"WRONG")),
        ("permitted_files", ("apps/strategy-engine/OTHER.py",)),
        ("conflicting", True),
        ("revoked", True),
        ("status", "REVOKED"),
        ("status", "REJECTED"),
        ("rollback_authority", None),
        ("governance_fingerprint", p.sha(b"WRONG")),
        ("implementation_scope", "UNBOUNDED"),
        ("tests", ()),
        ("source", "PENDING_OWNER_INPUT"),
    ],
)
def test_bad_decisions(field: str, replacement: Any) -> None:
    changed = decision().model_copy(update={field: replacement})
    with pytest.raises(ValueError):
        g.verify_synthetic_decision(changed, proposal(), p.fingerprint(changed), NOW)


def test_invalid_evidence_fingerprint() -> None:
    with pytest.raises(ValueError, match="DECISION_FINGERPRINT"):
        g.verify_synthetic_decision(decision(), proposal(), p.sha(b"WRONG"), NOW)


@pytest.mark.parametrize(
    "path",
    [
        "../escape",
        "apps/strategy-engine/../escape",
        "C:/outside",
        "apps/strategy-engine/evil\\path",
    ],
)
def test_unsafe_file_scope(path: str) -> None:
    proposed = proposal().model_copy(update={"permitted_files": (path,)})
    value = decision().model_copy(
        update={"permitted_files": (path,), "governance_fingerprint": p.fingerprint(proposed)}
    )
    with pytest.raises(ValueError, match="UNSAFE_PATH"):
        g.verify_synthetic_decision(value, proposed, p.fingerprint(value), NOW)


def result_family() -> g.ResultFamily:
    return g.ResultFamily(
        contract="LEGACY_SERIES_V1",
        basis="RAW_UNADJUSTED",
        methodology=p.sha(b"M"),
        identity=p.sha(b"I"),
        availability=p.sha(b"A"),
        actions=p.sha(b"CA"),
        costs=p.sha(b"C"),
        execution=p.sha(b"E"),
        qualification=p.sha(b"Q"),
        source_type="SYNTHETIC",
    )


@pytest.mark.parametrize(
    "field",
    ["methodology", "identity", "availability", "actions", "costs", "execution", "qualification"],
)
def test_separate_changed_families(field: str) -> None:
    a = result_family()
    with pytest.raises(ValueError, match="INCOMPATIBLE_RESULT_FAMILIES"):
        g.compare(a, a.model_copy(update={field: p.sha(b"CHANGED")}))


@pytest.mark.parametrize(
    "field,replacement",
    [
        ("contract", "CANDIDATE_SERIES_V2"),
        ("basis", "PROVIDER_ADJUSTED"),
        ("basis", "LOCALLY_ADJUSTED"),
        ("basis", "ADJUSTMENT_UNVERIFIED"),
        ("source_type", "GENUINE"),
    ],
)
def test_historical_result_pooling_rejected(field: str, replacement: str) -> None:
    a = result_family()
    with pytest.raises(ValueError):
        g.compare(a, a.model_copy(update={field: replacement}))


def test_no_synthetic_to_genuine_promotion() -> None:
    value = result_family()
    assert g.compare(value, value) == "SAME_METADATA_FAMILY_NOT_QUALIFICATION_APPROVAL"
    with pytest.raises(ValueError, match="GENUINE_QUALIFICATION_NOT_AUTHORIZED"):
        g.compare(value, value, genuine_claim=True)


@pytest.mark.parametrize(
    "action,identity,basis",
    [
        ("UNKNOWN", True, "RAW_UNADJUSTED"),
        ("SPLIT", False, "RAW_UNADJUSTED"),
        ("BONUS", True, "MIXED"),
        ("MERGER", True, "ADJUSTMENT_UNVERIFIED"),
    ],
)
def test_unsupported_actions_identity_and_basis(action: str, identity: bool, basis: str) -> None:
    with pytest.raises(ValueError):
        g.scenario_gate(action, identity, basis)


@pytest.mark.parametrize("action", g.SCENARIOS)
def test_review_is_not_adoption(action: str) -> None:
    assert g.scenario_gate(action, True, "RAW_UNADJUSTED") == "PENDING_OWNER_DECISION"


def test_legacy_report_invariance() -> None:
    from test_phase1441 import candidate

    value, batch = candidate()
    before = p.canonical(batch.series.model_dump(mode="json"))
    g.assessment()
    g.pending(proposal())
    assert p.canonical(batch.series.model_dump(mode="json")) == before
    assert value.status == "PROPOSED_NOT_ADOPTED"


def test_no_network_or_production_activation() -> None:
    import inspect

    text = inspect.getsource(g)
    assert not any(x in text for x in ["import socket", "import requests", "import kiteconnect"])
    assert "phase1442" not in inspect.getsource(p.research)
    result = g.assessment()
    assert result["genuine_performance"] is None
    assert result["real_kite_requests"] == result["genuine_qualified_pairs"] == 0
