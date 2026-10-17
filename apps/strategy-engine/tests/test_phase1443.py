"""Synthetic-only owner decisions; no stage can acquire real implementation authority."""

from datetime import UTC, datetime, timedelta
from typing import Any

import pytest

from strategy_engine.phase1439 import pipeline as p
from strategy_engine.phase1442 import governance as g
from strategy_engine.phase1443 import decisions as d

NOW = datetime(2024, 1, 2, tzinfo=UTC)
KEY = b"SYNTHETIC_ONLY_NOT_A_CREDENTIAL_1443"
EVIDENCE = b"SYNTHETIC_OWNER_DECISION"


def fixture() -> tuple[d.Scope, d.Bindings, d.Decision]:
    prohibited = {"legacy.py": p.sha(b"IMMUTABLE")}
    rollback = p.sha(b"ROLLBACK")
    stages = tuple(
        d.Stage(
            number=i,
            name=f"SYNTHETIC_STAGE_{i}",
            permitted=(
                d.FileCommitment(path=f"synthetic/stage_{i}.py", sha256=p.sha(str(i).encode())),
            ),
            prohibited_fingerprint=p.sha(p.canonical(prohibited)),
            interfaces=("SYNTHETIC_ONLY",),
            tests=("FULL",),
            evidence=("RECEIPTS",),
            budget_seconds=300,
            rollback=rollback,
            requires_stage=i - 1,
        )
        for i in range(1, 7)
    )
    scope = d.Scope(baseline="1" * 40, prohibited_files=prohibited, stages=stages)
    bindings = d.Bindings(
        baseline=scope.baseline,
        candidate=p.sha(b"CANDIDATE"),
        corporate_action=p.sha(b"ACTION"),
        identity=p.sha(b"IDENTITY"),
        comparability=p.sha(b"COMPARABILITY"),
        scope=p.fingerprint(scope),
        rollback=rollback,
    )
    decision = d.Decision(
        state="APPROVED",
        source="SYNTHETIC_TEST",
        proposal_fingerprint=p.fingerprint(bindings),
        owner="SYNTHETIC_OWNER",
        decided_at=NOW,
        evidence=p.sha(EVIDENCE),
        approved=bindings,
        corporate_choice="D",
        identity_choice="B",
        rollback_authority="SYNTHETIC_AUTHORITY",
        expires_at=NOW + timedelta(days=1),
        stage=1,
    )
    return scope, bindings, sign(decision)


def sign(decision: d.Decision) -> d.Decision:
    return decision.model_copy(update={"signature": d.synthetic_signature(decision, KEY)})


def check(decision: d.Decision, bindings: d.Bindings, scope: d.Scope) -> str:
    return d.verify_synthetic(decision, bindings, scope, EVIDENCE, KEY, NOW)


def test_valid_synthetic_never_authorizes_real_stage() -> None:
    scope, bindings, decision = fixture()
    assert check(decision, bindings, scope).endswith("REAL_IMPLEMENTATION_NOT_AUTHORIZED")
    with pytest.raises(ValueError, match="NO_STAGE_EXECUTION_AUTHORIZED"):
        d.activate_stage()


def test_pending_fields_null() -> None:
    _, bindings, _ = fixture()
    value = d.pending(bindings)
    assert value.state == "PENDING_OWNER_DECISION"
    assert all(
        getattr(value, name) is None
        for name in [
            "owner",
            "decided_at",
            "evidence",
            "approved",
            "corporate_choice",
            "identity_choice",
            "rollback_authority",
            "expires_at",
            "stage",
            "signature",
        ]
    )


@pytest.mark.parametrize(
    "field,replacement",
    [
        ("owner", None),
        ("owner", " "),
        ("decided_at", None),
        ("decided_at", NOW + timedelta(days=1)),
        ("evidence", p.sha(b"BAD")),
        ("expires_at", None),
        ("expires_at", NOW),
        ("rollback_authority", None),
        ("conflicting", True),
        ("corporate_choice", None),
        ("corporate_choice", "Z"),
        ("identity_choice", None),
        ("stage", None),
        ("stage", 2),
        ("completed_stage", 1),
        ("proposal_fingerprint", p.sha(b"BAD")),
    ],
)
def test_invalid_signed_decisions(field: str, replacement: Any) -> None:
    scope, bindings, decision = fixture()
    changed = sign(decision.model_copy(update={field: replacement}))
    with pytest.raises(ValueError):
        check(changed, bindings, scope)


