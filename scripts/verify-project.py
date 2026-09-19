"""Check required artifacts and fail-closed configuration without external services."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
required = [
    "AGENTS.md", "README.md", ".gitignore", ".env.example", "docker-compose.yml",
    "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties", "apps/strategy-engine/uv.lock",
    "contracts/schemas/v1/envelope.schema.json", "contracts/schemas/v1/SignalEvent.v1.schema.json",
    "apps/trading-core/src/main/resources/db/migration/V1__platform_baseline.sql",
    "docs/architecture/system-overview.md",
]
for name in required:
    assert (ROOT / name).is_file(), name
# Preserve all ten Phase 1 decisions while permitting later additive ADRs.
for number in range(1, 11):
    assert len(list((ROOT / "docs/adr").glob(f"ADR-{number:03d}-*.md"))) == 1
for profile in ("development", "test", "paper", "production"):
    assert (ROOT / f"apps/trading-core/src/main/resources/application-{profile}.yml").is_file()
config = (ROOT / "apps/trading-core/src/main/resources/application.yml").read_text()
example = (ROOT / ".env.example").read_text()
for token in ("TRADING_MODE:PAPER", "ENABLE_LIVE_TRADING:false", "EMERGENCY_STOP:true"):
    assert token in config, token
for line in ("TRADING_MODE=PAPER", "ENABLE_LIVE_TRADING=false", "EMERGENCY_STOP=true"):
    assert line in example.splitlines(), line
for path in (ROOT / "contracts").rglob("*.json"):
    json.loads(path.read_text())
assert "org.gradle.java.installations.auto-download=false" in (ROOT / "gradle.properties").read_text()
print("Project artifacts, profile presence, safety defaults and JSON syntax verified.")
