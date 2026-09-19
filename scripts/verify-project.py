"""Check required artifacts and fail-closed configuration without external services."""
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
required = [
    "AGENTS.md", "README.md", ".gitignore", ".env.example", "docker-compose.yml",
    "pom.xml", "apps/trading-core/pom.xml", "mvnw", "mvnw.cmd",
    ".mvn/wrapper/maven-wrapper.properties", "apps/strategy-engine/uv.lock",
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

# Maven uses an existing JDK; compiler release and Enforcer reject other Java versions.
# No toolchain/JDK download is needed or configured.
namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
parent_pom = ET.parse(ROOT / "pom.xml").getroot()
ET.parse(ROOT / "apps/trading-core/pom.xml")
assert parent_pom.findtext("m:packaging", namespaces=namespace) == "pom"
assert parent_pom.findtext("m:modules/m:module", namespaces=namespace) == "apps/trading-core"
assert parent_pom.findtext("m:properties/m:java.version", namespaces=namespace) == "21"
assert parent_pom.findtext("m:properties/m:maven.compiler.release", namespaces=namespace) in (
    "21", "${java.version}"
)
assert parent_pom.findtext(
    ".//m:requireJavaVersion/m:version", namespaces=namespace
) == "[21,22)"
assert any(
    "${java.home}/bin/javac" in (element.text or "")
    for element in parent_pom.findall(".//m:requireFilesExist/m:files/m:file", namespace)
), "Enforcer must require an existing JDK compiler"
wrapper = dict(
    line.split("=", 1)
    for line in (ROOT / ".mvn/wrapper/maven-wrapper.properties").read_text().splitlines()
    if line.strip() and not line.lstrip().startswith("#") and "=" in line
)
assert wrapper.get("distributionType") == "only-script"
assert wrapper.get("distributionUrl", "").startswith("https://")
assert "apache-maven-3.9.11-bin.zip" in wrapper["distributionUrl"]
assert re.fullmatch(r"[0-9a-f]{64}", wrapper.get("distributionSha256Sum", ""))
print("Maven artifacts/JDK 21 requirements, profiles, safety defaults and JSON syntax verified.")