@pytest.mark.parametrize(
    "state", ["PENDING_OWNER_DECISION", "REJECTED", "DEFERRED", "EXPIRED", "REVOKED"]
)
def test_inactive_state(state: str) -> None:
    scope, bindings, decision = fixture()
    with pytest.raises(ValueError):
        check(sign(decision.model_copy(update={"state": state})), bindings, scope)


@pytest.mark.parametrize(
    "field",
    ["baseline", "candidate", "corporate_action", "identity", "comparability", "scope", "rollback"],
)
def test_wrong_policy_binding(field: str) -> None:
    scope, bindings, decision = fixture()
    changed = bindings.model_copy(
        update={field: "2" * 40 if field == "baseline" else p.sha(b"BAD")}
    )
    with pytest.raises(ValueError, match="POLICY_BINDING"):
        check(sign(decision.model_copy(update={"approved": changed})), bindings, scope)


@pytest.mark.parametrize("failure", ["signature", "key", "tamper", "evidence", "source"])
def test_authentication_failures(failure: str) -> None:
    scope, bindings, decision = fixture()
    key, evidence = KEY, EVIDENCE
    if failure == "signature":
        decision = decision.model_copy(update={"signature": p.sha(b"BAD")})
    if failure == "key":
        key = b"DIFFERENT_SYNTHETIC_TEST_KEY_1443_XXX"
    if failure == "tamper":
        decision = decision.model_copy(update={"owner": "OTHER"})
    if failure == "evidence":
        evidence = b"ALTERED"
    if failure == "source":
        decision = decision.model_copy(update={"source": "PENDING_OWNER_INPUT"})
    with pytest.raises(ValueError):
        d.verify_synthetic(decision, bindings, scope, evidence, key, NOW)


@pytest.mark.parametrize("failure", ["empty", "hash", "legacy", "path", "dependency", "protection"])
def test_scope_fail_closed(failure: str) -> None:
    scope, bindings, decision = fixture()
    stage = scope.stages[0]
    updates: dict[str, Any] = {
        "empty": {"permitted": ()},
        "hash": {"permitted": (d.FileCommitment(path="synthetic/new.py"),)},
        "legacy": {"permitted": (d.FileCommitment(path="legacy.py", sha256=p.sha(b"X")),)},
        "path": {"permitted": (d.FileCommitment(path="../escape", sha256=p.sha(b"X")),)},
        "dependency": {"requires_stage": 1},
        "protection": {"prohibited_fingerprint": p.sha(b"X")},
    }
    scope = scope.model_copy(
        update={"stages": (stage.model_copy(update=updates[failure]), *scope.stages[1:])}
    )
    bindings = bindings.model_copy(update={"scope": p.fingerprint(scope)})
    decision = sign(
        decision.model_copy(
            update={"approved": bindings, "proposal_fingerprint": p.fingerprint(bindings)}
        )
    )
    with pytest.raises(ValueError):
        check(decision, bindings, scope)


def test_changed_stage_inventory_and_scope_rejected() -> None:
    scope, bindings, decision = fixture()
    with pytest.raises(ValueError):
        check(decision, bindings, scope.model_copy(update={"stages": scope.stages[:-1]}))


def test_no_automatic_next_stage_and_restart_verification() -> None:
    scope, bindings, decision = fixture()
    restored = d.Decision.model_validate_json(decision.model_dump_json())
    assert check(restored, bindings, scope) == check(decision, bindings, scope)
    changed = sign(decision.model_copy(update={"stage": 2, "completed_stage": 1}))
    with pytest.raises(ValueError, match="MISSING_PRIOR_RECEIPT"):
        check(changed, bindings, scope)
    changed = sign(changed.model_copy(update={"prior_receipt": p.sha(b"SYNTHETIC_PRIOR")}))
    assert check(changed, bindings, scope).startswith("SYNTHETIC_AUTH_VERIFIED")
    with pytest.raises(ValueError):
        d.activate_stage()


def test_result_separation_and_genuine_rejection() -> None:
    from test_phase1442 import result_family

    family = result_family()
    with pytest.raises(ValueError):
        g.compare(family, family.model_copy(update={"contract": "CANDIDATE_SERIES_V2"}))
    with pytest.raises(ValueError):
        g.compare(family, family, genuine_claim=True)


def test_historical_series_unchanged() -> None:
    from test_phase1441 import candidate

    _, batch = candidate()
    before = p.fingerprint(batch.series)
    scope, bindings, decision = fixture()
    check(decision, bindings, scope)
    assert p.fingerprint(batch.series) == before


def test_no_broker_or_active_import() -> None:
    import inspect

    source = inspect.getsource(d)
    assert not any(x in source for x in ["import socket", "import requests", "import kiteconnect"])
    assert "phase1443" not in inspect.getsource(p.research)
