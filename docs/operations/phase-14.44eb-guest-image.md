# Phase1444EBGuestImageAttestation.v1

TRUST_UNVERIFIED. Guest OS identity, image build/version/source, original image hash, independent provenance, architecture, required patches, supported provider, offline activation and licensing requirements remain null in isolation-assessment.json. Host OS metadata is not guest-image identity. No downloaded image, existing VM disk or Windows installation directory is treated as an approved image archive.

Before separately authorized provisioning, bind exact original image bytes, source/provenance evidence, license and offline activation rights, supported host/provider/guest matrix, patch baseline and security review. Verify boot/system/runtime disk identities independently. Reject modified/missing/unattested image, unsupported architecture/build, expired authority or insecure restore. Retain original hashes and approval, not just a filename or snapshot label.

No image download, conversion, mount, activation, patch installation, guest provisioning or destruction occurred. Snapshot support does not prove freshness: restoring a guest can restore approvals/ledger history. Require an independent latest checkpoint and new launch nonce; otherwise deny execution.
