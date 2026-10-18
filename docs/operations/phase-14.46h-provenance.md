# Publisher provenance requirements

Boot3.5.16 and Security BOM6.5.11 management bindings and Java21 are supported by unchanged local POM bytes. Reconciliation preserves28 artifacts,76 local file hashes,40 missing POM/JAR paths,43 present imported BOM POMs andthree missing roots.

A local SHA-256 identifies bytes; sidecar checksum consistency and central repository IDs do not establish publisher authenticity. POM-declared licenses do not authenticate missing archive licenses. Expected independent archive hashes, publisher identity, signature evidence, approved endpoint and root archive licenses remain null.

Before future acquisition/release require exact coordinates and types/classifiers, trusted parent/BOM bindings, approved repository identity/URL, metadata-origin evidence, independently established checksums or approved signed provenance policy, actual fetched hashes, licensing and immutable verification receipts. Any signature verification must use an independently approved trust root. Never establish expected hashes solely from the same untrusted payload source.

Graph remains PARTIAL with MISSING_METADATA, null required transitive edges and UNVERIFIED_PROVENANCE. UNRESOLVED_VERSION remains the classification for metadata unable to establish a version; known root versions are verified static management bindings, not a complete Maven resolution.
