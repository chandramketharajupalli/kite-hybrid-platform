# Source-bound implementation proposals ? NOT APPLIED

All proposals require separate exact implementation approval and qualified runtime tests. Java API compatibility and compilation are NOT_EXECUTED. External proposal-bindings.json retains exact proposed texts/diffs and before/after hashes. No target file was written.

## Smallest bounded next increment

Add three BOM-managed dependencies, reuse TradingReadConfiguration from the eight-file proposal for a deny-only boundary, and propose one additional profile YAML. Protected reads remain disabled. The global deny fallback intentionally also blocks existing broker/diagnostic/actuator routes: this requires explicit behavior-change approval and regression tests, not silent deployment or automatic route exceptions.

The profile suppresses generated default-user configuration and strengthens cookies; it has no OAuth registration and is not activated here. Future adoption must load the approved profile atomically with the dependency/configuration change. Feature flags alone confer no authority. No Basic/form fallback or custom cryptography.

POM rollback requires a separately authorized restoration of the before-bytes; this phase performs none. New-file rollback removes only approved additions and invalidates sessions under approved authority. Never rewrite sealed evidence.

## Exact POM patch

Before SHA-256: fb41bf8741237991cb3edf7ffbeede0c014493eb90ccf7f2695843352129f87c

Proposed after SHA-256: 8648ed8f661c3dd990f93b813e8234b0bc015302c41cba892f71a4d8bcf51b02

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

## New-file proposal: apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingReadConfiguration.java

Absent target; proposed UTF-8/LF SHA-256: 458ea7d89c7300120aac31fbef72d5bfeb227bc6d6a02490f9db6f300d90ae07

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

## New-file proposal: apps/trading-core/src/main/resources/application-operator-security.yml

Absent target; proposed UTF-8/LF SHA-256: b6bfc0e2951809fc62a2c22df4362a99191d1d366270fe1af2428f54cdf08277

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

## Retained eight-file proposal

- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/application/TradingApiAuthorization.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/application/TradingProjectionService.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/domain/TradingProjection.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingReadConfiguration.java
- apps/trading-core/src/main/java/com/kitehybrid/platform/trading/infrastructure/TradingReadController.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingApiAuthorizationTest.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingProjectionTest.java
- apps/trading-core/src/test/java/com/kitehybrid/platform/TradingReadControllerTest.java

TradingApiAuthorization consumes a verified principal/permission port, not headers. TradingProjectionService/TradingProjection expose source-bound unavailable/read-only results. TradingReadController never invokes broker/order commands. Existing proposed tests cover authorization, projections and HTTP denial/status. No files are implemented.

The profile resource above is one new path beyond the original eight; POM modification is also outside that scope. Both need explicit future approval. Reuse TradingReadControllerTest for the minimum boundary tests; no extra test path is assumed approved.

## OIDC activation delta still blocked

After provider approval, separately freeze TradingReadConfiguration changes for explicit login/callback, session fixation, permission rules and approved principal mapping. Supply exact issuer/client/redirect through approved secret custody. Session absolute expiry, revocation and audit adapters may require further exact scope; do not silently add them.

Required future tests: wrong issuer/audience/state/nonce, expiry/revocation, unknown role, missing account/permission, backend outage, forged headers/broker tokens, CSRF, POST logout, fixation, cookie flags, redirects, feature disabled, unavailable catalogs, invalid pagination, no broker/order calls, no secret disclosure, schema compatibility and original status. Test all fallback routes and generated-user suppression. Synthetic fixtures only. Static patch checks do not certify HTTP behavior.
