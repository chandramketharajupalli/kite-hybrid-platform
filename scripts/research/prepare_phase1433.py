"""Read-only preparation: verify retained index custody and draft pending acquisition."""

import json
from pathlib import Path
from typing import Any

from strategy_engine.phase1416.imports import canonical, sha
from strategy_engine.phase1432.attestation import Evidence, endpoint, evaluate
from strategy_engine.phase1433.acquisition import ATTESTATION, packet, quarantine_path

ROOT = Path(__file__).resolve().parents[2]


def custody() -> tuple[dict[str, Any], dict[str, Any], dict[str, str]]:
    acquisition = json.loads((ROOT / (
        "research/phase-14.31/generation-1/artifact-acquisition-manifest.json"
    )).read_bytes())
    prior_path = ROOT / "research/phase-14.32/generation-1/evidence-manifest.json"
    prior = json.loads(prior_path.read_bytes())
    external = Path(prior["manifest"]["external_directory"]) / "metadata-approved-w2s5s6tq"
    observation = json.loads((external / "attestation-result.json").read_bytes())
    assert sha(canonical(observation["manifest"])) == observation["fingerprint"] == (
        "1f3d426249500e275c8eae8c0327aade863961cf0da44a5c866083675d595c3b"
    )
    owner = json.loads((external / "approval.json").read_bytes())
    assert sha(canonical(owner["manifest"])) == owner["fingerprint"]
    assert owner["fingerprint"] == observation["manifest"]["approval_fingerprint"]
    assert owner["manifest"]["scope"] == "METADATA_ONLY"
    assert owner["manifest"]["decision"] == "APPROVED"
    assert owner["manifest"]["request_fingerprint"] == (
        "585905f4b586e0c566aabed68289c72cd31c10e8ed718c8f4299c6e9b00f9996"
    )
    request_log = json.loads((external / "request-log.json").read_bytes())
    assert len(request_log) == 8
    supplied = []
    trusted = {}
    total = 0
    for index, artifact in enumerate(acquisition["manifest"]["artifacts"], 1):
        path = external / f"{index}.transport.json"
        receipt = json.loads(path.read_bytes())
        recorded = observation["manifest"]["results"][index - 1]
        assert sha(path.read_bytes()) == recorded["transport_receipt_sha256"]
        assert receipt["url"] == request_log[index - 1]["url"] == endpoint(artifact)
        assert receipt["status"] == 200 and receipt["complete"]
        assert receipt["seconds"] < 20 and receipt["redirects"] == receipt["retries"] == 0
        assert receipt["tls_verification"] == "DEFAULT_CA_AND_HOSTNAME_VERIFICATION"
        raw = (external / f"{index}.raw.json").read_bytes()
        assert len(raw) == receipt["bytes"] <= 2097152
        assert sha(raw) == receipt["raw_sha256"] == recorded["raw_metadata_sha256"]
        total += len(raw)
        item = Evidence(
            artifact["artifact_id"], receipt["url"], receipt["observed_at"], raw, sha(raw)
        )
        supplied.append(item)
        trusted[sha(raw)] = (item.source_url, item.observed_at)
    assert total == 295536
    ledger = evaluate(acquisition, supplied, trusted)
    assert ledger == json.loads((external / "attestation-ledger.json").read_bytes())
    assert ledger["fingerprint"] == ATTESTATION
    evidence_hashes = {
        p.name: sha(p.read_bytes()) for p in sorted(external.iterdir()) if p.is_file()
    }
    return acquisition, ledger, evidence_hashes


def prepare() -> dict[str, Any]:
    acquisition, attestation, evidence_hashes = custody()
    proposed = (
        Path("D:/Yogendra/offline-artifact-quarantine/phase-14.33") / acquisition["fingerprint"]
    )
    destination = quarantine_path(proposed, ROOT, fresh=True)
    approval = packet(acquisition, attestation, destination)
    report = dict(
        version="Phase1433PreparationReport.v1", provenance="OFFLINE_PREPARATION_ONLY",
        acquisition_fingerprint=acquisition["fingerprint"],
        attestation_fingerprint=attestation["fingerprint"],
        approval_fingerprint=approval["fingerprint"],
        independent_metadata_matches=8, retained_metadata_bytes=295536,
        retained_custody_hashes=evidence_hashes, quarantine_destination=destination,
        quarantine_created=False, ownership_and_acl="REQUIRED_AFTER_APPROVAL_NOT_YET_APPLIED",
        acquisition_authorization="PENDING", acquisition_execution="NOT_AUTHORIZED",
        artifacts_downloaded=0, real_raw_archive_hashes_verified=0,
        installation_authorization="NOT_AUTHORIZED", clean_installation="NOT_ATTEMPTED",
        archive_provenance="UNVERIFIED_UNTIL_ACTUAL_ACQUISITION",
        classification="PHASE_14_33_ACQUISITION_READY_FOR_OWNER_DECISION",
        recommendation="OFFLINE_RC_CONDITIONAL", genuine_qualified_pairs=0,
        network_requests=0, real_kite_requests=0, Gate_A="NOT_AUTHORIZED", Gate_B="NOT_AUTHORIZED",
        Stage_B="ZERO_CALL_ABORT", Phase_12="BLOCK", H1_H2="FROZEN", July="SEALED",
        operational="NO_GO",
    )
    return {
        "acquisition-approval.json": approval,
        "preparation-report.json": {"manifest": report, "fingerprint": sha(canonical(report))},
    }


if __name__ == "__main__":
    print(json.dumps(prepare(), sort_keys=True))
