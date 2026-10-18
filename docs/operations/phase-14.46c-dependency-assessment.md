# Spring Security dependency readiness

Static local metadata identifies Spring Boot 3.5.16, Java/compiler release 21 and Maven wrapper distribution 3.9.11. Preserve root dependency management. No Maven command was run.

| Candidate | Managed version | Scope | Local POM/JAR |
|---|---|---|---|
| spring-boot-starter-security | 3.5.16 | compile | unavailable |
| spring-boot-starter-oauth2-client | 3.5.16 | compile | unavailable |
| spring-security-test | 6.5.11 | test | unavailable |

The Boot BOM imports Security BOM 6.5.11. Exact metadata paths and raw hashes are in dependency-readiness.json. These are managed versions, not an executed dependency resolution. The default local cache lacks the candidate POM/JARs and inspected Security core/web/config/oauth2-core/client/jose JARs; archive hashes remain null. Alternate cache locations and credential-bearing Maven settings were not read.

The module declares web, validation, JDBC, actuator, Redis, HTTP client, Flyway, PostgreSQL, Jackson and test dependencies, but no Security/OAuth2 starter. Candidate transitive components include Security core/config/web and OAuth2/OIDC/JOSE support. Exact transitive versions, archive/plugin closure and vulnerability assessment remain incomplete without missing metadata. BOM presence does not prove offline readiness.

Next step: separately approve acquisition of a complete offline dependency/plugin closure with provenance and archive hashes, then qualify the runner. No acquisition is authorized here. The exact POM proposal uses BOM management without individual versions or a Boot upgrade.
