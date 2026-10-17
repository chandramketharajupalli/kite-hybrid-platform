# Phase 14.32 attestation trust boundary

The isolated phase1432 layer reuses Phase 14.31 manifest validation and Phase 14.16 canonical hashing. No trading, packaging, dependency, calendar or risk code changes. The exact Phase 14.31 manifest is pinned to 74b40b6723fd6bfc5401a48e76fd8e94f2cd68919b5c532fa4b271c7f59db65c.

Inputs are a sealed manifest, explicit raw metadata evidence and separately reviewed custody mapping. A mapping binds a raw SHA-256 to the exact official endpoint and observation time. A self-declared host field is insufficient; an empty custody map never yields a positive attestation. Authentication of that custody and owner approvals is an external review responsibility. No built-in network client, dependency installer, signature verifier or automatic approval is present.

The verifier accepts the official PyPI version-JSON shape (info and urls), an exact filename, normalized name, exact version, wheel type/tags, exact artifact URL, SHA-256, size and explicit yanked status. Duplicate JSON keys or evidence identities, altered bytes and unsupported schemas fail closed. Duplicate file records are ambiguous; missing exact files remain absent. Unknown status is never interpreted as not yanked. An upload timestamp is preserved where supplied and cannot follow observation time. Reordered evidence produces identical results.

Official index metadata agreement is distinct from independently authenticated publisher signatures, artifact safety, original archive verification, lockfile correspondence, installed RECORD integrity and filename/version observations. Synthetic fixture matches exercise the verifier only. No actual metadata observation has been supplied in this phase.

Static import guards and pure offline execution do not establish OS-level egress containment. PRODUCTION_EGRESS_NOT_VERIFIED. No independent custody, physical durability or multi-host control is claimed. Existing complete-authority rollback limitations remain unchanged.

All results concern offline preparation. No network requests, distribution downloads or installations occurred. Acquisition PENDING; installation NOT_AUTHORIZED; clean installation NOT_ATTEMPTED; OFFLINE_RC_CONDITIONAL. Gate A/B NOT_AUTHORIZED; Stage B ZERO_CALL_ABORT; Phase 12 BLOCK; H1/H2 FROZEN; July SEALED; genuine qualified pairs zero; operational NO_GO.
