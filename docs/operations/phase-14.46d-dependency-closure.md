# Offline dependency closure

Reinspection confirms Boot 3.5.16, Security BOM 6.5.11, Java/compiler release 21 and Maven wrapper distribution 3.9.11. Exact BOM/source paths and SHA-256 values are retained in dependency-inspection.json in restricted custody. These are static managed versions, not Maven resolution.

| Artifact | Managed version | Local POM | Local JAR |
|---|---|---|---|
| spring-boot-starter-security | 3.5.16 | absent | absent |
| spring-boot-starter-oauth2-client | 3.5.16 | absent | absent |
| spring-security-test | 6.5.11 | absent | absent |

Inspected core/web/config/oauth2-core/oauth2-client/oauth2-jose 6.5.11 JARs are absent. Missing archive hashes remain null. Only default local cache metadata was inspected; no settings, credentials, alternate repository or remote service read. Existing module dependencies remain untouched.

Offline closure is UNVERIFIED. Starter POM absence prevents a complete transitive graph. Framework/test/plugin/archive provenance, licenses and vulnerability review remain required; BOM presence is not executable closure. Future acquisition approval must bind exact coordinates, sources, hashes, transitive/plugin inventory, destination and bounded network/cost policy. Separate implementation approval is required to change POM. No download or installation occurs.
