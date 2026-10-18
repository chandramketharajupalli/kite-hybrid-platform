# Offline artifact-source assessment

52 relevant local POMs were inspected by raw hash: application/root POMs, recorded management/plugin POMs and imported BOMs. Source-assessment.json binds the inspection. Seven repository declarations were found, including staging/snapshot endpoints and unresolved property placeholders. They are raw declarations, not evaluated active Maven configuration, approved endpoints or verified download origins. Profiles, inheritance and Maven super-POM behavior were not resolved.

Candidate comparison:
- Maven Central: local origin sidecars say central. An ID alone does not bind a URL, authenticated publisher or approved source. Candidate URL remains null pending independent evidence.
- Spring repositories: no approved local Spring endpoint record found in inspected sources. URL and approved identity remain null; channel, access and release suitability require verification.
- Organization mirror: no approved mirror evidenced. Settings/server passwords were not read. URL/identity remain null.
- Existing local Maven cache: C:/Users/Chandram K/.m2/repository, read-only evidence only. Integrity hashes are available but required root POMs/JARs are missing; authenticity and closure remain unverified.
- Deferral: recommended until independently approved source, full allowlist, hashes and authority exist.

Remote candidates require HTTPS with verified TLS and endpoint ownership, approved exact release paths, redirect policy and publisher provenance. No candidate is approved or contacted. No effective repository selected. No network requests.
