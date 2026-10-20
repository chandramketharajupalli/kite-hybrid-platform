package com.kitehybrid.platform.trading.infrastructure;

import com.kitehybrid.platform.trading.application.OperatorSessionLifecycle;
import com.kitehybrid.platform.trading.application.OperatorSessionRegistryPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

/** Development diagnostic lifecycle only. No broker or projection dependency and no Trading grant. */
@RestController
@Profile("development & entra-development & !production")
@ConditionalOnProperty(prefix = "operator.entra-development", name = "enabled", havingValue = "true")
@RequestMapping(EntraDevelopmentAuthenticationController.ROOT)
public final class EntraDevelopmentAuthenticationController {
    public static final String ROOT = "/api/development/operator-auth";
    private static final String HANDLE = EntraDevelopmentAuthenticationController.class.getName() + ".handle";
    private final EntraDevelopmentAuthenticationProperties settings;
    private final OperatorOidcAuthorizationRequestRepository requests;
    private final EntraOperatorAuthenticationAdapter adapter;
    private final OperatorSessionLifecycle lifecycle;
    private final HttpSessionSecurityContextRepository contexts;
    public EntraDevelopmentAuthenticationController(
            @Qualifier("entraDevelopmentPolicy") EntraDevelopmentAuthenticationProperties settings,
            OperatorOidcAuthorizationRequestRepository requests, EntraOperatorAuthenticationAdapter adapter,
            @Qualifier("entraDevelopmentRegistry") OperatorSessionRegistryPort registry,
            @Qualifier("entraDevelopmentClock") Clock clock, HttpSessionSecurityContextRepository contexts) {
        this.settings = settings; this.requests = requests; this.adapter = adapter;
        this.contexts = contexts; lifecycle = new OperatorSessionLifecycle(registry, clock, settings.localPolicy());
    }
    private void requireLocal(HttpServletRequest request) {
        URI expected = URI.create(settings.callback());
        if (!request.isSecure() || !"localhost".equals(request.getServerName())
                || request.getServerPort() != expected.getPort()
                || !List.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr())
                || request.getQueryString() != null && !request.getRequestURI().equals(ROOT + "/callback"))
            throw new IllegalArgumentException("Local request required");
        var names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement().toLowerCase(java.util.Locale.ROOT);
            if (name.equals("forwarded") || name.startsWith("x-forwarded-")) throw new IllegalArgumentException("Proxy denied");
        }
        var hosts = request.getHeaders("Host");
        if (hosts != null && hosts.hasMoreElements()) {
            if (!("localhost:" + expected.getPort()).equals(hosts.nextElement()) || hosts.hasMoreElements())
                throw new IllegalArgumentException("Host denied");
        }
        int sessionCookies = 0;
        if (request.getCookies() != null) for (var cookie : request.getCookies())
            if ("JSESSIONID".equals(cookie.getName())) sessionCookies++;
        if (sessionCookies > 1 || request.isRequestedSessionIdFromURL())
            throw new IllegalArgumentException("Ambiguous session binding");
    }
    @GetMapping(value = "/login", produces = "text/html")
    public ResponseEntity<String> page(HttpServletRequest request) {
        requireLocal(request);
        var token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) return safe(403).body("Unavailable");
        return safe(200).body("<!doctype html><title>Development operator sign-in</title><form method=\"post\" action=\""
                + ROOT + "/login\"><input type=\"hidden\" name=\"" + HtmlUtils.htmlEscape(token.getParameterName())
                + "\" value=\"" + HtmlUtils.htmlEscape(token.getToken()) + "\"><button>Sign in</button></form>");
    }
    @PostMapping("/login")
    public ResponseEntity<Void> begin(HttpServletRequest request) {
        requireLocal(request);
        return safe(303).location(URI.create(requests.begin(request).getAuthorizationRequestUri())).build();
    }
    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(HttpServletRequest request, HttpServletResponse response) {
        requireLocal(request);
        var result = adapter.authenticate(request); // Sole owner of state consumption and token exchange.
        if (result.status() != EntraOperatorAuthenticationAdapter.Status.DIAGNOSTIC_SESSION_ESTABLISHED) {
            String previous = handle(request);
            if (previous != null) lifecycle.logout(previous);
            clear(request, response);
            return safe(401).body(Map.of("code", "AUTHENTICATION_DENIED", "productionAuthorized", false));
        }
        request.getSession(false).setAttribute(HANDLE, result.sessionHandle());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("development-diagnostic", null, List.of()));
        contexts.saveContext(context, request, response);
        return safe(303).location(URI.create(ROOT + "/status")).build();
    }
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpServletRequest request) {
        requireLocal(request);
        String handle = handle(request);
        var result = lifecycle.assess(handle, "GET", endpoint(), settings.selectedAccount());
        return safe(result.diagnosticReady() ? 200 : 401).body(
                Map.of("diagnosticReady", result.diagnosticReady(), "productionAuthorized", false));
    }
    private String endpoint() {
        // Independent server-selected permission scope, not caller input.
        return switch (settings.permissions().stream().sorted().findFirst().orElseThrow()) {
            case INSTRUMENTS_READ -> "/api/trading/instruments";
            case STRATEGIES_READ -> "/api/trading/strategies";
            case QUALIFICATIONS_READ -> "/api/trading/qualifications";
            case SIGNALS_READ -> "/api/trading/signals";
            case POSITIONS_READ -> "/api/trading/positions";
            case ORDERS_READ -> "/api/trading/orders";
            case RISK_READ -> "/api/trading/risk/status";
        };
    }
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse response) {
        requireLocal(request);
        String handle = handle(request);
        boolean revoked = handle != null && lifecycle.logout(handle).diagnosticReady();
        clear(request, response);
        return safe(revoked ? 200 : 503).body(Map.of("localRevocationConfirmed", revoked, "productionAuthorized", false));
    }
    private static String handle(HttpServletRequest request) {
        var session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(HANDLE);
        return value instanceof String text ? text : null;
    }
    private void clear(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        contexts.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        response.addHeader("Set-Cookie", "JSESSIONID=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax");
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> unavailable() {
        return safe(503).body(Map.of("code", "AUTHENTICATION_UNAVAILABLE", "productionAuthorized", false));
    }
    private static ResponseEntity.BodyBuilder safe(int status) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").header("Pragma", "no-cache")
                .header("Referrer-Policy", "no-referrer").header("X-Content-Type-Options", "nosniff");
    }
}
