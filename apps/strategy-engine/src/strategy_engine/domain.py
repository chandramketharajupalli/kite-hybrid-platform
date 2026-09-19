"""Immutable signal contracts. Quantity means whole instrument units, not lots."""
import re
from datetime import UTC, datetime
from decimal import Decimal
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, field_serializer, field_validator

UUID_PATTERN = r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
PRICE_PATTERN = r"^(0|[1-9][0-9]{0,11})(\.[0-9]{1,8})?$"
UTC_PATTERN = r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?Z$"
Identifier = Annotated[str, Field(strict=True, pattern=UUID_PATTERN)]
StrategyId = Annotated[str, Field(strict=True, pattern=r"^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}$")]


class SignalPayload(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")
    signal_id: Identifier
    strategy_id: StrategyId
    instrument_id: Identifier
    side: Literal["BUY", "SELL"]
    quantity: Annotated[int, Field(strict=True, ge=1, le=2_147_483_647)]
    reference_price: Decimal

    @field_validator("reference_price", mode="before")
    @classmethod
    def decimal_string(cls, value: object) -> Decimal:
        if not isinstance(value, str) or re.fullmatch(PRICE_PATTERN, value) is None:
            raise ValueError("reference_price must be an unsigned plain decimal string")
        return Decimal(value)

    @field_serializer("reference_price")
    def serialize_price(self, value: Decimal) -> str:
        return format(value, "f")


class SignalEvent(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")
    schema_version: Annotated[int, Field(strict=True, ge=1, le=1)]
    event_id: Identifier
    event_type: Literal["SignalEvent"]
    event_timestamp: datetime
    correlation_id: Identifier | None = None
    payload: SignalPayload

    @field_validator("correlation_id", mode="before")
    @classmethod
    def correlation_if_present(cls, value: object) -> object:
        if value is None:
            raise ValueError("omit correlation_id when absent; explicit null is invalid")
        return value

    @field_validator("event_timestamp", mode="before")
    @classmethod
    def utc_timestamp(cls, value: object) -> datetime:
        if not isinstance(value, str) or re.fullmatch(UTC_PATTERN, value) is None:
            raise ValueError("event_timestamp must be a UTC ISO timestamp ending in Z")
        return datetime.fromisoformat(value).astimezone(UTC)

    def to_wire_json(self) -> str:
        return self.model_dump_json(exclude_none=True)
