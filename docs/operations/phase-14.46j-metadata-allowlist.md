# Exact three-POM scope

Copied from sealed I metadata-allowlist.json, raw SHA2563461af016b10457fa0db7ce0cb8f134ca95307e4262675bead97e2d7497efcd6:

- https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-security/3.5.16/spring-boot-starter-security-3.5.16.pom
- https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-oauth2-client/3.5.16/spring-boot-starter-oauth2-client-3.5.16.pom
- https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-test/6.5.11/spring-security-test-6.5.11.pom

No dynamic URL construction or discovery for retrieval. Requests must exactly match one of these three GET URLs, including path and pinned release version. No wildcard, snapshot, JAR, plugin, alternate host, query, fragment, credentials or mirror fallback. Exact relative paths and coordinates remain bound to I.

Full dependency metadata closure remains PARTIAL; parent/transitive requirements are unknown. No maven-metadata.xml or additional URL is implicitly allowed. Additional metadata requires a new exact approval. Effective retrieval remains NOT_AUTHORIZED.
