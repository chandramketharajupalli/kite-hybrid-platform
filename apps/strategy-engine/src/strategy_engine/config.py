"""Research process configuration. This process cannot activate live execution."""
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="STRATEGY_", extra="forbid")
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR"] = "INFO"
