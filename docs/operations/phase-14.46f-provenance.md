# Provenance requirements

Raw hashes identify bytes, not publishers. Current status UNVERIFIED_PROVENANCE. Local repository IDs/checksum sidecars are unauthenticated claims. POM licenses are metadata declarations, not completed archive/legal review.

Require approved repository identity/HTTPS origin, exact coordinates/type/classifier, independently trusted expected SHA256 and size, approved signature/checksum trust policy, parent/BOM/POM/plugin integrity, license review and destination. No public snippets, cache presence or adjacent untrusted checksums establish authenticity.

Reject unapproved origins/mirrors/redirects, mutable versions, ranges and wildcard transitives. Missing independent hashes remain null. No keys, signatures, owner identity or approval timestamps are generated.
