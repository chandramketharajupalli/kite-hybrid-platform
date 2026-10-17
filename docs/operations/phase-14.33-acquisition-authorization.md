# Phase 14.33 acquisition authorization request

Version Phase1433ArtifactAcquisitionApproval.v1. Decision PENDING; owner identity, owner evidence and issue/expiry timestamps are null. No artifact download is authorized. Packet fingerprint: d83acfa123546c39e707e50d3b726d0226c88cb7b909aa26be365169303c0562.

Bound manifest: 74b40b6723fd6bfc5401a48e76fd8e94f2cd68919b5c532fa4b271c7f59db65c.
Bound attestation: 4cd34fe05156683bde92483d246819ac63cccda5d2a8267bb422042d709f4a7f.

The packet binds all eight exact filenames, versions, wheel tags, HTTPS URLs, SHA-256 values and expected sizes from the frozen manifest. Sole artifact host: files.pythonhosted.org. The metadata host pypi.org is not an approved artifact host. No alternate mirrors or package index resolution. Maximum eight requests/eight artifacts, 3,029,164 aggregate bytes, exact expected per-artifact size, 20 seconds total per request. Redirects and retries are prohibited, including same-host redirects. No credentials or compressed response bodies.

Proposed destination: D:/Yogendra/offline-artifact-quarantine/phase-14.33/74b40b6723fd6bfc5401a48e76fd8e94f2cd68919b5c532fa4b271c7f59db65c. This directory has not been created. No existing directory may be reused. Owner-controlled custody and restrictive ACL verification must precede any write after authorization. The operator must also name a separate immutable receipt custody location before execution; the future executor must retain receipts there without adding them to the final eight-wheel inventory.

A separate owner decision must explicitly approve ACQUIRE_RAW_WHEELS_ONLY, bind this packet fingerprint and every scope field, identify the owner through approved decision evidence and set issue/expiry timestamps no more than 24 hours apart. The verifier accepts the decision only when its canonical hash appears in independently authenticated owner custody. Missing, stale, wrong-scope or altered approvals fail closed. Phase 14.32 metadata approval is insufficient.

Approval does not run a downloader. Future controlled execution must record one-use request consumption before retrieval and stop after the first discrepancy. This preparation does not implement or claim that durable execution control. Installation, environment creation, package execution, application execution and release acceptance need separate decisions. Phase 14.34 installation preparation follows only after all eight real archives have verified acquisition receipts.

Exact allowlist:

- annotated_types-0.8.0-py3-none-any.whl | 13427 bytes | f072f4d804ea359e4eaf198b1af7a8b0943881a87f31bb764f8bf219bb9419e0
  https://files.pythonhosted.org/packages/99/91/8acff4f5e50511b911bbccb72b8628a49c68ce14148cd9f6431094859a90/annotated_types-0.8.0-py3-none-any.whl
- pydantic-2.13.5-py3-none-any.whl | 472589 bytes | 346a034f080da3755d8e9cb5e00e8b07de1d39e4f6e2c87d8ab7cafa0b269a73
  https://files.pythonhosted.org/packages/eb/47/c95ffc2009878c7aac0c5e08528022dcb885933252a88b5f170058014464/pydantic-2.13.5-py3-none-any.whl
- pydantic_core-2.46.5-cp312-cp312-win_amd64.whl | 2043140 bytes | 5e81740c09e310f5aa5cbd3e434a01c154d4bef93241c7877b39f211d2b78ba8
  https://files.pythonhosted.org/packages/cf/c9/4c19f41b84cf6b622a72fbeed7665b25d47a187d68d47d0d430c07f23268/pydantic_core-2.46.5-cp312-cp312-win_amd64.whl
- pydantic_settings-2.15.0-py3-none-any.whl | 69413 bytes | 0ba092c291c94baceb5eff768aa0d56400a457585bc0175925a5a5510303da42
  https://files.pythonhosted.org/packages/30/a4/2bffa9f8e804325a09867f0e9d30795c80ea9f8d62560bd1b6ad6220eb2f/pydantic_settings-2.15.0-py3-none-any.whl
- python_dotenv-1.2.3-py3-none-any.whl | 22780 bytes | 904552145e8bfed22162c09dab1c2b9b54fefa7b23ba780f4f26ca0316b0f0d9
  https://files.pythonhosted.org/packages/0d/17/c5c6b53ddc18f297992099b3d9ec16c855c0ccc83263a21fe4d1c625ec6c/python_dotenv-1.2.3-py3-none-any.whl
- typing_extensions-4.16.0-py3-none-any.whl | 45571 bytes | 481caa481374e813c1b176ada14e97f1f67a4539ce9cfeb3f350d78d6370c2e8
  https://files.pythonhosted.org/packages/49/d3/b8441a820a491ddfc024b0b0cf0393375b75ea13866d9c66727e54c2fc80/typing_extensions-4.16.0-py3-none-any.whl
- typing_inspection-0.4.4-py3-none-any.whl | 14750 bytes | 65b8397ba37ccbce054456aaccddfc91e6e3083c92824df348d96ca832f3f147
  https://files.pythonhosted.org/packages/67/81/4add07e5172b7ac40d8ed5ff580409a7801a4fe26d529bdd915401dabfbe/typing_inspection-0.4.4-py3-none-any.whl
- tzdata-2026.4-py2.py3-none-any.whl | 347494 bytes | c2169a8b0a7a5e9674da5a135ccdfb2b3e671b333ed9fed17b41f73c34476e81
  https://files.pythonhosted.org/packages/f9/bc/8737e8d54cf51106118039b83f485a4783112fab49ea9d044b234978a46e/tzdata-2026.4-py2.py3-none-any.whl

PREPARATION_ONLY. Acquisition PENDING and NOT_AUTHORIZED; installation NOT_AUTHORIZED; clean installation NOT_ATTEMPTED. Zero artifacts downloaded and zero real raw archive hashes verified. OFFLINE_RC_CONDITIONAL. Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase 12 BLOCK; H1/H2 FROZEN; July SEALED; genuine qualified pairs zero; operational NO_GO.
