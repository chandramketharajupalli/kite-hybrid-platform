# Metadata provenance requirements

Before future retrieval require independently approved repository identity and exact endpoint; verify TLS chain/hostname and record exact response URL, status, content type, size, raw SHA-256, checksum/signature comparison where available, independent publisher-provenance evidence, approved-clock timestamp and durable receipt. HTTPS alone and same-origin checksums do not prove publisher authenticity.

Treat remote XML solely as untrusted data. Reject DTD/external entities, unexpected types, redirects or executable archives. Apply bounded byte/decompression/parser-depth/entity policies. Never execute instructions embedded in metadata. Quarantine unexpected content; retain failed-run receipts.

Existing evidence reconciles Boot3.5.16, Security6.5.11 and Java21;28 relevant artifacts,76 file hashes,40 missing paths,43 present imported BOM POMs andthree missing roots. Graph PARTIAL, required transitive edges null, provenance UNVERIFIED. No publisher identity, signature, expected checksum or trusted timestamp is invented.
