# Phase 14.34A installer provenance and decisions

Baseline: c33ce219344b81bc93cf31c6e4afdea8b5a5507b. PREPARATION_ONLY.
Installer: pip-25.0.1-py3-none-any.whl, 1841526 bytes.
SHA-256: c46efd13b6aa8279f33f2864459c8ce587ea6a1a59ee20de055868d8f7688f7f.
438 wheel RECORD entries checked using static archive reads; no extraction or execution. This confirms internal consistency only. The installer matches the Phase 14.31 local-candidate digest; that historical candidate remains approved=false, provenance=UNVERIFIED.

Local Python: CPython 3.12.14 Windows AMD64, ensurepip declares pip 25.0.1. Interpreter and DLL raw hashes and ensurepip source hash are bound in the installation packet. No independent Python-distribution provenance or publisher authentication has been established. Presence in ensurepip is bundle identity, not independent provenance.

No pip 25.0.1 independent metadata in approved custody checked (Phase 14.32 metadata evidence and Phase 14.33B receipt custody). The eight runtime-wheel attestations do not cover pip. Their quarantine certificate and all raw bytes verify, total 3,029,164 bytes. Retained application wheel hash verifies at 80c55e74f63ef0406436bb5ac0aef9722ff9f17b80b3d692ebc2f5c82887c50a.

Metadata request fingerprint: cef4afc290f26d04d34b09af9b6faf6d72adb6d7f6ac6cc94460b435c14d23fe.
One GET to https://pypi.org/pypi/pip/25.0.1/json is proposed, not authorized. Maximum 2 MiB response, one request, 20 seconds, no redirects/retries, credentials or archive downloads. Preserve exact raw response and transport/approval receipts. Reject missing/duplicate file, identity/hash/size mismatch, yanked artifact or untrusted source. Do not fabricate observation times or metadata.

Installation packet fingerprint: 683a8ba453aa7514002c14b4b05c79bd4c8fe5852df8642dac7eaec98081206b.
Proposed destination: D:/Yogendra/offline-rc-installations/phase-14.34A/rc-01 (absent). Creation, installation and execution NOT_AUTHORIZED. Packet binds installer, application, eight wheels, runtime, certificate, limits and proposed commands. A verified attestation must be separately bound by a future owner decision; metadata approval is not installation approval. No environment is created here. Proposed commands are not evidence of successful install or runtime isolation.

Official index agreement, if obtained, establishes correspondence to index metadata, not a publisher signature or package safety. Python-distribution provenance remains separately limited. Before actual installation, review owner acceptance of these trust limits, exact runtime identity, installation filesystem controls and guarded execution. PRODUCTION_EGRESS_NOT_VERIFIED. Audit settings were excluded from prior ACL writes but not independently read back. Offline RC remains CONDITIONAL; genuine qualification blocked; Gate A/B NOT_AUTHORIZED; operational NO_GO.
