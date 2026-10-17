"""Gate 31-A local audit and packet generation; no network or installation code."""

import importlib.metadata as md
import json
import platform
import subprocess
import sys
import tomllib
from pathlib import Path
from typing import Any

from packaging.markers import default_environment
from packaging.specifiers import SpecifierSet
from packaging.tags import sys_tags
from packaging.utils import canonicalize_name

from strategy_engine.phase1415.hardening import ROOT
from strategy_engine.phase1416.imports import canonical, external, sha
from strategy_engine.phase1429.provenance import audit
from strategy_engine.phase1430.assessment import graph
from strategy_engine.phase1431.artifacts import (
    HOST,
    LOCK_ONLY,
    VERSION,
    locked_closure,
    packet,
    quarantine,
    seal,
    select_wheel,
    validate_manifest,
)


def prepare() -> dict[str, Any]:
    project = ROOT / "apps/strategy-engine"
    spec = tomllib.loads((project / "pyproject.toml").read_text())
    lock = tomllib.loads((project / "uv.lock").read_text())
    env = {k: str(v) for k, v in default_environment().items()} | {"extra": ""}
    assert sys.platform == "win32" and platform.machine() == "AMD64"
    assert env["python_version"] == "3.12"
    tags = set(sys_tags())
    locked = locked_closure(spec, lock, env)
    installed = {str(canonicalize_name(d.metadata["Name"])): d for d in md.distributions()}
    actual = graph(spec["project"]["dependencies"], installed)
    assert [{k: r[k] for k in ("name", "version", "direct")} for r in locked] == actual
    provenance = audit()
    packages = {p["name"]: p for p in lock["package"]}
    rows = []
    for item in locked:
        name = item["name"]
        dist = installed[name]
        python = dist.metadata["Requires-Python"]
        assert python and env["python_full_version"] in SpecifierSet(python)
        selected = select_wheel(packages[name], tags)
        candidates = [
            c
            for c in provenance["candidates"]
            if c.get("name") == name
            and c.get("version") == item["version"]
            and c["filename"] == selected["filename"]
            and c.get("applicable")
        ]
        matches = [c for c in candidates if c["sha256"] == selected["expected_sha256"]]
        assert len(matches) <= 1, "DUPLICATE_LOCAL_ARTIFACT"
        row = (
            item
            | selected
            | dict(
                artifact_id=name + "==" + item["version"] + "/" + selected["filename"],
                source_host=HOST,
                source_status="CANDIDATE_NOT_CONTACTED_NOT_APPROVED",
                platform_marker="sys_platform == 'win32'" if name == "tzdata" else None,
                python_requires=python,
                python_requirement_source="INSTALLED_METADATA_RECORD_VERIFIED",
                groups=["runtime", "windows-runtime"] if name == "tzdata" else ["runtime"],
                expected_digest_source="apps/strategy-engine/uv.lock exact wheel entry",
                hash_provenance=LOCK_ONLY,
                independent_digest_attestation=False,
                digest_trusted_for_acquisition=False,
                local_availability="LOCK_HASH_MATCH" if matches else "MISSING",
                local_candidates=[c["location"] for c in candidates],
                acquisition_necessary=not bool(matches),
                verification_status="NOT_ACQUIRED_UNTRUSTED_EXPECTED_DIGEST",
                compatibility="CPYTHON_312_WINDOWS_AMD64_COMPATIBLE",
            )
        )
        rows.append(row)
    previous = json.loads(
        (ROOT / "research/phase-14.30/generation-1/evidence-manifest.json").read_bytes()
    )
    prior = json.loads(
        (ROOT / "research/phase-14.30/generation-1/runtime-assessment.json").read_bytes()
    )
    old_out = Path(previous["manifest"]["external_directory"])
    wheel = next((old_out / "build-a/wheel").glob("*.whl"))
    assert sha(wheel.read_bytes()) == prior["build"]["builds"][0]["wheel"]
    installer = list((Path(sys.base_prefix) / "Lib/ensurepip/_bundled").glob("*.whl"))
    installer_candidates = [
        dict(
            filename=p.name,
            sha256=sha(p.read_bytes()),
            source="LOCAL_PYTHON_ENSUREPIP_BUNDLE",
            provenance="UNVERIFIED",
            approved=False,
        )
        for p in installer
    ]
    body = dict(
        version=VERSION,
        scope="GATE_31_A_PREPARATION_ONLY",
        baseline=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip(),
        lock_sha256=sha((project / "uv.lock").read_bytes()),
        pyproject_sha256=sha((project / "pyproject.toml").read_bytes()),
        target=dict(
            python=env["python_full_version"],
            implementation="CPython",
            platform="win32",
            machine="AMD64",
            primary_tag=str(next(sys_tags())),
            project_python_constraint=spec["project"]["requires-python"],
        ),
        closure_status="LOCK_AND_INSTALLED_METADATA_AGREE",
        artifacts=rows,
        max_artifacts=len(rows),
        max_total_bytes=sum(r["expected_bytes"] for r in rows),
        source_hosts=[HOST],
        expected_lock_digest_coverage=len(rows),
        independent_trusted_digest_coverage=0,
        missing_artifact_count=sum(r["local_availability"] == "MISSING" for r in rows),
        original_archive_provenance="UNVERIFIED",
        acquisition_readiness="PARTIAL",
        acquisition_authorization="PENDING",
        installation_authorization="NOT_AUTHORIZED",
        clean_installation="NOT_ATTEMPTED",
        recommendation="OFFLINE_RC_CONDITIONAL",
        local_package=dict(
            filename=wheel.name,
            sha256=sha(wheel.read_bytes()),
            source="SEALED_PHASE_14_30_BUILD_A",
            download_required=False,
            installation_authorized=False,
        ),
        installation_runtime_wheel_count=len(rows) + 1,
        installer_candidates=installer_candidates,
        installer_gate="SEPARATE_IDENTITY_PROVENANCE_AND_INSTALLATION_APPROVAL_REQUIRED",
        excluded_groups=["build", "test", "development", "unselected_optional_extras"],
        build_backend=spec["build-system"],
        dependency_scope_inventory=[
            {
                k: r[k]
                for k in ("name", "version", "scope", "installed_integrity", "archive_provenance")
            }
            for r in provenance["dependencies"]
        ],
        local_cache_inventory=provenance["candidates"],
        historical_provenance=dict(
            installed_RECORD_verified=39,
            missing_originals=38,
            original_matches=0,
            independent_registry_attestation=False,
        ),
        registry_candidates_evaluated_offline=True,
        internal_repository=None,
        owner_approval=None,
        real_kite_requests=0,
        downloads=0,
        installations=0,
        Gate_A="NOT_AUTHORIZED",
        Gate_B="NOT_AUTHORIZED",
        operational="NO_GO",
    )
    envelope = seal(body)
    validate_manifest(envelope, tags)
    destination = ROOT.parent / "offline-artifact-quarantine/phase-14.31" / envelope["fingerprint"]
    request = packet(envelope, quarantine(destination, ROOT))
    return dict(acquisition=envelope, authorization=seal(request))


if __name__ == "__main__":
    out = external(Path(sys.argv[1]))
    result = prepare()
    assert out.is_dir()
    for name, key in (
        ("artifact-acquisition-manifest.json", "acquisition"),
        ("acquisition-authorization.json", "authorization"),
    ):
        path = out / name
        assert not path.exists(), "OUTPUT_EXISTS"
        path.write_bytes(canonical(result[key]) + b"\n")
    print(json.dumps({key: value["fingerprint"] for key, value in result.items()}, sort_keys=True))
