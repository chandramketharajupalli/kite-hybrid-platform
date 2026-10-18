"""Disposable external SQLite tests; no genuine signatures or authority enrollment."""

import json
import sqlite3
import subprocess
import sys
from concurrent.futures import Future, ThreadPoolExecutor
from datetime import UTC, datetime, timedelta
from pathlib import Path
from threading import Event
from typing import Any, Literal

import pytest

from strategy_engine.phase1444c import crypto as c
from strategy_engine.phase1444c.replay import Ledger

NOW = datetime(2026, 1, 2, tzinfo=UTC)
IDENTITY = "TEST_ONLY:ledger1"
H = "a" * 64


def decision() -> c.SyntheticDecision:
    return c.SyntheticDecision(
        decision_id="TEST_ONLY:decision1",
        nonce=H,
        signed_payload_fingerprint="b" * 64,
        owner_authority_reference="TEST_ONLY:owner",
        trust_root_fingerprint="c" * 64,
        key_reference="TEST_ONLY:key",
        enrollment_reference="d" * 64,
        enrolled_fixture=True,
        approval_timestamp="2026-01-01T00:00:00.000000Z",
        expiry="2026-01-08T00:00:00.000000Z",
        trusted_time_reference="e" * 64,
    )


@pytest.fixture
def ledger(tmp_path: Path) -> Ledger:
    return Ledger.create(tmp_path / "fixture.sqlite", IDENTITY)


def request() -> c.VerificationRequest:
    return c.VerificationRequest(
        algorithm="Ed25519",
        domain="KITE_GOVERNANCE_DECISION:Phase1444B:v1",
        canonical_payload=b"{}",
        signature=b"\0" * 64,
        public_key=b"\0" * 32,
        public_key_fingerprint=H,
        enrollment_reference=H,
        provider_identity="UNAPPROVED",
    )


@pytest.mark.parametrize(
    "case",
    [
        "valid_signature",
        "wrong_signature",
        "modified_payload",
        "wrong_domain_signature",
        "wrong_public_key",
    ],
)
def test_ed25519_unavailable(case: str) -> None:
    pytest.skip("NOT_RUN_PROVIDER_UNAVAILABLE:" + case)


def test_provider_unavailable_denies() -> None:
    with pytest.raises(ValueError, match="CRYPTO_PROVIDER_NOT_AUTHORIZED"):
        c.verify_signature(request())


@pytest.mark.parametrize(
    "changes",
    [
        {"algorithm": "HMAC"},
        {"domain": "WRONG"},
        {"public_key": b"short"},
        {"signature": b"short"},
        {"unknown": "field"},
        {"enrollment_reference": None},
    ],
)
def test_verification_schema_rejects(changes: dict[str, Any]) -> None:
    with pytest.raises(ValueError):
        c.VerificationRequest.model_validate(request().model_dump() | changes)


@pytest.mark.parametrize(
    ("changes", "reason"),
    [
        ({"enrolled_fixture": False}, "OWNER_NOT_ENROLLED"),
        ({"enrollment_reference": None}, "OWNER_NOT_ENROLLED"),
        ({"trusted_time_reference": None}, "TRUSTED_TIME_UNVERIFIED"),
        ({"source_git_baseline": "0" * 40}, "WRONG_BASELINE"),
        ({"amendment_raw": H}, "WRONG_AMENDMENT_RAW"),
        ({"amendment_canonical": H}, "WRONG_AMENDMENT_CANONICAL"),
        ({"certificate": H}, "WRONG_CERTIFICATE"),
    ],
)
def test_decision_binding(ledger: Ledger, changes: dict[str, Any], reason: str) -> None:
    with pytest.raises(ValueError, match=reason):
        ledger.consume_test(decision().model_copy(update=changes), lambda: NOW)
    assert ledger.inspect()["consumed"] == 0


@pytest.mark.parametrize(
    "changes",
    [{"trust_root_fingerprint": None}, {"source": "REAL"}, {"action": "SERIES_V2_IMPLEMENTATION"}],
)
def test_fixture_cannot_supply_real_authority(ledger: Ledger, changes: dict[str, Any]) -> None:
    with pytest.raises(ValueError):
        ledger.consume_test(decision().model_copy(update=changes), lambda: NOW)


