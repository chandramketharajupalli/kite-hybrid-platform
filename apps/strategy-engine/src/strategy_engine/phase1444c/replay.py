"""Disposable SQLite replay fixture. Never authorizes or dispatches real actions."""

import json
import sqlite3
import stat
from collections.abc import Callable
from contextlib import closing
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Literal

from strategy_engine.phase1444b.trust import canonical, instant, require, sha
from strategy_engine.phase1444c.crypto import SyntheticDecision, check_fixture

CUSTODY = Path(r"D:\Yogendra\offline-validation-evidence\phase-14.44C\generation-1")
VERSION = "Phase1444CDurableReplayLedger.v1.TEST_ONLY"
SCHEMA = {
    "metadata": "CREATE TABLE metadata (identity TEXT PRIMARY KEY, version TEXT NOT NULL)",
    "events": "CREATE TABLE events (seq INTEGER PRIMARY KEY, previous TEXT NOT NULL, "
    "body TEXT NOT NULL, digest TEXT NOT NULL)",
    "consumed": "CREATE TABLE consumed (decision_id TEXT PRIMARY KEY, nonce TEXT UNIQUE NOT NULL, "
    "record TEXT NOT NULL)",
    "revoked": "CREATE TABLE revoked (kind TEXT NOT NULL, reference TEXT NOT NULL, "
    "PRIMARY KEY (kind, reference))",
}


def safe_path(path: Path) -> Path:
    require(path.is_absolute() and path.is_relative_to(CUSTODY), "UNAPPROVED_LEDGER_PATH")
    require(path.suffix == ".sqlite", "LEDGER_EXTENSION")
    for parent in (path, *path.parents):
        if parent.exists():
            require(
                not parent.is_symlink()
                and not parent.stat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT,
                "REPARSE_LEDGER_PATH",
            )
    require(path.resolve().is_relative_to(CUSTODY.resolve()), "LEDGER_ESCAPE")
    return path


