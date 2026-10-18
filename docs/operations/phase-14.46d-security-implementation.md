# Source-bound security implementation proposals

No code or POM changes are authorized. The exact Phase 14.46C proposal bytes are retained unchanged in D custody and reconstructed in memory against current source. Before/after hashes below bind proposals only, not implementation or compilation.

The POM adds BOM-managed security/OAuth2-client dependencies and test-scoped security-test. The proposed TradingReadConfiguration reuses one of the original eight paths for a deny-only chain. The additional profile YAML lies outside those eight paths and needs separate future approval. A profile flag never conveys governance authority.

## apps/trading-core/pom.xml

Before SHA-256: fb41bf8741237991cb3edf7ffbeede0c014493eb90ccf7f2695843352129f87c

Proposed UTF-8 SHA-256: 8648ed8f661c3dd990f93b813e8234b0bc015302c41cba892f71a4d8bcf51b02

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

Proposed UTF-8 SHA-256: 458ea7d89c7300120aac31fbef72d5bfeb227bc6d6a02490f9db6f300d90ae07

~~~diff
--- /dev/null
+++ b/apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingReadConfiguration.java
@@ -0,0 +1,28 @@
+package com.kitehybrid.platform.trading.infrastructure;
+
+import org.springframework.context.annotation.Bean;
+import org.springframework.context.annotation.Configuration;
+import org.springframework.http.HttpMethod;
+import org.springframework.http.HttpStatus;
+import org.springframework.security.config.annotation.web.builders.HttpSecurity;
+import org.springframework.security.web.SecurityFilterChain;
+import org.springframework.security.web.authentication.HttpStatusEntryPoint;
+
+/** Proposed deny-only boundary; no operator login or protected reads enabled. */
+@Configuration(proxyBeanMethods = false)
+public class TradingReadConfiguration {
+    @Bean
+    SecurityFilterChain tradingBoundary(HttpSecurity http) throws Exception {
+        http.authorizeHttpRequests(rules -> rules
+                .requestMatchers(HttpMethod.GET, "/api/trading/status").permitAll()
+                .anyRequest().denyAll());
+        http.httpBasic(basic -> basic.disable());
+        http.formLogin(form -> form.disable());
+        http.logout(logout -> logout.disable());
+        http.requestCache(cache -> cache.disable());
+        http.exceptionHandling(errors -> errors
+                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
+        // Preserve framework CSRF; no state-changing route is allowed.
+        return http.build();
+    }
+}
~~~

## apps/trading-core/src/main/resources/application-operator-security.yml

Before SHA-256: None

Proposed UTF-8 SHA-256: b6bfc0e2951809fc62a2c22df4362a99191d1d366270fe1af2428f54cdf08277

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

## Scope, activation and rollback

Retained eight-file proposal:

- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/application/TradingApiAuthorization.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/application/TradingProjectionService.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/domain/TradingProjection.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingReadConfiguration.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingReadController.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingApiAuthorizationTest.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingProjectionTest.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingReadControllerTest.java

No target exists. Domain/application projection contracts must reuse InstrumentRegistry and verified metadata; absent qualification data remains unavailable. No broker login/download or Python execution to populate catalogs. Versioned schemas, source identity, bounded pagination and account-scoped permissions remain required.

The global deny-only chain would block broker/diagnostic/actuator HTTP routes as well as protected reads. It is not transparent compatibility; review route-compatibility.md before any approval. Do not apply the POM independently: framework auto-configuration can change exposure and generate default-user behavior. The proposed security profile suppresses default-user configuration but is not activated. Require atomic reviewed dependency/configuration adoption and qualified tests.

Future approved OIDC integration needs a distinct source-bound diff for login/callback, fixed redirect, issuer validation, framework state/nonce/signature/audience/expiry checks, allowlisted issuer+subject principal/permission mapping, server-side session fixation protection, Secure/HttpOnly cookies, reviewed SameSite behavior, bounded idle/absolute expiry, CSRF-protected POST logout and independent revocation. Unknown claims, permissions and backend failures deny. Never accept user-controlled identity headers or Kite tokens. No provider configuration is invented here.

Keep public status static/opt-in/no-store. API authentication errors must be bounded 401/403, not implicit redirects to unknown issuers. Audit must exclude secrets/tokens/raw claims. A future approval must cover operator-account lifecycle and trusted time separately from governance owner authorization.

Rollback requirements: separately authorized restoration of exact POM before-bytes and removal of only approved new files; invalidate introduced sessions under approved authority and preserve failed receipts. No rollback executed. Java proposals are not compiled or certified. Dependency acquisition, implementation, provider registration and runtime approval are independent gates.
