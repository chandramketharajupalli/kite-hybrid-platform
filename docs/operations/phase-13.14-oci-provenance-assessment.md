# Phase 13.14 OCI provenance assessment

Verdict: **OCI_SIGNATURE_NOT_VERIFIED**. Content identity is verified; publisher
signature authentication is not. No new image, signature tool or trust root acquired.

| Identity layer | Value / evidence |
|---|---|
| Repository | docker.io/library/eclipse-temurin |
| Platform | linux/amd64, freshly inspected |
| Platform manifest and current local ID | sha256:743b453cb8639d815a8251e128f7d8e88840c684e43fdbde1b1e79788437b5cf |
| Retained index | sha256:b468c3fc688b14450571494f588bd939378e7fd542ed5a73f8efc13f17872a87 |
| Retained config | sha256:5b87e2cf305796b0082d40718341f9b75c2754e9cf81d228262b6902cae4e0b1 |
| Source revision | 511f9356dc4d50932a0a5f8cfb0f87ed1aef4f07, 21/jdk/ubuntu/noble |
| Published build / historical Java observation | 21.0.12.1_1-jdk-noble / 21.0.12.1+1-LTS; not rerun |
| Current image size/layers | 684697895 bytes; five rootfs diff IDs match cached config |
| Acquisition | Historical Phase13.11 receipt/pull; current inspect is not a new acquisition |
| Signer, trusted root, verified signature time, revocation | NOT_VERIFIED; no invented identity/date/status |

All 13 retained official metadata receipts rehash against Phase13.13 registrations.
The platform/config/index chain and unsigned subject linkage are replayed offline.
The unsigned SLSA statement digest is
`f43f1db2c17065465ff78718b1ec21c978088eae1c71fa4b8614070b4ec93243`.
Its declared subject/build identity is supporting metadata, not a verified signer.
Retained SPDX2.3 SBOM digest
`9be2f81a4df1ee28bbab5bd3699ac01926ee397989e0258c15980db097425b62`
describes 203 packages historically; this is no current vulnerability clearance.

Fresh bounded reads: [Adoptium container guidance](https://adoptium.net/installation/containers)
links the official image; [GPG guidance](https://adoptium.net/installation/gpg-verification)
describes signatures alongside OpenJDK binaries. Neither establishes an authenticated
signature for the selected OCI platform manifest. Recipe GPG commands, HTTPS,
registry identity, digest pins and unrelated signed artifacts cannot substitute.
Host command discovery found no cosign, notation or gpg; no tool was installed.
The bounded review is not proof that no signature exists anywhere.

Closure requires publisher-authorized verification procedure, independently trusted
signer/root, exact subject digest and predicate/build binding, validity/revocation
policy, verification output and timestamp. Reject wrong subject, unsigned statements,
untrusted keys and invalid/expired claims. A different image needs separately scoped
approval and provenance review; host migration cannot close this supply-chain gap.