class Ledger:
    def __init__(self, path: Path, expected_identity: str) -> None:
        self.path = safe_path(path)
        self.identity = expected_identity
        require(path.is_file(), "LEDGER_UNAVAILABLE_NO_RESET")
        with closing(self._connect()) as connection:
            connection.execute("BEGIN")
            self._validate(connection)
            connection.rollback()

    @classmethod
    def create(cls, path: Path, identity: str) -> "Ledger":
        safe_path(path)
        require(bool(identity) and identity.startswith("TEST_ONLY:"), "FIXTURE_ID_REQUIRED")
        with path.open("xb"):
            pass
        with closing(sqlite3.connect(path, timeout=5)) as connection:
            connection.execute("PRAGMA journal_mode=DELETE")
            connection.execute("PRAGMA synchronous=FULL")
            connection.execute("BEGIN IMMEDIATE")
            for sql in SCHEMA.values():
                connection.execute(sql)
            connection.execute("INSERT INTO metadata VALUES (?,?)", (identity, VERSION))
            connection.commit()
        return cls(path, identity)

    def _connect(self) -> sqlite3.Connection:
        safe_path(self.path)
        connection = sqlite3.connect(self.path.as_uri() + "?mode=rw", uri=True, timeout=5)
        connection.execute("PRAGMA synchronous=FULL")
        return connection

    def _validate(self, connection: sqlite3.Connection) -> tuple[str, int, datetime | None]:
        require(connection.execute("PRAGMA quick_check").fetchone() == ("ok",), "LEDGER_CORRUPT")
        actual = dict(
            connection.execute(
                "SELECT name,sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
            ).fetchall()
        )
        require(actual == SCHEMA, "LEDGER_SCHEMA_CHANGED")
        require(
            connection.execute("SELECT * FROM metadata").fetchall() == [(self.identity, VERSION)],
            "LEDGER_IDENTITY",
        )
        expected: dict[str, tuple[str, str]] = {}
        revoked: set[tuple[str, str]] = set()
        previous = sha(self.identity.encode())
        last: datetime | None = None
        seq = 0
        for number, prior, body, digest in connection.execute("SELECT * FROM events ORDER BY seq"):
            require(number == seq + 1 and prior == previous, "AUDIT_LINK_CHANGED")
            require(sha(prior.encode() + b"\0" + body.encode()) == digest, "AUDIT_HASH_CHANGED")
            event = json.loads(body)
            require(canonical(event).decode() == body, "AUDIT_CANONICAL_CHANGED")
            now = instant(event["at"])
            require(last is None or now >= last, "CLOCK_ROLLBACK")
            if event["kind"] == "consume":
                record = event["record"]
                decision = SyntheticDecision.model_validate(record["decision"])
                require(decision.decision_id not in expected, "AUDIT_DUPLICATE")
                expected[decision.decision_id] = (decision.nonce, canonical(record).decode())
            elif event["kind"] == "revoke":
                revoked.add((event["target_kind"], event["reference"]))
            else:
                raise ValueError("UNKNOWN_AUDIT_EVENT")
            previous, seq, last = digest, number, now
        actual_consumed = {x: (n, b) for x, n, b in connection.execute("SELECT * FROM consumed")}
        require(actual_consumed == expected, "CONSUMPTION_TAMPER")
        require(
            set(connection.execute("SELECT * FROM revoked").fetchall()) == revoked,
            "REVOCATION_TAMPER",
        )
        return previous, seq, last

    def _append(
        self, connection: sqlite3.Connection, prior: str, seq: int, event: dict[str, Any]
    ) -> str:
        body = canonical(event).decode()
        digest = sha(prior.encode() + b"\0" + body.encode())
        connection.execute("INSERT INTO events VALUES (?,?,?,?)", (seq + 1, prior, body, digest))
        return digest

    def inspect(self) -> dict[str, Any]:
        with closing(self._connect()) as connection:
            connection.execute("BEGIN")
            head, seq, _ = self._validate(connection)
            count = connection.execute("SELECT count(*) FROM consumed").fetchone()[0]
            connection.rollback()
            return {
                "identity": self.identity,
                "events": seq,
                "consumed": count,
                "head": head,
                "source": "TEST_ONLY",
                "real_authority": "NOT_ESTABLISHED",
            }

    def consume_test(
        self,
        decision: SyntheticDecision,
        clock: Callable[[], datetime],
        before_commit: Callable[[], None] | None = None,
        after_commit: Callable[[], None] | None = None,
    ) -> str:
        decision = SyntheticDecision.model_validate(decision.model_dump())
        with closing(self._connect()) as connection:
            connection.execute("BEGIN IMMEDIATE")
            prior, seq, last = self._validate(connection)
            now = clock()
            check_fixture(decision, now)
            require(last is None or now >= last, "CLOCK_ROLLBACK")
            for kind, reference in (
                ("owner", decision.owner_authority_reference),
                ("key", decision.key_reference),
                ("root", decision.trust_root_fingerprint),
                ("decision", decision.decision_id),
            ):
                require(
                    connection.execute(
                        "SELECT 1 FROM revoked WHERE kind=? AND reference=?", (kind, reference)
                    ).fetchone()
                    is None,
                    "REVOKED",
                )
            require(
                connection.execute(
                    "SELECT 1 FROM consumed WHERE decision_id=? OR nonce=?",
                    (decision.decision_id, decision.nonce),
                ).fetchone()
                is None,
                "DUPLICATE_OR_REPLAY",
            )
            timestamp = now.strftime("%Y-%m-%dT%H:%M:%S.%fZ")
            record = {
                "decision": decision.model_dump(),
                "consumption_timestamp": timestamp,
                "revocation_state": "SYNTHETIC_ACTIVE_AT_COMMIT",
            }
            connection.execute(
                "INSERT INTO consumed VALUES (?,?,?)",
                (decision.decision_id, decision.nonce, canonical(record).decode()),
            )
            self._append(
                connection, prior, seq, {"kind": "consume", "at": timestamp, "record": record}
            )
            if before_commit is not None:
                before_commit()
            final_time = clock()
            check_fixture(decision, final_time)
            require(final_time >= now, "CLOCK_ROLLBACK")
            connection.commit()
            if after_commit is not None:
                after_commit()
        return "TEST_ONLY_CONSUMED_NO_ACTION_AUTHORIZED"

    def revoke_test(
        self,
        kind: Literal["owner", "key", "root", "decision"],
        reference: str,
        clock: Callable[[], datetime],
    ) -> None:
        require(
            kind in ("owner", "key", "root", "decision") and bool(reference), "REVOCATION_SCOPE"
        )
        with closing(self._connect()) as connection:
            connection.execute("BEGIN IMMEDIATE")
            prior, seq, last = self._validate(connection)
            now = clock()
            require(now.utcoffset() == timedelta(0), "UTC_REQUIRED")
            require(last is None or now >= last, "CLOCK_ROLLBACK")
            connection.execute("INSERT INTO revoked VALUES (?,?)", (kind, reference))
            self._append(
                connection,
                prior,
                seq,
                {
                    "kind": "revoke",
                    "target_kind": kind,
                    "reference": reference,
                    "at": now.strftime("%Y-%m-%dT%H:%M:%S.%fZ"),
                },
            )
            connection.commit()
