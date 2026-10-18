# Dependency closure assessment

Static local metadata: Boot 3.5.16 imports Security BOM 6.5.11; Java baseline21; Maven wrapper3.9.11. Direct candidates are spring-boot-starter-security:3.5.16, spring-boot-starter-oauth2-client:3.5.16 and spring-security-test:6.5.11 (test). These are managed candidate versions, not an executed dependency resolution.

Default-cache candidate POM/JAR files remain absent. Inspected Security core/web/config/oauth2-core/oauth2-client/oauth2-jose6.5.11 JARs remain absent. Two local BOM files are hash-bound in the packet. Their presence proves neither runnable framework nor complete closure.

Exact transitive inventory, plugins, repository origin, independent expected archive hashes, licenses and actual missing archive hashes remain null/unresolved. Known managed modules are explicitly labeled as incomplete leads, not a resolved dependency graph. No settings or credentials read; no alternate cache or remote metadata consulted. No network Maven resolution.

Future acquisition needs a complete reviewed artifact-and-plugin graph with exact versions, trusted source/checksum/license evidence, permitted destination, bounded network/storage/time/byte limits and no unreviewed transitive expansion. The official-style Maven Central URL is a proposal only, not a verified origin or approved network boundary. Missing facts prevent acquisition authorization and offline build qualification.
