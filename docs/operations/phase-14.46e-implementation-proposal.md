# Source-bound future implementation proposal

No patch is applied. Proposed exact bytes/hashes reside in restricted proposal-bindings.json. The existing POM before-hash binds the current source; new-file before-hashes are null because targets are absent. Reuse TradingReadConfiguration from the eight-file scope; the profile YAML is an additional future path requiring separate approval. No dependencies, source or configuration are installed.

## apps/trading-core/pom.xml

Before SHA-256: fb41bf8741237991cb3edf7ffbeede0c014493eb90ccf7f2695843352129f87c

Proposed SHA-256: 8648ed8f661c3dd990f93b813e8234b0bc015302c41cba892f71a4d8bcf51b02

~~~diff
--- a/apps/trading-core/pom.xml
+++ b/apps/trading-core/pom.xml
@@ -17,6 +17,19 @@
         <kite.diagnostic>profile</kite.diagnostic>
     </properties>
     <dependencies>
+        <dependency>
+            <groupId>org.springframework.boot</groupId>
+            <artifactId>spring-boot-starter-security</artifactId>
+        </dependency>
+        <dependency>
+            <groupId>org.springframework.boot</groupId>
+            <artifactId>spring-boot-starter-oauth2-client</artifactId>
+        </dependency>
+        <dependency>
+            <groupId>org.springframework.security</groupId>
+            <artifactId>spring-security-test</artifactId>
+            <scope>test</scope>
+        </dependency>
         <!-- Isolated equity read: explicitly disable HTTP retries and redirects. BOM-managed. -->
         <dependency>
             <groupId>org.apache.httpcomponents.client5</groupId>
~~~

## apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingReadConfiguration.java

Before SHA-256: None

Proposed SHA-256: 748449d5f225d0ac72cfc9fad3b6e6a7bbd7532dc2b12d9420afcf5e40a66fc8

~~~diff
--- /dev/null
+++ b/apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingReadConfiguration.java
@@ -0,0 +1,32 @@
+package com.kitehybrid.platform.trading.infrastructure;
+
+import org.springframework.context.annotation.Bean;
+import org.springframework.context.annotation.Configuration;
+import org.springframework.core.annotation.Order;
+import org.springframework.http.HttpMethod;
+import org.springframework.http.HttpStatus;
+import org.springframework.security.config.annotation.web.builders.HttpSecurity;
+import org.springframework.security.web.SecurityFilterChain;
+import org.springframework.security.web.authentication.HttpStatusEntryPoint;
+
+/** Proposed route-only inactive boundary. No operator identity provider exists. */
+@Configuration(proxyBeanMethods = false)
+public class TradingReadConfiguration {
+    @Bean
+    @Order(1)
+    SecurityFilterChain tradingBoundary(HttpSecurity http) throws Exception {
+        http.securityMatcher("/api/trading/**");
+        http.authorizeHttpRequests(rules -> rules
+                .requestMatchers(HttpMethod.GET, "/api/trading/status").permitAll()
+                .anyRequest().denyAll());
+        http.httpBasic(basic -> basic.disable());
+        http.formLogin(form -> form.disable());
+        http.logout(logout -> logout.disable());
+        http.requestCache(cache -> cache.disable());
+        http.exceptionHandling(errors -> errors
+                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
+        // CSRF stays enabled; no trading mutation or protected read is activated.
+        // No catch-all chain: unmatched routes retain their existing controls.
+        return http.build();
+    }
+}
~~~

## apps/trading-core/src/main/resources/application-operator-security.yml

Before SHA-256: None

Proposed SHA-256: b6bfc0e2951809fc62a2c22df4362a99191d1d366270fe1af2428f54cdf08277

~~~diff
--- /dev/null
+++ b/apps/trading-core/src/main/resources/application-operator-security.yml
@@ -0,0 +1,12 @@
+# Proposed profile only: no client registration and no secret.
+spring:
+  autoconfigure:
+    exclude:
+      - org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
+server:
+  servlet:
+    session:
+      cookie:
+        secure: true
+        http-only: true
+        same-site: lax
~~~

## Exact scope and staged integration

Retained eight-file proposal:

- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/application/TradingApiAuthorization.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/application/TradingProjectionService.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/domain/TradingProjection.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingReadConfiguration.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingReadController.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingApiAuthorizationTest.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingProjectionTest.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingReadControllerTest.java

The first future increment is route-scoped deny-only infrastructure plus pure projection/authorization ports; it remains NOT_AUTHORIZED. No verified principal source exists, so the proposed chain grants no protected read. The future principal adapter must consume only an approved framework-validated issuer+subject/role mapping, not headers or broker session. Do not invent the adapter or activate hasAuthority rules before provider and governance approval.

TradingProjectionService/TradingProjection must bind existing InstrumentRegistry and verified strategy/research source identities. Missing qualification data is unavailable, not zero; preserve separate methodology families. TradingReadController must never call broker/download/order services. Proposed tests reuse TradingApiAuthorizationTest, TradingProjectionTest and TradingReadControllerTest; add no unapproved paths.

Profile YAML disables generated-user auto-configuration and proposes secure cookie settings but is not activated. Do not apply the dependency change alone; Boot auto-configuration/management behavior could change before the reviewed boundary exists. Compile/API compatibility remain unverified.

Future OIDC/session/CSRF/principal configuration needs another exact source-bound diff once issuer, registration, role/account claims, session lifetimes and revocation are approved. Preserve CSRF on session state changes and use separate POST operator logout; broker state/cookie protection is distinct. No custom cryptography.

Rollback: only separately authorized restoration of the exact POM before-bytes and removal of approved new files, with session invalidation under approved authority and failed evidence retained. No rollback performed.

Future targeted runtime tests cover static status, anonymous/invalid/missing/wrong permission, expired/revoked sessions, CSRF, fixation, mutations, every preserved broker/diagnostic route, management exposure, unmatched/malformed paths, secrets and zero broker/order side effects. Require approved offline JDK/Maven/dependency identities and qualified process/filesystem/network/credential containment before any command. No Maven/JUnit or original46/18/31 execution here.
