package com.kitehybrid.platform.broker.infrastructure.kite;

import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationException;
import com.kitehybrid.platform.broker.application.auth.KiteAuthenticationUseCase;
import com.kitehybrid.platform.broker.application.auth.KiteLoginAttemptUseCase;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.kitehybrid.platform.broker.application.auth.KiteAuthenticationException.Code.*;

/** Thin delivery adapter; session exchange and startup continuation belong to the use case. */
@RestController
@RequestMapping("/api/broker/kite/auth")
public final class KiteAuthenticationController {
    private static final Logger LOG = LoggerFactory.getLogger(KiteAuthenticationController.class);
    private static final String NONCE_COOKIE = "KITE_LOGIN_NONCE";
    private static final String COOKIE_PATH = "/api/broker/kite/auth";
    private final KiteAuthenticationUseCase authentication;
    private final KiteAuthenticationProperties properties;
    private final KiteLoginAttemptUseCase loginAttempts;

    public KiteAuthenticationController(KiteAuthenticationUseCase authentication,
                                         KiteAuthenticationProperties properties, KiteLoginAttemptUseCase loginAttempts) {
        this.authentication = authentication;
        this.properties = properties;
        this.loginAttempts = loginAttempts;
    }

    @GetMapping("/status") public ResponseEntity<?> status() {
        return safe(200).body(authentication.status());
    }

    @GetMapping("/login") public ResponseEntity<?> login(HttpServletRequest request) {
        var status = authentication.status();
        if ("KITE_AUTH_UNAVAILABLE".equals(status.code()) || "KITE_INITIALIZATION_PENDING".equals(status.code()))
            status = authentication.restore();
        if (status.authenticated()) return safe(200).body(status);
        if ("KITE_AUTH_DISABLED".equals(status.code())) throw new KiteAuthenticationException(DISABLED);
        // Canonicalize before issuing the host-only nonce cookie; it is independent of JSESSIONID.
        URI callback = properties.redirectUri();
        if (!sameOrigin(request, callback)) {
            String login = callback.resolve(KiteAuthenticationUseCase.LOGIN_ENDPOINT).toASCIIString();
            return safe(302).header(HttpHeaders.LOCATION, login).build();
        }
        String state = loginAttempts.start();
        String url = authentication.loginUrl(state);
        return safe(302).header(HttpHeaders.LOCATION, url)
                .header(HttpHeaders.SET_COOKIE, nonceCookie(state, KiteLoginAttemptUseCase.LIFETIME)).build();
    }

    @GetMapping("/callback") public ResponseEntity<?> callback(
            @RequestParam(name = "request_token", required = false) String requestToken,
            @RequestParam(required = false) String status, @RequestParam(required = false) String action,
            @RequestParam(required = false) String type, @RequestParam(required = false) String state,
            HttpServletRequest request, HttpServletResponse response) {
        if (requestToken == null || !requestToken.matches("[A-Za-z0-9_-]{1,256}"))
            throw new KiteAuthenticationException(INVALID_CALLBACK);
        if (status != null && !"success".equalsIgnoreCase(status))
            throw new KiteAuthenticationException(LOGIN_REJECTED);
        if (action != null && !"login".equalsIgnoreCase(action))
            throw new KiteAuthenticationException(LOGIN_REJECTED);
        var validation = loginAttempts.validateAndConsume(singleParameter(request, "state", state), browserNonce(request));
        if (!validation.accepted()) {
            // Only local reason codes and booleans; never log the callback URL, nonce, digest or any cookie value.
            LOG.warn("Kite callback state validation failed: reason={}, statePresent={}, stateValid={}, "
                            + "browserNoncePresent={}, browserNonceValid={}, stateMatched={}, attemptConsumed={}",
                    validation.reason(), validation.statePresent(), validation.stateValid(),
                    validation.browserNoncePresent(), validation.browserNonceValid(),
                    validation.stateMatched(), validation.attemptConsumed());
            return safe(403).body(Map.of("broker", "KITE", "code", "KITE_CALLBACK_STATE_INVALID"));
        }
        // Consumption commits before exchange. Even if exchange fails or the process stops, this attempt cannot replay.
        response.addHeader(HttpHeaders.SET_COOKIE, nonceCookie("", Duration.ZERO));
        return safe(200).body(authentication.complete(requestToken, status, action, type));
    }

    @PostMapping("/reset") public ResponseEntity<?> reset(
            @RequestHeader(name = "X-Kite-Auth-Reset", required = false) String confirmation,
            HttpServletRequest request) {
        // A custom header prevents a cross-site HTML form from resetting local broker state.
        if (!"true".equals(confirmation)) return safe(403).body(Map.of("code", "KITE_RESET_HEADER_REQUIRED"));
        loginAttempts.cancel(browserNonce(request));
        var status = authentication.reset();
        return safe(200).header(HttpHeaders.SET_COOKIE, nonceCookie("", Duration.ZERO)).body(status);
    }

    @ExceptionHandler(KiteAuthenticationException.class)
    public ResponseEntity<?> failure(KiteAuthenticationException failure) {
        int code = switch (failure.code()) {
            case INVALID_CALLBACK -> 400;
            case LOGIN_REJECTED, AUTHENTICATION_FAILED -> 401;
            case REQUEST_TOKEN_ALREADY_USED -> 409;
            case EXCHANGE_FAILED -> 502;
            case DISABLED, CONFIGURATION, STORAGE_UNAVAILABLE, BROKER_UNAVAILABLE -> 503;
        };
        return safe(code).body(Map.of("broker", "KITE", "code", "KITE_" + failure.code().name(),
                "loginUrl", KiteAuthenticationUseCase.LOGIN_ENDPOINT));
    }

    private static ResponseEntity.BodyBuilder safe(int status) {
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache").header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff");
    }
    private static boolean sameOrigin(HttpServletRequest request, URI uri) {
        int port = uri.getPort() < 0 ? "https".equals(uri.getScheme()) ? 443 : 80 : uri.getPort();
        return uri.getScheme().equals(request.getScheme()) && uri.getHost().equals(request.getServerName())
                && port == request.getServerPort();
    }
    private String nonceCookie(String nonce, Duration lifetime) {
        return ResponseCookie.from(NONCE_COOKIE, nonce).path(COOKIE_PATH).httpOnly(true).sameSite("Lax")
                .secure("https".equals(properties.redirectUri().getScheme())).maxAge(lifetime).build().toString();
    }
    private static String browserNonce(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        String nonce = null;
        if (cookies != null) for (Cookie cookie : cookies) {
            if (!NONCE_COOKIE.equals(cookie.getName())) continue;
            if (nonce != null) return ""; // Reject ambiguous duplicate cookies instead of picking a value.
            nonce = cookie.getValue();
        }
        return nonce;
    }
    private static String singleParameter(HttpServletRequest request, String name, String value) {
        String[] values = request.getParameterValues(name);
        return values != null && values.length != 1 ? "" : value;
    }
}
