# Metadata-only proposal

Exactly three GET POM URLs, each with management source/hash binding:
- https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-security/3.5.16/spring-boot-starter-security-3.5.16.pom
- https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-oauth2-client/3.5.16/spring-boot-starter-oauth2-client-3.5.16.pom
- https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-test/6.5.11/spring-security-test-6.5.11.pom

Origins are proposed, not approved. Status PARTIAL; complete=false; executable=false. Parent and transitive metadata are unknown and may require separately authorized URLs. Pinned versions have no demonstrated need for maven-metadata.xml, so none is included.

Proposed acceptable media types:application/xml or text/xml, subject to future approved content policy; no remote response observed. Maximum object sizes and independent expected SHA-256 values remain null. JARs, executable archives, plugins, snapshots, wildcards, query strings, fragments, credentials, alternate ports, traversal/percent-encoding and dynamic redirects are excluded.