def test_inspection_does_not_consume_and_reopen_survives(ledger: Ledger) -> None:
    assert ledger.inspect()["consumed"] == 0
    assert ledger.inspect()["consumed"] == 0
    assert ledger.consume_test(decision(), lambda: NOW) == "TEST_ONLY_CONSUMED_NO_ACTION_AUTHORIZED"
    reopened = Ledger(ledger.path, IDENTITY)
    assert reopened.inspect()["consumed"] == 1
    with pytest.raises(ValueError, match="DUPLICATE_OR_REPLAY"):
        reopened.consume_test(decision(), lambda: NOW)


@pytest.mark.parametrize("change", [{"decision_id": "TEST_ONLY:other"}, {"nonce": "f" * 64}])
def test_unique_id_and_nonce(ledger: Ledger, change: dict[str, Any]) -> None:
    ledger.consume_test(decision(), lambda: NOW)
    with pytest.raises(ValueError, match="DUPLICATE_OR_REPLAY"):
        ledger.consume_test(decision().model_copy(update=change), lambda: NOW)


@pytest.mark.parametrize(
    "when", [datetime(2025, 12, 31, tzinfo=UTC), datetime(2026, 1, 8, tzinfo=UTC)]
)
def test_future_and_expired(ledger: Ledger, when: datetime) -> None:
    with pytest.raises(ValueError, match="EXPIRED_OR_FUTURE"):
        ledger.consume_test(decision(), lambda: when)


def test_clock_rollback_survives_reopen(ledger: Ledger) -> None:
    ledger.revoke_test("key", "TEST_ONLY:unrelated", lambda: NOW)
    with pytest.raises(ValueError, match="CLOCK_ROLLBACK"):
        Ledger(ledger.path, IDENTITY).consume_test(decision(), lambda: NOW - timedelta(seconds=1))


@pytest.mark.parametrize("kind", ["owner", "key", "root", "decision"])
def test_revocation_survives_restart(
    ledger: Ledger,
    kind: Literal["owner", "key", "root", "decision"],
) -> None:
    item = decision()
    reference = {
        "owner": item.owner_authority_reference,
        "key": item.key_reference,
        "root": item.trust_root_fingerprint,
        "decision": item.decision_id,
    }[kind]
    ledger.revoke_test(kind, reference, lambda: NOW)
    with pytest.raises(ValueError, match="REVOKED"):
        Ledger(ledger.path, IDENTITY).consume_test(item, lambda: NOW)


CHILD = """import json,sys,os
from pathlib import Path
from datetime import UTC,datetime
from strategy_engine.phase1444c.replay import Ledger
from strategy_engine.phase1444c.crypto import SyntheticDecision
args=json.loads(sys.stdin.readline())
ledger=Ledger(Path(args['path']),args['identity'])
decision=SyntheticDecision.model_validate(args['decision'])
mode=args['mode']
def crash(): os._exit(17)
try:
 result=ledger.consume_test(decision,lambda:datetime(2026,1,2,tzinfo=UTC),
  before_commit=crash if mode=='before' else None,after_commit=crash if mode=='after' else None)
 print(result)
except ValueError as error: print(str(error))
"""


def child(ledger: Ledger, mode: str) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        [sys.executable, "-B", "-c", CHILD],
        input=json.dumps(
            {
                "path": str(ledger.path),
                "identity": IDENTITY,
                "decision": decision().model_dump(),
                "mode": mode,
            }
        ),
        text=True,
        capture_output=True,
        timeout=20,
    )
    receipt = ledger.path.parent / ("child-" + mode + ".json")
    receipt.write_text(
        json.dumps(
            {
                "mode": mode,
                "exit_code": result.returncode,
                "stdout": result.stdout,
                "stderr": result.stderr,
            }
        ),
        encoding="utf-8",
    )
    return result


def test_process_restart(ledger: Ledger) -> None:
    first = child(ledger, "first")
    second = child(ledger, "second")
    assert first.returncode == second.returncode == 0
    assert "TEST_ONLY_CONSUMED" in first.stdout and "DUPLICATE_OR_REPLAY" in second.stdout


def test_cross_process_concurrent_consumers(ledger: Ledger) -> None:
    with ThreadPoolExecutor(max_workers=2) as pool:
        outputs = list(pool.map(lambda mode: child(ledger, mode), ("concurrent1", "concurrent2")))
    assert all(x.returncode == 0 for x in outputs)
    assert sum("TEST_ONLY_CONSUMED" in x.stdout for x in outputs) == 1
    assert sum("DUPLICATE_OR_REPLAY" in x.stdout for x in outputs) == 1
    assert ledger.inspect()["consumed"] == 1


