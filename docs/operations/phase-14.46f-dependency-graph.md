# Offline graph assessment

Boot3.5.16 parent/dependency BOM and Security6.5.11 BOM are available locally. Module -> repository parent -> Boot starter-parent -> Boot dependencies inheritance is source-bound; the Security BOM import property resolves to6.5.11. Repository Java21 overrides Boot parent Java17; compiler release resolves to21.

Top-level proposals, jar/default classifier:
- org.springframework.boot:spring-boot-starter-security:3.5.16, compile.
- org.springframework.boot:spring-boot-starter-oauth2-client:3.5.16, compile.
- org.springframework.security:spring-security-test:6.5.11, test.

Versions are verified static management bindings, not executed Maven resolution. All three candidate POMs are missing: required transitive edges remain null/MISSING_METADATA. An absent edge set is not proof of no dependencies. Graph PARTIAL, never COMPLETE.

18 Security management entries and 43 Boot BOM imports are recorded. Management entries do not imply runtime reachability. Security management is expanded; other imports are indexed only. Parent/import declarations are VERIFIED source facts; effective resolution/plugin closure remain PARTIAL and provenance UNVERIFIED_PROVENANCE.

Unknown properties become null/UNRESOLVED_VERSION; cycles reject resolution. Preserve optional and exclusion declarations; exclusions are path-specific, optional transitives need explicit inclusion, compile/test scope differs and type/classifier belongs to identity. Nearest-depth then declaration-order mediation requires complete paths. Missing paths prevent conflict resolution. Profiles/effective model remain unexecuted. No network/Maven.
