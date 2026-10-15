"""Versioned exact-value risk policy canonicalization, independent of Decimal context."""

from collections.abc import Mapping
from decimal import Decimal
from typing import Any, Literal

from strategy_engine.phase1416.imports import canonical

Version = Literal["Phase1418RiskPolicyCanonical.v2"]
VERSION: Version = "Phase1418RiskPolicyCanonical.v2"


def normalize(value: Any) -> Any:
    """Typed nodes prevent collisions between Decimal values and user-supplied maps."""
    if value is None:
        return ["null"]
    if type(value) is bool:
        return ["bool", value]
    if type(value) is int:
        return ["int", str(value)]
    if type(value) is str:
        return ["str", value]
    if isinstance(value, Decimal):
        if not value.is_finite():
            raise ValueError("NONFINITE_POLICY_NUMBER")
        parts = value.as_tuple()
        digits = list(parts.digits)
        exponent = parts.exponent
        assert isinstance(exponent, int)
        if not any(digits):
            return ["decimal", 0, "0", 0]
        while digits[-1] == 0:
            digits.pop()
            exponent += 1
        return ["decimal", parts.sign, "".join(str(d) for d in digits), exponent]
    if isinstance(value, Mapping):
        if any(type(key) is not str for key in value):
            raise ValueError("POLICY_MAPPING_KEYS_MUST_BE_STRINGS")
        return ["map", [[key, normalize(value[key])] for key in sorted(value)]]
    if isinstance(value, (list, tuple)):
        return ["sequence", [normalize(item) for item in value]]
    raise ValueError("UNSUPPORTED_POLICY_VALUE:" + type(value).__name__)


def payload(value: Any, version: str) -> bytes:
    if version != VERSION:
        raise ValueError("UNSUPPORTED_POLICY_FINGERPRINT_VERSION")
    return canonical({"schema": version, "policy": normalize(value)})
