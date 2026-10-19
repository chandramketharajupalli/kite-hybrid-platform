# Exact sealed metadata allowlist

- https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-security/3.5.16/spring-boot-starter-security-3.5.16.pom
- https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-oauth2-client/3.5.16/spring-boot-starter-oauth2-client-3.5.16.pom
- https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-test/6.5.11/spring-security-test-6.5.11.pom
Exactly three existing J POM URLs; release versions 3.5.16, 3.5.16 and 6.5.11. HTTPS origin, full paths and filenames are immutable. No wildcard, snapshot, JAR, extra path or alternate origin.
Canonical allowlist SHA256: 6d116c140e657d842c7f2546e8e47ce03c7b09055cb939709de6b30f5e1a7321
Field-name mapping only: J connection_timeout_seconds -> K connect_timeout_seconds; J http_method -> K method. Numeric values and semantics are unchanged. No metadata retrieval is performed or authorized.
