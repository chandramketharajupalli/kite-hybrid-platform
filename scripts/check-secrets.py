"""Heuristic source scan. Reports locations only, never matching contents."""
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {".git", ".gradle", ".venv", "build", "target", "__pycache__", ".pytest_cache",
            ".ruff_cache", ".mypy_cache", ".uv-cache"}
PATTERNS = [
    re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    re.compile(r"""(?i)(?:kite_api_key|kite_api_secret|kite_access_token|kite_token_encryption_key|db_password|postgres_password|redis_password|rediscli_auth)\s*[:=]\s*["']?([A-Za-z0-9/+_-]{12,})"""),
]


def ignored_local_environment(path: Path) -> bool:
    """Allow local env files only when Git ignores them and they are untracked."""
    if path.name == ".env.example" or not (
        path.name == ".env" or path.name.startswith(".env.")
    ):
        return False
    result = subprocess.run(
        ["git", "check-ignore", "--quiet", "--", str(path.relative_to(ROOT))],
        cwd=ROOT,
        check=False,
    )
    if result.returncode not in (0, 1):
        raise SystemExit("Unable to verify whether a local environment file is ignored.")
    return result.returncode == 0


findings = []
count = 0
for path in sorted(ROOT.rglob("*")):
    if not path.is_file() or any(part in EXCLUDED for part in path.relative_to(ROOT).parts):
        continue
    if path.suffix in {".jar", ".pyc", ".png", ".zip"}:
        continue
    if ignored_local_environment(path):
        continue
    try:
        content = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    count += 1
    for number, line in enumerate(content.splitlines(), 1):
        if any(pattern.search(line) for pattern in PATTERNS):
            findings.append((path.relative_to(ROOT), number))
for path, number in findings:
    print(f"potential secret detected: {path}:{number}; remove and rotate if real")
print(f"Scanned {count} text files; potential secret locations: {len(findings)}")
raise SystemExit(1 if findings else 0)
