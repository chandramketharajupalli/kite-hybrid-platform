# Partial artifact allowlist

Phase1446HArtifactAllowlist.v1 contains exactly three managed candidate roots:
- org.springframework.boot:spring-boot-starter-security:3.5.16, jar/default classifier, compile.
- org.springframework.boot:spring-boot-starter-oauth2-client:3.5.16, jar/default classifier, compile.
- org.springframework.security:spring-security-test:6.5.11, jar/default classifier, test.

Each root has two required files: POM and JAR. Six known root-file requests are review scope only, not a request budget or executable acquisition allowlist. Parent/BOM source identities and hashes bind the versions.

All root POMs/JARs remain missing. Expected/actual archive hashes, sizes, independently approved origin and license are null. Required transitives and full transitive allowlist remain null. Complete=false, executable=false, status=PARTIAL. Missing transitive metadata prevents exact full closure.

No wildcards, ranges, LATEST, snapshots, implicit transitives or scope expansion are permitted. Complete dependency and plugin closure requires separate exact evidence before any executable acquisition approval.
