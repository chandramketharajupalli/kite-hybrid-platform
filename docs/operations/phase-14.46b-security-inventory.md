# Existing security inventory

Exact source paths and raw SHA-256 values are in security-assessment.json and external source-inventory.json.

Root pom.xml specifies Spring Boot3.5.16 and Java21; module POM declares web, validation, JDBC, actuator, Redis and existing test dependencies. It declares no Spring Security or OAuth2 starter. No Maven dependency graph was executed, so resolved runtime classpath is UNVERIFIED. Default user Maven cache inspection found only spring-security-bom under org/springframework/security; security and oauth2-client starter directories absent. BOM metadata is not a runnable security framework or approved offline closure. Alternate repository locations were not inspected; no settings/credentials read.

No SecurityFilterChain, OncePerRequestFilter, HandlerInterceptor or @PreAuthorize occurrences were found in inspected application Java source. This is a bounded source search, not proof about external deployment middleware.

KiteAuthenticationController implements broker status/login/callback/reset. Its callback validates state and browser nonce, consumes attempts before token exchange, rejects duplicate parameters/cookies and returns bounded codes. Nonce cookie is HttpOnly, SameSite=Lax, Secure only for HTTPS callback, scoped to broker auth path. It is not an operator session. Reset's custom header is an anti-cross-site-form control, not operator identity. Existing broker GET login/callback can perform broker effects; they must never back new projection GETs.

OperatorConsoleApplication requires an explicit interactive terminal entry; TrustedOperatorConsole calls guarded operational services. Terminal presence and typed confirmation do not establish HTTP identity, a role registry or signed governance approval.

KiteTradingReadDiagnosticController is development-only/opt-in with loopback/Host/proxy restrictions and sanitized error responses. Its providers can call the broker. Do not reuse its routes/providers to satisfy no-broker projection requirements. Loopback is not operator authentication.

application.yml has loopback default server binding and HttpOnly/SameSite session settings. These do not create operator authentication or establish TLS/Secure-cookie deployment. TradingFoundationController is conditional, static, no-store metadata without account/broker dependencies. Preserve it.

No secrets or token values were read. Source constants and dependency metadata only were inspected.