def test_crash_before_commit_rolls_back(ledger: Ledger) -> None:
    assert child(ledger, "before").returncode == 17
    recovered = Ledger(ledger.path, IDENTITY)
    assert recovered.inspect()["consumed"] == 0
    assert recovered.consume_test(decision(), lambda: NOW).startswith("TEST_ONLY_CONSUMED")


def test_crash_after_commit_cannot_reconsume(ledger: Ledger) -> None:
    assert child(ledger, "after").returncode == 17
    recovered = Ledger(ledger.path, IDENTITY)
    assert recovered.inspect()["consumed"] == 1
    with pytest.raises(ValueError, match="DUPLICATE_OR_REPLAY"):
        recovered.consume_test(decision(), lambda: NOW)


def test_corrupt_storage_denies(ledger: Ledger) -> None:
    ledger.path.write_bytes(b"TEST_ONLY_CORRUPT_DATABASE")
    with pytest.raises(sqlite3.DatabaseError):
        Ledger(ledger.path, IDENTITY)


def test_tampered_consumption_denies(ledger: Ledger) -> None:
    ledger.consume_test(decision(), lambda: NOW)
    db = sqlite3.connect(ledger.path)
    try:
        db.execute("DELETE FROM consumed")
        db.commit()
    finally:
        db.close()
    with pytest.raises(ValueError, match="CONSUMPTION_TAMPER"):
        Ledger(ledger.path, IDENTITY)


def test_missing_database_never_resets(ledger: Ledger) -> None:
    ledger.path.unlink()
    with pytest.raises(ValueError, match="LEDGER_UNAVAILABLE_NO_RESET"):
        Ledger(ledger.path, IDENTITY)
    assert not ledger.path.exists()


def test_no_overwrite_or_wrong_identity(ledger: Ledger) -> None:
    with pytest.raises(FileExistsError):
        Ledger.create(ledger.path, IDENTITY)
    with pytest.raises(ValueError, match="LEDGER_IDENTITY"):
        Ledger(ledger.path, "TEST_ONLY:different")


def test_expiry_race_before_commit_denies(ledger: Ledger) -> None:
    clocks = iter([NOW, datetime(2026, 1, 8, tzinfo=UTC)])
    with pytest.raises(ValueError, match="EXPIRED_OR_FUTURE"):
        ledger.consume_test(decision(), lambda: next(clocks))
    assert ledger.inspect()["consumed"] == 0


def test_revocation_serializes_before_consumer(ledger: Ledger) -> None:
    other = Ledger(ledger.path, IDENTITY)
    ledger.revoke_test("owner", decision().owner_authority_reference, lambda: NOW)
    with pytest.raises(ValueError, match="REVOKED"):
        other.consume_test(decision(), lambda: NOW)


def test_revocation_race_consumer_commits_first(ledger: Ledger) -> None:
    started = Event()

    def revoke() -> None:
        started.set()
        Ledger(ledger.path, IDENTITY).revoke_test(
            "owner", decision().owner_authority_reference, lambda: NOW
        )

    with ThreadPoolExecutor(max_workers=1) as pool:
        pending: list[Future[None]] = []

        def during_transaction() -> None:
            future = pool.submit(revoke)
            pending.append(future)
            assert started.wait(2)
            assert not future.done()

        ledger.consume_test(decision(), lambda: NOW, before_commit=during_transaction)
        pending[0].result(timeout=10)
    other = decision().model_copy(update={"decision_id": "TEST_ONLY:later", "nonce": "f" * 64})
    with pytest.raises(ValueError, match="REVOKED"):
        ledger.consume_test(other, lambda: NOW)


@pytest.mark.parametrize(
    "operation",
    [
        "KEY_ROTATION",
        "REAL_OWNER_ENROLLMENT",
        "AMENDMENT_ADOPTION",
        "SERIES_V2_IMPLEMENTATION",
        "KITE_REQUEST",
    ],
)
def test_real_operations_always_denied(operation: str) -> None:
    with pytest.raises(ValueError, match="REAL_AUTHORITY_NOT_ESTABLISHED"):
        c.real_authority(operation)
