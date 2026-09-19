import json
from datetime import UTC
from decimal import Decimal
from pathlib import Path
from typing import Any

import pytest
from jsonschema import Draft202012Validator, FormatChecker
from pydantic import ValidationError
from referencing import Registry, Resource

from strategy_engine.domain import SignalEvent

CONTRACTS = Path(__file__).resolve().parents[3] / "contracts"


def validator() -> Draft202012Validator:
    registry: Registry[Any] = Registry()
    for path in (CONTRACTS / "schemas/v1").glob("*.json"):
        schema = json.loads(path.read_text(encoding="utf-8"))
        Draft202012Validator.check_schema(schema)
        registry = registry.with_resource(schema["$id"], Resource.from_contents(schema))
    schema = json.loads(
        (CONTRACTS / "schemas/v1/SignalEvent.v1.schema.json").read_text(encoding="utf-8")
    )
    return Draft202012Validator(schema, registry=registry, format_checker=FormatChecker())


def test_shared_fixture_round_trip() -> None:
    raw = (CONTRACTS / "fixtures/v1/signal.valid.json").read_text(encoding="utf-8")
    validator().validate(json.loads(raw))
    event = SignalEvent.model_validate_json(raw)
    assert event.payload.reference_price == Decimal("123.4500")
    assert event.event_timestamp.tzinfo == UTC
    encoded = event.to_wire_json()
    validator().validate(json.loads(encoded))
    assert json.loads(encoded) == json.loads(raw)


@pytest.mark.parametrize("path", sorted((CONTRACTS / "fixtures/v1").glob("*.invalid.json")))
def test_shared_invalid_fixtures(path: Path) -> None:
    raw = path.read_text(encoding="utf-8")
    assert list(validator().iter_errors(json.loads(raw)))
    with pytest.raises(ValidationError):
        SignalEvent.model_validate_json(raw)


def test_immutable_signal_and_optional_correlation() -> None:
    data = json.loads((CONTRACTS / "fixtures/v1/signal.valid.json").read_text())
    del data["correlation_id"]
    event = SignalEvent.model_validate(data)
    validator().validate(json.loads(event.to_wire_json()))
    with pytest.raises(ValidationError):
        event.payload.quantity = 12


def test_floating_quantity_token_is_not_coerced() -> None:
    raw = (CONTRACTS / "fixtures/v1/signal.valid.json").read_text()
    raw = raw.replace('"quantity": 10', '"quantity": 10.0')
    # Schema integer semantics cannot enforce lexical integer tokens; the model can.
    with pytest.raises(ValidationError):
        SignalEvent.model_validate_json(raw)
